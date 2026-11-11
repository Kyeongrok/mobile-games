package com.kyeongrok.games

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class JewelTest {
    private fun kinds(game: JewelGame) = game.board.map { row -> row.map { it!!.kind } }

    @Test
    fun newBoardHasNoLinesButAMove() {
        repeat(50) {
            val game = JewelGame(Random(it))
            assertTrue(game.matches().isEmpty())
            assertNotNull(game.hint())
        }
    }

    @Test
    fun swapWithoutLineIsUndone() {
        val game = JewelGame(Random(1))
        val before = kinds(game)
        val hint = game.hint()!!
        // 늘어서지 않는 이웃 바꾸기를 찾는다.
        var tried = false
        loop@ for (row in 0 until JewelGame.SIZE) for (col in 0 until JewelGame.SIZE - 1) {
            val a = Cell(row, col)
            val b = Cell(row, col + 1)
            if (a to b == hint) continue
            val probe = JewelGame(Random(1))
            if (!probe.swap(a, b)) {
                assertFalse(game.swap(a, b))
                tried = true
                break@loop
            }
        }
        assertTrue(tried)
        assertEquals(before, kinds(game))
        assertEquals(JewelGame.MOVES, game.movesLeft)
        assertFalse(game.swap(Cell(0, 0), Cell(2, 0)))
    }

    @Test
    fun playingHintScoresAndLeavesFullSettledBoard() {
        val random = Random(7)
        repeat(30) { seed ->
            val game = JewelGame(Random(seed))
            while (!game.over) {
                val (a, b) = game.hint()!!
                assertTrue(game.play(a, b))
                assertTrue(game.board.all { row -> row.all { it != null } })
                assertTrue(game.matches().isEmpty())
                assertNotNull(game.hint())
                assertTrue(game.moveScore >= 30)
            }
            assertEquals(0, game.movesLeft)
            assertTrue(game.score >= JewelGame.MOVES * 30)
            assertFalse(game.play(Cell(0, 0), Cell(0, 1)))
            random.nextInt()
        }
    }

    @Test
    fun chainMultipliesScore() {
        val game = JewelGame(Random(2))
        game.clear(setOf(Cell(0, 0), Cell(0, 1), Cell(0, 2)))
        assertEquals(30, game.score)
        game.clear(setOf(Cell(1, 0), Cell(1, 1), Cell(1, 2)))
        assertEquals(30 + 60, game.score)
        assertEquals(2, game.bestChain)
    }

    @Test
    fun matchesFindsRowsAndColumns() {
        val game = JewelGame(Random(3))
        // 판 전체를 0, 1 이 엇갈리게 놓고 한 줄 · 한 열만 맞춘다.
        for (r in 0 until 8) for (c in 0 until 8) game.board[r][c] = Gem(1000 + r * 8 + c, (r + c) % 2)
        for (c in 2..5) game.board[4][c] = Gem(2000 + c, 5)
        for (r in 0..2) game.board[r][7] = Gem(3000 + r, 4)
        val expected = (2..5).map { Cell(4, it) }.toSet() + (0..2).map { Cell(it, 7) }.toSet()
        assertEquals(expected, game.matches())
    }
}
