package app.smartqr

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class FloatingDetectorService : Service() {
    companion object {
        const val EXTRA_SCAN_SCREEN = "smartqr_scan_screen"
    }

    private lateinit var windowManager: WindowManager
    private var bubble: TextView? = null
    private var panel: LinearLayout? = null
    private var expanded = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showBubble()
    }

    private fun params(width: Int, height: Int): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        return WindowManager.LayoutParams(
            width, height, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.END; x = 18; y = 220 }
    }

    private fun rounded(color: Int, radius: Float): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = radius }

    private fun showBubble() {
        if (bubble != null) return
        bubble = TextView(this).apply {
            text = "QR"
            textSize = 15f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = rounded(Color.rgb(101, 85, 232), 100f)
            elevation = 12f
            setOnClickListener { togglePanel() }
        }
        windowManager.addView(bubble, params(58, 58))
    }

    private fun togglePanel() {
        if (expanded) {
            panel?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
            panel = null
            expanded = false
            return
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 18, 20, 16)
            background = rounded(Color.WHITE, 28f)
            elevation = 18f
        }
        container.addView(TextView(this).apply {
            text = "SmartQR detector"
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(32, 28, 48))
        })
        container.addView(TextView(this).apply {
            text = "Scan a QR code on the app currently visible."
            textSize = 13f
            setTextColor(Color.DKGRAY)
            setPadding(0, 8, 0, 14)
        })
        container.addView(Button(this).apply {
            text = "Scan this screen"
            setOnClickListener {
                val launch = Intent(this@FloatingDetectorService, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(EXTRA_SCAN_SCREEN, true)
                startActivity(launch)
                togglePanel()
            }
        })
        container.addView(Button(this).apply {
            text = "Close panel"
            setOnClickListener { togglePanel() }
        })
        panel = container
        windowManager.addView(container, params(280, WindowManager.LayoutParams.WRAP_CONTENT))
        expanded = true
    }

    override fun onDestroy() {
        bubble?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        panel?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        bubble = null
        panel = null
        super.onDestroy()
    }
}
