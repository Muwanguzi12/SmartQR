package app.smartqr

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import java.io.File
import java.io.FileOutputStream

class ScreenCaptureForegroundService : Service() {
    companion object {
        const val ACTION_CAPTURE_COMPLETE = "app.smartqr.CAPTURE_COMPLETE"
        const val EXTRA_PATH = "capture_path"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        private const val CHANNEL_ID = "smartqr_screen_capture"
        private const val NOTIFICATION_ID = 7413
    }

    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var display: VirtualDisplay? = null
    private var completed = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val captureTimeout = Runnable { finish(null) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID)
            else Notification.Builder(this)
        val notification = builder
            .setContentTitle("SmartQR screen scan")
            .setContentText("Capturing one frame after your approval")
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val data = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }
        if (data == null || resultCode == 0) {
            finish(null)
            return START_NOT_STICKY
        }

        try {
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = manager.getMediaProjection(resultCode, data)
            projection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    finish(null)
                }
            }, Handler(Looper.getMainLooper()))
            mainHandler.postDelayed(captureTimeout, 12_000L)
            captureOneFrame()
        } catch (_: Exception) {
            finish(null)
        }
        return START_NOT_STICKY
    }

    private fun captureOneFrame() {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager)
            .defaultDisplay.getRealMetrics(metrics)
        // Remove abandoned temporary frames from earlier interrupted scans.
        try {
            cacheDir.listFiles()?.filter {
                it.name.startsWith("smartqr-frame-") &&
                    System.currentTimeMillis() - it.lastModified() > 24L * 60L * 60L * 1000L
            }?.forEach { it.delete() }
        } catch (_: Exception) {}
        // Bound capture dimensions to limit peak memory use on high-resolution devices.
        val scale = minOf(1f, 1920f / metrics.widthPixels, 1920f / metrics.heightPixels)
        val width = (metrics.widthPixels * scale).toInt().coerceAtLeast(1)
        val height = (metrics.heightPixels * scale).toInt().coerceAtLeast(1)
        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        reader?.setOnImageAvailableListener({ source ->
            val image = source.acquireLatestImage() ?: return@setOnImageAvailableListener
            var bitmap: Bitmap? = null
            var cropped: Bitmap? = null
            var outputFile: File? = null
            try {
                val plane = image.planes[0]
                val paddedWidth = plane.rowStride / plane.pixelStride
                val sourceBitmap = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
                bitmap = sourceBitmap
                sourceBitmap.copyPixelsFromBuffer(plane.buffer)
                val croppedBitmap = Bitmap.createBitmap(sourceBitmap, 0, 0, width, height)
                cropped = croppedBitmap
                val frameFile = File(cacheDir, "smartqr-frame-${System.currentTimeMillis()}.png")
                outputFile = frameFile
                FileOutputStream(frameFile).use { stream ->
                    if (!croppedBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                        throw IllegalStateException("Could not encode the captured frame")
                    }
                }
            } catch (_: Exception) {
                try { outputFile?.delete() } catch (_: Exception) {}
                outputFile = null
            } finally {
                try { bitmap?.recycle() } catch (_: Exception) {}
                try { cropped?.recycle() } catch (_: Exception) {}
                try { image.close() } catch (_: Exception) {}
            }
            finish(outputFile?.absolutePath)
        }, Handler(Looper.getMainLooper()))

        display = projection?.createVirtualDisplay(
            "SmartQR-one-frame",
            width,
            height,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader!!.surface,
            null,
            Handler(Looper.getMainLooper())
        )
        if (display == null) finish(null)
    }

    private fun finish(path: String?) {
        if (completed) return
        completed = true
        mainHandler.removeCallbacks(captureTimeout)
        val result = Intent(ACTION_CAPTURE_COMPLETE).setPackage(packageName)
            .putExtra(EXTRA_PATH, path)
        sendBroadcast(result, "app.smartqr.permission.INTERNAL")
        display?.release()
        display = null
        reader?.close()
        reader = null
        try { projection?.stop() } catch (_: Exception) {}
        projection = null
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE) else stopForeground(true)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "Screen scanning", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(captureTimeout)
        if (!completed) finish(null)
        super.onDestroy()
    }
}
