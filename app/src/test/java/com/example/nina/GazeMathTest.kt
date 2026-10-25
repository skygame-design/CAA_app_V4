package com.example.nina

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
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
