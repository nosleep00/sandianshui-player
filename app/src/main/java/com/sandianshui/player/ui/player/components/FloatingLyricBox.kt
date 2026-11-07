package com.sandianshui.player.ui.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sandianshui.player.ui.theme.TextPrimary
import com.sandianshui.player.ui.theme.TextTertiary
import kotlin.math.roundToInt

/**
 * 可自由拖动的悬浮字幕 / 歌词显示框：
 * - 实时显示当前句（[text]）；
 * - 不限制行数、自动换行，长句完整显示（[TextOverflow.Visible]）；
 * - 位置可任意拖动并限制在屏幕范围内；文本变长 / 屏幕变化时自动收回边界内。
 *
 * @param hasCues 当前是否已加载字幕 / 歌词（决定空态文案）。
 */
@Composable
fun FloatingLyricBox(
    text: String?,
    hasCues: Boolean,
    parentSize: IntSize,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val margin = with(density) { 12.dp.roundToPx() }
    val defaultY = with(density) { 96.dp.roundToPx() }
    val maxWidthDp = with(density) {
        (parentSize.width - margin * 2).coerceAtLeast(0).toDp()
    }

    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var pos by remember { mutableStateOf<IntOffset?>(null) }

    fun clamp(p: IntOffset): IntOffset {
        val maxX = (parentSize.width - boxSize.width - margin * 2).coerceAtLeast(0)
        val maxY = (parentSize.height - boxSize.height - margin).coerceAtLeast(0)
        return IntOffset(p.x.coerceIn(0, maxX), p.y.coerceIn(0, maxY))
    }

    // 父容器 / 自身尺寸就绪后确定初始位置（水平居中、靠上），变化时保证不越界
    LaunchedEffect(parentSize, boxSize) {
        if (parentSize.width > 0 && boxSize.width > 0) {
            val current = pos
            pos = if (current == null) {
                IntOffset(
                    ((parentSize.width - boxSize.width) / 2).coerceAtLeast(margin),
                    defaultY,
                )
            } else {
                clamp(current)
            }
        }
    }

    val current = pos ?: IntOffset(margin, defaultY)

    Box(
        modifier = modifier
            .offset { current }
            .onSizeChanged { boxSize = it }
            .widthIn(max = maxWidthDp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xF21C1C1E))
            .border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(14.dp))
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    val base = pos ?: IntOffset(margin, defaultY)
                    val maxX = (parentSize.width - boxSize.width - margin * 2).coerceAtLeast(0)
                    val maxY = (parentSize.height - boxSize.height - margin).coerceAtLeast(0)
                    pos = IntOffset(
                        (base.x + drag.x.roundToInt()).coerceIn(0, maxX),
                        (base.y + drag.y.roundToInt()).coerceIn(0, maxY),
                    )
                }
            }
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // 顶部拖动指示条
            Box(
                modifier = Modifier
                    .padding(bottom = 7.dp)
                    .width(34.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0x73FFFFFF)),
            )
            Text(
                text = text ?: if (hasCues) "—" else "暂无歌词 · 暂无字幕",
                color = if (text != null) TextPrimary else TextTertiary,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                fontWeight = if (text != null) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                // 关键：不限行数、自动换行、不省略不截断
                maxLines = Int.MAX_VALUE,
                softWrap = true,
                overflow = TextOverflow.Visible,
            )
        }
    }
}
