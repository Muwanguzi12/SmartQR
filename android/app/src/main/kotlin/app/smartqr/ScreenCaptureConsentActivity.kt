package app.smartqr

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast

/**
 * A transparent consent host. It leaves the app underneath visible while
 * Android displays its required MediaProjection consent dialog and while
 * the one-frame capture is taken. SmartQR is opened only after capture.
 */
class ScreenCaptureConsentActivity : Activity() {
    private val requestCode = 7414
    private var receiverRegistered = false

    private val captureReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val path = intent?.getStringExtra(ScreenCaptureForegroundService.EXTRA_PATH)
            if (path.isNullOrBlank()) {
                Toast.makeText(this@ScreenCaptureConsentActivity, "Could not capture this screen. Try again.", Toast.LENGTH_SHORT).show()
                finish()
                return
            }

            val openSmartQr = Intent(this@ScreenCaptureConsentActivity, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(MainActivity.EXTRA_CAPTURED_SCREEN_PATH, path)
            }
            startActivity(openSmartQr)
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep the underlying app visible and interactive during the brief capture.
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        )
        window.setDimAmount(0f)

        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(manager.createScreenCaptureIntent(), requestCode)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != this.requestCode) return

        if (resultCode != Activity.RESULT_OK || data == null) {
            finish()
            return
        }

        val filter = IntentFilter(ScreenCaptureForegroundService.ACTION_CAPTURE_COMPLETE)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(captureReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(captureReceiver, filter)
        }
        receiverRegistered = true

        val serviceIntent = Intent(this, ScreenCaptureForegroundService::class.java).apply {
            putExtra(ScreenCaptureForegroundService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenCaptureForegroundService.EXTRA_RESULT_DATA, data)
        }
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(serviceIntent) else startService(serviceIntent)
        } catch (_: Exception) {
            Toast.makeText(this, "Screen capture could not start. Please try again.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onDestroy() {
        if (receiverRegistered) {
            try { unregisterReceiver(captureReceiver) } catch (_: Exception) {}
            receiverRegistered = false
        }
        super.onDestroy()
    }
}
