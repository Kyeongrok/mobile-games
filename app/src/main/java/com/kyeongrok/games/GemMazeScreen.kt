package com.kyeongrok.games

import android.app.Application
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyeongrok.games.GemMazeGame.Phase
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private typealias GM = GemMazeGame

/** 보석 미로 판과 최고 기록(막아 낸 웨이브). */
class GemMazeViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("gem-maze", Context.MODE_PRIVATE)

    var game = GM()
        private set
    /** 판이 바뀔 때마다 늘린다 — 그리는 쪽이 이것을 읽어 다시 그린다. */
    var version by mutableIntStateOf(0)
        private set
    /** 고르기에서 고른 보석, 또는 살펴보는 탑. */
    var selected by mutableStateOf<MazeGem?>(null)
        private set
    var speed by mutableIntStateOf(1)
    var best by mutableIntStateOf(preferences.getInt("best", 0))
        private set
    var bestAtStart = best
        private set

    fun tap(cell: Cell) {
        val g = game
        val atCell = (g.placed + g.towers).firstOrNull { it.row == cell.row && it.col == cell.col }
        when (g.phase) {
            Phase.Build -> when {
                atCell != null -> selected = atCell
                cell in g.rocks -> if (g.breakRock(cell)) selected = null
                g.place(cell) != null -> selected = null
            }
            Phase.Choose, Phase.Wave -> selected = atCell
            Phase.Over -> Unit
        }
        version++
    }

    fun keep(merge: Boolean) {
        val gem = selected ?: return
        if (game.keep(gem, merge)) {
            selected = null
            version++
        }
    }

    fun undo() {
        if (game.undo()) {
            selected = null
            version++
        }
    }

    fun finishPlacing() {
        game.finishPlacing()
        version++
    }

    fun step(dt: Float) {
        if (game.step(dt)) {
            if (game.cleared > best) {
                best = game.cleared
                preferences.edit().putInt("best", best).apply()
            }
        }
        version++
    }

    fun newGame() {
        game = GM()
        selected = null
        speed = 1
        bestAtStart = best
        version++
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GemMazeTopBar(onBack: () -> Unit) {
    val viewModel: GemMazeViewModel = viewModel()
    TopAppBar(
        title = { Text("보석 미로") },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
            }
        },
        actions = { TextButton(onClick = viewModel::newGame) { Text("새 게임") } },
    )
}

@Composable
fun GemMazeScreen() {
    val viewModel: GemMazeViewModel = viewModel()
    @Suppress("UNUSED_VARIABLE") val version = viewModel.version
    val game = viewModel.game

    if (game.phase == Phase.Wave) {
        LaunchedEffect(game, game.wave) {
            var last = 0L
            while (true) {
                withFrameNanos { now ->
                    if (last != 0L) viewModel.step(((now - last) / 1e9f).coerceAtMost(0.05f) * viewModel.speed)
                    last = now
                }
            }
        }
    }

    if (game.phase == Phase.Over) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(if (game.cleared > viewModel.bestAtStart) "새 기록!" else "게임 끝") },
            text = { Text("${game.cleared}웨이브를 막았습니다 · 탑 ${game.towers.size}개\n최고 기록 ${viewModel.best}웨이브") },
            confirmButton = { TextButton(onClick = viewModel::newGame) { Text("새 게임") } },
        )
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            val waveLabel = when {
                game.isBossWave -> " · 보스"
                game.isFastWave -> " · 빠른 적"
                else -> ""
            }
            Text("웨이브 ${game.wave}$waveLabel", style = MaterialTheme.typography.titleMedium)
            Text("♥ ${game.lives}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
            Text("최고 ${viewModel.best}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val cell = min(maxWidth / GM.COLS, maxHeight / GM.ROWS)
            MazeBoard(
                viewModel,
                Modifier
                    .size(cell * GM.COLS, cell * GM.ROWS)
                    .pointerInput(Unit) {
                        detectTapGestures { p ->
                            val s = size.width.toFloat() / GM.COLS
                            val row = (p.y / s).toInt()
                            val col = (p.x / s).toInt()
                            if (row in 0 until GM.ROWS && col in 0 until GM.COLS) viewModel.tap(Cell(row, col))
                        }
                    },
            )
        }
        Controls(viewModel)
    }
}

@Composable
private fun Controls(viewModel: GemMazeViewModel) {
    @Suppress("UNUSED_VARIABLE") val version = viewModel.version   // 읽어 두면 판이 바뀔 때 다시 그린다
    val game = viewModel.game
    val selected = viewModel.selected
    Column(Modifier.fillMaxWidth().heightIn(min = 112.dp).padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val info = selected?.let { "${it.kind.label} ${it.level}등급 — ${it.kind.effect} · 피해 ${it.damage.toInt()}" }
        when (game.phase) {
            Phase.Build -> {
                Text(
                    "빈 칸을 눌러 보석을 놓으세요 (${game.placed.size}/${GM.PLACEMENTS}). 적은 위에서 아래로 가장 짧은 길로 옵니다.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (game.rocks.isNotEmpty()) {
                    Text(
                        if (game.breaksLeft > 0) "돌을 누르면 깹니다 (이번 라운드 ${game.breaksLeft}번)" else "이번 라운드에는 돌을 더 깰 수 없습니다",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                info?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                game.nextGem?.let { (kind, level) ->
                    Text("다음에 놓일 보석: ${kind.label} ${level}등급 (되돌린 보석)", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (game.canUndo()) OutlinedButton(onClick = viewModel::undo) { Text("되돌리기") }
                    if (!game.anyPlaceable()) {
                        Button(onClick = viewModel::finishPlacing) { Text("놓을 곳이 없습니다 — 계속") }
                    }
                }
            }
            Phase.Choose -> {
                Text(
                    if (selected == null) "남길 보석 하나를 누르세요. 나머지는 돌이 됩니다." else info!!,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = viewModel::undo) { Text("되돌리기") }
                    Button(onClick = { viewModel.keep(false) }, enabled = selected != null && selected in game.placed) { Text("이것 남기기") }
                    if (selected != null && game.canMerge(selected)) {
                        OutlinedButton(onClick = { viewModel.keep(true) }) { Text("합쳐서 ${selected.level + 1}등급으로") }
                    }
                }
            }
            Phase.Wave -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("적 ${game.enemies.size}", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { viewModel.speed = viewModel.speed % 3 + 1 }) { Text("빠르기 ${viewModel.speed}배") }
                }
                info?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Phase.Over -> Unit
        }
    }
}

private val KindColors = mapOf(
    MazeGemKind.Fire to Color(0xFFE53935),
    MazeGemKind.Ice to Color(0xFF29B6F6),
    MazeGemKind.Bolt to Color(0xFFFDD835),
    MazeGemKind.Venom to Color(0xFF43A047),
)

@Composable
private fun MazeBoard(viewModel: GemMazeViewModel, modifier: Modifier) {
    val ground = MaterialTheme.colorScheme.surfaceVariant
    val line = MaterialTheme.colorScheme.outlineVariant
    val pathColor = MaterialTheme.colorScheme.primary
    val rock = Color(0xFF8D8A85)
    val enemyColor = MaterialTheme.colorScheme.onSurface
    val danger = MaterialTheme.colorScheme.error
    Canvas(modifier) {
        @Suppress("UNUSED_VARIABLE") val v = viewModel.version   // 읽어 두면 판이 바뀔 때 다시 그린다
        val game = viewModel.game
        val s = size.width / GM.COLS
        fun center(row: Float, col: Float) = Offset((col + 0.5f) * s, (row + 0.5f) * s)

        drawRect(ground)
        for (c in 1 until GM.COLS) drawLine(line, Offset(c * s, 0f), Offset(c * s, size.height), 1f)
        for (r in 1 until GM.ROWS) drawLine(line, Offset(0f, r * s), Offset(size.width, r * s), 1f)
        // 입구 · 출구
        drawRect(pathColor.copy(alpha = 0.25f), Offset(GM.START.col * s, 0f), Size(s, s))
        drawRect(danger.copy(alpha = 0.25f), Offset(GM.EXIT.col * s, GM.EXIT.row * s), Size(s, s))

        // 적이 갈 길
        if (game.phase != Phase.Over) {
            val p = Path()
            game.path.forEachIndexed { i, c ->
                val o = center(c.row.toFloat(), c.col.toFloat())
                if (i == 0) p.moveTo(o.x, o.y) else p.lineTo(o.x, o.y)
            }
            drawPath(p, pathColor.copy(alpha = if (game.phase == Phase.Wave) 0.15f else 0.35f), style = Stroke(s * 0.12f))
        }

        game.rocks.forEach {
            drawRoundRect(rock, Offset(it.col * s + s * 0.08f, it.row * s + s * 0.08f), Size(s * 0.84f, s * 0.84f), CornerRadius(s * 0.2f))
        }
        val selected = viewModel.selected
        selected?.let {
            drawCircle(KindColors.getValue(it.kind).copy(alpha = 0.15f), it.range * s, center(it.row.toFloat(), it.col.toFloat()))
            drawCircle(KindColors.getValue(it.kind).copy(alpha = 0.6f), it.range * s, center(it.row.toFloat(), it.col.toFloat()), style = Stroke(2f))
        }
        game.towers.forEach { drawMazeGem(it, center(it.row.toFloat(), it.col.toFloat()), s, tower = true) }
        game.placed.forEach {
            val c = center(it.row.toFloat(), it.col.toFloat())
            if (game.phase == Phase.Choose && game.canMerge(it)) {
                drawCircle(pathColor, s * 0.48f, c, style = Stroke(s * 0.05f))
            }
            drawMazeGem(it, c, s, tower = false)
        }
        selected?.let {
            drawRoundRect(pathColor, Offset(it.col * s + 2f, it.row * s + 2f), Size(s - 4f, s - 4f), CornerRadius(s * 0.15f), style = Stroke(s * 0.07f))
        }

        game.shots.forEach {
            val color = KindColors.getValue(it.kind)
            drawLine(color, center(it.fromY, it.fromX), center(it.toY, it.toX), s * 0.07f * (it.life / MazeShot.SHOT_LIFE).coerceIn(0.3f, 1f))
        }
        game.enemies.forEach { e ->
            val c = center(e.y, e.x)
            val r = s * when {
                e.boss -> 0.36f
                e.fast -> 0.2f
                else -> 0.25f
            }
            val body = when {
                e.poisonTime > 0f -> Color(0xFF2E7D32)
                e.slowTime > 0f -> Color(0xFF0277BD)
                else -> enemyColor
            }
            drawCircle(body, r, c)
            // 체력 막대
            val w = s * 0.7f
            val top = c.y - r - s * 0.12f
            drawRect(Color.Black.copy(alpha = 0.4f), Offset(c.x - w / 2, top), Size(w, s * 0.07f))
            drawRect(danger, Offset(c.x - w / 2, top), Size(w * (e.hp / e.maxHp).coerceIn(0f, 1f), s * 0.07f))
        }
    }
}

/** 종류마다 다른 모양 · 색, 등급만큼 아래에 점. 탑은 받침을 깐다. */
private fun DrawScope.drawMazeGem(gem: MazeGem, c: Offset, s: Float, tower: Boolean) {
    val color = KindColors.getValue(gem.kind)
    if (tower) drawRoundRect(Color.Black.copy(alpha = 0.18f), Offset(c.x - s * 0.44f, c.y - s * 0.44f), Size(s * 0.88f, s * 0.88f), CornerRadius(s * 0.2f))
    val r = s * (0.24f + 0.025f * gem.level)
    val sides = when (gem.kind) {
        MazeGemKind.Fire -> 3
        MazeGemKind.Ice -> 6
        MazeGemKind.Bolt -> 4
        MazeGemKind.Venom -> 5
    }
    val start = if (gem.kind == MazeGemKind.Bolt) 0.0 else -PI / 2
    val path = Path().apply {
        for (i in 0 until sides) {
            val a = start + 2 * PI * i / sides
            val x = c.x + r * cos(a).toFloat()
            val y = c.y - s * 0.06f + r * sin(a).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
    drawPath(path, if (tower) color else color.copy(alpha = 0.75f))
    drawPath(path, Color.Black.copy(alpha = 0.3f), style = Stroke(s * 0.03f))
    // 등급 점
    val dot = s * 0.045f
    val y = c.y + s * 0.33f
    val first = c.x - (gem.level - 1) * dot * 1.4f
    for (i in 0 until gem.level) drawCircle(Color.Black.copy(alpha = 0.65f), dot, Offset(first + i * dot * 2.8f, y))
}
