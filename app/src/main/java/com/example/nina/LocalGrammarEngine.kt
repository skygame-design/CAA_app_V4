package com.example.nina

/**
 * Moteur de grammaire local.
 * Transforme le langage "télégraphique" en français correct, sans réseau :
 * - "Moi vouloir manger" -> "Je veux manger"
 * - "Maman aller maison" -> "Maman va maison"
 * - "Moi pas aimer" -> "Je n'aime pas"
 * - "Moi se laver" -> "Je me lave"
 * Seul le premier verbe après le sujet est conjugué ; les suivants restent à l'infinitif (je veux manger).
 * Les formes déjà conjuguées (choisies par appui long) sont laissées telles quelles.
 */
object LocalGrammarEngine {

    private val SUBJECTS = mapOf(
        "je" to Person.JE, "tu" to Person.TU, "il" to Person.IL, "elle" to Person.IL, "on" to Person.IL,
        "nous" to Person.NOUS, "vous" to Person.VOUS, "ils" to Person.ILS, "elles" to Person.ILS
    )

    /** Pronoms toniques transformés en pronoms sujets, seulement devant un verbe ("avec moi" reste "avec moi"). */
    private val TONIC = mapOf(
        "moi" to ("je" to Person.JE), "toi" to ("tu" to Person.TU),
        "lui" to ("il" to Person.IL), "eux" to ("ils" to Person.ILS)
    )

    private const val LETTER = "\\p{L}'’"

    fun refine(input: String): String {
        val tokens = input.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return ""

        val out = mutableListOf<String>()
        var subject: Person? = null // Sujet qui attend son verbe
        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]

            // 1. Sujet
            val tonic = TONIC[t]
            if (tonic != null && startsVerbGroup(tokens, i + 1)) {
                out += tonic.first; subject = tonic.second; i++; continue
            }
            val pronoun = SUBJECTS[t]
            if (pronoun != null) {
                out += t; subject = pronoun; i++; continue
            }
            // Nom en début de phrase suivi d'un verbe : "Maman manger" -> "Maman mange"
            if (i == 0 && !FrenchMorphology.isInvariable(t) && !FrenchMorphology.isVerb(t) && startsVerbGroup(tokens, 1)) {
                out += t; subject = Person.IL; i++; continue
            }

            // 2. Le mot qui suit le sujet est la place du verbe
            if (subject != null) {
                val person: Person = subject
                subject = null
                val negate = t == "pas"
                val start = if (negate) i + 1 else i
                val (verb, length) = readInfinitive(tokens, start)
                if (verb != null) {
                    val conjugated = FrenchMorphology.conjugate(verb, person) ?: verb
                    if (negate) { out += "ne"; out += conjugated; out += "pas" } else out += conjugated
                    i = start + length
                    continue
                }
                // Pas un infinitif : déjà conjugué ou autre mot, on n'y touche pas
            }

            out += t
            i++
        }
        return polish(out.joinToString(" "))
    }

    /** Vrai si un verbe à l'infinitif commence à cette position ("manger", "pas manger", "se laver"). */
    private fun startsVerbGroup(tokens: List<String>, index: Int): Boolean {
        val start = if (tokens.getOrNull(index) == "pas") index + 1 else index
        return readInfinitive(tokens, start).first != null
    }

    /** Lit un infinitif à cette position : "se laver" (2 mots), "s'habiller" ou "manger" (1 mot). */
    private fun readInfinitive(tokens: List<String>, index: Int): Pair<String?, Int> {
        val t = tokens.getOrNull(index) ?: return null to 0
        val next = tokens.getOrNull(index + 1)
        if (t == "se" && next != null && FrenchMorphology.isVerb(next)) return "se $next" to 2
        if (FrenchMorphology.isVerb(t)) return t to 1
        return null to 0
    }

    /** Élisions (j'aime, l'eau), contractions (au parc, du pain) et majuscule. */
    private fun polish(sentence: String): String {
        var s = sentence
        s = s.replace(Regex("(?<![$LETTER])(je|me|te|se|le|la|ne|de|que) (?=[aeiouyhâàéèêëîïôûù])")) {
            it.groupValues[1].dropLast(1) + "'"
        }
        listOf("à le" to "au", "à les" to "aux", "de le" to "du", "de les" to "des").forEach { (from, to) ->
            s = s.replace(Regex("(?<![$LETTER])$from(?![$LETTER])"), to)
        }
        return s.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}
