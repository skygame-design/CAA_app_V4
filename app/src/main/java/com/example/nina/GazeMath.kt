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
 * yaw / pitch : rotation de la tête en 3D (radians, pose calculée par MediaPipe), gauche-droite et haut-bas.
 */
data class GazeFeatures(
    val gazeX: Double, val gazeY: Double,
    val headX: Double, val headY: Double,
    val faceX: Double, val faceY: Double, val faceSize: Double,
    val eyeOpenness: Double,
    val lidRatio: Double = 0.5,
    val meanOpenness: Double = eyeOpenness,
    val yaw: Double = 0.0, val pitch: Double = 0.0
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
    private val coefX: DoubleArray, private val coefY: DoubleArray,
    /** Compensation de la rotation de la tête mesurée à la calibration (null : pas de compensation). */
    val compensation: HeadCompensation? = null
) {
    /** Variables de régression d'une image, rotation de la tête compensée. */
    fun regressorsOf(f: GazeFeatures): DoubleArray = (compensation?.apply(f) ?: f).regressors()

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

        fun fit(samples: List<GazeSample>, lambda: Double = DEFAULT_LAMBDA, compensation: HeadCompensation? = null): GazeModel? {
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
            val model = GazeModel(mean, scale, cx, cy, compensation)
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
 * @param driftFrames petit déplacement à l'intérieur du rayon (vers une case voisine) : si les dernières images
 *   se sont toutes décalées dans la même direction, le pointeur les rejoint aussitôt au lieu de glisser lentement
 *   (le tremblement, lui, part dans tous les sens et se compense dans la moyenne)
 */
class FixationFilter(var radius: Double = 0.07, private val jumpFrames: Int = 3, private val window: Int = 20, private val driftFrames: Int = 5) {
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
            if (points.size > driftFrames * 2) {
                val recent = points.takeLast(driftFrames)
                val rx = recent.map { it.first }.average(); val ry = recent.map { it.second }.average()
                val (ax, ay) = center()
                if (hypot(rx - ax, ry - ay) > radius * 0.4) { points.clear(); points.addAll(recent) }
            }
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

/**
 * Correction de distance et de décalage, appliquée après le modèle de regard.
 * Le modèle est appris à une distance donnée : si l'enfant s'éloigne, ses yeux tournent moins pour regarder
 * le même picto, et le regard estimé se rapproche du centre de l'écran (et inversement s'il s'approche).
 * On écarte donc le point du centre en proportion de la distance (la taille du visage dans l'image varie
 * comme l'inverse de la distance), puis on ajoute le décalage mesuré par le recentrage à 1 point.
 *
 * @param refFaceSize taille du visage pendant la calibration (null : pas de correction de distance)
 */
data class GazeCorrection(val refFaceSize: Double? = null, val offsetX: Double = 0.0, val offsetY: Double = 0.0) {
    /** Rapport de distance actuelle / distance de calibration (1 = même distance), borné pour rester prudent. */
    fun distanceRatio(faceSize: Double): Double =
        if (refFaceSize == null || faceSize <= 0.0) 1.0 else (refFaceSize / faceSize).coerceIn(0.6, 1.7)

    fun apply(x: Double, y: Double, faceSize: Double, centerX: Double = 0.5, centerY: Double = 0.5): Pair<Double, Double> {
        val k = distanceRatio(faceSize)
        return (centerX + (x - centerX) * k + offsetX) to (centerY + (y - centerY) * k + offsetY)
    }

    /** Opération inverse : où le modèle (avant correction) doit placer un point pour qu'il arrive en (x, y) à l'écran. */
    fun invert(x: Double, y: Double, faceSize: Double, centerX: Double = 0.5, centerY: Double = 0.5): Pair<Double, Double> {
        val k = distanceRatio(faceSize)
        return (centerX + (x - offsetX - centerX) / k) to (centerY + (y - offsetY - centerY) / k)
    }

    /** Sans le décalage de recentrage (pour mesurer un nouveau décalage). */
    fun withoutOffset() = copy(offsetX = 0.0, offsetY = 0.0)
}

/**
 * Repérage fin de l'iris dans l'image en haute définition : MediaPipe ne voit le visage qu'en 256 × 256 pixels,
 * alors que la caméra en fournit beaucoup plus. Autour de l'iris trouvé par MediaPipe, on cherche le centre
 * de la zone sombre (iris + pupille) pixel par pixel, avec une précision inférieure au pixel.
 *
 * @param gray niveaux de gris (0..255) d'un recadrage w × h autour de l'œil, ligne par ligne
 * @param cx, cy centre de l'iris selon MediaPipe, en pixels du recadrage
 * @param radius rayon de l'iris selon MediaPipe, en pixels
 * @param top, bottom hauteur des paupières (pixels du recadrage) : la peau au-dessus et en dessous est ignorée
 * @return centre affiné (pixels du recadrage), ou null si le résultat n'est pas fiable (reflet, œil fermé…)
 */
fun darkCentroid(gray: IntArray, w: Int, h: Int, cx: Double, cy: Double, radius: Double, top: Double, bottom: Double): Pair<Double, Double>? {
    if (radius < 2 || w * h != gray.size) return null
    val r = radius * 1.3
    val inside = mutableListOf<Int>() // indices des pixels dans le cercle de recherche, entre les paupières
    for (y in 0 until h) {
        if (y < top || y > bottom) continue
        for (x in 0 until w) if (hypot(x - cx, y - cy) <= r) inside += y * w + x
    }
    if (inside.size < 20) return null
    // Seuil : les 35 % de pixels les plus sombres de la zone (iris et pupille sont plus sombres que le blanc de l'œil)
    val sorted = inside.map { gray[it] }.sorted()
    val threshold = sorted[(sorted.size * 0.35).toInt()]
    var sw = 0.0; var sx = 0.0; var sy = 0.0
    for (i in inside) {
        val v = gray[i]
        if (v <= threshold) {
            val wgt = (threshold - v + 1).toDouble() // plus c'est sombre, plus le pixel compte
            sw += wgt; sx += wgt * (i % w); sy += wgt * (i / w)
        }
    }
    if (sw <= 0) return null
    val rx = sx / sw; val ry = sy / sw
    // Garde-fou : le centre affiné doit rester près de celui de MediaPipe (sinon reflet, cils, lunettes…)
    return if (hypot(rx - cx, ry - cy) <= radius * 0.6) rx to ry else null
}

/** Positions des cibles de calibration (coordonnées normalisées), dans l'ordre d'affichage. */
object GazeTargets {
    val calibration9 = listOf(0.5 to 0.5, 0.08 to 0.1, 0.5 to 0.1, 0.92 to 0.1, 0.92 to 0.5, 0.92 to 0.9, 0.5 to 0.9, 0.08 to 0.9, 0.08 to 0.5)
    val verification5 = listOf(0.25 to 0.3, 0.75 to 0.3, 0.5 to 0.55, 0.25 to 0.8, 0.75 to 0.8)
}

/** Garde la valeur dans 0..1 (le curseur reste sur l'écran). */
fun clamp01(v: Double) = v.coerceIn(0.0, 1.0)

/**
 * Pixel de l'image caméra d'origine (largeur w, hauteur h, non tournée) qui se retrouve en (x, y)
 * dans l'image tournée de `rotation` degrés dans le sens horaire (convention CameraX).
 */
fun rotatedToSource(x: Int, y: Int, w: Int, h: Int, rotation: Int): Pair<Int, Int> = when ((rotation % 360 + 360) % 360) {
    90 -> y to (h - 1 - x)
    180 -> (w - 1 - x) to (h - 1 - y)
    270 -> (w - 1 - y) to x
    else -> x to y
}

/**
 * Compensation de la rotation de la tête. Quand la tête tourne, les yeux tournent en sens inverse pour garder
 * la même cible : les caractéristiques des yeux changent alors que le point regardé ne bouge pas, d'où la dérive.
 * Pendant l'étape « garde les yeux sur l'étoile et tourne doucement la tête », on mesure de combien chaque
 * caractéristique varie par radian de rotation (pentes), puis on retire cet effet de chaque image :
 * les caractéristiques deviennent celles qu'on aurait mesurées avec la tête dans la position de référence.
 *
 * Ordre des pentes : gazeX, gazeY, lidRatio, meanOpenness, headX, headY, faceX, faceY.
 */
data class HeadCompensation(
    val yaw0: Double, val pitch0: Double,
    val yawSlopes: DoubleArray, val pitchSlopes: DoubleArray
) {
    fun apply(f: GazeFeatures): GazeFeatures {
        if (yawSlopes.size < FIELDS || pitchSlopes.size < FIELDS) return f
        val dy = f.yaw - yaw0; val dp = f.pitch - pitch0
        fun c(i: Int, v: Double) = v - yawSlopes[i] * dy - pitchSlopes[i] * dp
        return f.copy(
            gazeX = c(0, f.gazeX), gazeY = c(1, f.gazeY), lidRatio = c(2, f.lidRatio), meanOpenness = c(3, f.meanOpenness),
            headX = c(4, f.headX), headY = c(5, f.headY), faceX = c(6, f.faceX), faceY = c(7, f.faceY)
        )
    }

    companion object {
        private const val FIELDS = 8
        /** Rotation minimale (écart-type, radians ≈ 2°) pour mesurer une pente fiable sur un axe. */
        const val MIN_MOTION = 0.035

        private fun values(f: GazeFeatures) =
            doubleArrayOf(f.gazeX, f.gazeY, f.lidRatio, f.meanOpenness, f.headX, f.headY, f.faceX, f.faceY)

        /**
         * @param frames images prises pendant que la personne fixe une même cible en tournant la tête
         * @return null si la tête n'a pas assez bougé (aucune compensation possible)
         */
        fun fit(frames: List<GazeFeatures>): HeadCompensation? {
            val ok = frames.filter { it.eyeOpenness > GazeFeatures.CLOSED_EYE }
            if (ok.size < 30) return null
            val n = ok.size.toDouble()
            val my = ok.sumOf { it.yaw } / n; val mp = ok.sumOf { it.pitch } / n
            val y = ok.map { it.yaw - my }; val p = ok.map { it.pitch - mp }
            val syy = y.sumOf { it * it }; val spp = p.sumOf { it * it }; val syp = y.indices.sumOf { y[it] * p[it] }
            val yawOk = sqrt(syy / n) >= MIN_MOTION; val pitchOk = sqrt(spp / n) >= MIN_MOTION
            if (!yawOk && !pitchOk) return null
            val vals = ok.map { values(it) }
            val yawSlopes = DoubleArray(FIELDS); val pitchSlopes = DoubleArray(FIELDS)
            for (i in 0 until FIELDS) {
                val mv = vals.sumOf { it[i] } / n
                val v = vals.map { it[i] - mv }
                val svy = v.indices.sumOf { v[it] * y[it] }; val svp = v.indices.sumOf { v[it] * p[it] }
                val det = syy * spp - syp * syp
                when {
                    yawOk && pitchOk && det > 1e-12 -> {
                        yawSlopes[i] = (svy * spp - svp * syp) / det
                        pitchSlopes[i] = (svp * syy - svy * syp) / det
                    }
                    yawOk -> yawSlopes[i] = svy / syy
                    else -> pitchSlopes[i] = svp / spp
                }
            }
            return HeadCompensation(my, mp, yawSlopes, pitchSlopes)
        }

        /** Rotation gauche-droite et haut-bas (radians) d'après la matrice de pose 4 × 4 de MediaPipe (colonne par colonne). */
        fun anglesFromMatrix(m: FloatArray): Pair<Double, Double>? {
            if (m.size < 16) return null
            // Direction vers laquelle pointe le visage = 3e colonne de la rotation
            val fx = m[8].toDouble(); val fy = m[9].toDouble(); val fz = m[10].toDouble()
            return kotlin.math.atan2(fx, fz) to kotlin.math.atan2(-fy, hypot(fx, fz))
        }
    }
}

/**
 * Images où l'œil est trop fermé (paupière qui cache l'iris : regard très bas, fatigue, début de clignement) :
 * l'iris y est mal repéré et le pointeur saute. Le seuil est propre à chaque personne : on compare l'ouverture
 * à l'ouverture habituelle mesurée sur les dernières secondes (75e centile, peu sensible aux clignements).
 * Regarder la rangée du bas ferme un peu l'œil (≈ −25 %) : ces images restent acceptées.
 */
class OpennessGate(private val ratio: Double = 0.55, private val window: Int = 90, private val warmup: Int = 15) {
    private val recent = ArrayDeque<Double>()

    /** Ouverture habituelle (null tant qu'il n'y a pas assez d'images). */
    fun baseline(): Double? {
        if (recent.size < warmup) return null
        val sorted = recent.sorted()
        return sorted[(sorted.size * 3 / 4).coerceAtMost(sorted.size - 1)]
    }

    /** @return true si l'image est utilisable (œil assez ouvert par rapport à l'habitude). */
    fun accept(openness: Double): Boolean {
        recent.addLast(openness)
        while (recent.size > window) recent.removeFirst()
        val base = baseline() ?: return true
        return openness >= base * ratio
    }

    fun reset() = recent.clear()
}

/**
 * Tremblement du pointeur : médiane des déplacements d'une image à l'autre (les vrais mouvements de l'œil,
 * plus grands, sont ignorés). Sert à régler le filtre de fixation : large quand ça tremble, fin quand c'est stable.
 */
class JitterEstimator(private val window: Int = 30, private val jumpLimit: Double = 0.15) {
    private val steps = ArrayDeque<Double>()
    private var last: Pair<Double, Double>? = null

    fun add(x: Double, y: Double) {
        last?.let { (lx, ly) ->
            val step = hypot(x - lx, y - ly)
            if (step < jumpLimit) {
                steps.addLast(step)
                while (steps.size > window) steps.removeFirst()
            }
        }
        last = x to y
    }

    /** Déplacement médian d'une image à l'autre, en fraction de l'écran (null : pas encore assez de mesures). */
    fun median(): Double? = if (steps.size < 10) null else steps.sorted()[steps.size / 2]

    /** Rayon de fixation adapté : ≈ 0,06 quand c'est stable, jusqu'à 0,09 (≈ 2 cm) quand ça tremble beaucoup. */
    fun fixationRadius(default: Double = 0.07): Double =
        median()?.let { (0.045 + 1.8 * it).coerceIn(0.05, 0.09) } ?: default

    fun reset() { steps.clear(); last = null }
}

/** Conseils pour l'adulte d'après la position de la tête (degrés) et la lumière (0..1) ; vide si tout va bien. */
fun postureAdvice(yawDeg: Double, pitchDeg: Double, brightness: Double): List<String> = buildList {
    when {
        pitchDeg < -15 -> add("Tête penchée vers le bas : relevez la tablette, caméra à hauteur des yeux")
        pitchDeg > 15 -> add("Tête levée : baissez un peu la tablette")
    }
    if (kotlin.math.abs(yawDeg) > 15) add("Tête tournée : placez-vous bien en face de la tablette")
    if (brightness in 0.25..0.55) add("Lumière faible : la caméra ralentit, éclairez le visage de face")
}

/**
 * Recentrage vertical automatique : à chaque picto validé, l'écart vertical entre le point estimé et le centre
 * du picto est appris peu à peu (20 % à chaque fois). Il rattrape un décalage d'ensemble, par exemple la tête
 * plus penchée qu'à la calibration, que la calibration continue (locale, picto par picto) corrige mal ailleurs.
 */
class VerticalOffsetLearner(
    var offset: Double = 0.0,
    private val rate: Double = 0.2,
    private val maxResidual: Double = 0.15, // Au-delà : sans doute pas la case regardée, on n'apprend rien
    private val maxOffset: Double = 0.12
) {
    /** @return true si l'exemple a été retenu */
    fun learn(estX: Double, estY: Double, targetX: Double, targetY: Double): Boolean {
        if (hypot(targetX - estX, targetY - estY) > maxResidual) return false
        offset = (offset + rate * (targetY - estY)).coerceIn(-maxOffset, maxOffset)
        return true
    }
}

/** Écart d'inclinaison de la tête (degrés) au-delà duquel la position ne correspond plus à celle de la calibration. */
const val PITCH_DRIFT_DEGREES = 7.0

/** Conseil si la tête penche nettement plus (ou moins) qu'à la calibration ; null si la position est comparable. */
fun pitchDriftAdvice(pitchDeg: Double, refPitchDeg: Double?): String? {
    if (refPitchDeg == null) return null
    val diff = pitchDeg - refPitchDeg
    return when {
        diff < -PITCH_DRIFT_DEGREES -> "Tête plus penchée qu'à la calibration (%.0f°) : relevez la tablette, ou recalibrez dans cette position".format(-diff)
        diff > PITCH_DRIFT_DEGREES -> "Tête plus levée qu'à la calibration (%.0f°) : baissez la tablette, ou recalibrez dans cette position".format(diff)
        else -> null
    }
}
