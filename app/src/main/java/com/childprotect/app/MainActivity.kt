package com.childprotect.app

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.childprotect.app.databinding.ActivityMainBinding
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var prefs: Prefs

    private val shizukuPermListener = Shizuku.OnRequestPermissionResultListener { _, result ->
        val granted = result == PackageManager.PERMISSION_GRANTED
        Toast.makeText(
            this,
            if (granted) "Shizuku access granted" else "Shizuku access denied",
            Toast.LENGTH_SHORT
        ).show()
        refreshStatus()
    }

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val denied = result.filterValues { !it }.keys
        if (denied.isEmpty()) {
            Toast.makeText(this, "All permissions granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Missing: ${denied.joinToString()}", Toast.LENGTH_LONG).show()
        }
        refreshStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        prefs = Prefs(this)

        b.editCode.setText(prefs.secretCode)
        b.editTrusted.setText(prefs.trustedContacts)
        b.switchPin.isChecked = prefs.pinEnabled
        b.editPin.setText(prefs.pin)
        b.switchShizuku.isChecked = prefs.useShizuku
        b.switchRoot.isChecked = prefs.attemptRootData
        b.switchAutoData.isChecked = prefs.autoEnableData
        b.switchAutoLoc.isChecked = prefs.autoEnableLocation
        b.switchRing.isChecked = prefs.ringEnabled
        b.switchSim.isChecked = prefs.simChangeAlert
        b.switchBattery.isChecked = prefs.lowBatteryAlert

        b.btnSave.setOnClickListener {
            prefs.secretCode = b.editCode.text.toString().trim()
            prefs.trustedContacts = b.editTrusted.text.toString().trim()
            prefs.pinEnabled = b.switchPin.isChecked
            prefs.pin = b.editPin.text.toString().trim()
            prefs.useShizuku = b.switchShizuku.isChecked
            prefs.attemptRootData = b.switchRoot.isChecked
            prefs.autoEnableData = b.switchAutoData.isChecked
            prefs.autoEnableLocation = b.switchAutoLoc.isChecked
            prefs.ringEnabled = b.switchRing.isChecked
            prefs.simChangeAlert = b.switchSim.isChecked
            prefs.lowBatteryAlert = b.switchBattery.isChecked
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        }

        b.btnPerms.setOnClickListener { requestPerms() }
        b.btnShizuku.setOnClickListener { requestShizuku() }
        b.btnGuide.setOnClickListener { showShizukuGuide() }
        b.btnAdmin.setOnClickListener { toggleAdmin() }

        Shizuku.addRequestPermissionResultListener(shizukuPermListener)
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(shizukuPermListener)
        super.onDestroy()
    }

    private fun requestPerms() {
        val needed = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            needed += Manifest.permission.POST_NOTIFICATIONS
        }
        permLauncher.launch(needed.toTypedArray())
    }

    private fun requestShizuku() {
        if (!Shizuku.pingBinder()) {
            Toast.makeText(this, "Shizuku isn't running — tap \"How to start\".", Toast.LENGTH_LONG)
                .show()
            return
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Already granted", Toast.LENGTH_SHORT).show()
            return
        }
        Shizuku.requestPermission(SHIZUKU_REQ)
    }

    private fun toggleAdmin() {
        if (AdminReceiver.isActive(this)) {
            // The owner is deactivating protection; then they can uninstall normally.
            AdminReceiver.dpm(this).removeActiveAdmin(AdminReceiver.component(this))
            Toast.makeText(this, "Uninstall protection disabled", Toast.LENGTH_SHORT).show()
            refreshStatus()
        } else {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, AdminReceiver.component(this@MainActivity))
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Lets Anti Theft block a thief from uninstalling it, and lock the " +
                        "phone remotely. You can turn this off anytime from here or in " +
                        "Settings → Security → Device admin apps.",
                )
            }
            startActivity(intent)
        }
    }

    private fun showShizukuGuide() {
        AlertDialog.Builder(this)
            .setTitle("Start Shizuku")
            .setMessage(
                "Shizuku lets this app turn on mobile data & location without root.\n\n" +
                    "Galaxy S23 Ultra (One UI 6):\n" +
                    "1. Install Shizuku from the Play Store.\n" +
                    "2. Settings → About phone → Software information → tap Build number " +
                    "7× to unlock Developer options.\n" +
                    "3. Developer options → turn on Wireless debugging.\n" +
                    "4. In Shizuku, tap \"Start via Wireless debugging\" and follow the pairing.\n" +
                    "5. If pairing is blocked, turn off Settings → Security and privacy → " +
                    "Auto Blocker, then retry.\n\n" +
                    "With a PC over USB:\n" +
                    "1. Enable USB debugging, connect the cable.\n" +
                    "2. Run: adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/" +
                    "start.sh\n\n" +
                    "After it's running, come back and tap \"Request access\".\n" +
                    "Note: Shizuku must be restarted after every reboot."
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun refreshStatus() {
        val granted = { p: String ->
            ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED
        }
        val ok = granted(Manifest.permission.RECEIVE_SMS) &&
            granted(Manifest.permission.SEND_SMS) &&
            granted(Manifest.permission.ACCESS_FINE_LOCATION)
        b.txtStatus.text = if (ok) {
            "✅ Permissions OK. Text your secret code to this phone to locate it.\n" +
                "Tip: set location to \"Allow all the time\" for background use."
        } else {
            "⚠️ Permissions incomplete. Tap \"Grant permissions\"."
        }

        b.txtShizuku.text = when {
            !Shizuku.pingBinder() ->
                "Shizuku: not running. Tap \"How to start\" (enables data + location toggling)."
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED ->
                "Shizuku: ✅ running & authorized — can turn on data + location."
            else -> "Shizuku: running but not authorized. Tap \"Request access\"."
        }

        val adminOn = AdminReceiver.isActive(this)
        b.txtAdmin.text = if (adminOn) {
            "Uninstall protection: ✅ on. A thief can't uninstall without deactivating " +
                "admin (needs your lock screen). You can also text your code + LOCK."
        } else {
            "Uninstall protection: off. Enable it so a thief can't just uninstall the app."
        }
        b.btnAdmin.text =
            if (adminOn) "Disable uninstall protection" else "Enable uninstall protection"
    }

    companion object {
        private const val SHIZUKU_REQ = 1001
    }
}
