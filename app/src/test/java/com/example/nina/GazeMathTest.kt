package com.example.nina

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random

class GazeMathTest {

    /** Visage synthétique : yeux horizontaux, iris décalés de (dx, dy) en fraction de la largeur de l'œil. */
    private fun face(dx: Double, dy: Double, open: Double = 0.3): List<FacePoint> {
        val pts = MutableList(478) { FacePoint(0.5f, 0.5f) }
        fun set(i: Int, x: Double, y: Double) { pts[i] = FacePoint(x.toFloat(), y.toFloat()) }
        // Œil droit (côté gauche de l'image) : de 0.35 à 0.45 ; œil gauche : de 0.55 à 0.65
        set(33, 0.35, 0.4); set(133, 0.45, 0.4); set(159, 0.40, 0.4 - open * 0.05); set(145, 0.40, 0.4 + open * 0.05)
        set(263, 0.65, 0.4); set(362, 0.55, 0.4); set(386, 0.60, 0.4 - open * 0.05); set(374, 0.60, 0.4 + open * 0.05)
        set(468, 0.40 + dx * 0.1, 0.4 + dy * 0.1)
        set(473, 0.60 + dx * 0.1, 0.4 + dy * 0.1)
        set(1, 0.5, 0.5)
        return pts
    }

    @Test
    fun features_followTheIrises() {
        val center = GazeFeatures.from(face(0.0, 0.0), aspect = 1f)!!
        val right = GazeFeatures.from(face(0.2, 0.0), aspect = 1f)!!
        val down = GazeFeatures.from(face(0.0, 0.15), aspect = 1f)!!
        assertEquals(0.0, center.gazeX, 1e-6)
        assertEquals(0.2, right.gazeX, 1e-6)
        assertEquals(0.15, down.gazeY, 1e-6)
        assertTrue(center.eyeOpenness > GazeFeatures.CLOSED_EYE)
        assertTrue(GazeFeatures.from(face(0.0, 0.0, open = 0.05), aspect = 1f)!!.eyeOpenness < GazeFeatures.CLOSED_EYE)
        assertNull(GazeFeatures.from(emptyList(), aspect = 1f))
    }

    @Test
    fun calibration_learnsTheMapping_evenWithNoise() {
        // Relation « vraie » regard -> écran, légèrement non linéaire, plus du bruit de mesure
        val rnd = Random(42)
        fun features(tx: Double, ty: Double): DoubleArray {
            val gx = (tx - 0.5) * 0.4 + 0.03 * (tx - 0.5) * (tx - 0.5) + rnd.nextDouble(-0.004, 0.004)
            val gy = (ty - 0.5) * 0.25 + rnd.nextDouble(-0.004, 0.004)
            return GazeFeatures(gx, gy, 0.0, 0.3, 0.5, 0.4, 0.3, 0.3).regressors()
        }
        val samples = GazeTargets.calibration9.map { (x, y) -> GazeSample(features(x, y), x, y) }
        val model = GazeModel.fit(samples)
        assertNotNull(model)
        GazeTargets.verification5.forEach { (x, y) ->
            val (px, py) = model!!.predict(features(x, y))
            assertTrue("x : $px au lieu de $x", abs(px - x) < 0.06)
            assertTrue("y : $py au lieu de $y", abs(py - y) < 0.06)
        }
        val loo = GazeModel.leaveOneOutError(samples)!!
        assertTrue("erreur validation croisée $loo", loo < 0.08)
    }

    @Test
    fun smallHeadMoveAfterCalibration_doesNotMakeTheCursorJump() {
        // Cas mesuré sur la tablette : tête presque immobile pendant la calibration (écart-type 0,002),
        // regard vertical surtout visible par les paupières, puis tête décalée de ~2 mm à la vérification
        val rnd = Random(3)
        fun features(tx: Double, ty: Double, faceShift: Double = 0.0): DoubleArray {
            val gx = (tx - 0.5) * 0.12 + rnd.nextDouble(-0.002, 0.002)
            val gy = -0.06 + (ty - 0.5) * 0.01 + rnd.nextDouble(-0.002, 0.002)
            val lid = 0.4 + (ty - 0.5) * 0.25 + rnd.nextDouble(-0.01, 0.01)
            val open = 0.32 - (ty - 0.5) * 0.08 + rnd.nextDouble(-0.005, 0.005)
            return GazeFeatures(
                // La tête suit un peu le regard (quelques millimètres vers la cible), comme mesuré
                gx, gy, headX = 0.05 + 0.006 * (tx - 0.5) + rnd.nextDouble(-0.001, 0.001), headY = 0.40 + 0.008 * (ty - 0.5) + rnd.nextDouble(-0.001, 0.001) + faceShift,
                faceX = 0.53 + 0.003 * (tx - 0.5) + rnd.nextDouble(-0.001, 0.001) + faceShift, faceY = 0.68 + 0.006 * (ty - 0.5) + rnd.nextDouble(-0.001, 0.001) + faceShift,
                faceSize = 0.18, eyeOpenness = open, lidRatio = lid, meanOpenness = open
            ).regressors()
        }
        val grid = (0 until 3).flatMap { r -> (0 until 6).map { c -> (0.08 + c * 0.17) to (0.2 + r * 0.3) } }
        val model = GazeModel.fit(grid.map { (x, y) -> GazeSample(features(x, y), x, y) })!!
        // Vérification avec la tête légèrement déplacée : l'erreur reste petite (avant la correction : ~0,3 de l'écran)
        val errors = listOf(0.25 to 0.35, 0.75 to 0.35, 0.5 to 0.65, 0.25 to 0.8, 0.75 to 0.8).map { (x, y) ->
            val (px, py) = model.predict(features(x, y, faceShift = 0.01))
            kotlin.math.hypot(px - x, py - y)
        }
        assertTrue("erreur après un petit mouvement de tête : ${errors.average()}", errors.average() < 0.08)
    }

    @Test
    fun fixationFilter_holdsStillDuringFixation_andFollowsRealMoves() {
        val rnd = Random(11)
        val f = FixationFilter(radius = 0.07, jumpFrames = 3)
        // Fixation sur (0.3 ; 0.4) avec un fort tremblement (±0.04) et des sauts isolés
        val outs = (0 until 60).map { i ->
            val spike = if (i % 13 == 0) 0.2 else 0.0
            f.filter(0.3 + rnd.nextDouble(-0.04, 0.04) + spike, 0.4 + rnd.nextDouble(-0.04, 0.04))
        }.drop(10)
        val spread = outs.maxOf { hypot(it.first - 0.3, it.second - 0.4) }
        assertTrue("pointeur stable pendant la fixation : écart max $spread", spread < 0.03)
        // Vrai déplacement vers (0.7 ; 0.6) : suivi en quelques images
        var out = 0.0 to 0.0
        repeat(6) { out = f.filter(0.7 + rnd.nextDouble(-0.02, 0.02), 0.6 + rnd.nextDouble(-0.02, 0.02)) }
        assertTrue("suit le déplacement : $out", hypot(out.first - 0.7, out.second - 0.6) < 0.04)
    }

    @Test
    fun medianWindow_dropsIsolatedSpikes() {
        val m = MedianWindow(5)
        listOf(1.0, 1.1, 0.9, 9.0, 1.0).forEach { m.add(doubleArrayOf(it)) }
        assertEquals(1.0, m.add(doubleArrayOf(1.05))[0], 0.06)
    }

    @Test
    fun distanceCorrection_restoresPositionWhenSittingFurther() {
        val c = GazeCorrection(refFaceSize = 0.19)
        // Même distance : rien ne change
        assertEquals(0.8, c.apply(0.8, 0.3, faceSize = 0.19).first, 1e-9)
        // 1,4 fois plus loin (visage plus petit) : le modèle estime trop près du centre (0.5 + 0.3 / 1.4), on corrige
        val (x, _) = c.apply(0.5 + 0.3 / 1.4, 0.5, faceSize = 0.19 / 1.4)
        assertEquals(0.8, x, 1e-6)
        // Valeurs aberrantes bornées ; décalage de recentrage ajouté
        assertEquals(1.7, c.distanceRatio(0.01), 1e-9)
        val shifted = GazeCorrection(0.19, offsetX = 0.02, offsetY = -0.03).apply(0.5, 0.5, 0.19)
        assertEquals(0.52, shifted.first, 1e-9); assertEquals(0.47, shifted.second, 1e-9)
    }

    @Test
    fun darkCentroid_findsTheIrisWithSubPixelPrecision() {
        // Œil synthétique de 60 × 40 pixels : blanc de l'œil clair, iris sombre de rayon 9 centré en (31,4 ; 19,7), bruit
        val rnd = Random(5)
        val w = 60; val h = 40; val ix = 31.4; val iy = 19.7
        val gray = IntArray(w * h) { i ->
            val x = i % w; val y = i / w
            val d = hypot(x - ix, y - iy)
            val base = if (d < 3.5) 20 else if (d < 9) 70 else 200
            (base + rnd.nextInt(-15, 15)).coerceIn(0, 255)
        }
        // MediaPipe s'est trompé de 2,5 pixels : le repérage fin retrouve le vrai centre
        val (rx, ry) = darkCentroid(gray, w, h, cx = 29.0, cy = 21.0, radius = 9.0, top = 8.0, bottom = 32.0)!!
        assertTrue("x : $rx au lieu de $ix", abs(rx - ix) < 0.6)
        assertTrue("y : $ry au lieu de $iy", abs(ry - iy) < 0.6)
        // Zone sans iris sombre (œil fermé : tout est de la peau) : pas de résultat fantaisiste loin du centre
        val skin = IntArray(w * h) { 180 + rnd.nextInt(-10, 10) }
        val r = darkCentroid(skin, w, h, cx = 29.0, cy = 21.0, radius = 9.0, top = 8.0, bottom = 32.0)
        assertTrue("peau uniforme : $r", r == null || hypot(r.first - 29.0, r.second - 21.0) < 5.4)
    }

    @Test
    fun rotatedToSource_matchesCameraRotation() {
        // Image d'origine 4 × 2 : tournée de 90° (sens horaire), son coin haut-gauche part en haut à droite
        assertEquals(0 to 0, rotatedToSource(1, 0, 4, 2, 90))
        assertEquals(3 to 1, rotatedToSource(0, 3, 4, 2, 90))
        // 270° : le coin haut-gauche part en bas à gauche
        assertEquals(0 to 0, rotatedToSource(0, 3, 4, 2, 270))
        assertEquals(3 to 1, rotatedToSource(1, 0, 4, 2, 270))
        assertEquals(3 to 1, rotatedToSource(0, 0, 4, 2, 180))
        assertEquals(2 to 1, rotatedToSource(2, 1, 4, 2, 0))
    }

    @Test
    fun headCompensation_removesTheDriftCausedByTurningTheHead() {
        // Modèle « physique » : les yeux tournent en sens inverse de la tête pour garder la cible.
        // gazeX = 0,4 × (regard voulu − 0,5) − 0,35 × rotation gauche-droite ; gazeY de même avec la rotation haut-bas
        val rnd = Random(8)
        fun frame(tx: Double, ty: Double, yaw: Double, pitch: Double) = GazeFeatures(
            gazeX = 0.4 * (tx - 0.5) - 0.35 * yaw + rnd.nextDouble(-0.003, 0.003),
            gazeY = 0.25 * (ty - 0.5) - 0.2 * pitch + rnd.nextDouble(-0.003, 0.003),
            headX = 0.05 + 0.6 * yaw, headY = 0.4 + 0.5 * pitch, faceX = 0.5, faceY = 0.6, faceSize = 0.2,
            eyeOpenness = 0.3, lidRatio = 0.45, meanOpenness = 0.3, yaw = yaw, pitch = pitch
        )
        // Étape « tourne la tête » : la cible ne bouge pas, la tête tourne de ±8° dans les deux sens
        val motion = (0 until 120).map { i ->
            val a = i / 120.0 * 2 * Math.PI
            frame(0.5, 0.5, yaw = 0.14 * kotlin.math.sin(a), pitch = 0.12 * kotlin.math.sin(2 * a))
        }
        val comp = HeadCompensation.fit(motion)!!
        assertEquals(-0.35, comp.yawSlopes[0], 0.03)
        assertEquals(-0.2, comp.pitchSlopes[1], 0.03)
        // Calibration tête immobile, puis utilisation la tête tournée de 6° : la compensation garde le pointeur en place
        val grid = (0 until 3).flatMap { r -> (0 until 5).map { c -> (0.1 + c * 0.2) to (0.2 + r * 0.3) } }
        fun errorAfterTurn(c: HeadCompensation?): Double {
            fun regs(f: GazeFeatures) = (c?.apply(f) ?: f).regressors()
            val model = GazeModel.fit(grid.map { (x, y) -> GazeSample(regs(frame(x, y, 0.0, 0.0)), x, y) }, compensation = c)!!
            return grid.map { (x, y) ->
                val (px, py) = model.predict(model.regressorsOf(frame(x, y, yaw = 0.1, pitch = 0.0)))
                hypot(px - x, py - y)
            }.average()
        }
        val without = errorAfterTurn(null); val with = errorAfterTurn(comp)
        assertTrue("avec compensation $with, sans $without", with < without * 0.3)
        // Tête immobile pendant l'étape : rien à mesurer
        assertNull(HeadCompensation.fit((0 until 60).map { frame(0.5, 0.5, 0.0, 0.0) }))
    }

    @Test
    fun anglesFromMatrix_readsYawAndPitch() {
        // Rotation de 0,3 rad autour de l'axe vertical : le visage regarde de côté
        val c = kotlin.math.cos(0.3).toFloat(); val s = kotlin.math.sin(0.3).toFloat()
        val m = floatArrayOf(c, 0f, -s, 0f, 0f, 1f, 0f, 0f, s, 0f, c, 0f, 0f, 0f, -40f, 1f)
        val (yaw, pitch) = HeadCompensation.anglesFromMatrix(m)!!
        assertEquals(0.3, yaw, 1e-5)
        assertEquals(0.0, pitch, 1e-5)
    }

    @Test
    fun opennessGate_dropsHalfClosedEyes_butKeepsLookingDown() {
        val g = OpennessGate()
        repeat(40) { assertTrue(g.accept(0.30 + (it % 5) * 0.01)) } // Ouverture habituelle ≈ 0,32
        assertTrue("regard vers la rangée du bas (−25 %)", g.accept(0.24))
        assertTrue("paupière qui cache l'iris", !g.accept(0.12))
        // Au démarrage, rien n'est refusé (pas encore de référence)
        assertTrue(OpennessGate().accept(0.05))
    }

    @Test
    fun jitterEstimator_widensTheFixationRadiusWhenThePointerShakes() {
        val rnd = Random(4)
        val calm = JitterEstimator(); val shaky = JitterEstimator()
        repeat(40) {
            calm.add(0.5 + rnd.nextDouble(-0.003, 0.003), 0.5 + rnd.nextDouble(-0.003, 0.003))
            shaky.add(0.5 + rnd.nextDouble(-0.05, 0.05), 0.5 + rnd.nextDouble(-0.05, 0.05))
        }
        assertTrue("stable : ${calm.fixationRadius()}", calm.fixationRadius() < 0.06)
        assertTrue("tremblant : ${shaky.fixationRadius()}", shaky.fixationRadius() > 0.10)
        assertEquals(0.07, JitterEstimator().fixationRadius(), 1e-9) // Pas encore mesuré : valeur par défaut
    }

    @Test
    fun postureAdvice_warnsAboutHeadAndLight() {
        assertTrue(postureAdvice(2.0, -5.0, 0.66).isEmpty())
        assertTrue(postureAdvice(0.0, -20.0, 0.66).single().contains("relevez"))
        assertTrue(postureAdvice(25.0, 0.0, 0.66).single().contains("en face"))
        assertTrue(postureAdvice(0.0, 0.0, 0.46).single().contains("Lumière faible"))
    }

    @Test
    fun notEnoughPoints_noModel() {
        val few = listOf(GazeSample(doubleArrayOf(0.0, 0.0), 0.5, 0.5), GazeSample(doubleArrayOf(1.0, 1.0), 0.6, 0.6))
        assertNull(GazeModel.fit(few))
    }

    @Test
    fun median_ignoresOutliers() {
        val frames = listOf(doubleArrayOf(1.0), doubleArrayOf(1.1), doubleArrayOf(50.0), doubleArrayOf(0.9), doubleArrayOf(1.0))
        assertEquals(1.0, medianFeatures(frames)!![0], 1e-9)
    }

    @Test
    fun oneEuro_smoothsJitterButFollowsMoves() {
        val f = OneEuroFilter(minCutoff = 1.0, beta = 0.02)
        var t = 0L
        // Tremblement autour de 0.5 : la sortie reste proche de 0.5
        var out = 0.0
        repeat(60) { i -> t += 33; out = f.filter(0.5 + if (i % 2 == 0) 0.03 else -0.03, t) }
        assertTrue("tremblement lissé : $out", abs(out - 0.5) < 0.02)
        // Déplacement franc vers 0.9 : la sortie y arrive en moins d'une seconde
        repeat(30) { t += 33; out = f.filter(0.9, t) }
        assertTrue("suit le déplacement : $out", abs(out - 0.9) < 0.03)
    }
}
