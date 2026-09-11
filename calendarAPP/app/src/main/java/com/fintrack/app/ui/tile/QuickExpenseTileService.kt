package com.fintrack.app.ui.tile

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.fintrack.app.MainActivity

@RequiresApi(Build.VERSION_CODES.N)
class QuickExpenseTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.let {
            it.state = Tile.STATE_ACTIVE
            it.updateTile()
        }
    }

    override fun onTileAdded() {
        super.onTileAdded()
        qsTile?.let {
            it.state = Tile.STATE_ACTIVE
            it.updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) {
            unlockAndRun { launchQuickEntry() }
        } else {
            launchQuickEntry()
        }
    }

    private fun launchQuickEntry() {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_QUICK_ENTRY
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivityAndCollapse(intent)
    }

    companion object {
        const val ACTION_QUICK_ENTRY = "com.fintrack.app.QUICK_ENTRY"
    }
}
