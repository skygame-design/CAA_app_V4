package com.example.nina

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random

class GazeSelectionTest {

    /** Grille de 4 × 3 cases comme l'accueil à 4 pictos par rangée. */
    private val grid = (0 until 3).flatMap { r -> (0 until 4).map { c ->
        NormRect(0.15 + c * 0.18, 0.15 + r * 0.25, 0.15 + c * 0.18 + 0.16, 0.15 + r * 0.25 + 0.22)
    } }

    @Test
    fun localCorrection_fixesBiasAtCalibratedPictos() {
        // Relation non linéaire que la régression globale apprend mal au centre (comme sur la tablette)
        val rnd = Random(7)
        fun features(tx: Double, ty: Double): DoubleArray {
            val bump = 0.03 * kotlin.math.exp(-((tx - 0.5) * (tx - 0.5) + (ty - 0.5) * (ty - 0.5)) / 0.01)
            val gx = (tx - 0.5) * 0.4 + bump + rnd.nextDouble(-0.002, 0.002)
            val gy = (ty - 0.5) * 0.25 + rnd.nextDouble(-0.002, 0.002)
            return GazeFeatures(gx, gy, 0.0, 0.3, 0.5, 0.4, 0.3, 0.3).regressors()
        }
        val targets = grid.map { it.centerX to it.centerY }
        val samples = targets.map { (x, y) -> GazeSample(features(x, y), x, y) }
        val model = GazeModel.fit(samples)!!
        var globalError = 0.0; var correctedError = 0.0
        targets.forEach { (x, y) ->
            val f = features(x, y)
            globalError += model.predictGlobal(f).let { hypot(it.first - x, it.second - y) }
            correctedError += model.predict(f).let { hypot(it.first - x, it.second - y) }
        }
        assertTrue("correction locale : $correctedError < $globalError", correctedError < globalError * 0.6)
    }

    @Test
    fun magnet_snapsToNearestCell_andIgnoresBriefJumps() {
        val m = GazeMagnet(switchMs = 250)
        var t = 0L
        // Regard un peu en dehors de la case 5 (marge) : rattaché à la case 5 après la stabilisation
        repeat(10) { t += 33; m.update(grid, grid[5].centerX, grid[5].bottom + 0.01, t) }
        assertEquals(5, m.current)
        // Saut bref (100 ms) vers la case 6 : la sélection ne bouge pas
        repeat(3) { t += 33; m.update(grid, grid[6].centerX, grid[6].centerY, t) }
        t += 33; m.update(grid, grid[5].centerX, grid[5].centerY, t)
        assertEquals(5, m.current)
        // Regard franc et durable sur la case 6 : changement
        repeat(12) { t += 33; m.update(grid, grid[6].centerX, grid[6].centerY, t) }
        assertEquals(6, m.current)
        // Regard très loin de toute case : on garde la dernière tant que ce n'est pas franc, puis plus rien
        repeat(12) { t += 33; m.update(grid, 0.02, 0.98, t) }
        assertNull(m.current)
    }

    @Test
    fun dwell_activatesOnce_thenNeedsCooldown() {
        val d = GazeDwell(dwellMs = 1000, cooldownMs = 1500)
        var t = 0L
        var fired = 0
        repeat(40) { t += 50; if (d.update(3, t).second == 3) fired++ } // 2 s sur la même case
        assertEquals("une seule activation en 2 s", 1, fired)
        repeat(60) { t += 50; if (d.update(3, t).second == 3) fired++ } // 3 s de plus : nouvelle activation après le repos
        assertEquals(2, fired)
        // Changer de case remet la progression à zéro
        val (p, _) = d.update(4, t + 50)
        assertTrue(p < 0.1f)
    }

    @Test
    fun calibrationTargets_dedupedSpreadAndCapped() {
        val centers = grid.map { it.centerX to it.centerY } + listOf(0.05 to 0.2, 0.051 to 0.201, 0.95 to 0.8)
        val all = GazeCalibrationPlan.pickTargets(centers, max = 18)
        assertEquals(14, all.size) // un doublon retiré
        val few = GazeCalibrationPlan.pickTargets(centers, max = 6)
        assertEquals(6, few.size)
        val minGap = few.flatMap { a -> few.filter { it != a }.map { b -> hypot(a.first - b.first, a.second - b.second) } }.min()
        assertTrue("cibles bien réparties : $minGap", minGap > 0.15)
    }

    @Test
    fun merge_replacesOnlyRemeasuredPictos() {
        val old = listOf(GazeSample(doubleArrayOf(1.0), 0.2, 0.2), GazeSample(doubleArrayOf(2.0), 0.8, 0.8))
        val new = listOf(GazeSample(doubleArrayOf(9.0), 0.21, 0.2))
        val merged = GazeCalibrationPlan.merge(old, new)
        assertEquals(2, merged.size)
        assertEquals(9.0, merged.first { abs(it.targetX - 0.21) < 1e-9 }.features[0], 0.0)
        assertTrue(merged.any { it.targetX == 0.8 })
    }
}
