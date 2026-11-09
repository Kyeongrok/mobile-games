package com.kyeongrok.games

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BrickBreakerTest {
    /** 턴이 끝날 때까지 1/60 초씩. */
    private fun play(game: BrickBreakerGame) {
        repeat(60 * 60) { if (game.step(1f / 60f)) return }
        error("턴이 끝나지 않았습니다")
    }

    @Test
    fun ballStraightUpBreaksBrickAboveAndTurnAdvances() {
        val game = BrickBreakerGame(Random(1))
        game.bricks += Brick(3, 2, 1)   // 쏘는 자리(3.5)의 바로 위
        assertTrue(game.launch(0f, -1f))
        play(game)
        assertTrue(game.bricks.none { it.row == 3 && it.col == 3 })
        assertEquals(2, game.turn)
        assertFalse(game.over)
        assertEquals(BrickBreakerGame.COLS / 2f, game.launchX, 0.01f)
        assertTrue("새 줄이 생긴다", game.bricks.any { it.row == 0 })
    }

    @Test
    fun brickHitOnceLosesOnePoint() {
        val game = BrickBreakerGame(Random(1))
        game.bricks += Brick(3, 2, 5)
        game.launch(0f, -1f)
        play(game)
        val brick = game.bricks.single { it.col == 3 && it.row == 3 }
        assertEquals(4, brick.hp)
    }

    @Test
    fun pickupAddsBallNextTurn() {
        val game = BrickBreakerGame(Random(1))
        game.pickups += BallPickup(3, 4)
        game.launch(0f, -1f)
        play(game)
        assertEquals(2, game.balls)
    }

    @Test
    fun brickReachingBottomRowEndsGame() {
        val game = BrickBreakerGame(Random(1))
        game.bricks += Brick(0, BrickBreakerGame.ROWS - 2, 99)
        game.launch(0f, -1f)
        play(game)
        assertTrue(game.over)
        assertFalse(game.launch(0f, -1f))
    }

    @Test
    fun aimingDownIsRefusedAndFlatAimIsRaised() {
        val game = BrickBreakerGame(Random(1))
        assertNull(game.aimDirection(1f, 0.5f))
        val (_, uy) = game.aimDirection(1f, -0.01f)!!
        assertTrue(uy <= -0.15f + 1e-4f)
    }

    @Test
    fun saveAndRestoreKeepsBoard() {
        val game = BrickBreakerGame.newGame(Random(7))
        game.launch(0.3f, -1f)
        play(game)
        val restored = BrickBreakerGame.restore(game.save())
        assertNotNull(restored)
        assertEquals(game.save(), restored!!.save())
        assertNull(BrickBreakerGame.restore("garbage"))
    }
}
