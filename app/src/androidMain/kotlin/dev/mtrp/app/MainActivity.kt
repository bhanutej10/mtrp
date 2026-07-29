package dev.mtrp.app

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import dev.mtrp.core.ChannelType
import dev.mtrp.core.MTRP
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat


class MainActivity : AppCompatActivity() {

	companion object {
    private const val REQUEST_SMS_PERMISSION = 1001
}
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 120, 64, 64)
            setBackgroundColor(Color.parseColor("#0f1117"))
        }

        layout.addView(TextView(this).apply {
            text = MTRP.version()
            textSize = 24f
            setTextColor(Color.parseColor("#2dd4bf"))
        })
        layout.addView(TextView(this).apply {
            text = "Phase 0 — Build Verified ✓"
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(0, 24, 0, 0)
        })
        layout.addView(TextView(this).apply {
            text = "${ChannelType.entries.size} transport channels defined"
            textSize = 13f
            setTextColor(Color.parseColor("#8b93a8"))
            setPadding(0, 16, 0, 0)
        })
        layout.addView(TextView(this).apply {
            text = "Next: Phase 1 — Protocol Spec"
            textSize = 13f
            setTextColor(Color.parseColor("#8b93a8"))
            setPadding(0, 8, 0, 0)
        })

        setContentView(layout)
        
 if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
    != PackageManager.PERMISSION_GRANTED) {
    ActivityCompat.requestPermissions(
        this,
        arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS),
        REQUEST_SMS_PERMISSION
    )
}
}
    }

