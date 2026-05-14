package com.example.nina

/**
 * Moteur de grammaire local très simplifié.
 * Transforme le langage "télégraphique" en français correct.
 * Ex: "Moi manger pomme" -> "Je mange une pomme"
 */
object LocalGrammarEngine {

    /**
     * Affine une phrase brute en appliquant des règles de base.
     */
    fun refine(input: String): String {
        if (input.isBlank()) return ""

        var refined = input.lowercase()

        // 1. Remplacement des sujets
        refined = refined.replace(Regex("\\bmoi\\b"), "je")
        refined = refined.replace(Regex("\\btoi\\b"), "tu")

        // 2. Conjugaison très basique (hardcodée pour Nina)
        // Note : C'est une version simplifiée. Un vrai moteur utiliserait un dictionnaire.
        if (refined.contains("je vouloir")) refined = refined.replace("je vouloir", "je veux")
        if (refined.contains("je manger")) refined = refined.replace("je manger", "je mange")
        if (refined.contains("je boire")) refined = refined.replace("je boire", "je bois")
        if (refined.contains("je aller")) refined = refined.replace("je aller", "je vais")
        
        // 3. Gestion des élisions (J', L')
        refined = refined.replace(Regex("\\bje ([aeiouyh])"), "j'$1")
        refined = refined.replace(Regex("\\ble ([aeiouyh])"), "l'$1")
        refined = refined.replace(Regex("\\bla ([aeiouyh])"), "l'$1")

        // 4. Mise en majuscule de la première lettre
        return refined.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}
