package app.smartqr

import android.app.Service
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.io.File

class FloatingDetectorService : Service() {
    private lateinit var windowManager: WindowManager
    private var bubble: TextView? = null
    private var panel: LinearLayout? = null
    private var resultPanel: LinearLayout? = null
    private var expanded = false
    private var receiverRegistered = false

    private val captureReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val path = intent?.getStringExtra(ScreenCaptureForegroundService.EXTRA_PATH)
            if (path.isNullOrBlank()) {
                showMessage("Could not capture this screen. Try again.")
            } else {
                decodeCapturedFrame(path)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showBubble()
        val filter = IntentFilter(ScreenCaptureForegroundService.ACTION_CAPTURE_COMPLETE)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(captureReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(captureReceiver, filter)
        }
        receiverRegistered = true
    }

    private fun params(width: Int, height: Int): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE
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
            var downX = 0f
            var downY = 0f
            var startX = 0
            var startY = 0
            var moved = false
            setOnTouchListener { view, event ->
                val layout = view.layoutParams as WindowManager.LayoutParams
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX
                        downY = event.rawY
                        startX = layout.x
                        startY = layout.y
                        moved = false
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - downX).toInt()
                        val dy = (event.rawY - downY).toInt()
                        if (kotlin.math.abs(dx) > 5 || kotlin.math.abs(dy) > 5) moved = true
                        if (moved) {
                            layout.x = (startX - dx).coerceAtLeast(0)
                            layout.y = (startY + dy).coerceAtLeast(0)
                            windowManager.updateViewLayout(view, layout)
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) togglePanel()
                        true
                    }
                    else -> true
                }
            }
        }
        windowManager.addView(bubble, params(58, 58))
    }

    private fun togglePanel() {
        removePanel()
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
                val capture = Intent(this@FloatingDetectorService, ScreenCaptureConsentActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                startActivity(capture)
                removePanel()
            }
        })
        container.addView(Button(this).apply {
            text = "Close panel"
            setOnClickListener { removePanel() }
        })
        panel = container
        windowManager.addView(container, params(280, WindowManager.LayoutParams.WRAP_CONTENT))
        expanded = true
    }

    private fun removePanel() {
        panel?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        panel = null
        expanded = false
    }

    private fun decodeCapturedFrame(path: String) {
        val file = File(path)
        if (!file.exists()) {
            showMessage("The screen image is no longer available.")
            return
        }
        try {
            val image = InputImage.fromFilePath(this, Uri.fromFile(file))
            BarcodeScanning.getClient().process(image)
                .addOnSuccessListener { barcodes ->
                    val value = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                    if (value.isNullOrBlank()) showMessage("No QR code or barcode was found on this screen.")
                    else showResult(value)
                }
                .addOnFailureListener {
                    showMessage("SmartQR could not read this screen. Please try again.")
                }
                .addOnCompleteListener {
                    try { file.delete() } catch (_: Exception) {}
                }
        } catch (_: Exception) {
            try { file.delete() } catch (_: Exception) {}
            showMessage("SmartQR could not process this screen image.")
        }
    }

    private fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showResult(value: String) {
        removePanel()
        resultPanel?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 18, 20, 16)
            background = rounded(Color.WHITE, 26f)
            elevation = 22f
        }
        card.addView(TextView(this).apply {
            text = "QR code detected"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(32, 28, 48))
        })
        card.addView(TextView(this).apply {
            text = value
            textSize = 13f
            setTextColor(Color.DKGRAY)
            maxLines = 5
            setPadding(0, 10, 0, 14)
        })
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(Button(this).apply {
            text = "Copy"
            setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("SmartQR result", value))
                Toast.makeText(this@FloatingDetectorService, "Copied", Toast.LENGTH_SHORT).show()
            }
        }, LinearLayout.LayoutParams(0, WindowManager.LayoutParams.WRAP_CONTENT, 1f))
        buttons.addView(Button(this).apply {
            text = "Continue"
            setOnClickListener { continueWith(value) }
        }, LinearLayout.LayoutParams(0, WindowManager.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(buttons)
        card.addView(Button(this).apply {
            text = "Dismiss"
            setOnClickListener {
                resultPanel?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
                resultPanel = null
            }
        })
        resultPanel = card
        windowManager.addView(card, params(310, WindowManager.LayoutParams.WRAP_CONTENT))
    }

    private fun continueWith(value: String) {
        val trimmed = value.trim()
        val intent = when {
            trimmed.startsWith("https://", true) || trimmed.startsWith("http://", true) ->
                Intent(Intent.ACTION_VIEW, Uri.parse(trimmed))
            trimmed.startsWith("mailto:", true) ->
                Intent(Intent.ACTION_SENDTO, Uri.parse(trimmed))
            trimmed.startsWith("tel:", true) ->
                Intent(Intent.ACTION_DIAL, Uri.parse(trimmed))
            trimmed.matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) ->
                Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$trimmed"))
            else -> null
        }
        if (intent == null) {
            showMessage("This QR content is ready to copy. More actions will be supported in SmartQR.")
            return
        }
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (_: Exception) {
            showMessage("No app is available to open this content.")
        }
    }

    override fun onDestroy() {
        if (receiverRegistered) {
            try { unregisterReceiver(captureReceiver) } catch (_: Exception) {}
            receiverRegistered = false
        }
        bubble?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        removePanel()
        resultPanel?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        bubble = null
        resultPanel = null
        super.onDestroy()
    }
}