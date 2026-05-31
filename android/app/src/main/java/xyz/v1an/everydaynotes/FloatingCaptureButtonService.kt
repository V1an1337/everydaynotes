package xyz.v1an.everydaynotes

import android.app.Service
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlin.math.abs

class FloatingCaptureButtonService : Service() {
    private var windowManager: WindowManager? = null
    private var buttonView: TextView? = null
    private var params: WindowManager.LayoutParams? = null
    private var downRawX = 0f
    private var downRawY = 0f
    private var downX = 0
    private var downY = 0
    private var didDrag = false

    override fun onCreate() {
        super.onCreate()
        startForeground(
            NOTIFICATION_ID,
            NotificationCompat.Builder(this, EverydayNotesApp.CHANNEL_CAPTURE)
                .setSmallIcon(android.R.drawable.ic_menu_upload)
                .setContentTitle("EverydayNotes")
                .setContentText("悬浮识别按钮运行中")
                .setOngoing(true)
                .build()
        )
        showButton()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (buttonView == null) showButton()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        buttonView?.let { view ->
            runCatching { windowManager?.removeView(view) }
        }
        buttonView = null
        super.onDestroy()
    }

    private fun showButton() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先允许 EverydayNotes 显示悬浮窗", Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }
        if (buttonView != null) return

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val size = dp(56)
        params = WindowManager.LayoutParams(
            size,
            size,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = resources.displayMetrics.widthPixels - size - dp(8)
            y = resources.displayMetrics.heightPixels / 2
        }

        buttonView = TextView(this).apply {
            text = "EN"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xEE365F53.toInt())
                setStroke(dp(2), 0xFFFFFFFF.toInt())
            }
            elevation = dp(8).toFloat()
            setOnClickListener { triggerCapture() }
            setOnTouchListener { _, event ->
                handleTouch(event)
            }
        }
        windowManager?.addView(buttonView, params)
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        val layoutParams = params ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                downX = layoutParams.x
                downY = layoutParams.y
                didDrag = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - downRawX).toInt()
                val dy = (event.rawY - downRawY).toInt()
                if (abs(dx) > DRAG_SLOP || abs(dy) > DRAG_SLOP) {
                    didDrag = true
                    layoutParams.x = downX + dx
                    layoutParams.y = downY + dy
                    windowManager?.updateViewLayout(buttonView, layoutParams)
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!didDrag) buttonView?.performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> return true
        }
        return false
    }

    private fun triggerCapture() {
        startActivity(
            Intent(this, CaptureTriggerActivity::class.java)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
        )
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()

    companion object {
        private const val NOTIFICATION_ID = 1002
        private const val DRAG_SLOP = 8
    }
}
