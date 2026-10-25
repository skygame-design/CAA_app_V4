package com.example.nina

import kotlin.math.hypot

/*
 * Sélection par le regard, séparée d'Android pour être testée :
 * - zones regardables (les pictos et boutons), en coordonnées d'écran normalisées (0..1) ;
 * - aimant : le regard est « attiré » par la zone la plus proche et n'en change qu'après une hésitation franche ;
 * - fixation : rester sur une zone pendant un temps donné l'active ;
 * - choix des cibles de calibration et fusion des calibrations successives.
 */

/** Un rectangle en coordonnées d'écran normalisées. */
data class NormRect(val left: Double, val top: Double, val right: Double, val bottom: Double) {
    val centerX get() = (left + right) / 2
    val centerY get() = (top + bottom) / 2
    val width get() = right - left
    val height get() = bottom - top

    /** Distance du point au rectangle, rapportée à sa taille (0 = dedans, 1 = à une demi-taille du bord). */
    fun relativeDistance(x: Double, y: Double): Double {
        val dx = maxOf(left - x, 0.0, x - right) / (width / 2).coerceAtLeast(1e-6)
        val dy = maxOf(top - y, 0.0, y - bottom) / (height / 2).coerceAtLeast(1e-6)
        return hypot(dx, dy)
    }

    fun contains(x: Double, y: Double) = x in left..right && y in top..bottom

    /** Distance réelle du point au rectangle, en hauteurs d'écran (aspect = largeur / hauteur de l'écran). */
    fun absoluteDistance(x: Double, y: Double, aspect: Double): Double =
        hypot(maxOf(left - x, 0.0, x - right) * aspect, maxOf(top - y, 0.0, y - bottom))
}

/**
 * Aimant : choisit la zone visée à partir du point de regard (bruité).
 * - Le regard est rattaché à la zone qui le contient, ou à la plus proche si elle est assez près (marge) ;
 * - pour changer de zone, la nouvelle doit être visée sans interruption pendant switchMs (hystérésis) :
 *   les petits sauts du regard ne font pas « clignoter » la sélection ;
 * - le curseur affiché se place au centre de la zone (stable, rassurant pour l'enfant).
 * La marge est proportionnelle à la taille de la zone, avec un minimum absolu (minReach, en hauteurs d'écran) :
 * les petits boutons (Lire, ⌫, dossiers…) attirent le regard d'aussi loin que les grandes cases.
 */
class GazeMagnet(
    private val switchMs: Long = 250, private val margin: Double = 0.6, private val keepMargin: Double = 1.2,
    var minReach: Double = 0.0, var aspect: Double = 1.0
) {
    var current: Int? = null
        private set
    private var candidate: Int? = null
    private var candidateSince = 0L

    /** Proximité du point à la zone : 0 = dedans, 1 = à la limite de la marge (relative ou absolue). */
    private fun score(r: NormRect, x: Double, y: Double, factor: Double): Double {
        val relative = r.relativeDistance(x, y) / (margin * factor)
        val absolute = if (minReach > 0) r.absoluteDistance(x, y, aspect) / (minReach * factor) else Double.MAX_VALUE
        return minOf(relative, absolute)
    }

    /** Zone la plus proche du point, si elle est dans la marge. */
    fun nearest(zones: List<NormRect>, x: Double, y: Double): Int? =
        zones.indices.minByOrNull { score(zones[it], x, y, 1.0) }
            ?.takeIf { score(zones[it], x, y, 1.0) <= 1.0 }

    fun update(zones: List<NormRect>, x: Double, y: Double, timeMs: Long): Int? {
        var near = nearest(zones, x, y)
        // Hystérésis : la case choisie est gardée tant que le point reste à peu près dessus (marge plus large),
        // sauf s'il entre franchement dans une autre case
        val cur = current
        if (cur != null && cur < zones.size && near != cur && score(zones[cur], x, y, keepMargin / margin) <= 1.0 &&
            (near == null || !zones[near].contains(x, y))) near = cur
        when {
            near == current -> candidate = null
            near != candidate -> { candidate = near; candidateSince = timeMs }
            timeMs - candidateSince >= switchMs -> { current = near; candidate = null }
        }
        // Une zone qui a disparu (changement de page) n'est plus visée
        if (current != null && current!! >= zones.size) current = null
        return current
    }

    fun reset() { current = null; candidate = null }
}

/**
 * Fixation : progression de 0 à 1 tant que la même zone reste visée ; à 1, la zone est activée une fois.
 * Tolérante : un écart bref (moins de graceMs) ne fait rien perdre, un passage dans l'espace vide entre les cases
 * (aucune case visée) met seulement l'anneau en pause pendant gapPauseMs ; au-delà, la progression redescend
 * à la même vitesse qu'elle monte, et une autre case ne prend le relais qu'une fois l'anneau vidé.
 * Après une activation, il faut attendre cooldownMs avant que la même zone puisse se remplir à nouveau :
 * l'enfant ne « répète » pas un mot sans le vouloir.
 */
class GazeDwell(
    var dwellMs: Long = 1200, private val cooldownMs: Long = 1500, private val graceMs: Long = 300,
    private val gapPauseMs: Long = 1000 // Point dans l'espace entre les cases : l'anneau se met en pause, sans se vider
) {
    /** Zone dont l'anneau se remplit (peut différer un instant de la zone visée). */
    var zone: Int? = null
        private set
    private var accumulated = 0.0
    private var lastTime = -1L
    private var awayMs = 0L
    private var firedZone: Int? = null
    private var firedAt = 0L

    /** @return la progression (0..1) de `zone` et, si l'activation vient d'avoir lieu, la zone activée. */
    fun update(target: Int?, timeMs: Long): Pair<Float, Int?> {
        val dt = if (lastTime < 0) 0L else (timeMs - lastTime).coerceIn(0, 200)
        lastTime = timeMs
        if (target != null && target == zone) {
            awayMs = 0
            // Même zone que la dernière activation : on attend la fin du délai de repos
            if (target == firedZone && timeMs - firedAt < cooldownMs) return 0f to null
            accumulated += dt
        } else {
            awayMs += dt
            val tolerance = if (target == null) gapPauseMs else graceMs
            if (zone != null && awayMs > tolerance) accumulated -= dt
            if (zone == null || accumulated <= 0) { zone = target; accumulated = 0.0; awayMs = 0 }
        }
        if (zone == null) return 0f to null
        val progress = (accumulated / dwellMs).coerceIn(0.0, 1.0).toFloat()
        if (progress >= 1f) {
            firedZone = zone; firedAt = timeMs; accumulated = 0.0
            return 1f to zone
        }
        return progress to null
    }

    fun reset() { zone = null; firedZone = null; accumulated = 0.0; awayMs = 0; lastTime = -1 }
}

object GazeCalibrationPlan {
    /**
     * Cibles de calibration à partir des centres des pictos visibles : on retire les doublons trop proches,
     * puis on en garde au plus `max`, bien répartis (on commence par le plus central, puis toujours le plus éloigné des déjà choisis).
     */
    fun pickTargets(centers: List<Pair<Double, Double>>, max: Int = 18, minGap: Double = 0.04): List<Pair<Double, Double>> {
        val unique = mutableListOf<Pair<Double, Double>>()
        centers.forEach { c -> if (unique.none { hypot(it.first - c.first, it.second - c.second) < minGap }) unique += c }
        if (unique.size <= max) return orderForDisplay(unique)
        val chosen = mutableListOf(unique.minBy { hypot(it.first - 0.5, it.second - 0.5) })
        while (chosen.size < max) {
            chosen += unique.filter { it !in chosen }.maxBy { c -> chosen.minOf { hypot(it.first - c.first, it.second - c.second) } }
        }
        return orderForDisplay(chosen)
    }

    /** Ordre d'affichage : de haut en bas, en zigzag (gauche → droite puis droite → gauche), pour un regard qui se déplace peu. */
    private fun orderForDisplay(points: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
        val rows = points.sortedBy { it.second }.fold(mutableListOf<MutableList<Pair<Double, Double>>>()) { acc, p ->
            if (acc.isNotEmpty() && kotlin.math.abs(acc.last().first().second - p.second) < 0.06) acc.last() += p else acc += mutableListOf(p)
            acc
        }
        return rows.flatMapIndexed { i, row -> if (i % 2 == 0) row.sortedBy { it.first } else row.sortedByDescending { it.first } }
    }

    /** Cibles de la retouche rapide : `count` pictos bien répartis. */
    fun pickRefineTargets(centers: List<Pair<Double, Double>>, count: Int = 6) = pickTargets(centers, max = count)

    /**
     * Ajoute de nouvelles mesures à une calibration existante : une nouvelle mesure remplace l'ancienne
     * au même endroit (à moins de `radius`), les autres sont gardées. La calibration s'améliore au fil du temps.
     */
    fun merge(old: List<GazeSample>, new: List<GazeSample>, radius: Double = 0.03): List<GazeSample> =
        old.filter { o -> new.none { n -> hypot(n.targetX - o.targetX, n.targetY - o.targetY) < radius } } + new

    /**
     * Calibration continue : un picto choisi pendant l'utilisation devient un point de calibration.
     * S'il existe déjà un point au même endroit, les deux sont mélangés (weight = part du nouveau) :
     * la calibration suit l'état du jour sans qu'une seule mesure ne la bouleverse.
     * Au-delà de `max` points, seuls les endroits déjà connus sont mis à jour.
     */
    fun learn(samples: List<GazeSample>, new: GazeSample, radius: Double = 0.03, weight: Double = 0.4, max: Int = 80): List<GazeSample> {
        val i = samples.indexOfFirst { hypot(it.targetX - new.targetX, it.targetY - new.targetY) < radius && it.features.size == new.features.size }
        if (i < 0) return if (samples.size < max) samples + new else samples
        val old = samples[i]
        val mixed = GazeSample(
            DoubleArray(old.features.size) { old.features[it] * (1 - weight) + new.features[it] * weight },
            old.targetX * (1 - weight) + new.targetX * weight, old.targetY * (1 - weight) + new.targetY * weight
        )
        return samples.toMutableList().also { it[i] = mixed }
    }
}
