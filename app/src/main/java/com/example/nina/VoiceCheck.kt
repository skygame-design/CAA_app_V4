package com.example.nina

import android.speech.tts.TextToSpeech
import android.speech.tts.Voice

/** État de la voix française de la tablette. */
enum class VoiceStatus {
    UNKNOWN,       // Moteur vocal pas encore prêt
    OK,            // Voix française enregistrée dans la tablette : fonctionne sans internet
    NETWORK_ONLY,  // Voix française seulement en ligne : Nina sera muette sans Wi-Fi
    MISSING        // Pas de voix française du tout
}

/** Ce qui compte d'une voix pour savoir si elle marche hors ligne (séparé d'Android pour être testé). */
data class VoiceInfo(val language: String, val needsNetwork: Boolean, val notInstalled: Boolean)

object VoiceCheck {

    /**
     * @param languageResult résultat de TextToSpeech.setLanguage(Locale.FRENCH)
     * @param voices voix proposées par le moteur
     */
    fun evaluate(languageResult: Int, voices: List<VoiceInfo>): VoiceStatus {
        if (languageResult == TextToSpeech.LANG_MISSING_DATA || languageResult == TextToSpeech.LANG_NOT_SUPPORTED) return VoiceStatus.MISSING
        val french = voices.filter { it.language == "fr" || it.language == "fra" }
        return when {
            french.any { !it.needsNetwork && !it.notInstalled } -> VoiceStatus.OK
            french.any { !it.notInstalled } -> VoiceStatus.NETWORK_ONLY
            // Certains moteurs ne listent pas leurs voix : on se fie alors à setLanguage
            french.isEmpty() && voices.isEmpty() -> VoiceStatus.OK
            else -> VoiceStatus.MISSING
        }
    }

    fun info(voice: Voice) = VoiceInfo(
        language = voice.locale.language,
        needsNetwork = voice.isNetworkConnectionRequired,
        notInstalled = voice.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) == true
    )

    /**
     * Vérifie la voix française et, si la voix choisie a besoin d'internet alors qu'une voix
     * française hors ligne existe, passe sur celle-ci (France en priorité, meilleure qualité).
     */
    fun check(tts: TextToSpeech): VoiceStatus {
        val languageResult = tts.setLanguage(java.util.Locale.FRANCE)
        val voices = runCatching { tts.voices?.toList() }.getOrNull().orEmpty()
        val status = evaluate(languageResult, voices.map { info(it) })
        val current = runCatching { tts.voice }.getOrNull()
        if (status == VoiceStatus.OK && current != null && (current.isNetworkConnectionRequired || current.locale.language != "fr")) {
            voices.filter { val i = info(it); i.language == "fr" && !i.needsNetwork && !i.notInstalled }
                .sortedWith(compareByDescending<Voice> { it.locale.country == "FR" }.thenByDescending { it.quality })
                .firstOrNull()?.let { tts.voice = it }
        }
        android.util.Log.i("Nina-Voix", "Voix française : $status (voix utilisée : ${runCatching { tts.voice?.name }.getOrNull()})")
        return status
    }
}
