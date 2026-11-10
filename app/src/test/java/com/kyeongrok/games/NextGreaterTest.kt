package com.kyeongrok.games

import com.kyeongrok.games.NextGreaterGame.Action.Pop
import com.kyeongrok.games.NextGreaterGame.Action.Push
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class NextGreaterTest {
    @Test
    fun exampleByHand() {
        val game = NextGreaterGame(listOf(2, 3, 3, 5))
        // i=0 push, i=1 pop(0) push, i=2 push(3 < 3 아님), i=3 pop pop push
        listOf(Push, Pop, Push, Push, Pop, Pop, Push).forEach { assertTrue(it.name, game.act(it)) }
        assertTrue(game.finished)
        assertEquals(listOf(3, 5, 5, -1), game.answer.toList())
        assertEquals(0, game.mistakes)
    }

    @Test
    fun wrongMoveCountsAndChangesNothing() {
        val game = NextGreaterGame(listOf(2, 3, 3, 5))
        assertFalse(game.act(Pop)) // 빈 스택에서 꺼낼 수 없다
        assertEquals(1, game.mistakes)
        assertEquals(0, game.index)
        assertTrue(game.stack.isEmpty())
        game.act(Push)
        assertFalse(game.act(Push)) // 2 < 3 이니 꺼내야 한다
        assertEquals(listOf(0), game.stack)
    }

    @Test
    fun autoStepsMatchSolveOnRandomProblems() {
        val random = Random(7)
        repeat(200) {
            val game = NextGreaterGame.random(random)
            while (!game.finished) assertTrue(game.step())
            assertEquals(NextGreaterGame.solve(game.numbers), game.answer.toList())
        }
    }
}
