package app.smartqr

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast

/**
 * A transparent, isolated consent host. The floating detector owns the result
 * flow; this activity never launches MainActivity.
 */
class ScreenCaptureConsentActivity : Activity() {
    private val requestCode = 7414

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        val serviceIntent = Intent(this, ScreenCaptureForegroundService::class.java).apply {
            putExtra(ScreenCaptureForegroundService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenCaptureForegroundService.EXTRA_RESULT_DATA, data)
        }
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(serviceIntent)
            else startService(serviceIntent)
        } catch (_: Exception) {
            Toast.makeText(this, "Screen capture could not start. Please try again.", Toast.LENGTH_SHORT).show()
        } finally {
            // Reveal the app the user was using. Capture and recognition continue independently.
            finish()
        }
    }
}