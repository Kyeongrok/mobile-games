package com.kyeongrok.games

import com.kyeongrok.games.BridgeGame.Action.Put
import com.kyeongrok.games.BridgeGame.Action.Wait
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BridgeTest {
    @Test
    fun programmersExamples() {
        assertEquals(8, BridgeGame.solve(2, 10, listOf(7, 4, 5, 6)))
        assertEquals(101, BridgeGame.solve(100, 100, listOf(10)))
        assertEquals(110, BridgeGame.solve(100, 100, List(10) { 10 }))
    }

    @Test
    fun example1ByHand() {
        val game = BridgeGame.example()
        // 1초 7 올림, 2초 4 못 올림, 3초 7 나가고 4 올림, 4초 5 올림, 5초 4 나감 6 못 올림, 6초 5 나가고 6 올림, 7 · 8초 6 건넘
        listOf(Put, Wait, Put, Put, Wait, Put, Wait, Wait).forEach { assertTrue(it.name, game.act(it)) }
        assertTrue(game.finished)
        assertEquals(8, game.time)
        assertEquals(listOf(0, 1, 2, 3), game.passed.toList())
        assertEquals(0, game.mistakes)
    }

    @Test
    fun wrongMoveCountsAndChangesNothing() {
        val game = BridgeGame.example()
        game.act(Put)
        assertFalse(game.act(Put)) // 7 + 4 > 10
        assertEquals(1, game.mistakes)
        assertEquals(1, game.time)
        assertEquals(1, game.next)
    }

    @Test
    fun autoStepsMatchSolveOnRandomProblems() {
        val random = Random(3)
        repeat(300) {
            val game = BridgeGame.random(random.nextInt(2, 10), random)
            while (!game.finished) assertTrue(game.step())
            assertEquals(BridgeGame.solve(game.bridgeLength, game.limit, game.trucks), game.time)
            assertEquals(game.trucks.indices.toList(), game.passed.toList())
        }
    }
}
