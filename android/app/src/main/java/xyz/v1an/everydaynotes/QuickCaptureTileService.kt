package xyz.v1an.everydaynotes

import android.content.Intent
import android.service.quicksettings.TileService

class QuickCaptureTileService : TileService() {
    override fun onClick() {
        super.onClick()
        startActivityAndCollapse(
            Intent(this, CaptureTriggerActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        )
    }
}
