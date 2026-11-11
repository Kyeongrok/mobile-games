package com.kyeongrok.games

import com.kyeongrok.games.GemMazeGame.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GemMazeTest {
    private val cols = GemMazeGame.COLS
    private val rows = GemMazeGame.ROWS

    @Test
    fun emptyBoardPathGoesStraightDown() {
        val path = GemMazeGame.shortestPath(emptySet())!!
        assertEquals(rows, path.size)
        assertEquals(GemMazeGame.START, path.first())
        assertEquals(GemMazeGame.EXIT, path.last())
    }

    @Test
    fun wallWithoutGapHasNoPath() {
        val wall = (0 until cols).map { Cell(5, it) }.toSet()
        assertNull(GemMazeGame.shortestPath(wall))
        assertNotNull(GemMazeGame.shortestPath(wall - Cell(5, 0)))
    }

    @Test
    fun cannotPlaceLastBlockingCell() {
        val game = GemMazeGame(Random(1))
        (1 until cols).forEach { game.rocks += Cell(5, it) }
        assertFalse(game.canPlace(Cell(5, 0)))
        assertFalse(game.canPlace(GemMazeGame.START))
        assertTrue(game.canPlace(Cell(3, 3)))
    }

    @Test
    fun keepOneTurnsOthersToRocks() {
        val game = GemMazeGame(Random(2))
        listOf(Cell(2, 0), Cell(2, 1), Cell(2, 2), Cell(2, 3)).forEach { assertNotNull(game.place(it)) }
        assertEquals(Phase.Choose, game.phase)
        assertNull(game.place(Cell(3, 3)))
        val kept = game.placed[1]
        assertTrue(game.keep(kept))
        assertEquals(listOf(kept), game.towers)
        assertEquals(setOf(Cell(2, 0), Cell(2, 2), Cell(2, 3)), game.rocks)
        assertEquals(Phase.Wave, game.phase)
    }

    @Test
    fun mergeRaisesLevelOnlyWithPair() {
        val game = GemMazeGame(Random(3))
        listOf(Cell(2, 0), Cell(2, 1), Cell(2, 2), Cell(2, 3)).forEach { game.place(it) }
        game.placed.clear()
        game.placed += MazeGem(MazeGemKind.Ice, 2, 2, 0)
        game.placed += MazeGem(MazeGemKind.Ice, 2, 2, 1)
        game.placed += MazeGem(MazeGemKind.Fire, 2, 2, 2)
        assertFalse(game.canMerge(game.placed[2]))
        assertFalse(game.keep(game.placed[2], merge = true))
        assertTrue(game.keep(game.placed[0], merge = true))
        assertEquals(3, game.towers.single().level)
        assertEquals(MazeGemKind.Ice, game.towers.single().kind)
    }

    @Test
    fun undefendedWaveCostsLives() {
        val game = GemMazeGame(Random(4))
        game.finishPlacing()
        assertEquals(Phase.Wave, game.phase)
        while (!game.step(0.05f)) Unit
        assertEquals(GemMazeGame.LIVES - GemMazeGame.WAVE_SIZE, game.lives)
        assertEquals(2, game.wave)
        assertEquals(Phase.Build, game.phase)
    }

    /** 간단한 전략(길 옆에 놓고 가장 높은 등급을 남김, 합칠 수 있으면 합침)으로 몇 웨이브 버티는지 — 너무 쉽거나 어렵지 않게. */
    @Test
    fun simpleStrategySurvivesSomeWavesButNotForever() {
        val results = (0 until 20).map { seed ->
            val game = GemMazeGame(Random(seed))
            val random = Random(seed + 100)
            var guard = 0
            while (game.phase != Phase.Over && game.wave <= 60 && guard++ < 200) {
                while (game.phase == Phase.Build) {
                    val near = game.path.flatMap { c -> listOf(Cell(c.row, c.col - 1), Cell(c.row, c.col + 1), Cell(c.row + 1, c.col)) }
                        .filter { game.canPlace(it) }
                    val cell = near.randomOrNull(random)
                    if (cell == null) game.finishPlacing() else game.place(cell)
                }
                if (game.phase == Phase.Choose) {
                    val merge = game.placed.filter { game.canMerge(it) }.maxByOrNull { it.level }
                    if (merge != null) game.keep(merge, merge = true) else game.keep(game.placed.maxBy { it.level })
                }
                while (game.phase == Phase.Wave) game.step(0.05f)
            }
            game.cleared
        }
        println("simple strategy cleared: ${results.sorted()} avg ${results.average()}")
        assertTrue(results.average() in 6.0..45.0)
    }
}
