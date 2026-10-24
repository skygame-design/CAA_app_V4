package com.example.nina

/** Personnes de conjugaison, dans l'ordre je, tu, il, nous, vous, ils. */
enum class Person { JE, TU, IL, NOUS, VOUS, ILS }

/** Une forme proposée pour un mot (ex. "mangeons", indice "nous"). */
data class WordForm(val text: String, val hint: String? = null)

/**
 * Morphologie française simplifiée, sans dictionnaire externe :
 * conjugaison au présent, participe passé, pluriel des noms.
 * Couvre les verbes réguliers des 1er et 2e groupes et les irréguliers courants chez l'enfant.
 */
object FrenchMorphology {

    private const val VOWELS = "aeiouyhâàäéèêëîïôöûùü"

    // Présent (je, tu, il, nous, vous, ils) + participe passé
    private class Conj(val present: List<String>, val participle: String)

    private fun c(je: String, tu: String, il: String, nous: String, vous: String, ils: String, pp: String) =
        Conj(listOf(je, tu, il, nous, vous, ils), pp)

    /** Verbes irréguliers, reconnus uniquement tels quels. */
    private val IRREGULAR = mapOf(
        "être" to c("suis", "es", "est", "sommes", "êtes", "sont", "été"),
        "avoir" to c("ai", "as", "a", "avons", "avez", "ont", "eu"),
        "aller" to c("vais", "vas", "va", "allons", "allez", "vont", "allé"),
        "vouloir" to c("veux", "veux", "veut", "voulons", "voulez", "veulent", "voulu"),
        "pouvoir" to c("peux", "peux", "peut", "pouvons", "pouvez", "peuvent", "pu"),
        "savoir" to c("sais", "sais", "sait", "savons", "savez", "savent", "su"),
        "devoir" to c("dois", "dois", "doit", "devons", "devez", "doivent", "dû"),
        "boire" to c("bois", "bois", "boit", "buvons", "buvez", "boivent", "bu"),
        "croire" to c("crois", "crois", "croit", "croyons", "croyez", "croient", "cru"),
        "mourir" to c("meurs", "meurs", "meurt", "mourons", "mourez", "meurent", "mort"),
        "fuir" to c("fuis", "fuis", "fuit", "fuyons", "fuyez", "fuient", "fui"),
        "pleuvoir" to c("pleut", "pleut", "pleut", "pleut", "pleut", "pleut", "plu"),
    )

    /** Familles : le verbe ou tout verbe qui finit pareil (prendre -> apprendre, comprendre...). */
    private val FAMILIES = mapOf(
        "prendre" to c("prends", "prends", "prend", "prenons", "prenez", "prennent", "pris"),
        "venir" to c("viens", "viens", "vient", "venons", "venez", "viennent", "venu"),
        "tenir" to c("tiens", "tiens", "tient", "tenons", "tenez", "tiennent", "tenu"),
        "mettre" to c("mets", "mets", "met", "mettons", "mettez", "mettent", "mis"),
        "faire" to c("fais", "fais", "fait", "faisons", "faites", "font", "fait"),
        "dire" to c("dis", "dis", "dit", "disons", "dites", "disent", "dit"),
        "lire" to c("lis", "lis", "lit", "lisons", "lisez", "lisent", "lu"),
        "rire" to c("ris", "ris", "rit", "rions", "riez", "rient", "ri"),
        "crire" to c("cris", "cris", "crit", "crivons", "crivez", "crivent", "crit"),
        "voir" to c("vois", "vois", "voit", "voyons", "voyez", "voient", "vu"),
        "cevoir" to c("çois", "çois", "çoit", "cevons", "cevez", "çoivent", "çu"),
        "dormir" to c("dors", "dors", "dort", "dormons", "dormez", "dorment", "dormi"),
        "partir" to c("pars", "pars", "part", "partons", "partez", "partent", "parti"),
        "sortir" to c("sors", "sors", "sort", "sortons", "sortez", "sortent", "sorti"),
        "sentir" to c("sens", "sens", "sent", "sentons", "sentez", "sentent", "senti"),
        "mentir" to c("mens", "mens", "ment", "mentons", "mentez", "mentent", "menti"),
        "servir" to c("sers", "sers", "sert", "servons", "servez", "servent", "servi"),
        "courir" to c("cours", "cours", "court", "courons", "courez", "courent", "couru"),
        "cueillir" to c("cueille", "cueilles", "cueille", "cueillons", "cueillez", "cueillent", "cueilli"),
        "battre" to c("bats", "bats", "bat", "battons", "battez", "battent", "battu"),
        "suivre" to c("suis", "suis", "suit", "suivons", "suivez", "suivent", "suivi"),
        "vivre" to c("vis", "vis", "vit", "vivons", "vivez", "vivent", "vécu"),
        "plaire" to c("plais", "plais", "plaît", "plaisons", "plaisez", "plaisent", "plu"),
        "aître" to c("ais", "ais", "aît", "aissons", "aissez", "aissent", "u"),
        "uire" to c("uis", "uis", "uit", "uisons", "uisez", "uisent", "uit"),
        "eindre" to c("eins", "eins", "eint", "eignons", "eignez", "eignent", "eint"),
        "aindre" to c("ains", "ains", "aint", "aignons", "aignez", "aignent", "aint"),
        "oindre" to c("oins", "oins", "oint", "oignons", "oignez", "oignent", "oint"),
    )
    // Les plus longues d'abord ("crire" avant "rire", "cevoir" avant "voir")
    private val FAMILY_KEYS = FAMILIES.keys.sortedByDescending { it.length }

    /** -eler / -eter qui doublent la consonne (j'appelle) au lieu de prendre un accent (j'achète). */
    private val DOUBLING = setOf("appeler", "rappeler", "jeter", "rejeter", "projeter", "épeler", "renouveler", "ficeler", "étinceler")

    /** Mots en -er / -ir qui ne sont pas des verbes. */
    private val NOT_VERBS = setOf(
        "mer", "hier", "hiver", "fer", "cher", "ver", "super", "enfer", "amer", "laser", "poster", "cancer", "hamster",
        "rocher", "plancher", "clocher", "boucher", "archer", "boulanger", "berger", "danger", "verger", "étranger", "léger", "oranger",
        "plaisir", "désir", "loisir", "avenir", "souvenir", "cuir", "saphir", "menhir", "tapir", "élixir", "maître", "affaire"
    )

    /** Seuls les -ier listés sont des verbes (les autres sont des noms : cahier, papier, panier...). */
    private val VERBS_IER = setOf(
        "crier", "oublier", "plier", "copier", "trier", "remercier", "marier", "étudier", "colorier", "vérifier", "skier",
        "photographier", "multiplier", "confier", "déplier", "expédier", "apprécier", "prier", "lier", "nier", "relier", "envier", "sacrifier"
    )

    /** Mots invariables : pas de pluriel ni de conjugaison à proposer. */
    private val INVARIABLE = setOf(
        "encore", "avec", "oui", "non", "bonjour", "bonsoir", "merci", "au revoir", "et", "mais", "ou", "donc", "aussi", "très", "plus",
        "moins", "beaucoup", "peu", "ici", "là", "maintenant", "après", "avant", "hier", "demain", "aujourd'hui", "pour", "dans", "sur",
        "sous", "de", "à", "en", "pas", "ne", "quoi", "qui", "où", "quand", "comment", "pourquoi", "fini", "stop", "aide", "salut",
        "je", "tu", "il", "elle", "on", "nous", "vous", "ils", "elles", "moi", "toi", "lui", "eux", "le", "la", "les", "un", "une", "des",
        "mon", "ma", "mes", "ton", "ta", "tes", "son", "sa", "ses", "ce", "cette", "ces", "chez", "vers", "sans", "rien", "tout", "toujours", "jamais"
    )

    private val REFLEXIVE = listOf("me", "te", "se", "nous", "vous", "se")

    /** Mot-outil ou mot invariable (encore, avec, oui, moi...). */
    fun isInvariable(word: String): Boolean = word.trim().lowercase() in INVARIABLE

    private fun startsWithVowel(word: String) = word.isNotEmpty() && word[0].lowercaseChar() in VOWELS

    /** Sépare "se laver" / "s'habiller" en (true, "laver"/"habiller"). */
    private fun splitReflexive(infinitive: String): Pair<Boolean, String> = when {
        infinitive.startsWith("se ") -> true to infinitive.removePrefix("se ").trim()
        infinitive.startsWith("s'") || infinitive.startsWith("s’") -> true to infinitive.drop(2).trim()
        else -> false to infinitive
    }

    /** Vrai si le mot ressemble à un verbe à l'infinitif que l'on sait conjuguer. */
    fun isVerb(word: String): Boolean {
        val (_, base) = splitReflexive(word.trim().lowercase())
        if (base.isEmpty() || base.contains(' ') || base in NOT_VERBS) return false
        if (base.endsWith("ier") && base !in VERBS_IER) return false
        return conjugation(base) != null
    }

    private fun conjugation(inf: String): Conj? {
        IRREGULAR[inf]?.let { return it }
        FAMILY_KEYS.firstOrNull { inf.endsWith(it) }?.let { key ->
            val prefix = inf.removeSuffix(key)
            val f = FAMILIES.getValue(key)
            return Conj(f.present.map { prefix + it }, prefix + f.participle)
        }
        return when {
            inf.length < 3 -> null
            inf.endsWith("oir") -> null // Les verbes en -oir sont tous irréguliers : le reste, ce sont des noms (miroir, couloir, soir...)
            inf.endsWith("vrir") || inf.endsWith("frir") -> {
                // ouvrir, offrir, couvrir, souffrir : présent comme le 1er groupe, participe en -ert
                val stem = inf.dropLast(2)
                Conj(listOf("e", "es", "e", "ons", "ez", "ent").map { stem + it }, stem.dropLast(1) + "ert")
            }
            inf.endsWith("er") -> firstGroup(inf)
            inf.endsWith("ir") -> {
                val stem = inf.dropLast(2)
                Conj(listOf("is", "is", "it", "issons", "issez", "issent").map { stem + it }, stem + "i")
            }
            inf.endsWith("dre") -> {
                // attendre, répondre, perdre, descendre...
                val stem = inf.dropLast(3)
                Conj(listOf("ds", "ds", "d", "dons", "dez", "dent").map { stem + it }, stem + "du")
            }
            else -> null
        }
    }

    private fun firstGroup(inf: String): Conj {
        val stem = inf.dropLast(2)
        // Radical des personnes où la fin est muette (je, tu, il, ils) : lève, achète, appelle, nettoie
        val consonants = stem.takeLastWhile { it !in VOWELS }
        val vowelIndex = stem.length - consonants.length - 1
        val vowel = stem.getOrNull(vowelIndex)
        val shortCluster = consonants.length == 1 || consonants in setOf("ch", "tr", "br", "gr", "gl", "vr", "bl", "cr", "pr")
        val muteStem = when {
            stem.endsWith("oy") || stem.endsWith("uy") || stem.endsWith("ay") -> stem.dropLast(1) + "i"
            inf in DOUBLING -> stem + stem.last()
            (vowel == 'e' || vowel == 'é') && shortCluster && vowelIndex > 0 ->
                stem.substring(0, vowelIndex) + "è" + consonants
            else -> stem
        }
        val nous = when {
            stem.endsWith("g") -> stem + "eons"
            stem.endsWith("c") -> stem.dropLast(1) + "çons"
            else -> stem + "ons"
        }
        return Conj(listOf(muteStem + "e", muteStem + "es", muteStem + "e", nous, stem + "ez", muteStem + "ent"), stem + "é")
    }

    /**
     * Présent de l'indicatif, sans le pronom sujet. Pour un verbe pronominal, inclut le pronom réfléchi
     * ("se laver", JE -> "me lave" ; "s'habiller", JE -> "m'habille").
     */
    fun conjugate(infinitive: String, person: Person): String? {
        val (reflexive, base) = splitReflexive(infinitive.trim().lowercase())
        if (!isVerb(base)) return null
        val form = conjugation(base)?.present?.get(person.ordinal) ?: return null
        if (!reflexive) return form
        val pronoun = REFLEXIVE[person.ordinal]
        return if (pronoun.length == 2 && startsWithVowel(form)) "${pronoun[0]}'$form" else "$pronoun $form"
    }

    fun pastParticiple(infinitive: String): String? {
        val (_, base) = splitReflexive(infinitive.trim().lowercase())
        return if (isVerb(base)) conjugation(base)?.participle else null
    }

    /** Pluriel d'un nom ou d'un adjectif. */
    fun plural(word: String): String {
        val w = word.trim()
        val lower = w.lowercase()
        return when {
            lower.endsWith("s") || lower.endsWith("x") || lower.endsWith("z") -> w
            lower in setOf("bijou", "caillou", "chou", "genou", "hibou", "joujou", "pou") -> w + "x"
            lower in setOf("bal", "carnaval", "chacal", "festival", "récital", "régal") -> w + "s"
            lower.endsWith("al") -> w.dropLast(2) + "aux"
            lower in setOf("pneu", "bleu") -> w + "s"
            lower.endsWith("eau") || lower.endsWith("au") || lower.endsWith("eu") -> w + "x"
            else -> w + "s"
        }
    }

    /**
     * Formes à proposer sur un appui long (comme les "Word Forms" de TD Snap).
     * Verbe : infinitif, présent (formes distinctes, avec les personnes en indice), participe passé.
     * Nom / adjectif : singulier et pluriel. Liste vide s'il n'y a rien à proposer.
     */
    fun wordForms(label: String): List<WordForm> {
        val word = label.trim().lowercase()
        if (word.isEmpty() || word in INVARIABLE) return emptyList()

        if (isVerb(word)) {
            val pronouns = listOf("je", "tu", "il", "nous", "vous", "ils")
            val forms = mutableListOf(WordForm(word, "infinitif"))
            Person.entries
                .map { conjugate(word, it)!! to pronouns[it.ordinal] }
                .groupBy({ it.first }, { it.second })
                .forEach { (form, persons) -> forms += WordForm(form, persons.joinToString(", ")) }
            pastParticiple(word)?.let { forms += WordForm(it, "passé") }
            return forms.distinctBy { it.text }
        }

        if (word.contains(' ') || word.contains('\'')) return emptyList()
        val plural = plural(word)
        return if (plural == word) emptyList() else listOf(WordForm(word, "un"), WordForm(plural, "plusieurs"))
    }
}
