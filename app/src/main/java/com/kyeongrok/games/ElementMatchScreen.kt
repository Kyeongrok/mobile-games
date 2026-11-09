package com.kyeongrok.games

import android.app.Application
import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private const val COLUMNS = 4
private const val CARD_RATIO = 1.25f // 세로 / 가로
private val PAIR_CHOICES = listOf(6, 8, 10)
private val CardShape = RoundedCornerShape(8.dp)
private val CardInk = Color(0xFF1F1F1F)

class ElementMatchViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("element-match", Context.MODE_PRIVATE)

    private val mutableRange = MutableStateFlow(
        ElementRange.entries.find { it.name == preferences.getString("range", null) } ?: ElementRange.Easy,
    )
    val range: StateFlow<ElementRange> = mutableRange

    private val mutablePairs = MutableStateFlow(preferences.getInt("pairs", 8).takeIf { it in PAIR_CHOICES } ?: 8)
    val pairs: StateFlow<Int> = mutablePairs

    private val mutableGame = MutableStateFlow(ElementMatchGame.create(mutableRange.value, mutablePairs.value))
    val game: StateFlow<ElementMatchGame> = mutableGame

    private val mutableRecords = MutableStateFlow(ElementMatchRecords.load(application))
    val records: StateFlow<List<ElementMatchRecord>> = mutableRecords

    fun newGame() {
        mutableGame.value = ElementMatchGame.create(mutableRange.value, mutablePairs.value)
    }

    fun setRange(range: ElementRange) {
        mutableRange.value = range
        preferences.edit().putString("range", range.name).apply()
        newGame()
    }

    fun setPairs(pairs: Int) {
        mutablePairs.value = pairs
        preferences.edit().putInt("pairs", pairs).apply()
        newGame()
    }

    fun flip(index: Int) {
        val game = mutableGame.value
        val next = game.flip(index)
        mutableGame.value = next
        if (next.isComplete && !game.isComplete) {
            val record = ElementMatchRecord(System.currentTimeMillis(), next.seconds, next.tries, next.pairs, next.range)
            mutableRecords.value = listOf(record) + mutableRecords.value
            ElementMatchRecords.save(getApplication(), mutableRecords.value)
        }
    }

    fun hideMismatch() {
        mutableGame.value = mutableGame.value.hideMismatch()
    }

    /** 첫 장을 뒤집은 뒤부터 다 맞출 때까지만 시간이 간다. */
    fun tick() {
        val game = mutableGame.value
        if (game.tries > 0 || game.open.isNotEmpty()) {
            if (!game.isComplete) mutableGame.value = game.copy(seconds = game.seconds + 1)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ElementMatchTopBar(onBack: () -> Unit) {
    val viewModel: ElementMatchViewModel = viewModel()
    val records by viewModel.records.collectAsStateWithLifecycle()
    val range by viewModel.range.collectAsStateWithLifecycle()
    val pairs by viewModel.pairs.collectAsStateWithLifecycle()
    var recordsOpen by rememberSaveable { mutableStateOf(false) }
    var levelOpen by rememberSaveable { mutableStateOf(false) }

    if (recordsOpen) {
        GameRecordsDialog(
            records.map {
                "${formatDuration(it.seconds)} · 시도 ${it.tries}번 · ${it.range.label} ${it.pairs}쌍" to it.finishedAt
            },
            onDismiss = { recordsOpen = false },
        )
    }

    if (levelOpen) {
        AlertDialog(
            onDismissRequest = { levelOpen = false },
            title = { Text("난이도") },
            text = {
                Column {
                    Text("원소 범위", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ElementRange.entries.forEach { choice ->
                            FilterChip(
                                selected = choice == range,
                                onClick = { viewModel.setRange(choice) },
                                label = { Text(choice.label, maxLines = 1) },
                            )
                        }
                    }
                    Text(
                        "카드 수",
                        Modifier.padding(top = 12.dp),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PAIR_CHOICES.forEach { choice ->
                            FilterChip(
                                selected = choice == pairs,
                                onClick = { viewModel.setPairs(choice) },
                                label = { Text("${choice}쌍", maxLines = 1) },
                            )
                        }
                    }
                    Text(
                        "바꾸면 새 게임이 시작됩니다.",
                        Modifier.padding(top = 12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { levelOpen = false }) { Text("닫기") } },
        )
    }

    TopAppBar(
        title = { Text("원소 짝맞추기") },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
            }
        },
        actions = {
            TextButton(onClick = { levelOpen = true }) { Text("난이도") }
            TextButton(onClick = { recordsOpen = true }) { Text("기록") }
            TextButton(onClick = viewModel::newGame) { Text("새 게임") }
        },
    )
}

@Composable
fun ElementMatchScreen() {
    val viewModel: ElementMatchViewModel = viewModel()
    val game by viewModel.game.collectAsStateWithLifecycle()
    val records by viewModel.records.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    LifecycleResumeEffect(Unit) {
        val timer = scope.launch {
            while (true) {
                delay(1000)
                viewModel.tick()
            }
        }
        onPauseOrDispose { timer.cancel() }
    }

    // 다 맞추면 마지막 카드가 뒤집히는 것을 잠깐 보여 준 뒤 결과 창을 띄운다. 창을 닫으면(누르면) 새 게임.
    var doneOpen by remember { mutableStateOf(false) }
    LaunchedEffect(game.isComplete) {
        doneOpen = false
        if (game.isComplete) {
            delay(600)
            doneOpen = true
        }
    }
    if (doneOpen && game.isComplete) {
        val best = bestSeconds(game, records)
        val next = { doneOpen = false; viewModel.newGame() }
        AlertDialog(
            onDismissRequest = next,
            title = { Text(if (best != null && game.seconds < best) "새 기록!" else "모두 맞췄습니다!") },
            text = {
                Column {
                    Text("${formatDuration(game.seconds)} · 시도 ${game.tries}번 · ${game.range.label} ${game.pairs}쌍")
                    Text(
                        finishVersusBest(game.seconds, best),
                        Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            confirmButton = { TextButton(onClick = next) { Text("새 게임") } },
        )
    }

    // 짝이 아닌 두 장은 잠깐 보여 주고 다시 덮는다.
    LaunchedEffect(game.open) {
        if (game.isMismatchShowing) {
            delay(900)
            viewModel.hideMismatch()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().padding(12.dp)) {
        val rows = (game.cards.size + COLUMNS - 1) / COLUMNS
        val gap = 8.dp
        val statusHeight = 120.dp
        val cardWidth = min(
            (maxWidth - gap * (COLUMNS - 1)) / COLUMNS,
            ((maxHeight - statusHeight - gap * (rows - 1)) / rows) / CARD_RATIO,
        )

        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                for (row in 0 until rows) {
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        for (column in 0 until COLUMNS) {
                            val index = row * COLUMNS + column
                            if (index < game.cards.size) {
                                ElementCardView(
                                    card = game.cards[index],
                                    faceUp = game.isFaceUp(index),
                                    matched = index in game.matched,
                                    width = cardWidth,
                                    onClick = { viewModel.flip(index) },
                                )
                            }
                        }
                    }
                }
            }
            ElementMatchStatus(game, bestSeconds(game, records), Modifier.fillMaxWidth().padding(top = 12.dp))
        }
    }
}

@Composable
private fun ElementMatchStatus(game: ElementMatchGame, best: Int?, modifier: Modifier) {
    val lastNumber = game.lastMatch
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            if (game.isComplete) {
                "모두 맞췄습니다! ${formatDuration(game.seconds)} · 시도 ${game.tries}번"
            } else {
                "${formatDuration(game.seconds)} · 시도 ${game.tries}번 · 남은 짝 ${game.pairs - game.matched.size / 2}"
            },
            color = if (game.isComplete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            compareToBest(game, best),
            Modifier.padding(top = 2.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (lastNumber != null) {
            val element = PeriodicTable.get(lastNumber)
            Text(
                "${element.number}번 ${element.symbol} · ${element.name} (${element.english}) · ${element.category.label}",
                Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        } else {
            Text(
                "원소기호와 이름이 같은 원소끼리 짝입니다.",
                Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ElementCardView(card: ElementCard, faceUp: Boolean, matched: Boolean, width: Dp, onClick: () -> Unit) {
    val rotation by animateFloatAsState(if (faceUp) 180f else 0f, tween(250), label = "flip")
    val height = width * CARD_RATIO
    val density = LocalDensity.current.density

    Box(
        Modifier
            .size(width, height)
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12f * density
            }
            .alpha(if (matched) 0.6f else 1f)
            .clickable(enabled = !faceUp, onClick = onClick),
    ) {
        if (rotation <= 90f) {
            CardBack(Modifier.fillMaxSize())
        } else {
            // 뒤집힌 채로 그리면 글자가 거울처럼 보이므로 앞면은 한 번 더 뒤집는다.
            CardFront(card, width, Modifier.fillMaxSize().graphicsLayer { rotationY = 180f })
        }
    }
}

@Composable
private fun CardBack(modifier: Modifier) {
    val back = MaterialTheme.colorScheme.primary
    val line = MaterialTheme.colorScheme.onPrimary
    Box(modifier.background(back, CardShape).border(1.dp, back.copy(alpha = 0.6f), CardShape)) {
        // 원자 모양: 가운데 핵과 세 개의 궤도.
        Canvas(Modifier.fillMaxSize().padding(14.dp)) {
            val radius = size.minDimension / 2
            val orbit = Size(radius * 2, radius * 0.75f)
            val topLeft = Offset(center.x - orbit.width / 2, center.y - orbit.height / 2)
            for (angle in listOf(0f, 60f, 120f)) {
                rotate(angle) {
                    drawOval(line.copy(alpha = 0.85f), topLeft, orbit, style = Stroke(width = radius * 0.08f))
                }
            }
            drawCircle(line, radius * 0.14f, center)
        }
    }
}

@Composable
private fun CardFront(card: ElementCard, width: Dp, modifier: Modifier) {
    val element = PeriodicTable.get(card.number)
    val fill = Color(element.category.color)
    Box(modifier.background(fill, CardShape).border(1.dp, CardInk.copy(alpha = 0.25f), CardShape)) {
        // 원소 번호 — 기호 카드와 이름 카드 모두 왼쪽 위에.
        Text(
            element.number.toString(),
            Modifier.align(Alignment.TopStart).padding(start = 6.dp, top = 4.dp),
            color = CardInk,
            style = MaterialTheme.typography.labelMedium,
        )
        if (card.isSymbol) {
            Text(
                element.symbol,
                Modifier.align(Alignment.Center),
                color = CardInk,
                fontWeight = FontWeight.Bold,
                fontSize = with(LocalDensity.current) { (width * 0.42f).toSp() },
            )
        } else {
            val size = when {
                element.name.length <= 2 -> 0.30f
                element.name.length <= 4 -> 0.22f
                else -> 0.16f
            }
            Text(
                element.name,
                Modifier.align(Alignment.Center).padding(horizontal = 4.dp),
                color = CardInk,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                fontSize = with(LocalDensity.current) { (width * size).toSp() },
                maxLines = 2,
            )
        }
    }
}

/**
 * 같은 범위 · 같은 카드 수에서 가장 빠른 기록(초). 없으면 null.
 * 다 맞춘 판은 기록 맨 앞에 막 들어갔으므로 그것을 빼고 견준다.
 */
private fun bestSeconds(game: ElementMatchGame, records: List<ElementMatchRecord>): Int? =
    (if (game.isComplete) records.drop(1) else records)
        .filter { it.range == game.range && it.pairs == game.pairs }
        .minOfOrNull { it.seconds }

/** 가장 빠른 기록과 지금 시간의 차이. 하는 중에는 남은 시간 · 넘긴 시간을, 다 맞추면 빠르고 느린 정도를 보여 준다. */
private fun compareToBest(game: ElementMatchGame, best: Int?): String {
    if (best == null) return if (game.isComplete) "이 설정의 첫 기록입니다" else "이 설정(${game.range.label} ${game.pairs}쌍)의 기록이 아직 없습니다"
    val diff = game.seconds - best
    val head = "최고 기록 ${formatDuration(best)} · "
    return head + when {
        game.isComplete && diff < 0 -> "${formatDuration(-diff)} 빨라 새 기록!"
        game.isComplete && diff == 0 -> "최고 기록과 같습니다"
        game.isComplete -> "${formatDuration(diff)} 느림"
        diff <= 0 -> "${formatDuration(-diff)} 남음"
        else -> "${formatDuration(diff)} 넘김"
    }
}

/** 결과 창의 한 줄 — 가장 빠른 기록보다 얼마나 빨리(늦게) 했는지. */
private fun finishVersusBest(seconds: Int, best: Int?): String = when {
    best == null -> "이 설정의 첫 기록입니다"
    seconds < best -> "최고 기록(${formatDuration(best)})보다 ${formatDuration(best - seconds)} 빨리 했습니다"
    seconds == best -> "최고 기록(${formatDuration(best)})과 같습니다"
    else -> "최고 기록(${formatDuration(best)})보다 ${formatDuration(seconds - best)} 늦었습니다"
}
