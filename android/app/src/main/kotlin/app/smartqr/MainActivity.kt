package app.smartqr

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.ContactsContract
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    companion object {
        const val EXTRA_CAPTURED_SCREEN_PATH = "captured_screen_path"
    }
    private val captureChannelName = "app.smartqr/screen_capture"
    private val overlayChannelName = "app.smartqr/overlay"
    private val actionsChannelName = "app.smartqr/actions"
    private val requestCode = 7412
    private var pendingResult: MethodChannel.Result? = null
    private var captureChannel: MethodChannel? = null
    private var overlayChannel: MethodChannel? = null
    private var pendingCapturedScreenPath: String? = null

    private val captureReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val path = intent?.getStringExtra(ScreenCaptureForegroundService.EXTRA_PATH)
            if (!path.isNullOrBlank()) {
                pendingResult?.success(path)
            } else {
                pendingResult?.error("CAPTURE_FAILED", "The screen frame could not be captured.", null)
            }
            pendingResult = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val filter = IntentFilter(ScreenCaptureForegroundService.ACTION_CAPTURE_COMPLETE)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(captureReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(captureReceiver, filter)
        }
        handleCapturedScreenIntent(intent)
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        captureChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, captureChannelName)
        captureChannel?.setMethodCallHandler { call, result ->
            if (call.method == "captureScreen") {
                if (pendingResult != null) {
                    result.error("BUSY", "A screen capture is already in progress.", null)
                    return@setMethodCallHandler
                }
                pendingResult = result
                val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
                startActivityForResult(manager.createScreenCaptureIntent(), requestCode)
            } else {
                result.notImplemented()
            }
        }

        overlayChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, overlayChannelName)
        overlayChannel?.setMethodCallHandler { call, result ->
            when (call.method) {
                "hasOverlayPermission" -> result.success(Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this))
                "consumePendingCapturedScreen" -> {
                    val path = pendingCapturedScreenPath
                    pendingCapturedScreenPath = null
                    result.success(path)
                }
                "requestOverlayPermission" -> {
                    if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
                        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                    }
                    result.success(null)
                }
                "startFloatingDetector" -> {
                    if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
                        result.error("OVERLAY_PERMISSION", "Display-over-other-apps permission is required.", null)
                    } else {
                        startService(Intent(this, FloatingDetectorService::class.java))
                        result.success(true)
                    }
                }
                "stopFloatingDetector" -> {
                    stopService(Intent(this, FloatingDetectorService::class.java))
                    result.success(true)
                }
                else -> result.notImplemented()
            }
        }
        deliverPendingCapturedScreen()

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, actionsChannelName)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "openWifiSettings" -> {
                        try {
                            startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                            result.success(true)
                        } catch (error: Exception) {
                            result.error("WIFI_SETTINGS", error.message, null)
                        }
                    }
                    "createContact" -> {
                        try {
                            val values = call.arguments as? Map<*, *> ?: emptyMap<String, Any>()
                            val insert = Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI).apply {
                                putExtra(ContactsContract.Intents.Insert.NAME, values["name"] as? String ?: "")
                                putExtra(ContactsContract.Intents.Insert.PHONE, values["phone"] as? String ?: "")
                                putExtra(ContactsContract.Intents.Insert.EMAIL, values["email"] as? String ?: "")
                                putExtra(ContactsContract.Intents.Insert.COMPANY, values["company"] as? String ?: "")
                            }
                            startActivity(insert)
                            result.success(true)
                        } catch (error: Exception) {
                            result.error("CONTACT_INSERT", error.message, null)
                        }
                    }
                    else -> result.notImplemented()
                }
            }
        handleFloatingIntent(intent)
    }

    private fun handleCapturedScreenIntent(intent: Intent?) {
        val path = intent?.getStringExtra(EXTRA_CAPTURED_SCREEN_PATH) ?: return
        intent.removeExtra(EXTRA_CAPTURED_SCREEN_PATH)
        pendingCapturedScreenPath = path
        deliverPendingCapturedScreen()
    }

    private fun deliverPendingCapturedScreen() {
        val path = pendingCapturedScreenPath ?: return
        overlayChannel?.invokeMethod("screenCaptureReady", path, object : MethodChannel.Result {
            override fun success(result: Any?) {
                if (pendingCapturedScreenPath == path) pendingCapturedScreenPath = null
            }
            override fun error(code: String, message: String?, details: Any?) {}
            override fun notImplemented() {}
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleCapturedScreenIntent(intent)
    }

    @Deprecated("Deprecated Android API retained for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != this.requestCode) return
        if (resultCode != Activity.RESULT_OK || data == null) {
            pendingResult?.error("CANCELLED", "Screen capture permission was not granted.", null)
            pendingResult = null
            return
        }
        val serviceIntent = Intent(this, ScreenCaptureForegroundService::class.java).apply {
            putExtra(ScreenCaptureForegroundService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenCaptureForegroundService.EXTRA_RESULT_DATA, data)
        }
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(serviceIntent) else startService(serviceIntent)
        } catch (error: Exception) {
            pendingResult?.error("CAPTURE_FAILED", error.message ?: "Could not start screen capture service.", null)
            pendingResult = null
        }
    }

    override fun onDestroy() {
        try { unregisterReceiver(captureReceiver) } catch (_: Exception) {}
        super.onDestroy()
    }
}
