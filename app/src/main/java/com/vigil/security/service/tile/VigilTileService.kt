package com.vigil.security.service.tile

import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.vigil.security.R
import com.vigil.security.VigilApplication

class VigilTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()

        val app = application as? VigilApplication ?: return
        val protectionRepo = app.container.protectionStateRepository
        val keyStoreManager = app.container.keyStoreManager

        val hasAccessibility = protectionRepo.isAccessibilityEnabled()

        if (!hasAccessibility) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            if (isLocked) {
                unlockAndRun {
                    startActivityAndCollapse(intent)
                }
            } else {
                startActivityAndCollapse(intent)
            }
            Toast.makeText(this, "Enable Vigil in Accessibility to start protection", Toast.LENGTH_SHORT).show()
            return
        }

        // Toggle state
        val newState = !keyStoreManager.isMonitoringEnabled
        keyStoreManager.isMonitoringEnabled = newState

        updateTileState()

        val msg = if (newState) "Vigil Link Protection: Active" else "Vigil Link Protection: Paused"
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val app = application as? VigilApplication ?: return
        val protectionRepo = app.container.protectionStateRepository
        val keyStoreManager = app.container.keyStoreManager

        val hasAccessibility = protectionRepo.isAccessibilityEnabled()
        val isMonitoring = keyStoreManager.isMonitoringEnabled
        val isActive = hasAccessibility && isMonitoring

        tile.state = if (isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Vigil"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (isActive) "Active" else "Paused"
        }

        // Transparent monochrome vector/PNG tile icon
        tile.icon = Icon.createWithResource(this, R.drawable.ic_qs_tile)
        tile.updateTile()
    }
}
