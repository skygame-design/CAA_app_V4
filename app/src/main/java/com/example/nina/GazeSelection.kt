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
}

/**
 * Aimant : choisit la zone visée à partir du point de regard (bruité).
 * - Le regard est rattaché à la zone qui le contient, ou à la plus proche si elle est assez près (marge) ;
 * - pour changer de zone, la nouvelle doit être visée sans interruption pendant switchMs (hystérésis) :
 *   les petits sauts du regard ne font pas « clignoter » la sélection ;
 * - le curseur affiché se place au centre de la zone (stable, rassurant pour l'enfant).
 */
class GazeMagnet(private val switchMs: Long = 250, private val margin: Double = 0.6) {
    var current: Int? = null
        private set
    private var candidate: Int? = null
    private var candidateSince = 0L

    /** Zone la plus proche du point, si elle est dans la marge (en demi-tailles de zone). */
    fun nearest(zones: List<NormRect>, x: Double, y: Double): Int? =
        zones.indices.minByOrNull { zones[it].relativeDistance(x, y) }
            ?.takeIf { zones[it].relativeDistance(x, y) <= margin }

    fun update(zones: List<NormRect>, x: Double, y: Double, timeMs: Long): Int? {
        val near = nearest(zones, x, y)
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
 * Après une activation, il faut quitter la zone (ou attendre cooldownMs) avant de pouvoir la réactiver :
 * l'enfant ne « répète » pas un mot sans le vouloir.
 */
class GazeDwell(var dwellMs: Long = 1200, private val cooldownMs: Long = 1500) {
    private var zone: Int? = null
    private var since = 0L
    private var firedZone: Int? = null
    private var firedAt = 0L

    /** @return la progression (0..1) et, si l'activation vient d'avoir lieu, la zone activée. */
    fun update(target: Int?, timeMs: Long): Pair<Float, Int?> {
        if (target != zone) { zone = target; since = timeMs }
        if (target == null) return 0f to null
        // Même zone que la dernière activation : on attend la fin du délai de repos
        if (target == firedZone && timeMs - firedAt < cooldownMs + dwellMs) {
            if (timeMs - firedAt < cooldownMs) return 0f to null
            since = maxOf(since, firedAt + cooldownMs)
        }
        val progress = ((timeMs - since).toFloat() / dwellMs).coerceIn(0f, 1f)
        if (progress >= 1f) {
            firedZone = target; firedAt = timeMs; since = timeMs
            return 1f to target
        }
        return progress to null
    }

    fun reset() { zone = null; firedZone = null }
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
}
