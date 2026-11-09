package com.kyeongrok.games

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.sqrt
import kotlin.random.Random

/** 벽돌 하나. 칸 좌표(열 · 줄)와 남은 맞을 횟수. */
class Brick(val col: Int, var row: Int, var hp: Int)

/** 공을 하나 늘려 주는 고리. 공이 지나가면 먹는다. */
class BallPickup(val col: Int, var row: Int)

class FlyingBall(var x: Float, var y: Float, var vx: Float, var vy: Float)

/**
 * 스와이프 벽돌깨기. 좌표는 칸 단위(가로 [COLS] 칸, 세로 [HEIGHT] 칸)이고 y 는 아래로 갈수록 커진다.
 *
 * 한 턴: 방향을 정해 쏘면([launch]) 가진 공이 차례로 나가 벽 · 천장 · 벽돌에 튕기고, 바닥에 닿으면 멈춘다.
 * 처음 닿은 공 자리가 다음 쏘는 자리다. 공이 다 내려오면 벽돌이 한 줄 내려오고 맨 위에 새 줄이 생긴다.
 * 벽돌이 맨 아래 줄([ROWS] - 1)까지 내려오면 끝이다.
 */
class BrickBreakerGame(private val random: Random = Random) {
    val bricks = mutableListOf<Brick>()
    val pickups = mutableListOf<BallPickup>()
    val flying = mutableListOf<FlyingBall>()

    var turn = 1
        private set
    var balls = 1
        private set
    var launchX = COLS / 2f
        private set
    var over = false
        private set
    /** 이번 턴에 먹은 고리 수 — 턴이 끝나면 공에 더한다. */
    var gained = 0
        private set
    /** 이번 턴에 처음 바닥에 닿은 공의 자리. */
    var landingX: Float? = null
        private set

    private var toLaunch = 0
    private var launchTimer = 0f
    private var dirX = 0f
    private var dirY = -1f

    val isFlying: Boolean get() = toLaunch > 0 || flying.isNotEmpty()
    /** 아직 쏘지 않고 쏘는 자리에 남은 공. */
    val waiting: Int get() = toLaunch

    /** 손가락 방향(쏘는 자리에서 본 dx, dy)을 쏠 수 있는 방향으로 — 아래쪽이면 null, 너무 눕히면 조금 세운다. */
    fun aimDirection(dx: Float, dy: Float): Pair<Float, Float>? {
        if (dy >= 0f) return null
        val length = hypot(dx, dy)
        if (length == 0f) return null
        var ux = dx / length
        var uy = dy / length
        if (-uy < MIN_VY) {
            uy = -MIN_VY
            ux = (if (ux < 0f) -1f else 1f) * sqrt(1f - uy * uy)
        }
        return ux to uy
    }

    fun launch(dx: Float, dy: Float): Boolean {
        if (over || isFlying) return false
        val (ux, uy) = aimDirection(dx, dy) ?: return false
        dirX = ux
        dirY = uy
        toLaunch = balls
        launchTimer = 0f
        landingX = null
        return true
    }

    /** 조준선 — 쏘는 자리에서 그 방향으로 벽 · 천장에 튕기며 length 칸만큼의 꺾은선. */
    fun guide(dx: Float, dy: Float, length: Float): List<Pair<Float, Float>> {
        var (ux, uy) = aimDirection(dx, dy) ?: return emptyList()
        var x = launchX
        var y = FLOOR
        var left = length
        val points = mutableListOf(x to y)
        while (left > 0f && points.size < 6) {
            val toWall = when {
                ux > 0f -> (COLS - RADIUS - x) / ux
                ux < 0f -> (RADIUS - x) / ux
                else -> Float.MAX_VALUE
            }
            val toTop = if (uy < 0f) (y - RADIUS) / -uy else Float.MAX_VALUE
            val t = minOf(toWall, toTop, left)
            x += ux * t
            y += uy * t
            left -= t
            points += x to y
            if (t == toWall) ux = -ux
            if (t == toTop) uy = -uy
        }
        return points
    }

    /** dt 초만큼 움직인다. 이 걸음으로 턴이 끝났으면 true. */
    fun step(dt: Float): Boolean {
        if (!isFlying) return false
        launchTimer -= dt
        while (toLaunch > 0 && launchTimer <= 0f) {
            flying += FlyingBall(launchX, FLOOR, dirX * SPEED, dirY * SPEED)
            toLaunch--
            launchTimer += INTERVAL
        }
        val iterator = flying.iterator()
        while (iterator.hasNext()) {
            if (advance(iterator.next(), dt)) iterator.remove()
        }
        if (!isFlying) {
            endTurn()
            return true
        }
        return false
    }

    /** 날고 있는 공을 모두 거둬 턴을 끝낸다. 아직 아무 공도 안 닿았으면 쏜 자리 그대로. */
    fun recall() {
        if (!isFlying) return
        flying.clear()
        toLaunch = 0
        endTurn()
    }

    /** 공 하나를 dt 만큼 — 벽돌을 건너뛰지 않게 잘게 나눠 움직인다. 바닥에 닿았으면 true. */
    private fun advance(ball: FlyingBall, dt: Float): Boolean {
        val steps = ceil(SPEED * dt / MAX_STEP).toInt().coerceAtLeast(1)
        val h = dt / steps
        repeat(steps) {
            ball.x += ball.vx * h
            ball.y += ball.vy * h
            if (ball.x < RADIUS) { ball.x = RADIUS; ball.vx = abs(ball.vx) }
            if (ball.x > COLS - RADIUS) { ball.x = COLS - RADIUS; ball.vx = -abs(ball.vx) }
            if (ball.y < RADIUS) { ball.y = RADIUS; ball.vy = abs(ball.vy) }
            if (ball.y >= FLOOR && ball.vy > 0f) {
                if (landingX == null) landingX = ball.x.coerceIn(RADIUS, COLS - RADIUS)
                return true
            }
            hitBrick(ball, h)
            collect(ball)
        }
        return false
    }

    private fun hitBrick(ball: FlyingBall, h: Float) {
        for (brick in bricks) {
            val left = brick.col + GAP
            val right = brick.col + 1 - GAP
            val top = brick.row + GAP
            val bottom = brick.row + 1 - GAP
            val cx = ball.x.coerceIn(left, right)
            val cy = ball.y.coerceIn(top, bottom)
            val dx = ball.x - cx
            val dy = ball.y - cy
            if (dx * dx + dy * dy >= RADIUS * RADIUS) continue
            when {
                // 가운데가 벽돌 안까지 들어왔으면 들어온 쪽으로 되돌린다.
                dx == 0f && dy == 0f -> {
                    ball.x -= ball.vx * h
                    ball.y -= ball.vy * h
                    if (ball.x < left || ball.x > right) ball.vx = -ball.vx else ball.vy = -ball.vy
                }
                abs(dx) > abs(dy) -> {
                    ball.vx = if (dx > 0f) abs(ball.vx) else -abs(ball.vx)
                    ball.x = cx + (if (dx > 0f) RADIUS else -RADIUS)
                }
                else -> {
                    ball.vy = if (dy > 0f) abs(ball.vy) else -abs(ball.vy)
                    ball.y = cy + (if (dy > 0f) RADIUS else -RADIUS)
                }
            }
            keepAngle(ball)
            brick.hp--
            if (brick.hp <= 0) bricks.remove(brick)
            return
        }
    }

    /** 거의 수평으로 튕기면 양쪽 벽만 오가며 안 내려오므로 조금 세운다. */
    private fun keepAngle(ball: FlyingBall) {
        if (abs(ball.vy) >= SPEED * MIN_VY) return
        ball.vy = (if (ball.vy < 0f) -1f else 1f) * SPEED * MIN_VY
        ball.vx = (if (ball.vx < 0f) -1f else 1f) * sqrt(SPEED * SPEED - ball.vy * ball.vy)
    }

    private fun collect(ball: FlyingBall) {
        val pickup = pickups.firstOrNull {
            hypot(ball.x - (it.col + 0.5f), ball.y - (it.row + 0.5f)) < RADIUS + PICKUP_RADIUS
        } ?: return
        pickups.remove(pickup)
        gained++
    }

    private fun endTurn() {
        balls += gained
        gained = 0
        landingX?.let { launchX = it }
        landingX = null
        turn++
        bricks.forEach { it.row++ }
        pickups.forEach { it.row++ }
        // 맨 아래까지 내려온 고리는 저절로 먹는다.
        val reached = pickups.filter { it.row >= ROWS - 1 }
        balls += reached.size
        pickups.removeAll(reached)
        if (bricks.any { it.row >= ROWS - 1 }) over = true else spawnRow()
    }

    /** 맨 위에 새 줄 — 벽돌 몇 개(맞을 횟수는 턴 수, 가끔 두 배)와 고리 하나. */
    private fun spawnRow() {
        val most = when {
            turn < 5 -> 3
            turn < 15 -> 4
            else -> 5
        }
        val count = random.nextInt(2, most + 1)
        val cols = (0 until COLS).shuffled(random)
        cols.take(count).forEach { col ->
            val hp = if (turn >= 10 && random.nextFloat() < 0.2f) turn * 2 else turn
            bricks += Brick(col, 0, hp)
        }
        pickups += BallPickup(cols[count], 0)
    }

    /** 턴 사이의 판을 글 한 줄로(날고 있는 공은 담지 않는다). */
    fun save(): String =
        "v1|$turn|$balls|$launchX|${if (over) 1 else 0}|" +
            bricks.joinToString(";") { "${it.col},${it.row},${it.hp}" } + "|" +
            pickups.joinToString(";") { "${it.col},${it.row}" }

    companion object {
        const val COLS = 7
        const val ROWS = 9
        const val HEIGHT = 10f
        const val RADIUS = 0.13f
        const val GAP = 0.05f
        const val PICKUP_RADIUS = 0.22f
        const val FLOOR = HEIGHT - RADIUS
        /** 공 빠르기(칸/초). */
        const val SPEED = 16f
        /** 공 사이 간격(초). */
        private const val INTERVAL = 0.07f
        /** 세로 빠르기의 최소 비율 — 거의 수평으로 쏘거나 튕기지 않게. */
        private const val MIN_VY = 0.15f
        /** 한 번에 움직이는 최대 거리(칸) — 공 반지름보다 작아야 벽돌을 건너뛰지 않는다. */
        private const val MAX_STEP = 0.06f

        fun newGame(random: Random = Random): BrickBreakerGame = BrickBreakerGame(random).apply { spawnRow() }

        fun restore(text: String, random: Random = Random): BrickBreakerGame? = try {
            val parts = text.split('|')
            require(parts.size == 7 && parts[0] == "v1")
            BrickBreakerGame(random).apply {
                turn = parts[1].toInt()
                balls = parts[2].toInt()
                launchX = parts[3].toFloat().coerceIn(RADIUS, COLS - RADIUS)
                over = parts[4] == "1"
                parts[5].split(';').filter { it.isNotEmpty() }.forEach {
                    val (c, r, hp) = it.split(',').map(String::toInt)
                    bricks += Brick(c, r, hp)
                }
                parts[6].split(';').filter { it.isNotEmpty() }.forEach {
                    val (c, r) = it.split(',').map(String::toInt)
                    pickups += BallPickup(c, r)
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
