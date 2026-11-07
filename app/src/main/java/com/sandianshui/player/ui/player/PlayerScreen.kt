package com.sandianshui.player.ui.player

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.sandianshui.player.core.overlay.OverlayLyricService
import com.sandianshui.player.core.subtitle.SubtitleEncoding
import com.sandianshui.player.core.translate.AsrProviderType
import com.sandianshui.player.core.translate.GroqContextLevel
import com.sandianshui.player.core.translate.TranslatorProviderType
import com.sandianshui.player.ui.player.components.CoverCard
import com.sandianshui.player.ui.player.components.FloatingLyricBox
import com.sandianshui.player.ui.theme.AccentGreen
import com.sandianshui.player.ui.theme.Black
import com.sandianshui.player.ui.theme.SurfaceVariant
import com.sandianshui.player.ui.theme.TextPrimary
import com.sandianshui.player.ui.theme.TextSecondary
import com.sandianshui.player.ui.theme.TextTertiary
import com.sandianshui.player.ui.theme.TrackInactive
import com.sandianshui.player.util.formatTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(vm: PlayerViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showImportSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    var showTranslateSheet by remember { mutableStateOf(false) }
    val translateSheetState = rememberModalBottomSheetState()
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    var tab by remember { mutableStateOf(0) } // 0 = 本地，1 = 在线

    // SAF：选择文件夹（音频 + 自动匹配）
    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> uri?.let(vm::onFolderPicked) }

    // SAF：选择单个音频文件（只加载选中的那一首）
    val audioFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(vm::onAudioFilePicked) }

    // SAF：选择单张封面
    val coverLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(vm::onCoverPicked) }

    // SAF：选择字幕 / 歌词
    val subtitleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(vm::onSubtitlePicked) }

    // SAF：创建文件，用于保存 AI 翻译生成的 SRT 字幕
    val saveSrtLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*"),
    ) { uri -> uri?.let(vm::onSaveSubtitleDocument) }

    // ===== 桌面悬浮歌词：切到别的 App / 桌面后仍显示 =====
    val context = LocalContext.current
    val viewLifecycleOwner = LocalLifecycleOwner.current
    var desktopLyricOn by remember { mutableStateOf(false) }
    var pendingDesktop by remember { mutableStateOf(false) }

    fun enableDesktopLyric() {
        if (!Settings.canDrawOverlays(context)) {
            pendingDesktop = true
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}"),
                ),
            )
        } else {
            OverlayLyricService.start(context)
            desktopLyricOn = true
        }
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ -> enableDesktopLyric() } // 通知权限无论是否授予都继续，不影响悬浮窗

    fun toggleDesktopLyric() {
        if (desktopLyricOn) {
            OverlayLyricService.stop(context)
            desktopLyricOn = false
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            enableDesktopLyric()
        }
    }

    // 从系统「显示在其他应用上层」设置页授权返回后，自动完成开启
    DisposableEffect(viewLifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME &&
                pendingDesktop && Settings.canDrawOverlays(context)
            ) {
                pendingDesktop = false
                OverlayLyricService.start(context)
                desktopLyricOn = true
            }
        }
        viewLifecycleOwner.lifecycle.addObserver(observer)
        onDispose { viewLifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 一次性消息提示
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .onSizeChanged { rootSize = it },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(16.dp),
        ) {
            // ===== 顶部页签：本地 / 在线 =====
            TabRow(
                selectedTabIndex = tab,
                containerColor = Black,
                contentColor = TextPrimary,
            ) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = { Text("本地") },
                    selectedContentColor = AccentGreen,
                    unselectedContentColor = TextSecondary,
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text("在线") },
                    selectedContentColor = AccentGreen,
                    unselectedContentColor = TextSecondary,
                )
            }

            Spacer(Modifier.height(12.dp))

            // ===== 中间区域：本地封面/时间轴，或在线搜索面板 =====
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                if (tab == 0) {
                    CoverCard(
                        state = state,
                        onToggle = vm::onToggleCard,
                        onCueClick = vm::onCueClick,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    OnlineSearchPanel(
                        state = state,
                        onSearch = vm::onAsmrSearch,
                        onWorkClick = vm::onAsmrWorkClick,
                        onTrackClick = vm::onAsmrTrackClick,
                        onBack = vm::onBackToResults,
                        onPageChange = vm::onAsmrPageChange,
                        onShowSourceConfig = vm::onShowSourceConfig,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            Text(
                text = state.trackName ?: "未选择音频",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            if (state.trackCount > 1) {
                Text(
                    text = "第 ${state.currentTrackIndex + 1} / ${state.trackCount} 首",
                    color = TextTertiary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                )
            }

            Spacer(Modifier.height(8.dp))

            val fraction =
                if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f
            Slider(
                value = fraction.coerceIn(0f, 1f),
                onValueChange = vm::onSeek,
                enabled = state.hasTrack,
                colors = SliderDefaults.colors(
                    thumbColor = AccentGreen,
                    activeTrackColor = TextSecondary,
                    inactiveTrackColor = TrackInactive,
                ),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(formatTime(state.positionMs), color = TextSecondary, fontSize = 12.sp)
                Text(formatTime(state.durationMs), color = TextSecondary, fontSize = 12.sp)
            }

            Spacer(Modifier.height(10.dp))

            // 倍速：滑动调节（0.5x – 5.0x），右侧实时显示数值
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("倍速", color = TextSecondary, fontSize = 13.sp)
                Slider(
                    value = state.speed,
                    onValueChange = vm::onSpeedChange,
                    enabled = state.hasTrack,
                    valueRange = MIN_SPEED..MAX_SPEED,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = AccentGreen,
                        activeTrackColor = TextSecondary,
                        inactiveTrackColor = TrackInactive,
                    ),
                )
                Text(
                    speedLabel(state.speed),
                    color = TextPrimary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(48.dp),
                )
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ControlButton(
                    text = "导入",
                    emphasized = false,
                    modifier = Modifier.weight(1f),
                ) { showImportSheet = true }

                CircleIconButton(
                    icon = Icons.Filled.SkipPrevious,
                    enabled = state.canPrevious,
                    size = 54.dp,
                ) { vm.onPrevious() }

                CircleIconButton(
                    icon = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    enabled = state.hasTrack,
                    emphasized = true,
                    size = 78.dp,
                ) { vm.onPlayPause() }

                CircleIconButton(
                    icon = Icons.Filled.SkipNext,
                    enabled = state.canNext,
                    size = 54.dp,
                ) { vm.onNext() }

                // 右下角：有字幕 / 歌词时显示「编码切换」按钮，否则显示「AI译」调试按钮
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    if (state.hasSubtitle) {
                        EncodingButton(
                            current = state.encoding,
                            onSelect = vm::onEncodingChange,
                        )
                    } else {
                        ControlButton(
                            text = "AI译",
                            emphasized = false,
                        ) { showTranslateSheet = true }
                    }
                }
            }
        }

        // AI 翻译进度遮罩（覆盖全屏，确保按钮可点击）
        if (state.translationState !is TranslationUiState.Idle) {
            TranslationOverlay(
                state = state.translationState,
                onSave = {
                    val name = (state.trackName ?: "subtitle")
                        .substringBeforeLast('.') + ".srt"
                    saveSrtLauncher.launch(name)
                },
                onDismiss = vm::onDismissTranslation,
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 120.dp),
        )

        // 可自由拖动的悬浮字幕 / 歌词框（仅有字幕/歌词时出现；开启桌面悬浮歌词后隐藏，避免重复）
        if (state.hasTrack && state.cues.isNotEmpty() &&
            rootSize.width > 0 && !desktopLyricOn
        ) {
            FloatingLyricBox(
                text = state.floatingText,
                hasCues = state.cues.isNotEmpty(),
                parentSize = rootSize,
            )
        }

        if (showImportSheet) {
            ModalBottomSheet(
                onDismissRequest = { showImportSheet = false },
                sheetState = sheetState,
                containerColor = SurfaceVariant,
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 18.dp, vertical = 8.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        "导入",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    ImportItem(
                        icon = Icons.Filled.Cloud,
                        title = "AI 翻译生成字幕",
                        subtitle = "对在线音频自动识别语音并翻译为中文字幕（${vm.translateConfigData.selectedAsr.displayName} + ${vm.translateConfigData.selectedTranslator.displayName}）",
                    ) {
                        showImportSheet = false
                        vm.onTranslateCurrentAudio()
                    }
                    ImportItem(
                        icon = Icons.Filled.SdStorage,
                        title = "导入音频（选择所在文件夹）",
                        subtitle = "支持平铺或音频 / 字幕分文件夹，自动匹配同名 srt / vtt / lrc",
                    ) {
                        showImportSheet = false
                        folderLauncher.launch(null)
                    }
                    ImportItem(
                        icon = Icons.Filled.MusicNote,
                        title = "导入单个音频文件",
                        subtitle = "mp3 / wav，只加载选中的那一首",
                    ) {
                        showImportSheet = false
                        audioFileLauncher.launch(arrayOf("audio/*"))
                    }
                    ImportItem(
                        icon = Icons.Filled.Image,
                        title = "导入封面图片",
                        subtitle = "显示在顶部卡片",
                    ) {
                        showImportSheet = false
                        coverLauncher.launch(arrayOf("image/*"))
                    }
                    ImportItem(
                        icon = Icons.Filled.Description,
                        title = "导入字幕 / 歌词",
                        subtitle = "支持 SRT / VTT / LRC，手动覆盖自动匹配",
                    ) {
                        showImportSheet = false
                        subtitleLauncher.launch(arrayOf("*/*"))
                    }
                    ImportItem(
                        icon = Icons.Filled.Search,
                        title = "深度检索字幕 / 歌词",
                        subtitle = "递归搜索整个文件夹 / 作品，为当前曲目跨目录找字幕或歌词",
                    ) {
                        showImportSheet = false
                        vm.onDeepSearchSubtitle()
                    }
                    // 桌面悬浮歌词开关
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                            .background(SurfaceVariant)
                            .clickable { toggleDesktopLyric() }
                            .padding(15.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("桌面悬浮歌词", color = TextPrimary, fontSize = 15.sp)
                            Text(
                                "切到其他 App / 桌面仍显示，需授予“显示在其他应用上层”",
                                color = TextTertiary,
                                fontSize = 12.sp,
                            )
                        }
                        Switch(
                            checked = desktopLyricOn,
                            onCheckedChange = { toggleDesktopLyric() },
                        )
                    }

                    Spacer(Modifier.height(18.dp))
                }
            }
        }

        // AI 翻译底部抽屉：开始翻译 / 配置
        if (showTranslateSheet) {
            ModalBottomSheet(
                onDismissRequest = { showTranslateSheet = false },
                sheetState = translateSheetState,
                containerColor = SurfaceVariant,
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                ) {
                    Text(
                        "AI 翻译",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    ImportItem(
                        icon = Icons.Filled.Translate,
                        title = "开始翻译",
                        subtitle = "识别当前音频语音并翻译为中文字幕（${vm.translateConfigData.selectedAsr.displayName} + ${vm.translateConfigData.selectedTranslator.displayName}）",
                    ) {
                        showTranslateSheet = false
                        vm.onTranslateCurrentAudio()
                    }
                    ImportItem(
                        icon = Icons.Filled.Settings,
                        title = "配置",
                        subtitle = "更换语音识别和文字翻译的 API Key",
                    ) {
                        showTranslateSheet = false
                        vm.onShowTranslateConfig()
                    }
                    Spacer(Modifier.height(18.dp))
                }
            }
        }

        // AI 翻译 API Key 配置对话框
        if (state.showTranslateConfig) {
            TranslateConfigDialog(
                initialData = vm.translateConfigData,
                onSave = vm::onSaveTranslateConfig,
                onDismiss = vm::onDismissTranslateConfig,
            )
        }

        // 在线音源接口地址配置对话框
        if (state.showSourceConfig) {
            SourceConfigDialog(
                initialUrl = vm.sourceConfigUrl,
                onSave = vm::onSaveSourceConfig,
                onDismiss = vm::onDismissSourceConfig,
            )
        }
    }
}

@Composable
private fun OnlineSearchPanel(
    state: PlayerUiState,
    onSearch: (String) -> Unit,
    onWorkClick: (com.sandianshui.player.core.remote.AsmrWork) -> Unit,
    onTrackClick: (Int) -> Unit,
    onBack: () -> Unit,
    onPageChange: (Int) -> Unit,
    onShowSourceConfig: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    Column(modifier = modifier) {
        // 搜索框 + 设置按钮 + 搜索按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("搜索关键词", color = TextTertiary) },
                modifier = Modifier.weight(1f),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = SurfaceVariant,
                    unfocusedContainerColor = SurfaceVariant,
                    focusedIndicatorColor = AccentGreen,
                    unfocusedIndicatorColor = TrackInactive,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                ),
            )
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(SurfaceVariant)
                    .clickable { onShowSourceConfig() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = "音源接口设置",
                    tint = TextSecondary,
                )
            }
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(AccentGreen)
                    .clickable { onSearch(query) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Search, contentDescription = "搜索", tint = Black)
            }
        }

        Spacer(Modifier.height(12.dp))

        when {
            // 已展开某作品：显示该作品全部音轨
            state.selectedWork != null -> AsmrTrackList(
                state = state,
                onTrackClick = onTrackClick,
                onBack = onBack,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            state.isSearchingAsmr -> Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = AccentGreen)
            }
            state.asmrResults.isEmpty() -> Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Cloud,
                        contentDescription = null,
                        tint = TextTertiary,
                        modifier = Modifier.size(44.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "点击左侧齿轮填写音源接口地址，再输入关键词搜索",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            else -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = rememberLazyListState(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(state.asmrResults) { work ->
                            AsmrResultRow(work = work, onClick = { onWorkClick(work) })
                        }
                    }
                    if (state.isLoadingTracks) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Black.copy(alpha = 0.65f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = AccentGreen)
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    "正在加载该作品音轨…",
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                )
                            }
                        }
                    }
                }

                // 分页栏：共 N 条 · 第 x / y 页，上一页 / 下一页
                PaginationBar(
                    currentPage = state.asmrCurrentPage,
                    totalPages = state.asmrTotalPages,
                    totalCount = state.asmrTotalCount,
                    onPageChange = onPageChange,
                )
            }
        }
    }
}

@Composable
private fun PaginationBar(
    currentPage: Int,
    totalPages: Int,
    totalCount: Int,
    onPageChange: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "共 $totalCount 条 · 第 $currentPage / $totalPages 页",
            color = TextSecondary,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PagerButton("上一页", enabled = currentPage > 1) {
                onPageChange(currentPage - 1)
            }
            Spacer(Modifier.width(16.dp))
            PagerButton("下一页", enabled = currentPage < totalPages) {
                onPageChange(currentPage + 1)
            }
        }
    }
}

@Composable
private fun PagerButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (enabled) AccentGreen else TrackInactive)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (enabled) Black else TextTertiary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun AsmrTrackList(
    state: PlayerUiState,
    onTrackClick: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val work = state.selectedWork
    val tracks = state.workTracks
    Column(modifier = modifier) {
        // 顶部：返回 + 作品标题
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(SurfaceVariant)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回搜索结果",
                    tint = TextPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                work?.title ?: "音轨",
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                "共 ${tracks.size} 首",
                color = TextSecondary,
                fontSize = 12.sp,
            )
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(tracks) { index, track ->
                val active = index == state.currentTrackIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (active) AccentGreen.copy(alpha = 0.16f) else SurfaceVariant)
                        .clickable { onTrackClick(index) }
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        String.format("%02d", index + 1),
                        color = if (active) AccentGreen else TextTertiary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.width(28.dp),
                    )
                    Text(
                        track.title,
                        color = if (active) TextPrimary else TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    track.durationSec?.let {
                        Text(
                            formatTrackDuration(it),
                            color = TextTertiary,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }
}

/** 把音轨秒数格式化为 m:ss 或 h:mm:ss。 */
private fun formatTrackDuration(sec: Double): String {
    val total = sec.toLong()
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) {
        String.format("%d:%02d:%02d", h, m, s)
    } else {
        String.format("%d:%02d", m, s)
    }
}

@Composable
private fun AsmrResultRow(
    work: com.sandianshui.player.core.remote.AsmrWork,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceVariant)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Black),
            contentAlignment = Alignment.Center,
        ) {
            if (work.coverUrl != null) {
                AsyncImage(
                    model = work.coverUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(Icons.Filled.MusicNote, contentDescription = null, tint = TextTertiary)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                work.title,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            val meta = buildList {
                if (work.circle.isNotBlank()) add(work.circle)
                if (work.vas.isNotEmpty()) add(work.vas.joinToString("、"))
                work.durationSec?.let { add(formatDurationSeconds(it)) }
            }.joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(meta, color = TextSecondary, fontSize = 12.sp)
            }
        }
    }
}

/** 把秒数格式化为「X小时Y分」或「X分」。 */
private fun formatDurationSeconds(sec: Int): String {
    val totalMin = sec / 60
    return if (totalMin >= 60) {
        "${totalMin / 60}小时${totalMin % 60}分"
    } else {
        "${totalMin}分"
    }
}

@Composable
private fun ControlButton(
    text: String,
    emphasized: Boolean,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit,
) {
    val container = if (emphasized) Color.White else SurfaceVariant
    val content = if (emphasized) Black else TextPrimary
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
        ),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.height(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CircleIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    size: Dp,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    onClick: () -> Unit,
) {
    val container = when {
        !enabled -> SurfaceVariant
        emphasized -> Color.White
        else -> SurfaceVariant
    }
    val tint = when {
        !enabled -> TextTertiary
        emphasized -> Black
        else -> TextPrimary
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(container)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size * 0.46f),
        )
    }
}

@Composable
private fun EncodingButton(
    current: SubtitleEncoding,
    onSelect: (SubtitleEncoding) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val options = buildList {
        add(SubtitleEncoding.AUTO)
        addAll(SubtitleEncoding.manualOptions)
    }
    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(SurfaceVariant)
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("编码", color = TextSecondary, fontSize = 12.sp)
            Text(
                current.displayName,
                color = AccentGreen,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = SurfaceVariant,
        ) {
            options.forEach { enc ->
                val selected = enc == current
                DropdownMenuItem(
                    text = {
                        Text(
                            if (selected) "${enc.displayName}  ✓" else enc.displayName,
                            color = if (selected) AccentGreen else TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(enc)
                    },
                )
            }
        }
    }
}

@Composable
private fun ImportItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .background(SurfaceVariant)
            .clickable(onClick = onClick)
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = TextSecondary)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = TextPrimary, fontSize = 15.sp)
            Text(subtitle, color = TextTertiary, fontSize = 12.sp)
        }
    }
}

/**
 * AI 翻译进度遮罩：覆盖在卡片区域，显示识别/翻译进度或完成/错误状态。
 */
@Composable
private fun TranslationOverlay(
    state: TranslationUiState,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (state) {
                is TranslationUiState.AsrRunning -> {
                    CircularProgressIndicator(color = AccentGreen)
                    Text("语音识别中… ${state.elapsedSeconds}s", color = TextPrimary, fontSize = 15.sp)
                    Text("长音频可能需要 1-3 分钟，请稍候", color = TextTertiary, fontSize = 12.sp)
                }
                is TranslationUiState.Translating -> {
                    CircularProgressIndicator(color = AccentGreen)
                    Text("翻译中…", color = TextPrimary, fontSize = 15.sp)
                    if (state.estimatedSeconds != null) {
                        Text(
                            "预估还需约 ${formatDuration(state.estimatedSeconds)}",
                            color = TextTertiary,
                            fontSize = 12.sp,
                        )
                    }
                }
                is TranslationUiState.Done -> {
                    Icon(Icons.Filled.Cloud, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(40.dp))
                    Text("AI 字幕已生成", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = onSave,
                            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                        ) { Text("保存到本地", color = Black, fontSize = 13.sp) }
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariant),
                        ) { Text("仅本次使用", color = TextPrimary, fontSize = 13.sp) }
                    }
                    Text("不保存则退出后自动清理，不占本地空间", color = TextTertiary, fontSize = 11.sp)
                }
                is TranslationUiState.Error -> {
                    Text("✕", color = Color(0xFFFF6B6B), fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Text(state.message, color = TextPrimary, fontSize = 14.sp, textAlign = TextAlign.Center)
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariant),
                    ) { Text("知道了", color = TextPrimary, fontSize = 13.sp) }
                }
                is TranslationUiState.Idle -> {}
            }
        }
    }
}

/**
 * AI 翻译配置对话框：可为语音识别 / 文字翻译分别选择服务商并填写 Key。
 * 每个服务商的 Key 独立保存，切换服务商时不会丢失。
 */
@Composable
private fun TranslateConfigDialog(
    initialData: TranslateConfigData,
    onSave: (TranslateConfigData) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedAsr by remember { mutableStateOf(initialData.selectedAsr) }
    var selectedTranslator by remember { mutableStateOf(initialData.selectedTranslator) }
    var groqLevel by remember { mutableStateOf(initialData.groqContextLevel) }
    val asrKeys = remember {
        mutableStateMapOf<AsrProviderType, String>().apply {
            initialData.asrTypes.forEach { put(it, initialData.asrKeys[it].orEmpty()) }
        }
    }
    val translatorKeys = remember {
        mutableStateMapOf<TranslatorProviderType, String>().apply {
            initialData.translatorTypes.forEach {
                put(it, initialData.translatorKeys[it].orEmpty())
            }
        }
    }
    val translatorKeys2 = remember {
        mutableStateMapOf<TranslatorProviderType, String>().apply {
            initialData.translatorTypes.forEach {
                put(it, initialData.translatorKeys2[it].orEmpty())
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = SurfaceVariant,
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "配置 AI 翻译",
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )

                // ===== 语音识别 =====
                Text("语音识别（ASR）", color = TextSecondary, fontSize = 13.sp)
                ProviderDropdown(
                    options = initialData.asrTypes,
                    selected = selectedAsr,
                    optionLabel = { it.displayName },
                    onSelect = { selectedAsr = it },
                )
                KeyField(
                    value = asrKeys[selectedAsr].orEmpty(),
                    onChange = { asrKeys[selectedAsr] = it },
                )

                // ===== 文字翻译 =====
                Text("文字翻译", color = TextSecondary, fontSize = 13.sp)
                ProviderDropdown(
                    options = initialData.translatorTypes,
                    selected = selectedTranslator,
                    optionLabel = { it.displayName },
                    onSelect = { selectedTranslator = it },
                )
                KeyField(
                    value = translatorKeys[selectedTranslator].orEmpty(),
                    onChange = { translatorKeys[selectedTranslator] = it },
                    placeholder = if (selectedTranslator == TranslatorProviderType.LARA)
                        "请输入 Access Key ID" else "请输入 API Key",
                )
                // Lara 需要额外的 Access Key Secret
                if (selectedTranslator == TranslatorProviderType.LARA) {
                    KeyField(
                        value = translatorKeys2[selectedTranslator].orEmpty(),
                        onChange = { translatorKeys2[selectedTranslator] = it },
                        placeholder = "请输入 Access Key Secret",
                    )
                }

                // Groq 上下文能力五档滑块
                if (selectedTranslator == TranslatorProviderType.GROQ) {
                    GroqContextLevelSelector(
                        level = groqLevel,
                        onChange = { groqLevel = it },
                    )
                }

                Text(
                    "Key 仅保存在本机、不会上传；切换服务商时已填 Key 会保留",
                    color = TextTertiary,
                    fontSize = 11.sp,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消", color = TextPrimary, fontSize = 13.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSave(
                                TranslateConfigData(
                                    asrTypes = initialData.asrTypes,
                                    selectedAsr = selectedAsr,
                                    asrKeys = initialData.asrTypes.associateWith {
                                        asrKeys[it].orEmpty()
                                    },
                                    translatorTypes = initialData.translatorTypes,
                                    selectedTranslator = selectedTranslator,
                                    translatorKeys = initialData.translatorTypes.associateWith {
                                        translatorKeys[it].orEmpty()
                                    },
                                    translatorKeys2 = initialData.translatorTypes.associateWith {
                                        translatorKeys2[it].orEmpty()
                                    },
                                    groqContextLevel = groqLevel,
                                ),
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                    ) { Text("保存", color = Black, fontSize = 13.sp) }
                }
            }
        }
    }
}

/**
 * Groq 上下文能力五档选择器。
 * 档位越高，参考上下文越多、推理越深，译文更自然，但耗时越长。
 */
@Composable
private fun GroqContextLevelSelector(
    level: GroqContextLevel,
    onChange: (GroqContextLevel) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Black)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "上下文能力（档位越高译文越自然）",
            color = TextSecondary,
            fontSize = 13.sp,
        )
        Slider(
            value = level.ordinal.toFloat(),
            onValueChange = { v ->
                onChange(GroqContextLevel.fromOrdinal(v.toInt()))
            },
            valueRange = 0f..4f,
            // 5 个离散位置：steps = 档位间隔数 - 1 = 3
            steps = 3,
            colors = SliderDefaults.colors(
                thumbColor = AccentGreen,
                activeTrackColor = AccentGreen,
                inactiveTrackColor = TextTertiary,
            ),
        )
        // 五档标签，当前档高亮
        Row(modifier = Modifier.fillMaxWidth()) {
            GroqContextLevel.entries.forEach { lvl ->
                val active = lvl == level
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        lvl.label,
                        color = if (active) AccentGreen else TextTertiary,
                        fontSize = 12.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
        Text(
            "${level.reasoningLabel} · 参考前后文各 ${level.contextSentences} 句 · " +
                "耗时约为“弱”档的 ${String.format("%.1f", level.timeMultiplier)} 倍",
            color = TextTertiary,
            fontSize = 11.sp,
        )
        Text(
            "预估约每 10 句 ${level.baseSecondsPerBatch} 秒（实际以网络为准，不含语音识别时间）",
            color = TextTertiary,
            fontSize = 11.sp,
        )
    }
}

/** 服务商下拉选择器。 */
@Composable
private fun <T> ProviderDropdown(
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Black)
                .clickable { expanded = true }
                .padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(optionLabel(selected), color = TextPrimary, fontSize = 14.sp)
            Icon(
                Icons.Filled.ArrowDropDown,
                contentDescription = "选择服务商",
                tint = TextSecondary,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option), color = TextPrimary) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** API Key 输入框。 */
@Composable
private fun KeyField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String = "请输入 API Key",
) {
    TextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(placeholder, color = TextTertiary) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Black,
            unfocusedContainerColor = Black,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
        ),
    )
}

/**
 * 在线音源接口地址配置对话框。
 * 应用不内置接口地址，由用户填写兼容的 API 根地址；仅保存在本机。
 */
@Composable
private fun SourceConfigDialog(
    initialUrl: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var url by remember { mutableStateOf(initialUrl) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = SurfaceVariant,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "音源接口设置",
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "本应用不内置在线音源接口。请填写与所用 JSON API 兼容的接口根地址，" +
                        "应用会在其下拼接 /api/search、/api/tracks 等路径。",
                    color = TextSecondary,
                    fontSize = 12.sp,
                )
                TextField(
                    value = url,
                    onValueChange = { url = it },
                    singleLine = true,
                    placeholder = {
                        Text("例如 https://your-api-host.example", color = TextTertiary)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Black,
                        unfocusedContainerColor = Black,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                    ),
                )
                Text(
                    "地址仅保存在本机、不会上传；留空保存即清除接口地址。",
                    color = TextTertiary,
                    fontSize = 11.sp,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消", color = TextPrimary, fontSize = 13.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onSave(url) },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                    ) { Text("保存", color = Black, fontSize = 13.sp) }
                }
            }
        }
    }
}
