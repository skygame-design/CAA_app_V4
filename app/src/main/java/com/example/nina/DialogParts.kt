package com.example.nina

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.Locale

/* Éléments communs des fenêtres : titres et cartes de réglages, barres système masquées, texte prononcé, couleur, photo. */

@Composable
fun SettingsSectionTitle(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = Color(0xFF4A5D91), // Bleu principal
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
    )
}
/**
 * Les fenêtres plein écran (aide, création de picto…) ont leur propre fenêtre Android :
 * sans cela, la barre d'état et la barre de navigation réapparaissent quand elles s'ouvrent.
 */
@Composable
fun HideSystemBarsInDialog() {
    val view = androidx.compose.ui.platform.LocalView.current
    SideEffect {
        (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.let { hideSystemBars(it) }
    }
}
/**
 * Champ optionnel "Texte prononcé" : ce que Nina dit quand on touche le picto.
 * Vide = le texte du picto est prononcé.
 */
@Composable
fun SpokenTextField(value: String, label: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("Texte prononcé (optionnel)") },
        placeholder = { Text(label.ifBlank { "Ex. : J'ai besoin d'aller aux toilettes" }, color = Color.LightGray) },
        supportingText = { Text("Dit à voix haute quand on touche le picto. La phrase garde le texte du picto.") },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    )
}
@Composable
fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            content()
        }
    }
}
/**
 * Sélecteur de couleur personnalisé (HSV) pour une précision totale.
 */
@Composable
fun CustomColorPicker(currentColor: Long, imageUrl: String? = null, onColorChange: (Long) -> Unit) {
    val hsv = remember(currentColor) {
        FloatArray(3).apply { android.graphics.Color.colorToHSV(currentColor.toInt(), this) }
    }
    var hue by remember(currentColor) { mutableFloatStateOf(hsv[0]) }
    var saturation by remember(currentColor) { mutableFloatStateOf(hsv[1]) }
    var value by remember(currentColor) { mutableFloatStateOf(hsv[2]) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Grand rectangle d'aperçu
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(currentColor))
                .border(2.dp, Color.LightGray, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            val isDark = value < 0.6f && (saturation > 0.4f || value < 0.3f)
            
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text(
                    "Aperçu",
                    color = if (isDark) Color.White else Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Teinte
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Teinte", modifier = Modifier.width(80.dp), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
                        )
                    )
            ) {
                Slider(
                    value = hue,
                    onValueChange = {
                        hue = it
                        val newColor = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value))
                        onColorChange(newColor.toLong() and 0xFFFFFFFFL)
                    },
                    valueRange = 0f..360f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.Transparent,
                        inactiveTrackColor = Color.Transparent
                    )
                )
            }
            Text("${hue.toInt()}°", modifier = Modifier.width(45.dp), textAlign = TextAlign.End, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Luminosité
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Lumière", modifier = Modifier.width(80.dp), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            // Dégradé du noir vers la couleur vive à 100% de saturation et valeur
            val lumiGradient = Brush.horizontalGradient(
                listOf(Color.Black, Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f))))
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(lumiGradient)
            ) {
                Slider(
                    value = value,
                    onValueChange = {
                        value = it
                        val newColor = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value))
                        onColorChange(newColor.toLong() and 0xFFFFFFFFL)
                    },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.Transparent,
                        inactiveTrackColor = Color.Transparent
                    )
                )
            }
            Text("${(value * 100).toInt()}%", modifier = Modifier.width(45.dp), textAlign = TextAlign.End, fontSize = 12.sp)
        }
    }
}
/**
 * Image d'un picto à partir d'une photo prise avec la tablette, ou de la galerie.
 * L'image est réduite (1024 pixels au plus), redressée et enregistrée dans la tablette :
 * elle reste visible sans réseau, et part avec le fichier de sauvegarde partagé.
 */
@Composable
fun PhotoSourceButtons(onImage: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    // Fichier temporaire où l'appareil photo écrit la photo (dans le cache, partagé via le FileProvider)
    val tempFile = remember { java.io.File(context.cacheDir, "photo_prise.jpg") }
    val tempUri = remember { androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile) }

    fun store(uri: Uri, deleteAfter: Boolean) {
        busy = true
        scope.launch {
            val path = withContext(kotlinx.coroutines.Dispatchers.IO) {
                saveImageToInternalStorage(context, uri).also { if (deleteAfter) tempFile.delete() }
            }
            busy = false
            if (path != null) onImage(path)
            else android.widget.Toast.makeText(context, "Impossible d'enregistrer l'image", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) store(tempUri, deleteAfter = true) }
    // L'app déclare la caméra (commande oculaire) : Android exige alors l'autorisation avant d'ouvrir l'appareil photo
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) cameraLauncher.launch(tempUri)
        else android.widget.Toast.makeText(context, "Autorisez la caméra pour prendre une photo", android.widget.Toast.LENGTH_LONG).show()
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { store(it, deleteAfter = false) } }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = {
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            if (granted) cameraLauncher.launch(tempUri) else permissionLauncher.launch(android.Manifest.permission.CAMERA)
        }, enabled = !busy) {
            Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Prendre une photo")
        }
        OutlinedButton(onClick = { galleryLauncher.launch("image/*") }, enabled = !busy) {
            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Galerie")
        }
        if (busy) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
    }
}
