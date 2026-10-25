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
    }

    @Test
    fun dwell_toleratesBriefExcursions_thenHandsOverAfterALongerOne() {
        val d = GazeDwell(dwellMs = 1000, cooldownMs = 1500, graceMs = 300)
        var t = 0L
        repeat(11) { t += 50; d.update(3, t) } // 500 ms sur la case 3
        // Écart de 200 ms vers la case 4 (tremblement, aimant) : rien n'est perdu
        repeat(4) { t += 50; d.update(4, t) }
        t += 50
        val (p, _) = d.update(3, t)
        assertEquals(0.55f, p, 0.06f)
        assertEquals(3, d.zone)
        // Regard durable sur la case 4 : l'anneau de la 3 se vide, puis la 4 prend le relais
        var last: Pair<Float, Int?> = 0f to null
        repeat(30) { t += 50; last = d.update(4, t) }
        assertEquals(4, d.zone)
        assertTrue("la case 4 se remplit : ${last.first}", last.first > 0.3f)
    }

    @Test
    fun dwell_pausesInTheGapBetweenCells() {
        val d = GazeDwell(dwellMs = 1000, cooldownMs = 1500, graceMs = 300, gapPauseMs = 1000)
        var t = 0L
        repeat(11) { t += 50; d.update(3, t) } // 500 ms sur la case 3
        repeat(14) { t += 50; d.update(null, t) } // 700 ms dans l'espace vide : pause, rien de perdu
        t += 50
        assertEquals(0.55f, d.update(3, t).first, 0.06f)
    }

    @Test
    fun magnet_keepsTheChosenCellWhileThePointStaysNearby() {
        val m = GazeMagnet(switchMs = 250)
        var t = 0L
        repeat(10) { t += 33; m.update(grid, grid[5].centerX, grid[5].centerY, t) }
        assertEquals(5, m.current)
        // Point dans l'espace entre la case 5 et la case 6, un peu plus près de la 6 : on reste sur la 5
        val between = (grid[5].right * 0.4 + grid[6].left * 0.6)
        repeat(20) { t += 33; m.update(grid, between, grid[5].centerY, t) }
        assertEquals(5, m.current)
    }

    @Test
    fun magnet_smallButtonsAttractFromAMinimumDistance() {
        // Petit bouton (2 % × 3 % de l'écran) et point à 0,05 hauteur d'écran de son bord
        val small = listOf(NormRect(0.90, 0.02, 0.92, 0.05))
        val proportional = GazeMagnet(switchMs = 0)
        repeat(3) { proportional.update(small, 0.91, 0.10, it * 50L) }
        assertNull("marge proportionnelle seule : trop petite", proportional.current)
        val withReach = GazeMagnet(switchMs = 0, minReach = 0.08, aspect = 1.6)
        repeat(3) { withReach.update(small, 0.91, 0.10, it * 50L) }
        assertEquals(0, withReach.current)
    }

    @Test
    fun continuousCalibration_blendsKnownPlaces_andAddsNewOnes() {
        val base = listOf(GazeSample(doubleArrayOf(1.0, 1.0), 0.2, 0.2), GazeSample(doubleArrayOf(2.0, 2.0), 0.8, 0.8))
        // Même picto (à 1 % près) : mélange 60 % ancien / 40 % nouveau, pas de point en plus
        val blended = GazeCalibrationPlan.learn(base, GazeSample(doubleArrayOf(2.0, 1.0), 0.21, 0.2))
        assertEquals(2, blended.size)
        assertEquals(1.4, blended[0].features[0], 1e-9)
        assertEquals(0.204, blended[0].targetX, 1e-9)
        // Nouvel endroit : ajouté
        assertEquals(3, GazeCalibrationPlan.learn(base, GazeSample(doubleArrayOf(0.0, 0.0), 0.5, 0.5)).size)
        // Plafond atteint : seuls les endroits connus sont mis à jour
        assertEquals(2, GazeCalibrationPlan.learn(base, GazeSample(doubleArrayOf(0.0, 0.0), 0.5, 0.5), max = 2).size)
    }

    @Test
    fun correctionInvert_undoesApply() {
        val c = GazeCorrection(refFaceSize = 0.19, offsetX = 0.02, offsetY = -0.01)
        val (mx, my) = c.invert(0.8, 0.3, faceSize = 0.15)
        val (x, y) = c.apply(mx, my, faceSize = 0.15)
        assertEquals(0.8, x, 1e-9); assertEquals(0.3, y, 1e-9)
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
