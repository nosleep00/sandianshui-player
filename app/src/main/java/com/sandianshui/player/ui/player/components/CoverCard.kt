package com.sandianshui.player.ui.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sandianshui.player.core.subtitle.Cue
import com.sandianshui.player.ui.player.CardMode
import com.sandianshui.player.ui.player.PlayerUiState
import com.sandianshui.player.ui.theme.Black
import com.sandianshui.player.ui.theme.Surface
import com.sandianshui.player.ui.theme.TextPrimary
import com.sandianshui.player.ui.theme.TextSecondary
import com.sandianshui.player.ui.theme.TextTertiary

/**
 * 顶部大卡片：封面视图 与 字幕/歌词时间轴视图 二选一，点击切换。
 */
@Composable
fun CoverCard(
    state: PlayerUiState,
    onToggle: () -> Unit,
    onCueClick: (Cue) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Surface)
            .clickable(onClick = onToggle),
    ) {
        // 顶部内容模式胶囊
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 14.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Black)
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Text(
                text = state.contentLabel,
                color = TextSecondary,
                fontSize = 12.sp,
            )
        }

        when {
            state.cardMode == CardMode.TIMELINE && state.cues.isNotEmpty() ->
                Timeline(state, onCueClick)

            state.cardMode == CardMode.TIMELINE ->
                CenterHint(
                    title = state.contentLabel,
                    subtitle = "点击卡片返回封面",
                )

            state.coverUri != null ->
                AsyncImage(
                    model = state.coverUri,
                    contentDescription = "封面",
                    modifier = Modifier.fillMaxSize(),
                )

            else ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Headphones,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    Text("请导入封面图片", color = TextSecondary, fontSize = 15.sp)
                    Text(
                        "（点击可切换歌词时间轴）",
                        color = TextTertiary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
        }
    }
}

@Composable
private fun Timeline(state: PlayerUiState, onCueClick: (Cue) -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.activeCueIndex) {
        if (state.activeCueIndex >= 0) {
            listState.animateScrollToItem(state.activeCueIndex)
        }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 48.dp, start = 14.dp, end = 14.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        itemsIndexed(state.cues) { index, cue ->
            val active = index == state.activeCueIndex
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    // 点击该句：跳转到它的时间点播放（消费手势，不触发外层的视图切换）
                    .clickable { onCueClick(cue) }
                    .background(if (active) MaterialTheme.colorScheme.surfaceVariant else androidx.compose.ui.graphics.Color.Transparent)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                // 不限制行数、不省略，长句自动换行完整显示
                Text(
                    text = cue.text,
                    color = if (active) TextPrimary else TextTertiary,
                    fontSize = 15.sp,
                    lineHeight = 24.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun CenterHint(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, color = TextSecondary, fontSize = 15.sp, textAlign = TextAlign.Center)
        Text(
            subtitle,
            color = TextTertiary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
