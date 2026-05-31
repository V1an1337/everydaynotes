package xyz.v1an.everydaynotes

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CaptureOverlayService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var overlayView: LinearLayout? = null
    private var windowManager: WindowManager? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            stopSelf()
            return START_NOT_STICKY
        }
        if (overlayView != null) {
            Toast.makeText(this, "EverydayNotes 还有一个待处理的保存弹窗", Toast.LENGTH_SHORT).show()
            return START_NOT_STICKY
        }
        show(intent)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        removeOverlay()
        scope.cancel()
        super.onDestroy()
    }

    private fun show(intent: Intent?) {
        val shareText = intent?.getStringExtra(EXTRA_SHARE_TEXT)
        val douyinUrl = intent?.getStringExtra(EXTRA_DOUYIN_URL) ?: shareText?.let(DouyinUrlExtractor::extract)
        val douyinCopiedTimeMillis = intent?.takeIf { it.hasExtra(EXTRA_DOUYIN_TIME_MILLIS) }
            ?.getLongExtra(EXTRA_DOUYIN_TIME_MILLIS, 0L)
            ?.takeIf { it > 0L }
        val screenshotUri = intent?.getStringExtra(EXTRA_SCREENSHOT_URI)?.let(Uri::parse)
        val screenshotName = intent?.getStringExtra(EXTRA_SCREENSHOT_NAME)
        val screenshotTimeMillis = intent?.takeIf { it.hasExtra(EXTRA_SCREENSHOT_TIME_MILLIS) }
            ?.getLongExtra(EXTRA_SCREENSHOT_TIME_MILLIS, 0L)
            ?.takeIf { it > 0L }
        val alwaysChooser = intent?.getBooleanExtra(EXTRA_ALWAYS_CHOOSER, false) == true
        val hasShare = shareText != null
        val hasScreenshot = screenshotUri != null
        val store = SettingsStore(this)
        if (!hasShare && !hasScreenshot && !alwaysChooser) {
            Toast.makeText(this, "没有发现可保存的内容", Toast.LENGTH_SHORT).show()
            stopSelf()
            return
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 24, 28, 24)
            setBackgroundColor(0xFFFFFDF9.toInt())
        }
        val heading = TextView(this).apply {
            text = if (alwaysChooser) {
                "选择要保存的内容"
            } else when {
                hasShare && hasScreenshot -> "选择要保存的内容"
                hasShare -> "保存抖音链接"
                else -> "保存截图"
            }
            textSize = 18f
            setTextColor(0xFF202124.toInt())
        }
        val subtitle = TextView(this).apply {
            text = if (alwaysChooser) {
                "点 EN 时会同时检查剪贴板和近期截图。"
            } else when {
                hasShare && hasScreenshot -> "检测到剪贴板里的抖音链接，也发现了近期截图。"
                hasShare -> "剪贴板里有一个抖音分享链接。"
                else -> "发现了一张近期截图。"
            }
            setTextColor(0xFF6B625B.toInt())
        }
        fun infoText(label: String, value: String): TextView {
            return TextView(this).apply {
                text = "$label\n$value"
                textSize = 14f
                setTextColor(0xFF4F4741.toInt())
                setPadding(0, 14, 0, 0)
            }
        }
        val remark = EditText(this).apply {
            hint = "备注"
            maxLines = 2
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }

        fun addButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
            row.addView(
                Button(this).apply {
                    this.text = text
                    isEnabled = enabled
                    setOnClickListener { onClick() }
                }
            )
        }

        addButton("丢弃") {
            removeOverlay()
            stopSelf()
        }
        if (alwaysChooser || (hasShare && hasScreenshot)) {
            addButton("保存链接", enabled = hasShare) {
                saveCapture(store, shareText, null, remark.text.toString())
            }
            addButton("保存截图", enabled = hasScreenshot) {
                saveCapture(store, null, screenshotUri, remark.text.toString())
            }
        } else {
            addButton("保存") {
                saveCapture(store, shareText, screenshotUri, remark.text.toString())
            }
        }

        container.addView(heading)
        container.addView(subtitle)
        if (alwaysChooser) {
            container.addView(
                infoText(
                    "当前链接",
                    formatDouyinInfo(douyinUrl ?: shareText?.take(160), douyinCopiedTimeMillis)
                )
            )
            container.addView(
                infoText(
                    "当前截图",
                    if (hasScreenshot) {
                        formatScreenshotInfo(screenshotName, screenshotTimeMillis)
                    } else {
                        "未检测到近期截图"
                    }
                )
            )
        }
        container.addView(remark)
        container.addView(row)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 80
        }
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager?.addView(container, params)
        overlayView = container
    }

    private fun saveCapture(store: SettingsStore, shareText: String?, screenshotUri: Uri?, remark: String) {
        scope.launch {
            runCatching {
                val token = store.token ?: error("请先登录 EverydayNotes")
                val api = EverydayNotesApi(this@CaptureOverlayService, store.apiBase, token)
                if (shareText != null) {
                    api.captureDouyin(shareText, remark)
                } else if (screenshotUri != null) {
                    api.uploadScreenshot(screenshotUri, remark)
                } else {
                    error("没有可保存的内容")
                }
            }.onSuccess {
                Toast.makeText(this@CaptureOverlayService, "已保存到 EverydayNotes", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(this@CaptureOverlayService, it.message ?: "保存失败", Toast.LENGTH_LONG).show()
            }
            removeOverlay()
            stopSelf()
        }
    }

    private fun removeOverlay() {
        overlayView?.let { view ->
            runCatching { windowManager?.removeView(view) }
        }
        overlayView = null
    }

    private fun formatTime(timeMillis: Long): String {
        return SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timeMillis))
    }

    private fun formatDouyinInfo(url: String?, copiedTimeMillis: Long?): String {
        if (url == null) return "未检测到抖音链接"
        val copiedText = copiedTimeMillis?.let {
            "${formatTime(it)} · ${formatAge(it, "复制", "可能是旧链接")}"
        }
        return listOfNotNull(url, copiedText).joinToString("\n")
    }

    private fun formatScreenshotInfo(name: String?, timeMillis: Long?): String {
        val timeText = timeMillis?.let {
            "${formatTime(it)} · ${formatAge(it, "截图", "可能是旧截图")}"
        }
        return listOfNotNull(name, timeText).joinToString("\n").ifBlank { "已检测到近期截图" }
    }

    private fun formatAge(timeMillis: Long, action: String, staleText: String): String {
        val ageSeconds = ((System.currentTimeMillis() - timeMillis) / 1000).coerceAtLeast(0)
        return when {
            ageSeconds < 90 -> "刚刚$action"
            ageSeconds < 3600 -> "${ageSeconds / 60} 分钟前$action"
            else -> staleText
        }
    }

    companion object {
        private const val EXTRA_SHARE_TEXT = "share_text"
        private const val EXTRA_DOUYIN_URL = "douyin_url"
        private const val EXTRA_DOUYIN_TIME_MILLIS = "douyin_time_millis"
        private const val EXTRA_SCREENSHOT_URI = "screenshot_uri"
        private const val EXTRA_SCREENSHOT_NAME = "screenshot_name"
        private const val EXTRA_SCREENSHOT_TIME_MILLIS = "screenshot_time_millis"
        private const val EXTRA_ALWAYS_CHOOSER = "always_chooser"

        fun showCandidates(context: Context, shareText: String?, screenshotUri: Uri?) {
            showCandidates(
                context,
                CaptureCandidates(
                    douyinText = shareText,
                    douyinUrl = shareText?.let(DouyinUrlExtractor::extract),
                    douyinCopiedTimeMillis = null,
                    screenshotUri = screenshotUri
                )
            )
        }

        fun showCandidates(context: Context, candidates: CaptureCandidates) {
            val intent = Intent(context, CaptureOverlayService::class.java)
                .putExtra(EXTRA_ALWAYS_CHOOSER, true)
            candidates.douyinText?.let { intent.putExtra(EXTRA_SHARE_TEXT, it) }
            candidates.douyinUrl?.let { intent.putExtra(EXTRA_DOUYIN_URL, it) }
            candidates.douyinCopiedTimeMillis?.let { intent.putExtra(EXTRA_DOUYIN_TIME_MILLIS, it) }
            candidates.screenshotUri?.let { intent.putExtra(EXTRA_SCREENSHOT_URI, it.toString()) }
            candidates.screenshotName?.let { intent.putExtra(EXTRA_SCREENSHOT_NAME, it) }
            candidates.screenshotTimeMillis?.let { intent.putExtra(EXTRA_SCREENSHOT_TIME_MILLIS, it) }
            context.startService(intent)
        }

        fun showDouyin(context: Context, shareText: String) {
            val intent = Intent(context, CaptureOverlayService::class.java)
            intent.putExtra(EXTRA_SHARE_TEXT, shareText)
            shareText.let(DouyinUrlExtractor::extract)?.let { intent.putExtra(EXTRA_DOUYIN_URL, it) }
            context.startService(intent)
        }

        fun showScreenshot(context: Context, uri: Uri) {
            val intent = Intent(context, CaptureOverlayService::class.java)
            intent.putExtra(EXTRA_SCREENSHOT_URI, uri.toString())
            context.startService(intent)
        }
    }
}
