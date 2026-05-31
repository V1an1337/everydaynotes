package xyz.v1an.everydaynotes

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle

class CaptureTriggerActivity : Activity() {
    private var triggered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    override fun onResume() {
        super.onResume()
        if (triggered) return
        triggered = true

        window.decorView.postDelayed(
            {
                val candidates = CaptureCandidateDetector.detect(this)
                CaptureOverlayService.showCandidates(this, candidates)
                finishAndRemoveTask()
            },
            160L
        )
    }
}
