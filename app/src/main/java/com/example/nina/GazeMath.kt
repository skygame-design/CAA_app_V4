package com.example.nina

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

/*
 * Calculs de la commande oculaire, séparés d'Android pour pouvoir être testés :
 * - caractéristiques du regard à partir des points du visage (MediaPipe Face Landmarker, 478 points avec les iris) ;
 * - régression de calibration (caractéristiques -> position sur l'écran) ;
 * - lissage du curseur (filtre « One Euro »).
 */

/** Un point du visage, en coordonnées normalisées de l'image (0..1). */
data class FacePoint(val x: Float, val y: Float)

/**
 * Ce qui décrit le regard sur une image de la caméra.
 * gazeX / gazeY : position des iris dans les yeux (axe des yeux), moyenne des deux yeux.
 * headX / headY : orientation approximative de la tête (nez par rapport au milieu des yeux).
 * faceX / faceY / faceSize : position et taille du visage dans l'image (la taille indique la distance).
 * lidRatio : hauteur de l'iris entre les paupières (0 = contre la paupière du haut, 1 = contre celle du bas).
 * meanOpenness : ouverture moyenne des yeux ; la paupière descend quand on regarde vers le bas.
 * Ces deux dernières donnent le regard vertical, que la position des iris seule mesure mal.
 */
data class GazeFeatures(
    val gazeX: Double, val gazeY: Double,
    val headX: Double, val headY: Double,
    val faceX: Double, val faceY: Double, val faceSize: Double,
    val eyeOpenness: Double,
    val lidRatio: Double = 0.5,
    val meanOpenness: Double = eyeOpenness
) {
    /** Variables utilisées par la régression (le terme constant est ajouté à part), dans l'ordre de SCALE_FLOOR et PENALTY. */
    fun regressors(): DoubleArray = doubleArrayOf(
        gazeX, gazeY, lidRatio, meanOpenness,
        gazeX * gazeY, gazeX * gazeX, gazeY * gazeY,
        headX, headY, faceX, faceY
    )

    companion object {
        /** Version des caractéristiques : une calibration faite avec une autre version doit être refaite. */
        const val FEATURE_VERSION = 2

        /**
         * Écart-type minimal de chaque variable, d'après des mouvements réels. Sans ce plancher, une variable presque
         * immobile pendant la calibration (la tête !) est « agrandie » énormément, et le moindre mouvement ensuite
         * fait sauter le curseur : 1,1 cm à la calibration, 7,6 cm à la vérification.
         */
        val SCALE_FLOOR = doubleArrayOf(0.02, 0.01, 0.05, 0.03, 0.0005, 0.0005, 0.0003, 0.03, 0.03, 0.03, 0.03)

        /** Frein supplémentaire sur la tête et la position du visage : le regard doit venir des yeux. */
        val PENALTY = doubleArrayOf(1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 6.0, 6.0, 6.0, 6.0)

        // Indices MediaPipe (maillage du visage avec iris)
        private const val RIGHT_OUTER = 33; private const val RIGHT_INNER = 133
        private const val RIGHT_TOP = 159; private const val RIGHT_BOTTOM = 145; private const val RIGHT_IRIS = 468
        private const val LEFT_OUTER = 263; private const val LEFT_INNER = 362
        private const val LEFT_TOP = 386; private const val LEFT_BOTTOM = 374; private const val LEFT_IRIS = 473
        private const val NOSE_TIP = 1

        /** Yeux considérés fermés (clignement) en dessous de ce rapport hauteur / largeur. */
        const val CLOSED_EYE = 0.13

        /**
         * @param points les 478 points du visage
         * @param aspect largeur / hauteur de l'image (pour que les distances soient correctes)
         */
        fun from(points: List<FacePoint>, aspect: Float): GazeFeatures? {
            if (points.size < 478) return null
            fun p(i: Int) = doubleArrayOf(points[i].x * aspect.toDouble(), points[i].y.toDouble())

            // Axe horizontal du visage : d'un coin extérieur des yeux à l'autre ; axe vertical perpendiculaire
            val a = p(RIGHT_OUTER); val b = p(LEFT_OUTER)
            val eyeSpan = hypot(b[0] - a[0], b[1] - a[1])
            if (eyeSpan < 1e-6) return null
            val ux = (b[0] - a[0]) / eyeSpan; val uy = (b[1] - a[1]) / eyeSpan
            val vx = -uy; val vy = ux

            /** Pour un œil : décalage horizontal et vertical de l'iris, ouverture, et position de l'iris entre les paupières. */
            fun eye(outer: Int, inner: Int, top: Int, bottom: Int, iris: Int): DoubleArray {
                val o = p(outer); val i = p(inner); val t = p(top); val bo = p(bottom); val ir = p(iris)
                val width = hypot(i[0] - o[0], i[1] - o[1]).coerceAtLeast(1e-6)
                val cx = (o[0] + i[0]) / 2; val cy = (o[1] + i[1]) / 2
                val dx = ((ir[0] - cx) * ux + (ir[1] - cy) * uy) / width
                val dy = ((ir[0] - cx) * vx + (ir[1] - cy) * vy) / width
                val open = hypot(bo[0] - t[0], bo[1] - t[1]) / width
                val lidSpan = ((bo[0] - t[0]) * vx + (bo[1] - t[1]) * vy).let { if (kotlin.math.abs(it) < 1e-6) 1e-6 else it }
                val lid = ((ir[0] - t[0]) * vx + (ir[1] - t[1]) * vy) / lidSpan
                return doubleArrayOf(dx, dy, open, lid)
            }
            val r = eye(RIGHT_OUTER, RIGHT_INNER, RIGHT_TOP, RIGHT_BOTTOM, RIGHT_IRIS)
            val l = eye(LEFT_OUTER, LEFT_INNER, LEFT_TOP, LEFT_BOTTOM, LEFT_IRIS)

            val mx = (a[0] + b[0]) / 2; val my = (a[1] + b[1]) / 2
            val nose = p(NOSE_TIP)
            val headX = ((nose[0] - mx) * ux + (nose[1] - my) * uy) / eyeSpan
            val headY = ((nose[0] - mx) * vx + (nose[1] - my) * vy) / eyeSpan

            return GazeFeatures(
                gazeX = (r[0] + l[0]) / 2, gazeY = (r[1] + l[1]) / 2,
                headX = headX, headY = headY,
                faceX = mx / aspect, faceY = my, faceSize = eyeSpan,
                eyeOpenness = minOf(r[2], l[2]),
                lidRatio = ((r[3] + l[3]) / 2).coerceIn(-1.0, 2.0),
                meanOpenness = (r[2] + l[2]) / 2
            )
        }
    }
}

/** Un point de calibration : les caractéristiques (médiane de plusieurs images) quand l'enfant regardait la cible. */
data class GazeSample(val features: DoubleArray, val targetX: Double, val targetY: Double)

/**
 * Modèle de regard : régression « ridge » (moindres carrés régularisés) sur des caractéristiques centrées-réduites.
 * Prédit la position regardée en coordonnées d'écran normalisées (0..1).
 */
class GazeModel private constructor(
    private val mean: DoubleArray, private val scale: DoubleArray,
    private val coefX: DoubleArray, private val coefY: DoubleArray
) {
    /**
     * Correction locale : pour chaque point de calibration, l'écart qui reste après la régression globale
     * (position estimée -> vraie position). Près d'un point appris, on applique son écart ; loin de tous, rien.
     * C'est ce qui rend la calibration « exacte » à l'emplacement des pictos.
     */
    private var anchors: List<Pair<Pair<Double, Double>, Pair<Double, Double>>> = emptyList()

    /** Position estimée par la seule régression globale (sans correction locale). */
    fun predictGlobal(features: DoubleArray): Pair<Double, Double> {
        val z = standardize(features, mean, scale)
        return dot(coefX, z) to dot(coefY, z)
    }

    fun predict(features: DoubleArray): Pair<Double, Double> {
        val (x, y) = predictGlobal(features)
        if (anchors.isEmpty()) return x to y
        var wSum = LOCAL_PRIOR
        var cx = 0.0; var cy = 0.0
        anchors.forEach { (at, residual) ->
            val d2 = (x - at.first).let { it * it } + (y - at.second).let { it * it }
            val w = kotlin.math.exp(-d2 / (2 * LOCAL_RADIUS * LOCAL_RADIUS))
            wSum += w; cx += w * residual.first; cy += w * residual.second
        }
        return (x + cx / wSum) to (y + cy / wSum)
    }

    companion object {
        /** Force de la régularisation : limite les termes peu informatifs (tête, position) quand il y a peu de points. */
        const val DEFAULT_LAMBDA = 0.5

        /** Portée de la correction locale (fraction de l'écran) : environ une demi-case de picto. */
        const val LOCAL_RADIUS = 0.07
        /** Poids « pas de correction » : loin de tout point appris, la correction s'efface. */
        const val LOCAL_PRIOR = 0.15

        fun fit(samples: List<GazeSample>, lambda: Double = DEFAULT_LAMBDA): GazeModel? {
            if (samples.size < 4) return null
            val n = samples.first().features.size
            val mean = DoubleArray(n) { j -> samples.map { it.features[j] }.average() }
            val floors = GazeFeatures.SCALE_FLOOR.takeIf { it.size == n }
            val penalty = GazeFeatures.PENALTY.takeIf { it.size == n } ?: DoubleArray(n) { 1.0 }
            val scale = DoubleArray(n) { j ->
                val m = mean[j]
                val sd = sqrt(samples.map { (it.features[j] - m).let { d -> d * d } }.average())
                maxOf(sd, floors?.get(j) ?: 1e-9).takeIf { it > 1e-9 } ?: 1.0
            }
            val rows = samples.map { standardize(it.features, mean, scale) }
            val cx = ridge(rows, samples.map { it.targetX }, lambda, penalty) ?: return null
            val cy = ridge(rows, samples.map { it.targetY }, lambda, penalty) ?: return null
            val model = GazeModel(mean, scale, cx, cy)
            // Écarts restants à chaque point (moyennés si plusieurs mesures du même picto)
            model.anchors = samples.map { s ->
                val (px, py) = model.predictGlobal(s.features)
                (px to py) to ((s.targetX - px) to (s.targetY - py))
            }
            return model
        }

        /** Ajoute le terme constant (1) en tête des caractéristiques centrées-réduites. */
        private fun standardize(f: DoubleArray, mean: DoubleArray, scale: DoubleArray) =
            DoubleArray(f.size + 1) { if (it == 0) 1.0 else (f[it - 1] - mean[it - 1]) / scale[it - 1] }

        private fun dot(a: DoubleArray, b: DoubleArray) = a.indices.sumOf { a[it] * b[it] }

        /** Résout (XᵀX + λ·diag(pénalités))β = Xᵀy ; le terme constant n'est pas régularisé. */
        private fun ridge(rows: List<DoubleArray>, y: List<Double>, lambda: Double, penalty: DoubleArray): DoubleArray? {
            val k = rows.first().size
            val a = Array(k) { DoubleArray(k) }
            val b = DoubleArray(k)
            rows.forEachIndexed { r, x ->
                for (i in 0 until k) {
                    b[i] += x[i] * y[r]
                    for (j in 0 until k) a[i][j] += x[i] * x[j]
                }
            }
            for (i in 1 until k) a[i][i] += lambda * penalty[i - 1]
            return solve(a, b)
        }

        /** Élimination de Gauss avec pivot partiel. */
        private fun solve(a: Array<DoubleArray>, b: DoubleArray): DoubleArray? {
            val n = b.size
            for (c in 0 until n) {
                val pivot = (c until n).maxByOrNull { abs(a[it][c]) } ?: return null
                if (abs(a[pivot][c]) < 1e-12) return null
                a[c] = a[pivot].also { a[pivot] = a[c] }
                b[c] = b[pivot].also { b[pivot] = b[c] }
                for (r in c + 1 until n) {
                    val f = a[r][c] / a[c][c]
                    for (j in c until n) a[r][j] -= f * a[c][j]
                    b[r] -= f * b[c]
                }
            }
            val x = DoubleArray(n)
            for (r in n - 1 downTo 0) {
                var s = b[r]
                for (j in r + 1 until n) s -= a[r][j] * x[j]
                x[r] = s / a[r][r]
            }
            return x
        }

        /**
         * Erreur moyenne en validation croisée « un point de côté » (en fraction de l'écran) :
         * pour chaque point, on calibre sans lui et on mesure l'écart. Plus réaliste que l'erreur sur les points appris.
         * Avec widthScale / heightScale = taille de l'écran en cm, le résultat est en cm.
         */
        fun leaveOneOutError(samples: List<GazeSample>, lambda: Double = DEFAULT_LAMBDA, widthScale: Double = 1.0, heightScale: Double = 1.0): Double? {
            if (samples.size < 5) return null
            val errors = samples.indices.mapNotNull { i ->
                val model = fit(samples.filterIndexed { j, _ -> j != i }, lambda) ?: return@mapNotNull null
                val (px, py) = model.predict(samples[i].features)
                hypot((px - samples[i].targetX) * widthScale, (py - samples[i].targetY) * heightScale)
            }
            return errors.takeIf { it.isNotEmpty() }?.average()
        }
    }
}

/** Médiane de chaque caractéristique sur plusieurs images (résiste aux images ratées). */
fun medianFeatures(frames: List<DoubleArray>): DoubleArray? {
    if (frames.isEmpty()) return null
    return DoubleArray(frames.first().size) { j ->
        val v = frames.map { it[j] }.sorted()
        if (v.size % 2 == 1) v[v.size / 2] else (v[v.size / 2 - 1] + v[v.size / 2]) / 2
    }
}

/**
 * Filtre « One Euro » : lisse fort quand le regard est presque immobile (curseur stable),
 * et peu quand il se déplace vite (curseur réactif).
 */
class OneEuroFilter(private val minCutoff: Double = 1.0, private val beta: Double = 0.02, private val dCutoff: Double = 1.0) {
    private var last: Double? = null
    private var lastDeriv = 0.0
    private var lastTime = 0L

    private fun alpha(cutoff: Double, dt: Double): Double {
        val tau = 1.0 / (2 * Math.PI * cutoff)
        return 1.0 / (1.0 + tau / dt)
    }

    fun filter(value: Double, timeMs: Long): Double {
        val prev = last
        if (prev == null || timeMs <= lastTime) { last = value; lastTime = timeMs; return value }
        val dt = (timeMs - lastTime) / 1000.0
        val deriv = (value - prev) / dt
        val ad = alpha(dCutoff, dt)
        lastDeriv = ad * deriv + (1 - ad) * lastDeriv
        val a = alpha(minCutoff + beta * abs(lastDeriv), dt)
        val result = a * value + (1 - a) * prev
        last = result; lastTime = timeMs
        return result
    }

    fun reset() { last = null; lastDeriv = 0.0 }
}

/**
 * Médiane glissante des dernières mesures : un point aberrant isolé (iris mal repéré sur une image) est ignoré.
 */
class MedianWindow(private val size: Int = 5) {
    private val buffer = ArrayDeque<DoubleArray>()
    fun add(v: DoubleArray): DoubleArray {
        buffer.addLast(v)
        while (buffer.size > size) buffer.removeFirst()
        return medianFeatures(buffer.toList())!!
    }
    fun reset() = buffer.clear()
}

/**
 * Filtre de fixation (comme le mode « stabilisé » des oculomètres) :
 * tant que le regard reste dans un petit rayon, le pointeur reste à la position moyenne de cette fixation (immobile) ;
 * il ne se déplace que lors d'un vrai mouvement de l'œil (point hors du rayon sur plusieurs images de suite).
 *
 * @param radius rayon d'une fixation, en fraction de l'écran
 * @param jumpFrames nombre d'images consécutives hors du rayon pour accepter un déplacement (ignore les sauts isolés)
 * @param window nombre de points moyennés au plus dans une fixation (les plus récents)
 */
class FixationFilter(private val radius: Double = 0.07, private val jumpFrames: Int = 3, private val window: Int = 20) {
    private val points = ArrayDeque<Pair<Double, Double>>()
    private val outside = mutableListOf<Pair<Double, Double>>()

    private fun center(): Pair<Double, Double> = points.map { it.first }.average() to points.map { it.second }.average()

    fun filter(x: Double, y: Double): Pair<Double, Double> {
        if (points.isEmpty()) { points.addLast(x to y); return x to y }
        val (cx, cy) = center()
        if (hypot(x - cx, y - cy) <= radius) {
            outside.clear()
            points.addLast(x to y)
            while (points.size > window) points.removeFirst()
        } else {
            outside += x to y
            // Déplacement confirmé : les points hors du rayon sont proches les uns des autres → nouvelle fixation
            if (outside.size >= jumpFrames) {
                val ox = outside.map { it.first }.average(); val oy = outside.map { it.second }.average()
                if (outside.all { hypot(it.first - ox, it.second - oy) <= radius }) {
                    points.clear(); points.addAll(outside)
                }
                outside.clear()
            }
        }
        return center()
    }

    fun reset() { points.clear(); outside.clear() }
}

/** Positions des cibles de calibration (coordonnées normalisées), dans l'ordre d'affichage. */
object GazeTargets {
    val calibration9 = listOf(0.5 to 0.5, 0.08 to 0.1, 0.5 to 0.1, 0.92 to 0.1, 0.92 to 0.5, 0.92 to 0.9, 0.5 to 0.9, 0.08 to 0.9, 0.08 to 0.5)
    val verification5 = listOf(0.25 to 0.3, 0.75 to 0.3, 0.5 to 0.55, 0.25 to 0.8, 0.75 to 0.8)
}

/** Garde la valeur dans 0..1 (le curseur reste sur l'écran). */
fun clamp01(v: Double) = v.coerceIn(0.0, 1.0)
