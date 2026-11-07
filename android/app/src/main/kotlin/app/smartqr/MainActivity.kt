package app.smartqr

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.io.File
import java.io.FileOutputStream

class MainActivity : FlutterActivity() {
    private val channelName = "app.smartqr/screen_capture"
    private val requestCode = 7412
    private var pendingResult: MethodChannel.Result? = null
    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var channel: MethodChannel? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        channel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, channelName)
        channel?.setMethodCallHandler { call, result ->
            if (call.method == "captureScreen") {
                if (pendingResult != null) {
                    result.error("BUSY", "A screen capture is already in progress.", null)
                    return@setMethodCallHandler
                }
                pendingResult = result
                val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                startActivityForResult(manager.createScreenCaptureIntent(), requestCode)
            } else {
                result.notImplemented()
            }
        }
    }

    @Deprecated("Deprecated in Android API; retained for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != this.requestCode) return
        if (resultCode != Activity.RESULT_OK || data == null) {
            pendingResult?.error("CANCELLED", "Screen capture permission was not granted.", null)
            pendingResult = null
            return
        }
        try {
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = manager.getMediaProjection(resultCode, data)
            captureOneFrame()
        } catch (error: Exception) {
            finishWithError("CAPTURE_FAILED", error.message ?: "Could not capture the screen.")
        }
    }

    private fun captureOneFrame() {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        val display = projection?.createVirtualDisplay(
            "SmartQR-screen-scan",
            width,
            height,
            metrics.densityDpi,
            0,
            reader!!.surface,
            null,
            Handler(Looper.getMainLooper())
        ) ?: throw IllegalStateException("Unable to create screen capture display.")

        reader?.setOnImageAvailableListener({ imageReader ->
            val image = imageReader.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val plane = image.planes[0]
                val buffer = plane.buffer
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val paddedWidth = rowStride / pixelStride
                val bitmap = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
                bitmap.copyPixelsFromBuffer(buffer)
                val cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height)
                val output = File(cacheDir, "smartqr-screen-${System.currentTimeMillis()}.png")
                FileOutputStream(output).use { stream -> cropped.compress(Bitmap.CompressFormat.PNG, 100, stream) }
                bitmap.recycle()
                cropped.recycle()
                image.close()
                display.release()
                cleanupProjection()
                pendingResult?.success(output.absolutePath)
                pendingResult = null
            } catch (error: Exception) {
                image.close()
                display.release()
                finishWithError("IMAGE_FAILED", error.message ?: "Could not save the captured frame.")
            }
        }, Handler(Looper.getMainLooper()))
    }

    private fun cleanupProjection() {
        reader?.close()
        reader = null
        projection?.stop()
        projection = null
    }

    private fun finishWithError(code: String, message: String) {
        cleanupProjection()
        pendingResult?.error(code, message, null)
        pendingResult = null
    }

    override fun onDestroy() {
        cleanupProjection()
        super.onDestroy()
    }
}
