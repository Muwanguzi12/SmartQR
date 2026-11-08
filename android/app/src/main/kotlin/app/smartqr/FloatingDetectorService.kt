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
import android.provider.ContactsContract
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
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

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

    private data class ContactCard(
        val name: String,
        val phone: String,
        val email: String,
        val company: String,
        val organizationTitle: String,
        val raw: String
    )

    private fun unfoldVCardLines(payload: String): List<String> {
        val physical = payload.replace("\r\n", "\n").replace("\r", "\n").split("\n")
        val logical = mutableListOf<String>()
        for (line in physical) {
            if (logical.isNotEmpty() && (line.startsWith(" ") || line.startsWith("\t"))) {
                logical[logical.lastIndex] += line.drop(1)
            } else if (logical.isNotEmpty() && logical.last().endsWith("=")) {
                logical[logical.lastIndex] = logical.last().dropLast(1) + line
            } else {
                logical += line
            }
        }
        return logical
    }

    private fun splitVCardProperty(line: String): Pair<String, String>? {
        var quoted = false
        var escaped = false
        for (index in line.indices) {
            val char = line[index]
            if (escaped) { escaped = false; continue }
            if (char == '\\') { escaped = true; continue }
            if (char == '"') { quoted = !quoted; continue }
            if (char == ':' && !quoted) return line.substring(0, index) to line.substring(index + 1)
        }
        return null
    }

    private fun unescapeVCard(value: String): String {
        val output = StringBuilder()
        var index = 0
        while (index < value.length) {
            if (value[index] == '\\' && index + 1 < value.length) {
                when (val next = value[index + 1]) {
                    'n', 'N' -> output.append('\n')
                    '\\', ',', ';' -> output.append(next)
                    else -> { output.append('\\'); output.append(next) }
                }
                index += 2
            } else {
                output.append(value[index])
                index++
            }
        }
        return output.toString().trim()
    }

    private fun decodeQuotedPrintable(value: String, charsetName: String): String {
        val bytes = ByteArrayOutputStream()
        var index = 0
        while (index < value.length) {
            if (value[index] == '=' && index + 2 < value.length) {
                val byte = value.substring(index + 1, index + 3).toIntOrNull(16)
                if (byte != null) {
                    bytes.write(byte)
                    index += 3
                    continue
                }
            }
            val charBytes = value[index].toString().toByteArray(Charsets.ISO_8859_1)
            bytes.write(charBytes)
            index++
        }
        val charset = try { Charset.forName(charsetName) } catch (_: Exception) { Charsets.ISO_8859_1 }
        return String(bytes.toByteArray(), charset)
    }

    private fun parseVCard(payload: String): ContactCard? {
        if (!payload.trimStart().startsWith("BEGIN:VCARD", ignoreCase = true)) return null
        val values = mutableMapOf<String, MutableList<String>>()
        for (line in unfoldVCardLines(payload)) {
            val pair = splitVCardProperty(line) ?: continue
            val rawKey = pair.first.substringAfterLast('.')
            val key = rawKey.substringBefore(';').uppercase()
            if (key.isBlank() || key.startsWith("BEGIN") || key.startsWith("END") || key == "VERSION") continue
            val header = pair.first.uppercase()
            val charsetName = Regex("CHARSET=([^;:]+)").find(header)?.groupValues?.get(1)?.trim('"')
                ?: "ISO-8859-1"
            val propertyValue = if (header.contains("ENCODING=QUOTED-PRINTABLE")) {
                decodeQuotedPrintable(pair.second, charsetName)
            } else {
                pair.second
            }
            val decoded = unescapeVCard(propertyValue)
            if (decoded.isNotBlank()) values.getOrPut(key) { mutableListOf() }.add(decoded)
        }
        fun first(vararg keys: String): String = keys.firstNotNullOfOrNull { key ->
            values[key]?.firstOrNull()?.takeIf { it.isNotBlank() }
        } ?: ""
        val structuredName = first("N").split(';')
        val fallbackName = listOf(
            structuredName.getOrNull(1).orEmpty(),
            structuredName.firstOrNull().orEmpty(),
            structuredName.getOrNull(2).orEmpty()
        ).filter { it.isNotBlank() }.joinToString(" ")
        val name = first("FN").ifBlank { fallbackName }
        val phone = first("TEL")
        val email = first("EMAIL")
        val company = first("ORG").replace(';', ' ')
        val title = first("TITLE")
        if (name.isBlank() && phone.isBlank() && email.isBlank() && company.isBlank()) return null
        return ContactCard(name, phone, email, company, title, payload)
    }

    private fun showResult(value: String) {
        val contact = parseVCard(value)
        if (contact != null) {
            showContactResult(contact)
            return
        }
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

    private fun showContactResult(contact: ContactCard) {
        removePanel()
        resultPanel?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 18, 20, 16)
            background = rounded(Color.WHITE, 26f)
            elevation = 22f
        }
        card.addView(TextView(this).apply {
            text = "Contact detected"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(32, 28, 48))
        })
        val details = listOf(
            "Name" to contact.name,
            "Phone" to contact.phone,
            "Email" to contact.email,
            "Company" to contact.company,
            "Title" to contact.organizationTitle
        ).filter { it.second.isNotBlank() }
        details.forEach { (label, value) ->
            card.addView(TextView(this).apply {
                text = "$label: $value"
                textSize = 14f
                setTextColor(Color.DKGRAY)
                setPadding(0, 8, 0, 0)
            })
        }
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(Button(this).apply {
            text = "Copy"
            setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("SmartQR contact", contact.raw))
                Toast.makeText(this@FloatingDetectorService, "Contact data copied", Toast.LENGTH_SHORT).show()
            }
        }, LinearLayout.LayoutParams(0, WindowManager.LayoutParams.WRAP_CONTENT, 1f))
        buttons.addView(Button(this).apply {
            text = "Save contact"
            setOnClickListener {
                try {
                    val insert = Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI).apply {
                        putExtra(ContactsContract.Intents.Insert.NAME, contact.name)
                        putExtra(ContactsContract.Intents.Insert.PHONE, contact.phone)
                        putExtra(ContactsContract.Intents.Insert.EMAIL, contact.email)
                        putExtra(ContactsContract.Intents.Insert.COMPANY, contact.company)
                        putExtra(ContactsContract.Intents.Insert.JOB_TITLE, contact.organizationTitle)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(insert)
                } catch (_: Exception) {
                    showMessage("No contacts app is available to save this contact.")
                }
            }
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
        val contact = parseVCard(value)
        if (contact != null) {
            showContactResult(contact)
            return
        }
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