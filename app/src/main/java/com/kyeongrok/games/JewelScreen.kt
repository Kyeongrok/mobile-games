package com.kyeongrok.games

import android.app.Application
import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private typealias JG = JewelGame

/** 보석 맞추기 판과 최고 점수. 바꾸기 · 지우기 · 떨어지기를 차례로 보여 주는 동안은 손을 받지 않는다. */
class JewelViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("jewel", Context.MODE_PRIVATE)

    var game = JG()
        private set
    /** 판이 바뀔 때마다 늘린다 — 그리는 쪽이 이것을 읽어 다시 그린다. */
    var version by mutableIntStateOf(0)
        private set
    var busy by mutableStateOf(false)
        private set
    var selected by mutableStateOf<Cell?>(null)
    /** 사라지는 중인 보석 id. */
    var clearing by mutableStateOf<Set<Int>>(emptySet())
        private set
    var hint by mutableStateOf<Pair<Cell, Cell>?>(null)
        private set
    /** 판 위에 잠깐 띄우는 글(연쇄 · 섞기). */
    var banner by mutableStateOf<String?>(null)
        private set
    var best by mutableIntStateOf(preferences.getInt("best", 0))
        private set
    var bestAtStart = best
        private set

    fun tap(cell: Cell) {
        if (busy || game.over) return
        val first = selected
        when {
            first == null -> selected = cell
            first == cell -> selected = null
            first.isNextTo(cell) -> swap(first, cell)
            else -> selected = cell
        }
    }

    fun swap(a: Cell, b: Cell) {
        if (busy || game.over || !a.isNextTo(b)) return
        selected = null
        hint = null
        busy = true
        viewModelScope.launch {
            if (!game.swap(a, b)) {
                // 늘어서는 줄이 없으면 바꿨다가 되돌리는 모습만 보여 준다.
                game.board.exchangeForShow(a, b)
                version++
                delay(SLIDE_MS)
                game.board.exchangeForShow(a, b)
                version++
                delay(SLIDE_MS)
                busy = false
                return@launch
            }
            version++
            delay(SLIDE_MS)
            while (true) {
                val cells = game.matches()
                if (cells.isEmpty()) break
                clearing = cells.mapNotNull { game[it]?.id }.toSet()
                delay(CLEAR_MS)
                game.clear(cells)
                if (game.chain >= 2) banner = "${game.chain}연쇄 ×${game.chain}"
                clearing = emptySet()
                game.collapse()
                version++
                delay(FALL_MS)
            }
            if (game.endMove()) {
                banner = "둘 곳이 없어 섞었습니다"
                version++
                delay(SLIDE_MS)
            }
            if (game.score > best) {
                best = game.score
                preferences.edit().putInt("best", best).apply()
            }
            busy = false
            delay(BANNER_MS)
            if (!busy) banner = null
        }
    }

    fun showHint() {
        if (busy || game.over) return
        hint = game.hint()
    }

    fun newGame() {
        if (busy) return
        game = JG()
        selected = null
        hint = null
        banner = null
        bestAtStart = best
        version++
    }

    companion object {
        const val SLIDE_MS = 180L
        const val CLEAR_MS = 220L
        const val FALL_MS = 320L
        const val BANNER_MS = 900L
    }
}

/** 보여 주기만 하는 바꾸기 — 이동 수나 점수는 그대로. */
private fun Array<Array<Gem?>>.exchangeForShow(a: Cell, b: Cell) {
    val t = this[a.row][a.col]
    this[a.row][a.col] = this[b.row][b.col]
    this[b.row][b.col] = t
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JewelTopBar(onBack: () -> Unit) {
    val viewModel: JewelViewModel = viewModel()
    TopAppBar(
        title = { Text("보석 맞추기") },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
            }
        },
        actions = { TextButton(onClick = viewModel::newGame, enabled = !viewModel.busy) { Text("새 게임") } },
    )
}

/** 보석마다 화면에 그려지는 자리(칸 단위). 판 자리를 향해 미끄러진다. */
private class GemPositions {
    val at = HashMap<Int, Offset>()
    var tick by mutableIntStateOf(0)
}

@Composable
fun JewelScreen() {
    val viewModel: JewelViewModel = viewModel()
    val game = viewModel.game
    val version = viewModel.version
    val positions = remember(game) { GemPositions() }

    // 판 자리를 향해 매 화면마다 움직인다. 새 보석은 그 줄의 빈 칸 수만큼 위에서 떨어진다.
    LaunchedEffect(positions) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.1f)
                last = now
                if (step(viewModel.game, positions, dt)) positions.tick++
            }
        }
    }

    val clearAnim = remember { Animatable(0f) }
    LaunchedEffect(viewModel.clearing) {
        clearAnim.snapTo(0f)
        if (viewModel.clearing.isNotEmpty()) clearAnim.animateTo(1f, tween(JewelViewModel.CLEAR_MS.toInt()))
    }

    if (game.over && !viewModel.busy) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(if (game.score > viewModel.bestAtStart) "새 기록!" else "게임 끝") },
            text = { Text("${game.score}점 · 가장 긴 연쇄 ${game.bestChain}\n최고 점수 ${viewModel.best}점") },
            confirmButton = { TextButton(onClick = viewModel::newGame) { Text("새 게임") } },
        )
    }

    Column(Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${game.score}점", style = MaterialTheme.typography.titleLarge)
            Column(horizontalAlignment = Alignment.End) {
                Text("남은 이동 ${game.movesLeft}", style = MaterialTheme.typography.titleMedium)
                Text("최고 ${viewModel.best}점", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val cell = min(maxWidth, maxHeight) / JG.SIZE
            val cellPx = with(LocalDensity.current) { cell.toPx() }
            fun cellAt(p: Offset): Cell? {
                val row = (p.y / cellPx).toInt()
                val col = (p.x / cellPx).toInt()
                return if (p.x >= 0 && p.y >= 0 && row < JG.SIZE && col < JG.SIZE) Cell(row, col) else null
            }
            JewelBoard(
                viewModel,
                positions,
                clearAnim.value,
                version,
                Modifier
                    .size(cell * JG.SIZE)
                    .pointerInput(cellPx) {
                        // 누른 칸에서 반 칸 가까이 밀면 그 방향 이웃과 바꾸고, 거의 안 움직이고 떼면 누르기로 친다.
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val from = cellAt(down.position) ?: return@awaitEachGesture
                            var swiped = false
                            var last = down.position
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                last = change.position
                                if (!change.pressed) break
                                val d = change.position - down.position
                                if (!swiped && d.getDistance() > cellPx * 0.4f) {
                                    swiped = true
                                    change.consume()
                                    val to = if (abs(d.x) > abs(d.y)) {
                                        Cell(from.row, from.col + if (d.x > 0) 1 else -1)
                                    } else {
                                        Cell(from.row + if (d.y > 0) 1 else -1, from.col)
                                    }
                                    if (to.row in 0 until JG.SIZE && to.col in 0 until JG.SIZE) viewModel.swap(from, to)
                                }
                            }
                            if (!swiped && (last - down.position).getDistance() <= cellPx * 0.4f) viewModel.tap(from)
                        }
                    },
            )
        }
        Row(
            Modifier.fillMaxWidth().height(48.dp).padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                viewModel.banner ?: "밀거나 두 칸을 눌러 바꿉니다",
                style = if (viewModel.banner != null) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                color = if (viewModel.banner != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = viewModel::showHint, enabled = !viewModel.busy && !game.over) { Text("힌트") }
        }
    }
}

/** 보석들을 판 자리 쪽으로 한 번 움직인다. 움직인 것이 있으면 true. */
private fun step(game: JG, positions: GemPositions, dt: Float): Boolean {
    var moved = false
    val alive = HashSet<Int>()
    val initial = positions.at.isEmpty()   // 처음 그릴 때는 떨어지지 않고 제자리에
    val newInColumn = IntArray(JG.SIZE)
    for (col in 0 until JG.SIZE) for (row in 0 until JG.SIZE) {
        val gem = game.board[row][col] ?: continue
        if (gem.id !in positions.at) newInColumn[col]++
    }
    for (row in 0 until JG.SIZE) for (col in 0 until JG.SIZE) {
        val gem = game.board[row][col] ?: continue
        alive += gem.id
        val target = Offset(col.toFloat(), row.toFloat())
        val now = positions.at[gem.id]
        if (now == null) {
            positions.at[gem.id] = if (initial) target else Offset(col.toFloat(), (row - newInColumn[col]).toFloat())
            moved = true
            continue
        }
        if (now == target) continue
        val d = target - now
        val distance = d.getDistance()
        val travel = SPEED * dt
        positions.at[gem.id] = if (distance <= travel) target else now + d * (travel / distance)
        moved = true
    }
    if (positions.at.keys.retainAll(alive)) moved = true
    return moved
}

/** 한 칸에 걸리는 시간이 짧게 — 초당 칸 수. */
private const val SPEED = 11f

@Composable
private fun JewelBoard(
    viewModel: JewelViewModel,
    positions: GemPositions,
    clearProgress: Float,
    @Suppress("UNUSED_PARAMETER") version: Int,
    modifier: Modifier,
) {
    val light = MaterialTheme.colorScheme.surfaceVariant
    val dark = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    val select = MaterialTheme.colorScheme.primary
    val hintColor = MaterialTheme.colorScheme.tertiary
    Canvas(modifier) {
        @Suppress("UNUSED_VARIABLE") val tick = positions.tick   // 읽어 두면 보석이 움직일 때 다시 그린다
        @Suppress("UNUSED_VARIABLE") val v = viewModel.version
        val game = viewModel.game
        val s = size.width / JG.SIZE
        for (row in 0 until JG.SIZE) for (col in 0 until JG.SIZE) {
            drawRect(if ((row + col) % 2 == 0) light else dark, Offset(col * s, row * s), Size(s, s))
        }
        viewModel.hint?.let { (a, b) ->
            listOf(a, b).forEach {
                drawRoundRect(hintColor, Offset(it.col * s + s * 0.04f, it.row * s + s * 0.04f), Size(s * 0.92f, s * 0.92f),
                    CornerRadius(s * 0.15f), style = Stroke(s * 0.06f))
            }
        }
        viewModel.selected?.let {
            drawRoundRect(select, Offset(it.col * s + s * 0.03f, it.row * s + s * 0.03f), Size(s * 0.94f, s * 0.94f),
                CornerRadius(s * 0.15f), style = Stroke(s * 0.08f))
        }
        val clearing = viewModel.clearing
        for (row in 0 until JG.SIZE) for (col in 0 until JG.SIZE) {
            val gem = game.board[row][col] ?: continue
            val at = positions.at[gem.id] ?: Offset(col.toFloat(), row.toFloat())
            if (at.y < -0.5f) continue
            val center = Offset((at.x + 0.5f) * s, (at.y + 0.5f) * s)
            val factor = if (gem.id in clearing) 1f - clearProgress else 1f
            if (factor <= 0.01f) continue
            scale(factor, center) { drawGem(gem.kind, center, s * 0.4f) }
        }
    }
}

private val GemColors = listOf(
    Color(0xFFE53935), // 빨강 마름모
    Color(0xFFFB8C00), // 주황 동그라미
    Color(0xFFFDD835), // 노랑 별
    Color(0xFF43A047), // 초록 네모
    Color(0xFF1E88E5), // 파랑 세모
    Color(0xFF8E24AA), // 보라 육각형
)

/** 종류마다 색과 모양이 달라 색을 구분하기 어려워도 모양으로 알 수 있다. r 는 반지름. */
private fun DrawScope.drawGem(kind: Int, c: Offset, r: Float) {
    val color = GemColors[kind]
    val path = when (kind) {
        0 -> polygon(c, r, 4, -PI / 2)
        1 -> null
        2 -> star(c, r)
        3 -> polygon(c, r * 1.05f, 4, PI / 4)
        4 -> polygon(c, r * 1.1f, 3, -PI / 2, dy = r * 0.15f)
        else -> polygon(c, r, 6, 0.0)
    }
    if (path == null) {
        drawCircle(color, r * 0.92f, c)
        drawCircle(Color.Black.copy(alpha = 0.25f), r * 0.92f, c, style = Stroke(r * 0.08f))
    } else {
        drawPath(path, color)
        drawPath(path, Color.Black.copy(alpha = 0.25f), style = Stroke(r * 0.08f))
    }
    // 반짝임
    drawCircle(Color.White.copy(alpha = 0.55f), r * 0.16f, c + Offset(-r * 0.3f, -r * 0.3f))
}

private fun polygon(c: Offset, r: Float, sides: Int, start: Double, dy: Float = 0f) = Path().apply {
    for (i in 0 until sides) {
        val a = start + 2 * PI * i / sides
        val x = c.x + r * cos(a).toFloat()
        val y = c.y + dy + r * sin(a).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private fun star(c: Offset, r: Float) = Path().apply {
    for (i in 0 until 10) {
        val a = -PI / 2 + PI * i / 5
        val radius = if (i % 2 == 0) r * 1.05f else r * 0.45f
        val x = c.x + radius * cos(a).toFloat()
        val y = c.y + r * 0.08f + radius * sin(a).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}
