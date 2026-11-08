package app.smartqr

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Adds a SmartQR shortcut to Android Quick Settings.
 *
 * The tile launches the existing consent activity so Android can show
 * its mandatory MediaProjection approval UI before screen capture begins.
 */
class SmartQrQuickSettingsTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()

        val scanIntent = Intent(this, ScreenCaptureConsentActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startActivityAndCollapse(scanIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(scanIntent)
            }
        } catch (_: Exception) {
            // Keep the tile available; Android will surface launch failures.
        }
    }
}