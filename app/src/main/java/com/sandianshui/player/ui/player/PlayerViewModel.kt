package com.sandianshui.player.ui.player

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sandianshui.player.core.media.FolderCategories
import com.sandianshui.player.core.media.FolderCategory
import com.sandianshui.player.core.media.FolderScanner
import com.sandianshui.player.core.media.MediaTrack
import com.sandianshui.player.core.media.Playlist
import com.sandianshui.player.core.media.TextFileLoader
import com.sandianshui.player.core.overlay.OverlayLyricBus
import com.sandianshui.player.core.player.Media3PlayerEngine
import com.sandianshui.player.core.player.PlayerEngine
import com.sandianshui.player.core.remote.AsmrOne
import com.sandianshui.player.core.remote.AsmrTrack
import com.sandianshui.player.core.remote.AsmrWork
import com.sandianshui.player.core.remote.RemoteSourceStore
import com.sandianshui.player.core.remote.SimpleHttp
import com.sandianshui.player.core.subtitle.Cue
import com.sandianshui.player.core.subtitle.SubtitleDecoder
import com.sandianshui.player.core.subtitle.SubtitleEncoding
import com.sandianshui.player.core.subtitle.SubtitleFormat
import com.sandianshui.player.core.subtitle.SubtitleMatcher
import com.sandianshui.player.core.subtitle.SubtitleParsers
import com.sandianshui.player.core.subtitle.activeIndexAt
import com.sandianshui.player.core.subtitle.displayedText
import com.sandianshui.player.core.translate.AsrProviderFactory
import com.sandianshui.player.core.translate.AsrProviderType
import com.sandianshui.player.core.translate.TranslatorProviderFactory
import com.sandianshui.player.core.translate.TranslatorProviderType
import com.sandianshui.player.core.translate.TranslateConfigStore
import com.sandianshui.player.core.translate.TranslationCache
import com.sandianshui.player.core.translate.TranslationPipeline
import com.sandianshui.player.core.translate.TranslateException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val engine: PlayerEngine = Media3PlayerEngine(app)

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    /** 当前文件夹的播放列表与当前曲目。 */
    private val playlist = Playlist<MediaTrack>()

    /**
     * 当前曲目字幕 / 歌词的原始字节（本地读 content、在线走 HTTP）。
     * 手动切换编码时据此重新解码，不必重新读取文件 / 网络。
     */
    private var subtitleBytes: ByteArray? = null

    /**
     * 手动导入的封面图片。一旦设置，切「上一首 / 下一首」时保留显示，
     * 不会因新曲目没有封面而消失，只有再次导入新图片才会覆盖；
     * 导入新文件夹 / 单个音频 / 在线播放时重置为 null（恢复自动匹配封面）。
     */
    private var persistentCover: Uri? = null

    /**
     * 本地「选择所在文件夹」导入时记录的根目录 Uri，供「深度检索字幕」递归遍历使用。
     * 单文件导入时为 null（SAF 不授权父目录，无法跨文件检索）。
     */
    private var localRootTreeUri: Uri? = null

    /** AI 翻译生成的 SRT 文本（内存中，用户可选择保存到本地）。 */
    private var translatedSrtText: String? = null

    /** AI 翻译配置与缓存（懒加载）。 */
    private val translateConfig by lazy { TranslateConfigStore(getApplication()) }
    private val translateCache by lazy { TranslationCache(getApplication()) }

    /** 在线音源接口地址存储（懒加载）。 */
    private val sourceStore by lazy { RemoteSourceStore(getApplication()) }

    /** 当前在线音源接口根地址；未配置为空串。 */
    private val sourceBaseUrl: String get() = sourceStore.baseUrl

    /** 每次调用时读取最新配置创建管线（用户改 Key / 切换服务商后立即生效）。 */
    private fun createTranslatePipeline(): TranslationPipeline =
        TranslationPipeline(
            asr = AsrProviderFactory.create(
                translateConfig.selectedAsr,
                translateConfig.currentAsrKey,
            ),
            translator = TranslatorProviderFactory.create(
                translateConfig.selectedTranslator,
                translateConfig.currentTranslatorKey,
                translateConfig.currentTranslatorKey2,
                translateConfig.groqContextLevel,
            ),
            cache = translateCache,
        )

    /** 拉取在线字幕 / 歌词时使用的浏览器 UA（与搜索请求一致）。 */
    private val ASMR_UA =
        "Mozilla/5.0 (Linux; Android 13; Pixel 8) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

    /** 当前曲目（可能为 null）；与 [playlist] 当前项保持同步。 */
    private val currentTrack: MediaTrack? get() = playlist.current

    init {
        // 轮询播放位置，驱动进度条与当前句高亮
        viewModelScope.launch {
            while (true) {
                delay(100)
                _state.update { s ->
                    if (!s.hasTrack) return@update s
                    val index = s.cues.activeIndexAt(engine.currentPositionMs)
                    s.copy(
                        isPlaying = engine.isPlaying,
                        positionMs = engine.currentPositionMs,
                        durationMs = engine.durationMs.takeIf { it > 0 } ?: s.durationMs,
                        activeCueIndex = index,
                        // 未命中时保留上一句，悬浮框不空白
                        floatingText = s.cues.displayedText(index, s.floatingText),
                    )
                }
            }
        }

        // 把当前句同步到系统悬浮窗总线，供「跨 App / 桌面悬浮歌词」使用
        viewModelScope.launch {
            _state.collect { s ->
                OverlayLyricBus.update(s.floatingText, s.cues.isNotEmpty())
            }
        }
    }

    // ===== 导入：选择包含音频的文件夹（SAF OpenDocumentTree）=====
    fun onFolderPicked(treeUri: Uri) {
        val tracks = FolderScanner.scan(getApplication(), treeUri)
        if (tracks.isEmpty()) {
            showMessage("该文件夹未找到 mp3 / wav 音频")
            return
        }
        playlist.set(tracks, index = 0)
        persistentCover = null
        localRootTreeUri = treeUri
        applyTrack(autoPlay = false)

        // 判断是否直接选了「音频」分类文件夹（此时同级字幕文件夹在授权范围之外）
        val leaf = FolderScanner.pickedLeafName(treeUri)
        val directlyChoseAudioFolder = leaf != null &&
            FolderCategories.categoryOf(leaf) == FolderCategory.AUDIO
        val anySubtitle = tracks.any { it.subtitleFormat != null }

        val message = when {
            directlyChoseAudioFolder && !anySubtitle ->
                "你选择的是「音频」文件夹，安卓限制无法读取同级的字幕文件夹。" +
                    "请改选同时包含音频与字幕文件夹的上一级文件夹，字幕会按同名自动匹配"
            tracks.size > 1 ->
                "文件夹内找到 ${tracks.size} 首，可用「上一首 / 下一首」切换"
            tracks.first().subtitleFormat != null ->
                "已自动匹配${if (tracks.first().subtitleFormat?.isSubtitle == true) "字幕" else "歌词"}（同名优先）"
            else -> null
        }
        if (message != null) showMessage(message)
    }

    // ===== 导入：单个音频文件（SAF OpenDocument，只加载选中的那一首）=====
    fun onAudioFilePicked(uri: Uri) {
        val track = FolderScanner.scanSingleAudio(getApplication(), uri)
        if (track == null) {
            showMessage("无法读取该音频文件")
            return
        }
        playlist.set(listOf(track), index = 0)
        persistentCover = null
        localRootTreeUri = null
        applyTrack(autoPlay = false)
        if (track.subtitleFormat == null) {
            showMessage("已加载：${track.displayName}（未自动匹配字幕/歌词，可手动导入或改用「选择文件夹」）")
        }
    }

    // ===== 上一首 / 下一首（同一文件夹内切换，自动匹配随曲目走）=====
    fun onPrevious() {
        if (!playlist.canPrevious) return
        playlist.previous()
        applyTrack(autoPlay = true)
    }

    fun onNext() {
        if (!playlist.canNext) return
        playlist.next()
        applyTrack(autoPlay = true)
    }

    // ===== 手动导入封面 =====
    fun onCoverPicked(uri: Uri) {
        FolderScanner.persistReadPermission(getApplication(), uri)
        persistentCover = uri
        _state.update { it.copy(coverUri = uri, cardMode = CardMode.COVER) }
        showMessage("封面已设置，切歌后仍保留，导入新图片才会替换")
    }

    // ===== 手动导入字幕 / 歌词 =====
    fun onSubtitlePicked(uri: Uri) {
        val current = currentTrack
        if (current == null) {
            showMessage("请先导入音频")
            return
        }
        val format = SubtitleFormat.fromFileName(uri.lastPathSegment)
        if (format == null) {
            showMessage("仅支持 SRT / VTT / LRC 文件")
            return
        }
        FolderScanner.persistReadPermission(getApplication(), uri)
        val bytes = TextFileLoader.readBytes(getApplication(), uri)
        subtitleBytes = bytes
        val cues = bytes?.let {
            SubtitleParsers.parse(format, SubtitleDecoder.decode(it, SubtitleEncoding.AUTO))
        }.orEmpty()
        val index = cues.activeIndexAt(engine.currentPositionMs)
        playlist.updateCurrent(current.copy(subtitleUri = uri, subtitleFormat = format))
        _state.update {
            it.copy(
                cues = cues,
                activeCueIndex = index,
                floatingText = cues.displayedText(index, null),
                contentLabel = labelFor(format, cues),
                hasSubtitle = true,
                encoding = SubtitleEncoding.AUTO,
            )
        }
        showMessage("已手动导入${if (format.isSubtitle) "字幕" else "歌词"}")
    }

    // ===== 深度检索字幕 / 歌词（本地递归遍历文件夹 / 在线遍历作品树，本地与在线通用）=====
    fun onDeepSearchSubtitle() {
        val track = currentTrack ?: run { showMessage("请先导入音频"); return }
        val baseName = track.displayName.substringBeforeLast('.', track.displayName)
        val isOnline = track.audioUri.scheme?.startsWith("http") == true
        viewModelScope.launch {
            showMessage("正在深度检索字幕 / 歌词…")
            val outcome = withContext(Dispatchers.IO) {
                if (isOnline) deepSearchOnline(baseName) else deepSearchLocal(baseName)
            }
            when (outcome) {
                is DeepSearchOutcome.Found -> applyFoundSubtitle(outcome)
                is DeepSearchOutcome.NotFound -> showMessage(outcome.reason)
            }
        }
    }

    /** 本地：递归遍历 [localRootTreeUri] 整棵目录树，严格同名优先、模糊包含兜底。 */
    private fun deepSearchLocal(baseName: String): DeepSearchOutcome {
        val root = localRootTreeUri
            ?: return DeepSearchOutcome.NotFound(
                "单文件导入无法跨文件检索，请改用「导入音频（选择所在文件夹）」"
            )
        val result = FolderScanner.deepSearchSubtitle(getApplication(), root, baseName)
            ?: return DeepSearchOutcome.NotFound("未在文件夹内找到匹配的字幕 / 歌词")
        FolderScanner.persistReadPermission(getApplication(), result.file.uri)
        return DeepSearchOutcome.Found(
            format = result.match.format,
            localUri = result.file.uri,
            candidateCount = result.candidateCount,
            fileName = result.match.fileName,
        )
    }

    /** 在线：拉取作品整棵文件树的全部字幕 / 歌词，再按当前音轨名匹配。 */
    private fun deepSearchOnline(baseName: String): DeepSearchOutcome {
        val work = _state.value.selectedWork
            ?: return DeepSearchOutcome.NotFound("在线作品信息缺失")
        val candidates = AsmrOne.subtitleCandidates(work.id, sourceBaseUrl)
        if (candidates.isEmpty()) {
            return DeepSearchOutcome.NotFound("该作品未找到任何字幕 / 歌词文件（网络不可达或该站需要代理）")
        }
        val match = SubtitleMatcher.matchDeep(baseName, candidates.map { it.fileName })
            ?: return DeepSearchOutcome.NotFound("在 ${candidates.size} 个字幕 / 歌词中未找到匹配项")
        val chosen = candidates.first { it.fileName == match.fileName }
        return DeepSearchOutcome.Found(
            format = chosen.format,
            onlineUrl = chosen.url,
            candidateCount = candidates.size,
            fileName = chosen.fileName,
        )
    }

    /** 把深度检索到的字幕应用到当前曲目：更新播放列表项、重置编码、异步加载字节并解码。 */
    private fun applyFoundSubtitle(found: DeepSearchOutcome.Found) {
        val current = currentTrack ?: return
        val updated = current.copy(
            subtitleUri = found.localUri,
            subtitleFormat = found.format,
            subtitleTextUrl = found.onlineUrl,
        )
        playlist.updateCurrent(updated)
        subtitleBytes = null
        _state.update {
            it.copy(
                cues = emptyList(),
                activeCueIndex = -1,
                floatingText = null,
                contentLabel = labelFor(found.format, emptyList()),
                hasSubtitle = true,
                encoding = SubtitleEncoding.AUTO,
            )
        }
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.IO) { loadSubtitleBytes(updated) }
            subtitleBytes = bytes
            val cues = bytes?.let {
                SubtitleParsers.parse(found.format, SubtitleDecoder.decode(it, SubtitleEncoding.AUTO))
            }.orEmpty()
            val position = engine.currentPositionMs
            val index = cues.activeIndexAt(position)
            _state.update {
                it.copy(
                    cues = cues,
                    activeCueIndex = index,
                    floatingText = cues.displayedText(index, null),
                    contentLabel = labelFor(found.format, cues),
                )
            }
        }
        val kind = if (found.format.isSubtitle) "字幕" else "歌词"
        showMessage("深度检索：在 ${found.candidateCount} 个文件中匹配到$kind「${found.fileName}」")
    }

    private sealed class DeepSearchOutcome {
        data class Found(
            val format: SubtitleFormat,
            val localUri: Uri? = null,
            val onlineUrl: String? = null,
            val candidateCount: Int,
            val fileName: String,
        ) : DeepSearchOutcome()

        data class NotFound(val reason: String) : DeepSearchOutcome()
    }

    // ===== AI 翻译生成字幕（在线音频，百炼 ASR + DeepSeek 翻译）=====
    fun onTranslateCurrentAudio() {
        Log.d("Translate", "onTranslateCurrentAudio called")
        val track = currentTrack ?: run { Log.d("Translate", "currentTrack is null"); showMessage("请先导入音频"); return }
        val isOnline = track.audioUri.scheme?.startsWith("http") == true
        Log.d("Translate", "track=${track.displayName}, isOnline=$isOnline, uri=${track.audioUri}")
        if (!isOnline) {
            showMessage("本期 AI 翻译仅支持在线音频，本地音频翻译开发中")
            return
        }
        Log.d(
            "Translate",
            "asr=${translateConfig.selectedAsr.id} keyLen=${translateConfig.currentAsrKey.length}, " +
                "translator=${translateConfig.selectedTranslator.id} keyLen=${translateConfig.currentTranslatorKey.length}, " +
                "isConfigured=${translateConfig.isConfigured}",
        )
        if (!translateConfig.isConfigured) {
            _state.update { it.copy(showTranslateConfig = true) }
            return
        }
        translatedSrtText = null
        viewModelScope.launch {
            _state.update { it.copy(translationState = TranslationUiState.AsrRunning(0)) }
            // 在线音频 URL 可能有时效，翻译前重新拉取作品音轨获取新鲜 URL
            val audioUrl = refreshOnlineAudioUrl(track.displayName) ?: track.audioUri.toString()
            withContext(Dispatchers.IO) {
                createTranslatePipeline().translate(
                    audioUrl = audioUrl,
                    sourceLang = "ja",
                    targetLang = "zh",
                ) { progress ->
                    when (progress) {
                        is TranslationPipeline.Progress.AsrRunning ->
                            _state.update { it.copy(translationState = TranslationUiState.AsrRunning(progress.elapsedSeconds)) }
                        is TranslationPipeline.Progress.Translating -> {
                            // 仅 Groq 提供按档位的耗时预估；其它服务商不显示具体秒数
                            val est = if (translateConfig.selectedTranslator == TranslatorProviderType.GROQ)
                                translateConfig.groqContextLevel.estimateSeconds(progress.sentenceCount)
                            else null
                            _state.update {
                                it.copy(translationState = TranslationUiState.Translating(est))
                            }
                        }
                        is TranslationPipeline.Progress.Done -> {
                            val srt = progress.result.srtText
                            translatedSrtText = srt
                            viewModelScope.launch(Dispatchers.Main) {
                                applyTranslatedSrt(srt)
                            }
                            _state.update { it.copy(translationState = TranslationUiState.Done) }
                        }
                        is TranslationPipeline.Progress.Failed ->
                            _state.update { it.copy(translationState = TranslationUiState.Error(progress.reason)) }
                    }
                }
            }
        }
    }

    /** 在线音轨 URL 可能有时效，重新拉取作品详情获取新鲜 URL。 */
    private suspend fun refreshOnlineAudioUrl(trackName: String): String? = withContext(Dispatchers.IO) {
        val work = _state.value.selectedWork ?: return@withContext null
        val tracks = runCatching { AsmrOne.tracks(work.id, sourceBaseUrl) }.getOrDefault(emptyList())
        tracks.firstOrNull { it.title == trackName }?.streamUrl
    }

    /** 把 AI 生成的 SRT 文本应用到当前曲目：更新播放列表项、解析 cues、更新状态。 */
    private fun applyTranslatedSrt(srtText: String) {
        val current = currentTrack ?: return
        val bytes = srtText.toByteArray(Charsets.UTF_8)
        subtitleBytes = bytes
        val cues = SubtitleParsers.parse(SubtitleFormat.SRT, srtText)
        val position = engine.currentPositionMs
        val index = cues.activeIndexAt(position)
        val updated = current.copy(
            subtitleFormat = SubtitleFormat.SRT,
            subtitleUri = null,
            subtitleTextUrl = null,
        )
        playlist.updateCurrent(updated)
        _state.update {
            it.copy(
                cues = cues,
                activeCueIndex = index,
                floatingText = cues.displayedText(index, null),
                contentLabel = "AI 翻译字幕",
                hasSubtitle = true,
                encoding = SubtitleEncoding.AUTO,
            )
        }
    }

    /** 保存 AI 翻译字幕到用户选择的文档 Uri（SAF CreateDocument 返回）。 */
    fun onSaveSubtitleDocument(uri: Uri) {
        val srt = translatedSrtText
        if (srt.isNullOrBlank()) {
            showMessage("没有可保存的字幕")
            return
        }
        try {
            getApplication<Application>().contentResolver.openOutputStream(uri)?.use { os ->
                os.write(srt.toByteArray(Charsets.UTF_8))
            }
            showMessage("字幕已保存")
            onDismissTranslation()
        } catch (e: Exception) {
            showMessage("保存失败：${e.message}")
        }
    }

    /** 关闭翻译进度/错误状态，回到 Idle（字幕仍保留显示）。 */
    fun onDismissTranslation() {
        _state.update { it.copy(translationState = TranslationUiState.Idle) }
    }

    // ===== AI 翻译 API Key 配置 =====
    fun onShowTranslateConfig() {
        _state.update { it.copy(showTranslateConfig = true) }
    }

    fun onDismissTranslateConfig() {
        _state.update { it.copy(showTranslateConfig = false) }
    }

    fun onSaveTranslateConfig(data: TranslateConfigData) {
        translateConfig.saveAll(
            selectedAsrType = data.selectedAsr,
            asrKeyMap = data.asrKeys,
            selectedTranslatorType = data.selectedTranslator,
            translatorKeyMap = data.translatorKeys,
            translatorKey2Map = data.translatorKeys2,
            groqContextLevel = data.groqContextLevel,
        )
        _state.update { it.copy(showTranslateConfig = false) }
        showMessage("API Key 已保存")
    }

    /** 当前完整配置（供配置对话框回显：服务商列表、选中项、各服务商 Key）。 */
    val translateConfigData: TranslateConfigData
        get() = TranslateConfigData(
            asrTypes = AsrProviderType.entries,
            selectedAsr = translateConfig.selectedAsr,
            asrKeys = AsrProviderType.entries.associateWith { translateConfig.getAsrKey(it) },
            translatorTypes = TranslatorProviderType.entries,
            selectedTranslator = translateConfig.selectedTranslator,
            translatorKeys = TranslatorProviderType.entries.associateWith { translateConfig.getTranslatorKey(it) },
            translatorKeys2 = TranslatorProviderType.entries.associateWith { translateConfig.getTranslatorKey2(it) },
            groqContextLevel = translateConfig.groqContextLevel,
        )

    // ===== 字幕 / 歌词编码切换（本地与在线通用）=====
    fun onEncodingChange(encoding: SubtitleEncoding) {
        val track = currentTrack
        val format = track?.subtitleFormat
        val bytes = subtitleBytes
        if (track == null || format == null || bytes == null) {
            showMessage("请先导入带字幕 / 歌词的音频")
            return
        }
        val cues = SubtitleParsers.parse(format, SubtitleDecoder.decode(bytes, encoding))
        val position = engine.currentPositionMs
        // 取「最近一条已经开始的句子」，使切换编码后立即显示当前句，而非等到下一句
        val displayIndex = cues.indexOfLast { it.startMs <= position }
        val text = if (displayIndex >= 0) cues[displayIndex].text else _state.value.floatingText
        val index = cues.activeIndexAt(position)
        _state.update {
            it.copy(
                encoding = encoding,
                cues = cues,
                activeCueIndex = index,
                floatingText = text,
                contentLabel = labelFor(format, cues),
            )
        }
    }

    // ===== 在线搜索：用户自备接口（销售数量降序，每页 20 条，可翻页）=====
    fun onAsmrSearch(keyword: String) {
        if (sourceBaseUrl.isBlank()) {
            _state.update { it.copy(showSourceConfig = true) }
            return
        }
        if (keyword.isBlank()) {
            showMessage("请输入搜索关键词")
            return
        }
        searchPage(keyword.trim(), page = 1)
    }

    // ===== 在线音源接口地址配置 =====
    fun onShowSourceConfig() {
        _state.update { it.copy(showSourceConfig = true) }
    }

    fun onDismissSourceConfig() {
        _state.update { it.copy(showSourceConfig = false) }
    }

    fun onSaveSourceConfig(baseUrl: String) {
        sourceStore.baseUrl = baseUrl
        _state.update { it.copy(showSourceConfig = false) }
        showMessage(
            if (sourceStore.isConfigured) "音源接口地址已保存" else "已清空音源接口地址",
        )
    }

    /** 当前音源接口地址（供配置对话框回显）。 */
    val sourceConfigUrl: String get() = sourceStore.baseUrl

    /** 翻到上一页 / 下一页（复用最近一次搜索关键词）。 */
    fun onAsmrPageChange(page: Int) {
        val keyword = _state.value.asmrKeyword ?: return
        val target = page.coerceAtLeast(1)
        if (target == _state.value.asmrCurrentPage) return
        searchPage(keyword, target)
    }

    /**
     * 拉取某关键词的某一页：先进入加载态并收起已展开的作品 / 音轨，
     * 完成后写入结果与分页信息。
     */
    private fun searchPage(keyword: String, page: Int) {
        _state.update {
            it.copy(
                isSearchingAsmr = true,
                asmrResults = emptyList(),
                selectedWork = null,
                workTracks = emptyList(),
                asmrKeyword = keyword,
                asmrCurrentPage = page,
            )
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { AsmrOne.search(keyword, sourceBaseUrl, page = page) }
                    .getOrNull()
            }
            _state.update {
                if (result == null) {
                    it.copy(
                        isSearchingAsmr = false,
                        asmrResults = emptyList(),
                        asmrTotalPages = 1,
                        asmrTotalCount = 0,
                        message = "网络不可达（该站点可能需要代理）",
                    )
                } else {
                    it.copy(
                        isSearchingAsmr = false,
                        asmrResults = result.works,
                        asmrCurrentPage = result.currentPage,
                        asmrTotalPages = result.totalPages,
                        asmrTotalCount = result.totalCount,
                        message = if (result.works.isEmpty()) "未找到结果" else null,
                    )
                }
            }
        }
    }

    /** 点击某条搜索结果：拉取该作品全部音轨并展开列表（不立即播放）。 */
    fun onAsmrWorkClick(work: AsmrWork) {
        if (_state.value.isLoadingTracks) return
        _state.update { it.copy(isLoadingTracks = true) }
        viewModelScope.launch {
            val tracks = withContext(Dispatchers.IO) {
                runCatching { AsmrOne.tracks(work.id, sourceBaseUrl) }.getOrDefault(emptyList())
            }
            if (tracks.isEmpty()) {
                _state.update {
                    it.copy(
                        isLoadingTracks = false,
                        message = "无法获取该作品音轨（网络不可达或该站点需要代理）",
                    )
                }
                return@launch
            }
            _state.update {
                it.copy(
                    isLoadingTracks = false,
                    selectedWork = work,
                    workTracks = tracks,
                )
            }
        }
    }

    /** 从展开的音轨列表里点击某条：把该作品全部音轨作为在线播放列表并播放所选。 */
    fun onAsmrTrackClick(index: Int) {
        val work = _state.value.selectedWork ?: return
        val tracks = _state.value.workTracks
        if (index !in tracks.indices) return
        val mediaTracks = tracks.toMediaTracks(work)
        playlist.set(mediaTracks, index = index)
        persistentCover = null
        localRootTreeUri = null
        applyTrack(autoPlay = true)
    }

    /** 从音轨列表返回搜索结果。 */
    fun onBackToResults() {
        _state.update { it.copy(selectedWork = null, workTracks = emptyList()) }
    }

    private fun List<AsmrTrack>.toMediaTracks(work: AsmrWork): List<MediaTrack> =
        map { t ->
            MediaTrack(
                audioUri = Uri.parse(t.streamUrl),
                displayName = t.title,
                coverUri = work.coverUrl?.let { Uri.parse(it) },
                subtitleTextUrl = t.subtitleUrl,
                subtitleFormat = t.subtitleFormat,
            )
        }

    // ===== 播放控制 =====
    fun onPlayPause() {        if (currentTrack == null) {
            showMessage("请先导入音频")
            return
        }
        if (engine.isPlaying) {
            engine.pause()
        } else {
            // 已播放到结尾时，再次播放从头开始
            val duration = engine.durationMs
            if (duration > 0 && engine.currentPositionMs >= duration - 200) {
                engine.seekTo(0L)
            }
            engine.play()
        }
        _state.update {
            it.copy(isPlaying = engine.isPlaying, positionMs = engine.currentPositionMs)
        }
    }

    fun onSeek(fraction: Float) {
        val duration = engine.durationMs
        if (duration <= 0) return
        val target = (fraction.coerceIn(0f, 1f) * duration).toLong()
        engine.seekTo(target)
        _state.update {
            val index = it.cues.activeIndexAt(target)
            it.copy(
                positionMs = target,
                activeCueIndex = index,
                floatingText = it.cues.displayedText(index, it.floatingText),
            )
        }
    }

    /** 点击时间轴上的某句：跳转到该句起点并开始播放。 */
    fun onCueClick(cue: Cue) {
        if (currentTrack == null) {
            showMessage("请先导入音频")
            return
        }
        engine.seekTo(cue.startMs)
        // 需求为「跳转到对应节点播放」：暂停状态下也自动开始播放
        if (!engine.isPlaying) engine.play()
        val index = _state.value.cues.activeIndexAt(cue.startMs)
        _state.update {
            it.copy(
                isPlaying = engine.isPlaying,
                positionMs = cue.startMs,
                activeCueIndex = index,
                floatingText = it.cues.displayedText(index, it.floatingText),
            )
        }
    }

    /** 倍速滑杆：拖动时实时设置（限速由 coerceSpeed 兜底）。 */
    fun onSpeedChange(value: Float) {
        val speed = coerceSpeed(value)
        engine.setSpeed(speed)
        _state.update { it.copy(speed = speed) }
    }

    fun onToggleCard() {
        if (currentTrack == null) {
            showMessage("请先导入音频")
            return
        }
        _state.update {
            it.copy(
                cardMode = if (it.cardMode == CardMode.TIMELINE) CardMode.COVER else CardMode.TIMELINE,
            )
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    override fun onCleared() {
        engine.release()
    }

    // ===== 内部实现 =====

    /**
     * 载入播放列表当前曲目：重置进度 / 字幕 / 悬浮框 / 封面，并刷新上一首 / 下一首可用性。
     * @param autoPlay 上一首 / 下一首切换时通常直接开始播放。
     */
    private fun applyTrack(autoPlay: Boolean) {
        val track = playlist.current ?: return

        engine.setMedia(track.audioUri)
        engine.setSpeed(_state.value.speed)
        if (autoPlay) engine.play()

        _state.update {
            PlayerUiState(
                trackName = track.displayName,
                isPlaying = autoPlay,
                positionMs = 0L,
                durationMs = 0L,
                speed = it.speed,
                canPrevious = playlist.canPrevious,
                canNext = playlist.canNext,
                trackCount = playlist.size,
                currentTrackIndex = playlist.currentIndex,
                cues = emptyList(),
                activeCueIndex = -1,
                floatingText = null,
                cardMode = CardMode.COVER,
                coverUri = persistentCover ?: track.coverUri,
                contentLabel = labelFor(track.subtitleFormat, emptyList()),
                hasSubtitle = track.subtitleFormat != null,
                encoding = SubtitleEncoding.AUTO,
                // 保留在线搜索结果与展开的音轨列表，使在线播放时仍可点选 / 高亮
                asmrResults = it.asmrResults,
                selectedWork = it.selectedWork,
                workTracks = it.workTracks,
            )
        }

        // 异步加载字幕 / 歌词原始字节（本地读 content，在线走 HTTP），再自动识别编码解码；
        // 原始字节缓存在 subtitleBytes，供用户手动切换编码时重新解码
        subtitleBytes = null
        if (track.subtitleFormat != null) {
            val format = track.subtitleFormat
            viewModelScope.launch {
                val bytes = withContext(Dispatchers.IO) { loadSubtitleBytes(track) }
                subtitleBytes = bytes
                val cues = if (bytes != null) {
                    SubtitleParsers.parse(format, SubtitleDecoder.decode(bytes, SubtitleEncoding.AUTO))
                } else {
                    emptyList()
                }
                _state.update {
                    it.copy(
                        cues = cues,
                        activeCueIndex = -1,
                        floatingText = null,
                        contentLabel = labelFor(format, cues),
                    )
                }
            }
        }
    }

    /**
     * 读取某曲目字幕 / 歌词的原始字节：在线（[MediaTrack.subtitleTextUrl]）走 HTTP，
     * 本地（[MediaTrack.subtitleUri]）走 ContentResolver；失败返回 null。
     */
    private fun loadSubtitleBytes(track: MediaTrack): ByteArray? {
        return when {
            track.subtitleTextUrl != null -> {
                val (code, bytes) = SimpleHttp.getBytes(track.subtitleTextUrl, ASMR_UA)
                if (code !in 200..299) null else bytes
            }
            track.subtitleUri != null ->
                TextFileLoader.readBytes(getApplication(), track.subtitleUri)
            else -> null
        }
    }

    private fun labelFor(format: SubtitleFormat?, cues: List<Cue>): String = when {
        format == null || cues.isEmpty() -> "暂无歌词 · 暂无字幕"
        format.isSubtitle -> "字幕"
        else -> "歌词"
    }

    private fun showMessage(msg: String) {
        _state.update { it.copy(message = msg) }
    }
}
