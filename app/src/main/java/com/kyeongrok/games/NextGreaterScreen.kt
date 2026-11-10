package com.kyeongrok.games

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay

/** 스택 게임의 판. 판 자체가 Compose 상태라 화면은 game 만 읽으면 된다. */
class NextGreaterViewModel : ViewModel() {
    var game by mutableStateOf(NextGreaterGame(NextGreaterGame.EXAMPLE))
        private set
    /** 자동 보기 중인지. */
    var playing by mutableStateOf(false)
    /** 끝 창을 이미 닫았는지. */
    var finishSeen by mutableStateOf(false)
    /** 아래 코드 창을 펼쳤는지 — 기본은 숨김. */
    var showCode by mutableStateOf(false)
    /** 틀렸을 때 버튼을 흔들듯 잠깐 빨갛게. */
    var wrongFlash by mutableIntStateOf(0)
        private set

    fun act(action: NextGreaterGame.Action) {
        if (playing) return
        if (!game.act(action)) wrongFlash++
    }

    fun step() {
        game.step()
        if (game.finished) playing = false
    }

    fun restart() = load(NextGreaterGame(game.numbers))
    fun example() = load(NextGreaterGame(NextGreaterGame.EXAMPLE))
    fun random() = load(NextGreaterGame.random())

    private fun load(next: NextGreaterGame) {
        game = next
        playing = false
        finishSeen = false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NextGreaterTopBar(onBack: () -> Unit) {
    val viewModel: NextGreaterViewModel = viewModel()
    TopAppBar(
        title = { Text("스택: 뒤의 큰 수") },
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

private val PopColor = Color(0xFFE57373)
private val PushColor = Color(0xFF64B5F6)

@Composable
fun NextGreaterScreen() {
    val viewModel: NextGreaterViewModel = viewModel()
    val game = viewModel.game

    LaunchedEffect(viewModel.playing, viewModel.game) {
        while (viewModel.playing && !viewModel.game.finished) {
            delay(800)
            viewModel.step()
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "numbers = [${game.numbers.joinToString(", ")}]   틀린 횟수 ${game.mistakes}",
            style = MaterialTheme.typography.bodyMedium,
        )
        NumberRow(game)
        StackView(game)
        Text(
            game.message,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        )
        ActionButtons(viewModel)
        TextButton(onClick = { viewModel.showCode = !viewModel.showCode }) {
            Text(if (viewModel.showCode) "코드 숨기기 ▲" else "코드 보기 ▼")
        }
        if (viewModel.showCode) CodePanel(game.lastLines)
    }

    if (game.finished && !viewModel.finishSeen) {
        val ok = game.answer.map { it ?: -1 } == NextGreaterGame.solve(game.numbers)
        AlertDialog(
            onDismissRequest = { viewModel.finishSeen = true },
            title = { Text(if (game.mistakes == 0) "완벽해요!" else "끝!") },
            text = {
                Text(
                    "answer = [${game.answer.joinToString(", ") { "${it ?: -1}" }}]\n" +
                        "틀린 횟수 ${game.mistakes}" + (if (ok) "" else "\n(정답과 다릅니다)"),
                )
            },
            confirmButton = { TextButton(onClick = viewModel::random) { Text("새 문제") } },
            dismissButton = { TextButton(onClick = { viewModel.finishSeen = true }) { Text("닫기") } },
        )
    }
}

/** numbers 카드 — 위에 칸 번호, 가운데 값, 아래 답. 지금 칸 · 스택에 든 칸 · 방금 꺼낸 칸을 색으로. */
@Composable
private fun NumberRow(game: NextGreaterGame) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        game.numbers.forEachIndexed { i, value ->
            val inStack = i in game.stack
            val target = when {
                i == game.index -> scheme.primary
                i == game.lastPopped -> PopColor
                inStack -> PushColor.copy(alpha = 0.45f)
                game.answer[i] != null -> scheme.surfaceVariant
                else -> scheme.surface
            }
            val fill by animateColorAsState(target, label = "card")
            val ink = if (i == game.index) scheme.onPrimary else scheme.onSurface
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (i == game.index) "i=$i" else "$i", fontSize = 12.sp, color = scheme.onSurfaceVariant)
                Box(
                    Modifier.fillMaxWidth().height(48.dp)
                        .background(fill, RoundedCornerShape(8.dp))
                        .border(1.dp, scheme.outline, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("$value", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = ink)
                }
                Spacer(Modifier.height(2.dp))
                val a = game.answer[i]
                Text(
                    a?.toString() ?: "?",
                    fontSize = 16.sp,
                    fontWeight = if (a != null) FontWeight.Bold else FontWeight.Normal,
                    color = when {
                        a == null -> scheme.outline
                        a == -1 -> scheme.onSurfaceVariant
                        else -> scheme.primary
                    },
                )
            }
        }
    }
    Text("↑ 아래 줄이 answer", fontSize = 12.sp, color = scheme.onSurfaceVariant)
}

/** 스택 — 아래에서 위로 쌓인 상자. 오른쪽에 지금 견줄 값. */
@Composable
private fun StackView(game: NextGreaterGame) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Column(
            Modifier.width(140.dp).heightIn(min = 150.dp)
                .border(2.dp, scheme.outline, RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Bottom),
        ) {
            if (game.stack.isEmpty()) {
                Text("비어 있음", color = scheme.outline, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
            game.stack.asReversed().forEachIndexed { k, i ->
                val isTop = k == 0
                Box(
                    Modifier.fillMaxWidth().height(40.dp)
                        .background(if (isTop) PushColor else PushColor.copy(alpha = 0.45f), RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("[$i]  ${game.numbers[i]}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF102030))
                }
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val top = game.top
            Text("스택 (인덱스 · 값)", fontSize = 12.sp, color = scheme.onSurfaceVariant)
            Text(
                if (top != null) "맨 위: [$top] → ${game.numbers[top]}" else "맨 위: 없음",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                if (!game.finished) "지금: [${game.index}] → ${game.numbers[game.index]}" else "지금: 끝",
                style = MaterialTheme.typography.titleMedium,
                color = scheme.primary,
            )
            if (top != null && !game.finished) {
                Text("${game.numbers[top]} < ${game.numbers[game.index]} ?", style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@Composable
private fun ActionButtons(viewModel: NextGreaterViewModel) {
    val game = viewModel.game
    val enabled = !game.finished && !viewModel.playing
    // 틀리면 잠깐 빨간 테두리.
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
                .border(2.dp, if (flash) PopColor else Color.Transparent, RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = { viewModel.act(NextGreaterGame.Action.Pop) },
                enabled = enabled,
                modifier = Modifier.weight(1f).height(56.dp),
            ) { Text("Pop (꺼내기)", fontSize = 16.sp) }
            Button(
                onClick = { viewModel.act(NextGreaterGame.Action.Push) },
                enabled = enabled,
                modifier = Modifier.weight(1f).height(56.dp),
            ) { Text("Push (넣기)", fontSize = 16.sp) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = viewModel::step, enabled = enabled, modifier = Modifier.weight(1f)) {
                Text("한 단계")
            }
            FilledTonalButton(
                onClick = { viewModel.playing = !viewModel.playing },
                enabled = !game.finished,
                modifier = Modifier.weight(1f),
            ) { Text(if (viewModel.playing) "멈춤" else "자동 재생") }
            OutlinedButton(onClick = viewModel::restart, modifier = Modifier.weight(1f)) { Text("처음부터") }
        }
    }
}

/** C# 코드 — 방금 한 일에 해당하는 줄을 칠한다. */
@Composable
private fun CodePanel(lines: Set<Int>) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().background(scheme.surfaceVariant, RoundedCornerShape(8.dp)).padding(8.dp),
    ) {
        NextGreaterGame.CODE.forEachIndexed { n, line ->
            val on = n in lines
            Text(
                line.ifEmpty { " " },
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = if (on) Color(0xFF102030) else scheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
                    .background(if (on) Color(0xFFFFE082) else Color.Transparent, RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
    }
    Spacer(Modifier.size(8.dp))
}
