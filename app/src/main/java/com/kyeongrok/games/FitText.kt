package com.kyeongrok.games

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * 한 줄에 들어갈 때까지 글자를 줄여 쓰는 Text — 「다리를 지나는 트럭」처럼 긴 이름이 두 줄로 넘어가지 않게.
 * 넘치면 10%씩 줄여 다시 그리고(그 사이엔 그리지 않아 깜박이지 않는다), minSize 아래로는 줄이지 않는다.
 */
@Composable
fun FitText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    textAlign: TextAlign? = null,
    minSize: TextUnit = 10.sp,
) {
    var size by remember(text, style) { mutableStateOf(style.fontSize) }
    var ready by remember(text, style) { mutableStateOf(false) }
    Text(
        text,
        modifier.drawWithContent { if (ready) drawContent() },
        style = style.copy(fontSize = size),
        textAlign = textAlign,
        maxLines = 1,
        softWrap = false,
        onTextLayout = { layout ->
            if (layout.hasVisualOverflow && size.value * 0.9f >= minSize.value) {
                size = (size.value * 0.9f).sp
            } else {
                ready = true
            }
        },
    )
}
