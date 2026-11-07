package com.sandianshui.player.core.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.flow.StateFlow

/**
 * 系统级悬浮歌词前台服务：
 * - 用 [WindowManager] + TYPE_APPLICATION_OVERLAY 添加一个可拖动的悬浮框，
 *   切到别的 App / 桌面后仍显示；
 * - 以前台服务保活，保证播放器界面在后台时进程不被回收、歌词仍实时更新；
 * - 文本来自 [OverlayLyricBus]（由 ViewModel 持续写入）。
 *
 * 前提：用户已授予「显示在其他应用上层」（SYSTEM_ALERT_WINDOW）。
 */
class OverlayLyricService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private lateinit var windowManager: WindowManager
    private var overlayView: ComposeView? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startAsForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        if (overlayView == null) addOverlay()
        return START_STICKY
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "桌面歌词", NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(channel)
        }
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("三点水播放器")
            .setContentText("桌面悬浮歌词运行中")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun addOverlay() {
        val density = resources.displayMetrics.density
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val maxWidthPx = screenWidth - (16 * density * 2).toInt()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // 不抢输入焦点；窗口外的触摸直接传给下层 App
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (16 * density).toInt()
            y = (200 * density).toInt()
        }

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayLyricService)
            setViewTreeSavedStateRegistryOwner(this@OverlayLyricService)
            setContent {
                MaterialTheme {
                    OverlayPill(
                        contentFlow = OverlayLyricBus.content,
                        maxWidthPx = maxWidthPx,
                        onDrag = { dx, dy -> moveOverlay(dx, dy, screenWidth, screenHeight) },
                        onClose = { stopSelf() },
                    )
                }
            }
        }

        windowManager.addView(view, params)
        overlayView = view
        layoutParams = params
    }

    private fun moveOverlay(dxPx: Float, dyPx: Float, screenWidth: Int, screenHeight: Int) {
        val params = layoutParams ?: return
        val view = overlayView ?: return
        val maxX = (screenWidth - view.width).coerceAtLeast(0)
        val maxY = (screenHeight - view.height).coerceAtLeast(0)
        params.x = (params.x + dxPx).toLong().coerceIn(0L, maxX.toLong()).toInt()
        params.y = (params.y + dyPx).toLong().coerceIn(0L, maxY.toLong()).toInt()
        windowManager.updateViewLayout(view, params)
    }

    override fun onDestroy() {
        overlayView?.let { runCatching { windowManager.removeView(it) } }
        overlayView = null
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "overlay_lyric"
        private const val NOTIF_ID = 2001

        fun start(context: Context) {
            context.startForegroundService(Intent(context, OverlayLyricService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayLyricService::class.java))
        }
    }
}

/**
 * 悬浮框本体：半透明圆角胶囊，顶部握把 + 关闭按钮，可整体拖动；
 * 文本不限行数、长句自动换行完整显示。
 */
@Composable
private fun OverlayPill(
    contentFlow: StateFlow<com.sandianshui.player.core.overlay.OverlayLyricContent>,
    maxWidthPx: Int,
    onDrag: (Float, Float) -> Unit,
    onClose: () -> Unit,
) {
    val content by contentFlow.collectAsState()
    val maxWidthDp = with(LocalDensity.current) { maxWidthPx.toDp() }

    Surface(
        color = Color(0xCC141414),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .widthIn(max = maxWidthDp)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            },
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 14.dp)
                .padding(top = 6.dp, bottom = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0x66FFFFFF)),
                    )
                }
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "关闭桌面歌词",
                    tint = Color(0x99FFFFFF),
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { onClose() },
                )
            }

            Spacer(Modifier.height(6.dp))

            Text(
                text = content.text ?: "—",
                color = Color.White,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                maxLines = Int.MAX_VALUE,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
