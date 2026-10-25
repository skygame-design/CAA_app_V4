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

/* Mode adulte : calcul d'accès, paramètres, calibration du tactile. */

/**
 * Dialogue de défi parental pour accéder au mode Admin.
 * Demande une addition simple pour vérifier que l'utilisateur est un adulte.
 */
@Composable
fun AdminChallengeDialog(onDismiss: () -> Unit, onSuccess: () -> Unit) {
    val num1 = remember { (1..10).random() }
    val num2 = remember { (1..5).random() }
    val expectedAnswer = num1 + num2
    var input by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    fun validateAndSubmit() {
        if (input.trim().toIntOrNull() == expectedAnswer) {
            onSuccess()
        } else {
            input = ""
            isError = true
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = Color(0xFFF0F0F5),
        title = {
            Text(
                stringResource(R.string.parental_lock_title),
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp
            )
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.parental_lock_instruction)); Spacer(
                modifier = Modifier.height(
                    16.dp
                )
            )
                Text(
                    "$num1 + $num2 = ?",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF4A5D91)
                ); Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = input,
                                    onValueChange = {
                    input = it
                    if (isError) isError = false
                },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = isError,
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                if (isError) {
                    Text(
                        stringResource(R.string.parental_lock_error),
                        color = Color.Red,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { validateAndSubmit() },
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD1D5E3))
            ) {
                Text(stringResource(R.string.btn_validate), color = Color(0xFF4A5D91))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel), color = Color(0xFF4A5D91))
            }
        }
    )
}
/**
 * Dialogue des paramètres globaux de l'application.
 * Divisé en 3 onglets : Apparence, Voix, et Tactile.
 */
@Composable
fun SettingsDialog(
    currentBg: Long,
    currentPitch: Float,
    currentRate: Float,
    currentDebounce: Long,
    currentCviMode: Boolean,
    currentAutoReturn: Boolean,
    onDismiss: () -> Unit,
    onSaveSettings: (Long, Float, Float, Long, Boolean, Boolean) -> Unit
) {
    var tempAutoReturn by remember { mutableStateOf(currentAutoReturn) }
    var selectedColor by remember { mutableLongStateOf(currentBg) }
    var tempPitch by remember { mutableFloatStateOf(currentPitch) }
    var tempRate by remember { mutableFloatStateOf(currentRate) }
    var tempDebounce by remember { mutableFloatStateOf(currentDebounce.toFloat() / 1000f) }
    var tempCviMode by remember { mutableStateOf(currentCviMode) }

    // Gestion des onglets
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("🎨 Apparence", "🗣️ Voix", "👆 Tactile")

    val suggestedColors = listOf(
        0xFFFAF3E0L, 0xFFE3F2FDL, 0xFFE8F5E9L, 0xFFFCE4ECL,
        0xFFF3E5F5L, 0xFFFFF3E0L, 0xFFF5F5F5L, 0xFFFFFFFFL
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = Color(0xFFF0F0F5),
        title = {
            Text("Paramètres", fontWeight = FontWeight.Bold, fontSize = 22.sp)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Barre d'onglets
                PrimaryTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = Color(0xFF4A5D91),
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title, fontSize = 12.sp, maxLines = 1) }
                        )
                    }
                }

                // Contenu défilant selon l'onglet sélectionné
                Box(modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                ) {
                    when (selectedTab) {
                        0 -> { // APPARENCE
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                                ) {
                                    Text("Contraste Élevé (CVI)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Switch(checked = tempCviMode, onCheckedChange = { tempCviMode = it })
                                }

                                Text("Couleur de fond :", fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        suggestedColors.take(4).forEach { colorVal ->
                                            Box(modifier = Modifier.weight(1f).aspectRatio(1.3f).clip(RoundedCornerShape(12.dp)).background(Color(colorVal))
                                                .border(if (selectedColor == colorVal) 3.dp else 1.dp, if (selectedColor == colorVal) Color(0xFF4A5D91) else Color.LightGray, RoundedCornerShape(12.dp))
                                                .clickable { selectedColor = colorVal })
                                        }
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        suggestedColors.drop(4).forEach { colorVal ->
                                            Box(modifier = Modifier.weight(1f).aspectRatio(1.3f).clip(RoundedCornerShape(12.dp)).background(Color(colorVal))
                                                .border(if (selectedColor == colorVal) 3.dp else 1.dp, if (selectedColor == colorVal) Color(0xFF4A5D91) else Color.LightGray, RoundedCornerShape(12.dp))
                                                .clickable { selectedColor = colorVal })
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                CustomColorPicker(selectedColor) { selectedColor = it }
                            }
                        }
                        1 -> { // VOIX
                            Column {
                                Text("Ajustement de la synthèse :", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Voix d'enfant", modifier = Modifier.width(100.dp), fontSize = 12.sp)
                                    Slider(value = tempPitch, onValueChange = { tempPitch = it }, valueRange = 0.5f..2.0f, modifier = Modifier.weight(1f))
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Vitesse", modifier = Modifier.width(100.dp), fontSize = 12.sp)
                                    Slider(value = tempRate, onValueChange = { tempRate = it }, valueRange = 0.3f..1.5f, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                        2 -> { // TACTILE
                            Column {
                                Text("Ajustements moteurs :", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Anti-tremblement", modifier = Modifier.width(100.dp), fontSize = 12.sp)
                                    Slider(value = tempDebounce, onValueChange = { tempDebounce = it }, valueRange = 0f..2f, modifier = Modifier.weight(1f))
                                    Text(String.format(Locale.US, "%.1fs", tempDebounce), modifier = Modifier.width(40.dp), textAlign = TextAlign.End, fontSize = 12.sp)
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Retour à l'accueil", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text("Après le choix d'un mot dans un dossier", fontSize = 12.sp, color = Color.Gray)
                                    }
                                    Switch(checked = tempAutoReturn, onCheckedChange = { tempAutoReturn = it })
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveSettings(selectedColor, tempPitch, tempRate, (tempDebounce * 1000).toLong(), tempCviMode, tempAutoReturn)
                    onDismiss()
                },
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD1D5E3))
            ) { Text("Enregistrer", color = Color(0xFF4A5D91)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler", color = Color(0xFF4A5D91)) }
        }
    )
}
/**
 * Jeu de calibration tactile (attraper la coccinelle).
 * Permet de mesurer l'imprécision tactile de l'utilisateur pour appliquer un offset compensatoire.
 */
@Composable
fun CalibrationTest(onFinished: (Float, Float) -> Unit) {
    val targets = remember { listOf(Offset(0.5f, 0.5f), Offset(0.2f, 0.2f), Offset(0.8f, 0.2f), Offset(0.2f, 0.8f), Offset(0.8f, 0.8f)) }
    var currentStep by remember { mutableIntStateOf(0) }
    val errorsX = remember { mutableStateListOf<Float>() }
    val errorsY = remember { mutableStateListOf<Float>() }
    val density = LocalDensity.current

    Box(modifier = Modifier.fillMaxSize().background(Color.White).pointerInput(Unit) {
        detectTapGestures { offset ->
            if (currentStep < targets.size) {
                val target = targets[currentStep]
                val targetPxX = target.x * size.width
                val targetPxY = target.y * size.height

                val errX = offset.x - targetPxX
                val errY = offset.y - targetPxY

                errorsX.add(errX)
                errorsY.add(errY)

                currentStep++
                if (currentStep >= targets.size) {
                    val avgX = errorsX.average().toFloat()
                    val avgY = errorsY.average().toFloat()
                    // On convertit les pixels en dp pour l'offset
                    with(density) {
                        onFinished(avgX.toDp().value, avgY.toDp().value)
                    }
                }
            }
        }
    }) {
        Text("Attrape la coccinelle", modifier = Modifier.align(Alignment.TopCenter).padding(32.dp), fontSize = 24.sp, fontWeight = FontWeight.Bold)
        // Affichage de la cible à une position relative
        Box(modifier = Modifier.fillMaxSize()) {
            if (currentStep < targets.size) {
                val target = targets[currentStep]
                Text("🐞", fontSize = 80.sp, modifier = Modifier.align(BiasAlignment(target.x * 2 - 1, target.y * 2 - 1)))
            }
        }
    }
}
