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
 */
data class GazeFeatures(
    val gazeX: Double, val gazeY: Double,
    val headX: Double, val headY: Double,
    val faceX: Double, val faceY: Double, val faceSize: Double,
    val eyeOpenness: Double
) {
    /** Variables utilisées par la régression (le terme constant est ajouté à part). */
    fun regressors(): DoubleArray = doubleArrayOf(
        gazeX, gazeY, gazeX * gazeY, gazeX * gazeX, gazeY * gazeY,
        headX, headY, faceX, faceY
    )

    companion object {
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

            fun eye(outer: Int, inner: Int, top: Int, bottom: Int, iris: Int): Triple<Double, Double, Double> {
                val o = p(outer); val i = p(inner); val t = p(top); val bo = p(bottom); val ir = p(iris)
                val width = hypot(i[0] - o[0], i[1] - o[1]).coerceAtLeast(1e-6)
                val cx = (o[0] + i[0]) / 2; val cy = (o[1] + i[1]) / 2
                val dx = ((ir[0] - cx) * ux + (ir[1] - cy) * uy) / width
                val dy = ((ir[0] - cx) * vx + (ir[1] - cy) * vy) / width
                val open = hypot(bo[0] - t[0], bo[1] - t[1]) / width
                return Triple(dx, dy, open)
            }
            val r = eye(RIGHT_OUTER, RIGHT_INNER, RIGHT_TOP, RIGHT_BOTTOM, RIGHT_IRIS)
            val l = eye(LEFT_OUTER, LEFT_INNER, LEFT_TOP, LEFT_BOTTOM, LEFT_IRIS)

            val mx = (a[0] + b[0]) / 2; val my = (a[1] + b[1]) / 2
            val nose = p(NOSE_TIP)
            val headX = ((nose[0] - mx) * ux + (nose[1] - my) * uy) / eyeSpan
            val headY = ((nose[0] - mx) * vx + (nose[1] - my) * vy) / eyeSpan

            return GazeFeatures(
                gazeX = (r.first + l.first) / 2, gazeY = (r.second + l.second) / 2,
                headX = headX, headY = headY,
                faceX = mx / aspect, faceY = my, faceSize = eyeSpan,
                eyeOpenness = minOf(r.third, l.third)
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
    fun predict(features: DoubleArray): Pair<Double, Double> {
        val z = standardize(features, mean, scale)
        return dot(coefX, z) to dot(coefY, z)
    }

    companion object {
        /** Force de la régularisation : limite les termes peu informatifs (tête, position) quand il y a peu de points. */
        const val DEFAULT_LAMBDA = 0.5

        fun fit(samples: List<GazeSample>, lambda: Double = DEFAULT_LAMBDA): GazeModel? {
            if (samples.size < 4) return null
            val n = samples.first().features.size
            val mean = DoubleArray(n) { j -> samples.map { it.features[j] }.average() }
            val scale = DoubleArray(n) { j ->
                val m = mean[j]
                sqrt(samples.map { (it.features[j] - m).let { d -> d * d } }.average()).takeIf { it > 1e-9 } ?: 1.0
            }
            val rows = samples.map { standardize(it.features, mean, scale) }
            val cx = ridge(rows, samples.map { it.targetX }, lambda) ?: return null
            val cy = ridge(rows, samples.map { it.targetY }, lambda) ?: return null
            return GazeModel(mean, scale, cx, cy)
        }

        /** Ajoute le terme constant (1) en tête des caractéristiques centrées-réduites. */
        private fun standardize(f: DoubleArray, mean: DoubleArray, scale: DoubleArray) =
            DoubleArray(f.size + 1) { if (it == 0) 1.0 else (f[it - 1] - mean[it - 1]) / scale[it - 1] }

        private fun dot(a: DoubleArray, b: DoubleArray) = a.indices.sumOf { a[it] * b[it] }

        /** Résout (XᵀX + λI)β = Xᵀy ; le terme constant n'est pas régularisé. */
        private fun ridge(rows: List<DoubleArray>, y: List<Double>, lambda: Double): DoubleArray? {
            val k = rows.first().size
            val a = Array(k) { DoubleArray(k) }
            val b = DoubleArray(k)
            rows.forEachIndexed { r, x ->
                for (i in 0 until k) {
                    b[i] += x[i] * y[r]
                    for (j in 0 until k) a[i][j] += x[i] * x[j]
                }
            }
            for (i in 1 until k) a[i][i] += lambda
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

/** Positions des cibles de calibration (coordonnées normalisées), dans l'ordre d'affichage. */
object GazeTargets {
    val calibration9 = listOf(0.5 to 0.5, 0.08 to 0.1, 0.5 to 0.1, 0.92 to 0.1, 0.92 to 0.5, 0.92 to 0.9, 0.5 to 0.9, 0.08 to 0.9, 0.08 to 0.5)
    val verification5 = listOf(0.25 to 0.3, 0.75 to 0.3, 0.5 to 0.55, 0.25 to 0.8, 0.75 to 0.8)
}

/** Garde la valeur dans 0..1 (le curseur reste sur l'écran). */
fun clamp01(v: Double) = v.coerceIn(0.0, 1.0)
