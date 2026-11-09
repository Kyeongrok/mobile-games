package com.kyeongrok.games

import android.app.Application
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

private typealias BB = BrickBreakerGame

private val BrickInk = Color(0xFF1F1F1F)

/** 벽돌깨기 판과 최고 기록. 판은 턴이 끝날 때마다 저장해 다시 켜면 그 턴부터 이어 한다. */
class BrickBreakerViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("brick-breaker", Context.MODE_PRIVATE)

    var game: BB = preferences.getString("game", null)?.let { BB.restore(it) } ?: BB.newGame()
        private set
    /** 판이 바뀔 때마다 늘린다 — 그리는 쪽이 이것을 읽어 다시 그린다. */
    var frame by mutableIntStateOf(0)
        private set
    var flying by mutableStateOf(game.isFlying)
        private set
    var best by mutableIntStateOf(preferences.getInt("best", 0))
        private set
    /** 이 판을 시작할 때의 최고 기록 — 끝났을 때 새 기록인지 본다. */
    var bestAtStart = best
        private set
    /** 공 빠르기 배율(1 · 2 · 3). 턴이 끝나면 1로. */
    var speed by mutableIntStateOf(1)

    fun launch(dx: Float, dy: Float) {
        if (game.launch(dx, dy)) {
            flying = true
            frame++
        }
    }

    fun step(dt: Float) {
        val ended = game.step(dt)
        frame++
        if (ended) turnEnded()
    }

    fun recall() {
        if (!game.isFlying) return
        game.recall()
        frame++
        turnEnded()
    }

    fun newGame() {
        game = BB.newGame()
        flying = false
        speed = 1
        bestAtStart = best
        frame++
        save()
    }

    private fun turnEnded() {
        flying = false
        speed = 1
        val survived = game.turn - 1
        if (survived > best) {
            best = survived
            preferences.edit().putInt("best", best).apply()
        }
        save()
    }

    private fun save() {
        preferences.edit().putString("game", game.save()).apply()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrickBreakerTopBar(onBack: () -> Unit) {
    val viewModel: BrickBreakerViewModel = viewModel()
    TopAppBar(
        title = { Text("벽돌깨기") },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
            }
        },
        actions = { TextButton(onClick = viewModel::newGame) { Text("새 게임") } },
    )
}

@Composable
fun BrickBreakerScreen() {
    val viewModel: BrickBreakerViewModel = viewModel()
    // 판이 바뀔 때마다 아래 글자도 다시 쓴다.
    @Suppress("UNUSED_VARIABLE") val frame = viewModel.frame
    val game = viewModel.game
    /** 손가락 자리(칸 단위). 끄는 동안만 있다. */
    var aim by remember { mutableStateOf<Offset?>(null) }

    // 공이 나는 동안만 화면이 그려질 때마다 움직인다. 화면이 끊기는 기기에서도 느려지지 않게 한 번에 0.1초까지는 그대로 움직이고,
    // 그보다 오래 멈췄다 오면(백그라운드) 크게 건너뛰지 않게 자른다.
    if (viewModel.flying) {
        LaunchedEffect(Unit) {
            var last = 0L
            while (true) {
                withFrameNanos { now ->
                    if (last != 0L) viewModel.step(((now - last) / 1e9f).coerceAtMost(0.1f) * viewModel.speed)
                    last = now
                }
            }
        }
    }

    if (game.over) {
        val survived = game.turn - 1
        AlertDialog(
            onDismissRequest = {},
            title = { Text(if (survived > viewModel.bestAtStart) "새 기록!" else "게임 끝") },
            text = { Text("${survived}턴을 버텼습니다 · 공 ${game.balls}개\n최고 기록 ${viewModel.best}턴") },
            confirmButton = { TextButton(onClick = viewModel::newGame) { Text("새 게임") } },
        )
    }

    Column(Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "턴 ${game.turn} · 공 ${game.balls}개" + (if (game.gained > 0) " (+${game.gained})" else ""),
                style = MaterialTheme.typography.titleMedium,
            )
            Text("최고 ${viewModel.best}턴", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val cell = min(maxWidth / BB.COLS, maxHeight / BB.HEIGHT)
            val cellPx = with(LocalDensity.current) { cell.toPx() }
            BrickField(
                viewModel,
                aim,
                Modifier
                    .size(cell * BB.COLS, cell * BB.HEIGHT)
                    .clipToBounds()
                    .pointerInput(cellPx) {
                        detectDragGestures(
                            onDragStart = { aim = it / cellPx },
                            onDrag = { change, _ ->
                                change.consume()
                                aim = change.position / cellPx
                            },
                            onDragEnd = {
                                aim?.let { viewModel.launch(it.x - viewModel.game.launchX, it.y - BB.FLOOR) }
                                aim = null
                            },
                            onDragCancel = { aim = null },
                        )
                    },
            )
        }
        Row(
            Modifier.fillMaxWidth().height(48.dp).padding(top = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (viewModel.flying) {
                TextButton(onClick = { viewModel.speed = viewModel.speed % 3 + 1 }) { Text("빠르기 ${viewModel.speed}배") }
                TextButton(onClick = viewModel::recall) { Text("공 거두기") }
            } else {
                Text(
                    "끌어서 방향을 정하고 놓으면 쏩니다",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BrickField(viewModel: BrickBreakerViewModel, aim: Offset?, modifier: Modifier) {
    val field = MaterialTheme.colorScheme.surfaceVariant
    val ball = MaterialTheme.colorScheme.primary
    val danger = MaterialTheme.colorScheme.error
    val measurer = rememberTextMeasurer(cacheSize = 64)

    Canvas(modifier) {
        @Suppress("UNUSED_VARIABLE") val frame = viewModel.frame   // 읽어 두면 판이 바뀔 때 다시 그린다
        val game = viewModel.game
        val s = size.width / BB.COLS
        drawRect(field)
        // 벽돌이 이 선을 넘어 내려오면 끝
        val limit = (BB.ROWS - 1) * s
        drawLine(danger.copy(alpha = 0.4f), Offset(0f, limit), Offset(size.width, limit), strokeWidth = s * 0.03f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(s * 0.2f, s * 0.15f)))

        // 색은 이번 턴의 두 배(가장 단단한 벽돌)에 견준다 — 막 생긴 벽돌은 가운데 색.
        val hardest = maxOf(game.bricks.maxOfOrNull { it.hp } ?: 1, game.turn * 2)
        val inner = (1 - 2 * BB.GAP) * s
        game.bricks.forEach { brick ->
            val topLeft = Offset((brick.col + BB.GAP) * s, (brick.row + BB.GAP) * s)
            drawRoundRect(brickColor(brick.hp / hardest.toFloat()), topLeft, Size(inner, inner), CornerRadius(s * 0.12f))
            val label = measurer.measure(
                brick.hp.toString(),
                TextStyle(color = BrickInk, fontWeight = FontWeight.Bold, fontSize = (s * if (brick.hp >= 100) 0.28f else 0.36f).toSp()),
            )
            drawText(label, topLeft = topLeft + Offset((inner - label.size.width) / 2, (inner - label.size.height) / 2))
        }
        game.pickups.forEach { pickup ->
            val center = Offset((pickup.col + 0.5f) * s, (pickup.row + 0.5f) * s)
            drawCircle(ball, BB.PICKUP_RADIUS * s, center, style = Stroke(s * 0.05f))
            drawCircle(ball, BB.RADIUS * s, center)
        }

        // 조준선 — 벽 · 천장에 튕기는 길까지
        if (aim != null && !game.isFlying && !game.over) {
            val points = game.guide(aim.x - game.launchX, aim.y - BB.FLOOR, 8f)
            if (points.size >= 2) {
                val path = Path().apply {
                    moveTo(points[0].first * s, points[0].second * s)
                    points.drop(1).forEach { (x, y) -> lineTo(x * s, y * s) }
                }
                drawPath(path, ball.copy(alpha = 0.7f), style = Stroke(s * 0.05f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(s * 0.12f, s * 0.12f))))
            }
        }

        game.flying.forEach { drawCircle(ball, BB.RADIUS * s, Offset(it.x * s, it.y * s)) }
        // 쏘는 자리 — 아직 남은 공이 있거나 쏘기 전. 이번 턴에 처음 닿은 자리는 흐리게.
        if (!game.over && (!game.isFlying || game.waiting > 0)) {
            drawCircle(ball, BB.RADIUS * s, Offset(game.launchX * s, BB.FLOOR * s))
        }
        game.landingX?.let { drawCircle(ball.copy(alpha = 0.45f), BB.RADIUS * s, Offset(it * s, BB.FLOOR * s)) }
    }
}

/** 약한 벽돌은 청록, 단단할수록 노랑 · 주황을 거쳐 빨강(색상환을 따라가 중간 색이 탁해지지 않게). */
private fun brickColor(hardness: Float): Color = Color.hsv(170f * (1f - hardness.coerceIn(0f, 1f)), 0.55f, 0.93f)
