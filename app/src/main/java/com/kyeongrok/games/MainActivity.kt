package com.kyeongrok.games

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** 앱 안의 화면들이다. Home은 게임을 고르는 첫 화면이다. */
private enum class Screen { Home, Sudoku, Shanghai, Riichi, FreeCell, Rummikub, Splendor }

private class GameEntry(val label: String, val icon: ImageVector, val target: Screen)

private val GAME_ENTRIES = listOf(
    GameEntry("스도쿠", AppIcons.Sudoku, Screen.Sudoku),
    GameEntry("상하이", AppIcons.Shanghai, Screen.Shanghai),
    GameEntry("리치마작", AppIcons.Riichi, Screen.Riichi),
    GameEntry("프리셀", AppIcons.FreeCell, Screen.FreeCell),
    GameEntry("루미큐브", AppIcons.Rummikub, Screen.Rummikub),
    GameEntry("스플렌더", AppIcons.Splendor, Screen.Splendor),
)

class MainActivity : ComponentActivity() {
    private val viewModel: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val colorTheme by viewModel.colorTheme.collectAsStateWithLifecycle()
            val dark = isDarkTheme(themeMode)
            // 앱 테마가 시스템과 달라도 상태바 아이콘이 배경과 대비되도록 맞춘다.
            LaunchedEffect(dark) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            AppTheme(dark, colorTheme) { MainScreen(viewModel) }
        }
    }
}

@Composable
private fun isDarkTheme(mode: ThemeMode) = when (mode) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

@Composable
private fun AppTheme(dark: Boolean, color: ColorTheme, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) darkColorScheme() else lightScheme(color),
        content = { Surface(Modifier.fillMaxSize(), content = content) },
    )
}

private fun lightScheme(color: ColorTheme): ColorScheme {
    if (color == ColorTheme.Purple) return lightColorScheme()
    val primary = ComposeColor(color.primary)
    val container = ComposeColor(color.container)
    val onContainer = ComposeColor(color.onContainer)
    val secondaryContainer = ComposeColor(color.secondaryContainer)
    val background = ComposeColor(color.background)
    val variant = ComposeColor(color.surfaceVariant)
    return lightColorScheme(
        primary = primary,
        primaryContainer = container,
        onPrimaryContainer = onContainer,
        secondary = primary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onContainer,
        tertiary = primary,
        tertiaryContainer = container,
        onTertiaryContainer = onContainer,
        background = background,
        surface = background,
        surfaceVariant = variant,
        surfaceTint = primary,
        surfaceContainerLowest = ComposeColor.White,
        surfaceContainerLow = background,
        surfaceContainer = variant,
        surfaceContainerHigh = variant,
        surfaceContainerHighest = variant,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(viewModel: GameViewModel) {
    val context = LocalContext.current
    // 처음 켜면 마지막으로 하던 게임을, 기록이 없으면 게임 고르는 화면을 연다.
    var screen by rememberSaveable {
        val last = AppSettings.loadLastScreen(context)
        mutableStateOf(Screen.entries.find { it.name == last } ?: Screen.Home)
    }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(screen) { AppSettings.saveLastScreen(context, screen.name) }
    BackHandler(screen != Screen.Home) { screen = Screen.Home }

    if (settingsOpen) SettingsDialog(viewModel, onDismiss = { settingsOpen = false })

    val home = { screen = Screen.Home }
    Scaffold(
        topBar = {
            when (screen) {
                Screen.Sudoku -> SudokuTopBar(viewModel, onBack = home)
                Screen.Shanghai -> ShanghaiTopBar(viewModel, onBack = home)
                Screen.Riichi -> RiichiTopBar(onBack = home)
                Screen.FreeCell -> FreeCellTopBar(viewModel, onBack = home)
                Screen.Rummikub -> RummikubTopBar(viewModel, onBack = home)
                Screen.Splendor -> SplendorTopBar(viewModel, onBack = home)
                Screen.Home -> TopAppBar(
                    title = { AppTitle() },
                    actions = {
                        IconButton(onClick = { settingsOpen = true }) {
                            Icon(Icons.Filled.Settings, contentDescription = "설정")
                        }
                    },
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            when (screen) {
                Screen.Home -> HomeScreen(
                    onOpen = { screen = it },
                    onExit = { (context as? Activity)?.finishAndRemoveTask() },
                )
                Screen.Sudoku -> SudokuScreen(viewModel)
                Screen.Shanghai -> ShanghaiScreen(viewModel)
                Screen.Riichi -> RiichiScreen()
                Screen.FreeCell -> FreeCellScreen(viewModel)
                Screen.Rummikub -> RummikubScreen(viewModel)
                Screen.Splendor -> SplendorScreen(viewModel)
            }
        }
    }
}

@Composable
private fun AppTitle() {
    Row(verticalAlignment = Alignment.Bottom) {
        Text("게임")
        Text(
            "v${BuildConfig.VERSION_NAME}",
            Modifier.padding(start = 8.dp, bottom = 2.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/** 게임을 고르는 첫 화면. 바둑판처럼 늘어놓고 맨 끝에 종료를 둔다. */
@Composable
private fun HomeScreen(onOpen: (Screen) -> Unit, onExit: () -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(104.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(GAME_ENTRIES, key = { it.target.name }) { entry ->
            HomeTile(entry.label, entry.icon, MaterialTheme.colorScheme.primary) { onOpen(entry.target) }
        }
        item(key = "exit") {
            HomeTile("종료", Icons.AutoMirrored.Filled.ExitToApp, MaterialTheme.colorScheme.error, onExit)
        }
    }
}

@Composable
private fun HomeTile(label: String, icon: ImageVector, tint: ComposeColor, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, Modifier.size(40.dp), tint = tint)
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsDialog(viewModel: GameViewModel, onDismiss: () -> Unit) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val colorTheme by viewModel.colorTheme.collectAsStateWithLifecycle()
    val tableSize by viewModel.rummikubTableSize.collectAsStateWithLifecycle()
    val blinkDuration by viewModel.rummikubBlinkDuration.collectAsStateWithLifecycle()
    val dark = isDarkTheme(themeMode)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("설정") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("테마", style = MaterialTheme.typography.titleSmall)
                ThemeMode.entries.forEach { mode ->
                    Row(
                        Modifier.fillMaxWidth().clickable { viewModel.setThemeMode(mode) }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = mode == themeMode, onClick = null)
                        Text(mode.label, Modifier.padding(start = 12.dp))
                    }
                }
                if (!dark) {
                    Text(
                        "색상",
                        Modifier.padding(top = 12.dp, bottom = 8.dp),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ColorTheme.entries.forEach { color ->
                            ColorSwatch(color, color == colorTheme) { viewModel.setColorTheme(color) }
                        }
                    }
                }
                Text(
                    "루미큐브 판 타일 크기",
                    Modifier.padding(top = 12.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.titleSmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RummikubTableSize.entries.forEach { size ->
                        FilterChip(
                            selected = size == tableSize,
                            onClick = { viewModel.setRummikubTableSize(size) },
                            label = { Text(size.label, maxLines = 1) },
                        )
                    }
                }
                Text(
                    "루미큐브 뽑은 타일 깜빡임",
                    Modifier.padding(top = 12.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.titleSmall,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RummikubBlinkDuration.entries.forEach { duration ->
                        FilterChip(
                            selected = duration == blinkDuration,
                            onClick = { viewModel.setRummikubBlinkDuration(duration) },
                            label = { Text(duration.label, maxLines = 1) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}

@Composable
private fun ColorSwatch(color: ColorTheme, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .background(ComposeColor(color.primary), CircleShape)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = color.label },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = ComposeColor.White)
    }
}
