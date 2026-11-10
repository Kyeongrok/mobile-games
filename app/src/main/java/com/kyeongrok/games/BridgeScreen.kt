package com.kyeongrok.games

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay

/** 다리를 지나는 트럭 판. 판 자체가 Compose 상태라 화면은 game 만 읽으면 된다. */
class BridgeViewModel : ViewModel() {
    var game by mutableStateOf(BridgeGame.example())
        private set
    var playing by mutableStateOf(false)
    var finishSeen by mutableStateOf(false)
    var showCode by mutableStateOf(false)
    var wrongFlash by mutableIntStateOf(0)
        private set
    /** 새 문제의 트럭 수. */
    var count by mutableIntStateOf(BridgeGame.EXAMPLE.third.size)
        private set

    fun act(action: BridgeGame.Action) {
        if (playing) return
        if (!game.act(action)) wrongFlash++
    }

    fun step() {
        game.step()
        if (game.finished) playing = false
    }

    fun restart() = load(BridgeGame(game.bridgeLength, game.limit, game.trucks))
    fun example() {
        count = BridgeGame.EXAMPLE.third.size
        load(BridgeGame.example())
    }
    fun random() = load(BridgeGame.random(count))

    fun changeCount(delta: Int) {
        val n = (count + delta).coerceIn(BridgeGame.MIN_COUNT, BridgeGame.MAX_COUNT)
        if (n == count) return
        count = n
        random()
    }

    private fun load(next: BridgeGame) {
        game = next
        playing = false
        finishSeen = false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BridgeTopBar(onBack: () -> Unit) {
    val viewModel: BridgeViewModel = viewModel()
    TopAppBar(
        title = { Text("다리를 지나는 트럭") },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
            }
        },
        actions = {
            TextButton(onClick = viewModel::example) { Text("예제") }
            TextButton(onClick = viewModel::random) { Text("새 문제") }
        },
    )
}

private val TruckColor = Color(0xFFFFB74D)
private val LeaveColor = Color(0xFFE57373)
private val TruckInk = Color(0xFF2A1A05)

@Composable
fun BridgeScreen() {
    val viewModel: BridgeViewModel = viewModel()
    val game = viewModel.game

    LaunchedEffect(viewModel.playing, viewModel.game) {
        while (viewModel.playing && !viewModel.game.finished) {
            delay(700)
            viewModel.step()
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("트럭 수", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { viewModel.changeCount(-1) }, enabled = viewModel.count > BridgeGame.MIN_COUNT) {
                Text("−", fontSize = 20.sp)
            }
            Text("${viewModel.count}", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { viewModel.changeCount(1) }, enabled = viewModel.count < BridgeGame.MAX_COUNT) {
                Text("+", fontSize = 20.sp)
            }
            Spacer(Modifier.weight(1f))
            Text("틀린 횟수 ${game.mistakes}", style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            "bridge_length = ${game.bridgeLength}, weight = ${game.limit}\ntruck_weights = [${game.trucks.joinToString(", ")}]",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text("${game.time}초", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TruckRow("대기 (왼쪽부터 오름)", (game.next until game.trucks.size).toList(), game, highlightFirst = true)
        }
        BridgeView(game)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TruckRow("다 건넘", game.passed.toList(), game, highlightFirst = false)
        }

        Text(game.message, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp))
        BridgeButtons(viewModel)
        TextButton(onClick = { viewModel.showCode = !viewModel.showCode }) {
            Text(if (viewModel.showCode) "코드 숨기기 ▲" else "코드 보기 ▼")
        }
        if (viewModel.showCode) BridgeCode(game.lastLines)
        Spacer(Modifier.size(8.dp))
    }

    if (game.finished && !viewModel.finishSeen) {
        val answer = BridgeGame.solve(game.bridgeLength, game.limit, game.trucks)
        AlertDialog(
            onDismissRequest = { viewModel.finishSeen = true },
            title = { Text(if (game.mistakes == 0) "완벽해요!" else "끝!") },
            text = {
                Text(
                    "답: ${game.time}초" + (if (game.time == answer) "" else " (정답 ${answer}초)") +
                        "\n틀린 횟수 ${game.mistakes}",
                )
            },
            confirmButton = { TextButton(onClick = viewModel::random) { Text("새 문제") } },
            dismissButton = { TextButton(onClick = { viewModel.finishSeen = true }) { Text("닫기") } },
        )
    }
}

/** 트럭 줄(대기 · 다 건넘) — 트럭 번호 목록. */
@Composable
private fun TruckRow(label: String, trucks: List<Int>, game: BridgeGame, highlightFirst: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Text(label, fontSize = 13.sp, color = scheme.onSurfaceVariant)
    Row(Modifier.heightIn(min = 30.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (trucks.isEmpty()) Text("없음", color = scheme.outline)
        trucks.forEachIndexed { k, t ->
            val first = highlightFirst && k == 0
            Box(
                Modifier.size(30.dp)
                    .background(if (first) TruckColor else TruckColor.copy(alpha = 0.35f), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("${game.trucks[t]}", fontWeight = FontWeight.Bold, color = TruckInk)
            }
        }
    }
}

/** 다리 — 왼쪽이 들어오는 쪽, 오른쪽 끝 칸이 곧 나가는 칸. 아래에 무게 막대. */
@Composable
private fun BridgeView(game: BridgeGame) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("→ 들어옴", fontSize = 12.sp, color = scheme.onSurfaceVariant)
            Text("나감 →", fontSize = 12.sp, color = LeaveColor)
        }
        Row(
            Modifier.fillMaxWidth().background(scheme.surfaceVariant, RoundedCornerShape(8.dp)).padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // 큐의 맨 앞(곧 나감)을 오른쪽 끝에 그린다.
            game.bridge.asReversed().forEachIndexed { k, cell ->
                val leaving = k == game.bridgeLength - 1
                Box(
                    Modifier.weight(1f).height(56.dp)
                        .border(2.dp, if (leaving) LeaveColor else scheme.outline.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .padding(4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (cell != 0) {
                        Box(
                            Modifier.fillMaxSize().background(TruckColor, RoundedCornerShape(4.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("🚚 ${game.trucks[cell - 1]}", fontWeight = FontWeight.Bold, color = TruckInk)
                        }
                    }
                }
            }
        }
        val load = game.load()
        LinearProgressIndicator(
            progress = { load.toFloat() / game.limit },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = if (load > game.limit * 0.8f) LeaveColor else scheme.primary,
        )
        Text(
            "다리 무게 $load / ${game.limit}" +
                (if (game.waitingAll) "   ·   다음 트럭 ${game.trucks[game.next]}" else ""),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun BridgeButtons(viewModel: BridgeViewModel) {
    val game = viewModel.game
    val enabled = !game.finished && !viewModel.playing
    var flash by remember { mutableStateOf(false) }
    LaunchedEffect(viewModel.wrongFlash) {
        if (viewModel.wrongFlash > 0) {
            flash = true
            delay(400)
            flash = false
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth()
                .border(2.dp, if (flash) LeaveColor else Color.Transparent, RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = { viewModel.act(BridgeGame.Action.Put) },
                enabled = enabled && game.waitingAll,
                modifier = Modifier.weight(1f).height(56.dp),
            ) { Text("올리기", fontSize = 16.sp) }
            Button(
                onClick = { viewModel.act(BridgeGame.Action.Wait) },
                enabled = enabled,
                modifier = Modifier.weight(1f).height(56.dp),
            ) { Text("기다리기 (1초)", fontSize = 16.sp) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = viewModel::step, enabled = enabled, modifier = Modifier.weight(1f)) { Text("한 단계") }
            FilledTonalButton(
                onClick = { viewModel.playing = !viewModel.playing },
                enabled = !game.finished,
                modifier = Modifier.weight(1f),
            ) { Text(if (viewModel.playing) "멈춤" else "자동 재생") }
            OutlinedButton(onClick = viewModel::restart, modifier = Modifier.weight(1f)) { Text("처음부터") }
        }
    }
}

@Composable
private fun BridgeCode(lines: Set<Int>) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().background(scheme.surfaceVariant, RoundedCornerShape(8.dp)).padding(8.dp)) {
        BridgeGame.CODE.forEachIndexed { n, line ->
            val on = n in lines
            Text(
                line,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = if (on) Color(0xFF102030) else scheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
                    .background(if (on) Color(0xFFFFE082) else Color.Transparent, RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
    }
}
