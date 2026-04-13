package com.example.nina

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.nina.ui.theme.NinaTheme
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.*
import kotlin.getValue
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.http.Headers
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.platform.LocalHapticFeedback
import android.media.MediaRecorder
import android.os.Build
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import android.Manifest
//import androidx.compose.material3.not
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue


// --- MODÈLE DE DONNÉES ---
class AudioRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null

    fun startRecording(fileName: String): String {
        val file = File(context.filesDir, "$fileName.3gp")
        try {
            recorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            println("ENREGISTREMENT DÉMARRÉ : ${file.absolutePath}")
        } catch (e: Exception) {
            println("ERREUR MICRO : ${e.message}") // C'est ici qu'on verra le vrai souci
            e.printStackTrace()
        }
        return file.absolutePath
    }

    fun stopRecording() {
        recorder?.apply {
            stop()
            release()
        }
        recorder = null
        // Vérification :
        val file = File(context.filesDir, "dernier_enregistrement.3gp") // Adapte au nom utilisé
        println("Fichier audio créé : ${file.exists()} à l'emplacement ${file.absolutePath}")
    }
}

// 1. Les modèles de données
data class ArasaacSearchResponse(val _id: Int)

data class OpenSymbolResponse(
    val name: String? = null,
    @SerializedName("image_url") // Indispensable pour OpenSymbols
    val imageUrl: String? = null
)

// 2. Les interfaces de services
interface ArasaacService {
    // On enlève le slash au début du GET pour qu'il s'ajoute bien à la baseUrl
    @GET("fr/search/{label}")
    suspend fun searchPictograms(@Path("label") label: String): List<ArasaacSearchResponse>
}



// 3. L'instance Retrofit unique avec les deux clients
// 3. L'instance Retrofit unique
object RetrofitInstance {
    private val client = okhttp3.OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    val arasaacApi: ArasaacService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.arasaac.org/api/pictograms/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ArasaacService::class.java)
    }
}

    // Service pour ARASAAC
    val arasaacApi: ArasaacService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.arasaac.org/api/pictograms/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ArasaacService::class.java)
    }




class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            println("Micro autorisé !")
        } else {
            println("Micro refusé...")
        }
    }
    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        requestPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)

        setContent {
            NinaTheme {
                val context = LocalContext.current
                val haptic = LocalHapticFeedback.current
                val boutons = remember { mutableStateListOf<AACCell>().apply { addAll(loadButtons()) } }
                val phrase = remember { mutableStateListOf<AACCell>() }
                val navigationStack = remember { mutableStateListOf<AACCell>() }

                var columnCount by remember { mutableIntStateOf(4) }
                var showAddDialog by remember { mutableStateOf(false) }
                var showEditDialog by remember { mutableStateOf(false) }
                var cellToEdit by remember { mutableStateOf<AACCell?>(null) }
                var isAdminMode: Boolean by remember { mutableStateOf(false) }
                var showMenu by remember { mutableStateOf(false) }

                val currentButtons = if (navigationStack.isEmpty()) boutons else navigationStack.last().content
                val stackSize = navigationStack.size

                val createDocLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
                    uri?.let {
                        context.contentResolver.openOutputStream(it)?.use { stream ->
                            val json = Gson().toJson(boutons.toList())
                            stream.write(json.toByteArray())
                        }
                    }
                }

                val openDocLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    uri?.let {
                        context.contentResolver.openInputStream(it)?.reader()?.use { reader ->
                            val json = reader.readText()
                            val type = object : TypeToken<List<AACCell>>() {}.type
                            val imported: List<AACCell> = Gson().fromJson(json, type)
                            boutons.clear()
                            boutons.addAll(imported)
                        }
                    }
                }

                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF5F7FA)) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {

                            // BARRE SUPÉRIEURE : LECTURE À GAUCHE, RETOUR À DROITE
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 1. BOUTON LECTURE (À GAUCHE)
                                IconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        speakFullSentence(phrase)
                                    },
                                    enabled = phrase.isNotEmpty(),
                                    modifier = Modifier
                                        .size(90.dp)
                                        .background(if (phrase.isNotEmpty()) Color(0xFF4CAF50) else Color(0xFFE0E0E0), CircleShape)
                                        .border(4.dp, Color.White, CircleShape)
                                ) {
                                    Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(60.dp))
                                }

                                // 2. BARRE DE PHRASE (CENTRE)
                                Box(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                                    SentenceBar(
                                        sentence = phrase,
                                        onClear = { phrase.clear() },
                                        haptic = haptic
                                    )
                                }

                                // 3. BOUTON RETOUR (À DROITE)
                                Box(modifier = Modifier.width(80.dp)) {
                                    if (navigationStack.isNotEmpty()) {
                                        IconButton(
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                navigationStack.removeAt(navigationStack.size - 1)
                                            },
                                            modifier = Modifier
                                                .size(70.dp)
                                                .background(Color.White, CircleShape)
                                                .border(3.dp, Color(0xFF6200EE), CircleShape)
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color(0xFF6200EE), modifier = Modifier.size(35.dp))
                                        }
                                    }
                                }
                            }

                            // GRILLE
                            Box(modifier = Modifier.fillMaxSize()) {
                                AnimatedContent(
                                    targetState = currentButtons,
                                    transitionSpec = {
                                        if (navigationStack.size >= stackSize) {
                                            (slideInHorizontally { it } + fadeIn()).togetherWith(slideOutHorizontally { -it } + fadeOut())
                                        } else {
                                            (slideInHorizontally { -it } + fadeIn()).togetherWith(slideOutHorizontally { it } + fadeOut())
                                        }.using(SizeTransform(clip = false))
                                    },
                                    label = ""
                                ) { targetButtons ->
                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(columnCount),
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        items(targetButtons) { cell ->
                                            AACCellButton(
                                                cell = cell,
                                                haptic = haptic,
                                                onClick = {
                                                    // --- LOGIQUE AUDIO ---
                                                    if (!cell.audioPath.isNullOrEmpty()) {
                                                        // On joue le son enregistré (Ta voix)
                                                        try {
                                                            val mp = android.media.MediaPlayer().apply {
                                                                setDataSource(cell.audioPath)
                                                                prepare()
                                                                start()
                                                                setOnCompletionListener { release() }
                                                            }
                                                        } catch (e: Exception) {
                                                            // Si le fichier audio a un souci, on bascule sur la voix robot par sécurité
                                                            speakText(cell.label)
                                                        }
                                                    } else {
                                                        // Pas de son enregistré ? On utilise le TTS classique
                                                        speakText(cell.label)
                                                    }

                                                    // --- LOGIQUE NAVIGATION / PHRASE ---
                                                    if (cell.isFolder) {
                                                        navigationStack.add(cell)
                                                    } else {
                                                        phrase.add(cell)
                                                    }
                                                },
                                                onLongClick = {
                                                    if (isAdminMode) {
                                                        cellToEdit = cell
                                                        showEditDialog = true
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // BARRE ADMIN
                        Column(
                            modifier = Modifier.width(100.dp).fillMaxHeight().padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            FloatingActionButton(
                                onClick = { isAdminMode = !isAdminMode; if(!isAdminMode) showMenu = false },
                                shape = CircleShape,
                                containerColor = if (isAdminMode) Color(0xFFFFEB3B) else Color(0xFF4CAF50)
                            ) { Icon(if (isAdminMode) Icons.Default.LockOpen else Icons.Default.Lock, null) }

                            if (isAdminMode) {
                                Box {
                                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(60.dp).background(Color.White, CircleShape).border(1.dp, Color.Gray, CircleShape))
                                    { Icon(Icons.Default.MoreVert, null) }

                                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                        DropdownMenuItem(text = { Text("➕ Ajouter") }, onClick = { showMenu = false; showAddDialog = true })
                                        HorizontalDivider()
                                        DropdownMenuItem(text = { Text("💾 Sauvegarder") }, onClick = { showMenu = false; createDocLauncher.launch("nina_aac.json") })
                                        DropdownMenuItem(text = { Text("📂 Restaurer") }, onClick = { showMenu = false; openDocLauncher.launch(arrayOf("application/json")) })
                                    }
                                }
                                Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
                                        IconButton(onClick = { if (columnCount < 8) columnCount++ }) { Icon(Icons.Default.KeyboardArrowUp, null) }
                                        Text("$columnCount", fontWeight = FontWeight.Black)
                                        IconButton(onClick = { if (columnCount > 1) columnCount-- }) { Icon(Icons.Default.KeyboardArrowDown, null) }
                                    }
                                }
                            }
                        }
                    }
                }

                if (showAddDialog) {
                    AddButtonDialog(onDismiss = { showAddDialog = false }, onConfirm = { nouveau ->
                        currentButtons.add(nouveau); saveButtons(boutons); showAddDialog = false
                    })
                }

                if (showEditDialog && cellToEdit != null) {
                    EditDialog(
                        cell = cellToEdit!!,
                        allButtons = boutons, // Liste racine pour trouver les dossiers
                        onDelete = {
                            currentButtons.remove(cellToEdit)
                            saveButtons(boutons)
                            showEditDialog = false
                        },
                        onRename = { new ->
                            val idx = currentButtons.indexOf(cellToEdit)
                            if (idx != -1) {
                                currentButtons[idx] = currentButtons[idx].copy(label = new)
                                saveButtons(boutons)
                            }
                            showEditDialog = false
                        },
                        onMoveInList = { dir ->
                            val idx = currentButtons.indexOf(cellToEdit)
                            val nIdx = idx + dir
                            if (nIdx in 0 until currentButtons.size) {
                                val item = currentButtons.removeAt(idx)
                                currentButtons.add(nIdx, item)
                                saveButtons(boutons)
                            }
                        },
                        onMoveToFolder = { targetFolder ->
                            // 1. On retire de l'endroit actuel
                            currentButtons.remove(cellToEdit)
                            // 2. On ajoute dans le contenu du dossier cible
                            targetFolder.content.add(cellToEdit!!)
                            // 3. On sauvegarde
                            saveButtons(boutons)
                            showEditDialog = false
                        },
                        onDismiss = { showEditDialog = false }
                    )
                }
            }
        }
    }

    override fun onInit(status: Int) { if (status == TextToSpeech.SUCCESS) { tts?.language = Locale.FRENCH; tts?.setSpeechRate(0.85f) } }
    private fun speakText(text: String) { tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null) }
    private fun speakFullSentence(sentence: List<AACCell>) {
        if (sentence.isNotEmpty()) tts?.speak(sentence.joinToString(" ") { it.label }, TextToSpeech.QUEUE_FLUSH, null, null)
    }
    private fun saveButtons(list: List<AACCell>) {
        val json = Gson().toJson(list)
        getSharedPreferences("nina_prefs", Context.MODE_PRIVATE).edit().putString("saved_buttons", json).apply()
    }
    private fun loadButtons(): List<AACCell> {
        val json = getSharedPreferences("nina_prefs", Context.MODE_PRIVATE).getString("saved_buttons", null)
        return if (json != null) try { Gson().fromJson(json, object : TypeToken<List<AACCell>>() {}.type) } catch (e: Exception) { emptyList() }
        else listOf(AACCell("Manger", 0xFFC8E6C9L, "https://static.arasaac.org/pictograms/2544/2544_300.png"))
    }
    override fun onDestroy() { tts?.shutdown(); super.onDestroy() }
}

@Composable

fun SentenceBar(sentence: SnapshotStateList<AACCell>, onClear: () -> Unit, haptic: androidx.compose.ui.hapticfeedback.HapticFeedback) {
    Card(
        modifier = Modifier.fillMaxWidth().height(100.dp),
        shape = RoundedCornerShape(50.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.weight(1f)) {
                if (sentence.isEmpty()) {
                    Text("Nina veut dire...", color = Color.LightGray, fontSize = 20.sp)
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Utilisation de itemsIndexed pour identifier la position exacte
                        itemsIndexed(sentence) { index, cell ->
                            AsyncImage(
                                model = cell.imageUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(75.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(1.dp, Color.LightGray, RoundedCornerShape(12.dp))
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        // On vérifie que l'index est toujours valide avant de supprimer
                                        if (index < sentence.size) {
                                            sentence.removeAt(index)
                                        }
                                    }
                            )
                        }
                    }
                }
            }

            // Bouton pour tout effacer (Corbeille)
            if (sentence.isNotEmpty()) {
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onClear()
                    },
                    modifier = Modifier
                        .size(55.dp)
                        .background(Color(0xFFFFEBEE), CircleShape)
                ) {
                    Icon(Icons.Default.Delete, null, tint = Color.Red)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AACCellButton(cell: AACCell, haptic: androidx.compose.ui.hapticfeedback.HapticFeedback, onClick: () -> Unit, onLongClick: () -> Unit) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isPressed) 0.94f else 1f)
    val folderShape = remember { FolderTabShape() }

    Box(modifier = Modifier.aspectRatio(1f).padding(if (cell.isFolder) 2.dp else 0.dp)) {
        Card(
            modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale)
                .combinedClickable(
                    onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() },
                    onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onLongClick() }
                ),
            shape = if (cell.isFolder) folderShape else RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(cell.colorLong)),
            elevation = CardDefaults.cardElevation(if (cell.isFolder) 6.dp else 2.dp),
            border = if (cell.isFolder) BorderStroke(2.dp, Color(0xFFC4A77D).copy(alpha = 0.6f)) else null
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(8.dp).padding(top = if(cell.isFolder) 12.dp else 0.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.7f)).padding(4.dp)) {
                    if (!cell.imageUrl.isNullOrEmpty()) AsyncImage(cell.imageUrl, null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    else if (cell.isFolder) Text("📂", fontSize = 40.sp, modifier = Modifier.align(Alignment.Center))
                }
                Text(cell.label.uppercase(), fontWeight = FontWeight.Black, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

class FolderTabShape(val tabWidthDp: Dp = 60.dp, val tabHeightDp: Dp = 12.dp, val cornerRadiusDp: Dp = 8.dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density): Outline {
        val tw = with(density) { tabWidthDp.toPx() }; val th = with(density) { tabHeightDp.toPx() }; val cr = with(density) { cornerRadiusDp.toPx() }
        val path = Path().apply {
            moveTo(0f, th + cr); quadraticTo(0f, th, cr, th)
            lineTo(size.width/2f - tw/2f - cr, th); quadraticTo(size.width/2f - tw/2f, th, size.width/2f - tw/2f, th/2f); quadraticTo(size.width/2f - tw/2f, 0f, size.width/2f - tw/2f + cr, 0f)
            lineTo(size.width/2f + tw/2f - cr, 0f); quadraticTo(size.width/2f + tw/2f, 0f, size.width/2f + tw/2f, th/2f); quadraticTo(size.width/2f + tw/2f, th, size.width/2f + tw/2f + cr, th)
            lineTo(size.width - cr, th); quadraticTo(size.width, th, size.width, th + cr)
            lineTo(size.width, size.height - cr); quadraticTo(size.width, size.height, size.width - cr, size.height)
            lineTo(cr, size.height); quadraticTo(0f, size.height, 0f, size.height - cr); close()
        }
        return Outline.Generic(path)
    }
}

@Composable
fun AddButtonDialog(onDismiss: () -> Unit, onConfirm: (AACCell) -> Unit) {
    val context = LocalContext.current
    var label by remember { mutableStateOf("") }
    var isFolder by remember { mutableStateOf(false) }
    var selectedUrl by remember { mutableStateOf<String?>(null) }
    var selectedColor by remember { mutableLongStateOf(0xFFFFFFFFL) }
    var isLoading by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    // États pour l'audio
    var audioPath by remember { mutableStateOf<String?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    val recorder = remember { AudioRecorder(context) }

    val scope = rememberCoroutineScope()
    val searchResults = remember { mutableStateListOf<OpenSymbolResponse>() }

    val photoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUrl = it.toString() }
    }

    val categories = listOf(
        "Sujet" to 0xFFFFF9C4L,
        "Action" to 0xFFC8E6C9L,
        "Objet" to 0xFFBBDEFBL,
        "Émotion" to 0xFFF8BBD0L,
        "Lieu" to 0xFFE1BEE7L,
        "Autre" to 0xFFFFFFFFL
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = Color(0xFFF0F0F5),
        confirmButton = {},
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Nouveau Bouton", fontSize = 24.sp, fontWeight = FontWeight.Bold)

                // 1. CHAMP DE SAISIE AVEC RECHERCHE ET PHOTO
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    placeholder = { Text("Nom du bouton") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        Row(modifier = Modifier.padding(end = 8.dp)) {
                            IconButton(onClick = { photoLauncher.launch("image/*") }) {
                                Icon(Icons.Default.AddAPhoto, contentDescription = "Photo", tint = Color(0xFF4A5D91))
                            }
                            IconButton(onClick = {
                                if (label.isBlank()) return@IconButton
                                scope.launch {
                                    isLoading = true
                                    searchResults.clear()
                                    try {
                                        val araResults = RetrofitInstance.arasaacApi.searchPictograms(label.trim().lowercase())
                                        araResults.forEach {
                                            searchResults.add(OpenSymbolResponse(
                                                name = "ARASAAC",
                                                imageUrl = "https://static.arasaac.org/pictograms/${it._id}/${it._id}_300.png"
                                            ))
                                        }
                                    } catch (e: Exception) { println("Erreur : ${e.message}") }
                                    finally { isLoading = false }
                                }
                            }) {
                                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                else Icon(Icons.Default.Search, contentDescription = "Chercher", tint = Color(0xFF4A5D91))
                            }
                        }
                    }
                )

                // 2. RÉSULTATS DE RECHERCHE ARASAAC
                if (searchResults.isNotEmpty()) {
                    Text("Résultats ARASAAC :", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(searchResults) { res ->
                            AsyncImage(
                                model = res.imageUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(85.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(
                                        if (selectedUrl == res.imageUrl) 4.dp else 1.dp,
                                        if (selectedUrl == res.imageUrl) Color(0xFF4A5D91) else Color.Gray,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { selectedUrl = res.imageUrl }
                            )
                        }
                    }
                }

                // 3. SECTION CATÉGORIES (COULEURS)
                Text("Catégorie :", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    categories.forEach { (name, color) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(45.dp)
                                    .clip(CircleShape)
                                    .background(Color(color))
                                    .border(if (selectedColor == color) 2.dp else 1.dp, Color.Black, CircleShape)
                                    .clickable { selectedColor = color }
                            )
                            Text(name, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }

                // 4. OPTION DOSSIER
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isFolder, onCheckedChange = { isFolder = it })
                    Text("Dossier 📂", fontSize = 16.sp)
                }

                // 5. ENREGISTREMENT VOCAL (NOUVEAU)
                Text("Voix personnalisée (restez appuyé) :", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // ON UTILISE BOX AU LIEU DE ICONBUTTON
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(if (isRecording) Color.Red else Color.Gray)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        try {
                                            val audioName = "audio_${System.currentTimeMillis()}"
                                            // On lance l'enregistrement
                                            audioPath = recorder.startRecording(audioName)
                                            isRecording = true

                                            awaitRelease() // Attend que le doigt se lève
                                        } finally {
                                            // S'exécute quand on lève le doigt
                                            recorder.stopRecording()
                                            isRecording = false
                                        }
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    Column {
                        Text(if (isRecording) "Enregistrement..." else "Maintenir pour enregistrer")
                        if (audioPath != null && !isRecording) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🎙️ Prêt !", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                                TextButton(onClick = { audioPath = null }) {
                                    Text("Effacer", color = Color.Red, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                // 6. APERÇU FINAL
                if (selectedUrl != null) {
                    AsyncImage(
                        model = selectedUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(100.dp)
                            .align(Alignment.CenterHorizontally)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(selectedColor).copy(alpha = 0.3f))
                            .padding(8.dp)
                    )
                }

                // 7. BOUTONS ACTIONS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Annuler", fontSize = 18.sp, color = Color(0xFF4A5D91))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(
                        onClick = {
                            isSaving = true
                            scope.launch(Dispatchers.IO) {
                                val finalUrl = if (selectedUrl?.startsWith("http") == true) {
                                    saveImageLocally(context, selectedUrl!!, "${label.trim()}_${System.currentTimeMillis()}")
                                } else {
                                    selectedUrl
                                }
                                withContext(Dispatchers.Main) {
                                    isSaving = false
                                    // ON AJOUTE audioPath ICI (le 4ème paramètre de AACCell)
                                    onConfirm(AACCell(label, selectedColor, finalUrl, audioPath, isFolder))
                                }
                            }
                        },
                        enabled = label.isNotEmpty() && selectedUrl != null && !isSaving,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD1D5E3))
                    ) {
                        if (isSaving) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                        else Text("Créer", fontSize = 18.sp, color = Color(0xFF4A5D91))
                    }
                }
            }
        }
    )
}

@Composable
fun EditDialog(
    cell: AACCell,
    allButtons: List<AACCell>, // On passe la liste globale pour trouver les dossiers
    onDelete: () -> Unit,
    onRename: (String) -> Unit,
    onMoveInList: (Int) -> Unit,
    onMoveToFolder: (AACCell) -> Unit, // Nouvelle action
    onDismiss: () -> Unit
) {
    var newName by remember { mutableStateOf(cell.label) }
    // On filtre pour ne proposer que les dossiers (et pas le bouton lui-même)
    val availableFolders = allButtons.filter { it.isFolder && it != cell }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = { onRename(newName) }) { Text("Enregistrer") }
        },
        dismissButton = {
            TextButton(onClick = onDelete) { Text("Supprimer", color = Color.Red) }
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                TextField(value = newName, onValueChange = { newName = it }, label = { Text("Nom") })

                Spacer(Modifier.height(16.dp))

                Text("Réorganiser dans la grille :", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(onClick = { onMoveInList(-1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null) }
                    Button(onClick = { onMoveInList(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }
                }

                if (availableFolders.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text("Déplacer vers un dossier :", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    LazyRow(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(availableFolders) { folder ->
                            Button(
                                onClick = { onMoveToFolder(folder) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(folder.colorLong))
                            ) {
                                Text("📂 ${folder.label}", color = Color.Black, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    )
}

// Fonction à ajouter dans ton MainActivity ou en dehors
fun saveImageLocally(context: Context, imageUrl: String, fileName: String): String? {
    return try {
        val file = File(context.filesDir, "$fileName.png")
        URL(imageUrl).openStream().use { input ->
            FileOutputStream(file).use { output ->
                input.copyTo(output)
            }
        }
        file.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}