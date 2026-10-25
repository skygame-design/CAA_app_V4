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

/* Recherche d'images dans les banques de pictogrammes (création et modification d'un picto). */

/**
 * Recherche d'images dans toutes les banques (ARASAAC, Mulberry, Sclera, Tawasol, Twemoji…),
 * avec un filtre par banque et la licence de l'image choisie (à respecter et créditer).
 * L'image choisie est téléchargée dans la tablette à l'enregistrement du picto.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PictoSearchPanel(query: String, selectedUrl: String?, onSelect: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var results by remember { mutableStateOf<List<PictoResult>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    var sourceFilter by remember { mutableStateOf<String?>(null) }
    val accent = Color(0xFF4A5D91)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = {
                if (query.isBlank() || isLoading) return@Button
                scope.launch {
                    isLoading = true; sourceFilter = null
                    results = PictoSearch.search(query)
                    isLoading = false; searched = true
                }
            },
            enabled = query.isNotBlank(),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = accent)
        ) {
            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
            else {
                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Chercher « ${query.trim().ifEmpty { "…" }} » dans les banques d'images", fontSize = 12.sp)
            }
        }

        if (searched && results.isEmpty()) {
            Text("Aucune image trouvée (ou pas de réseau). Essayez un autre mot, ou une photo de la galerie.", fontSize = 12.sp, color = Color.Gray)
        }

        if (results.isNotEmpty()) {
            // Filtre par banque, avec le nombre d'images
            val counts = results.groupingBy { it.source }.eachCount()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = sourceFilter == null, onClick = { sourceFilter = null }, label = { Text("Toutes (${results.size})") })
                counts.forEach { (source, count) ->
                    FilterChip(selected = sourceFilter == source, onClick = { sourceFilter = source }, label = { Text("$source ($count)") })
                }
            }
            val shown = results.filter { sourceFilter == null || it.source == sourceFilter }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(shown) { res ->
                    val selected = selectedUrl == res.imageUrl
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(76.dp)) {
                        AsyncImage(
                            model = res.imageUrl, contentDescription = res.source,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(70.dp).clip(RoundedCornerShape(8.dp)).background(Color.White)
                                .border(if (selected) 3.dp else 1.dp, if (selected) accent else Color.LightGray, RoundedCornerShape(8.dp))
                                .clickable { onSelect(res.imageUrl) }.padding(4.dp)
                        )
                        Text(res.source, fontSize = 10.sp, color = Color.Gray, maxLines = 1)
                    }
                }
            }
            results.firstOrNull { it.imageUrl == selectedUrl }?.let {
                Text("Image : ${it.source} — licence ${it.licence}", fontSize = 11.sp, color = Color.Gray)
            }
        }
    }
}
