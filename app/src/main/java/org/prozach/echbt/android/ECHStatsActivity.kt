package org.prozach.echbt.android

import android.content.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.identity.SavePasswordRequest
import com.google.android.gms.auth.api.identity.SignInPassword
import kotlinx.android.synthetic.main.activity_ech_stats.*
import timber.log.Timber

class ECHStatsActivity : AppCompatActivity() {

    private lateinit var echStatsFloating: ECHStatsFloating
    private var floatingWindowShown: Boolean = false
    private var receiverRegistered: Boolean = false

    companion object {
        private const val REQUEST_CODE_DRAW_OVERLAY_PERMISSION = 5
    }

    var statsService: ECHStatsService? = null
    var isBound = false
    private val echStatsServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            Timber.d("Activity Connected")
            val binder = service as ECHStatsService.ECHStatsBinder
            statsService = binder.getService()
            statsService?.floatingWindow("dismiss")
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName) {
            Timber.d("Activity Disconnected")
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ech_stats)

        val intent = Intent(this, ECHStatsService::class.java)
        bindService(intent, echStatsServiceConnection, BIND_AUTO_CREATE)

        val filter = IntentFilter()
        filter.addAction("com.prozach.echbt.android.stats")
        registerReceiver(broadcastHandler, filter)
        receiverRegistered = true

        echStatsFloating = ECHStatsFloating(applicationContext)

        pipButton.setOnClickListener {
            if (canDrawOverlays) {
                echStatsFloating.show()
                floatingWindowShown = true
                finish()
                // Return to home screen
                val startMain = Intent(Intent.ACTION_MAIN)
                startMain.addCategory(Intent.CATEGORY_HOME)
                startMain.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(startMain)
            } else {
                startManageDrawOverlaysPermission()
            }
        }

        if (canDrawOverlays) {
            pip_help.visibility = View.INVISIBLE
        } else {
            pip_help.visibility = View.VISIBLE
        }

        ic_reset_stats.setOnClickListener{
            statsService?.clearStats()
            statsService?.forceResistance(32u)
        }
        reset_stats.setOnClickListener{
            statsService?.clearStats()
            statsService?.forceResistance(32u)
        }
        ic_reset_time.setOnClickListener{
            statsService?.clearStats()
            statsService?.forceResistance(1u)
        }
        reset_time.setOnClickListener{
            statsService?.clearTime()
            statsService?.forceResistance(1u)
        }
        stats_format_echelon.setOnClickListener{
            statsService?.setStatsFormat(ECHStatsService.StatsFormat.ECHELON)
        }
        stats_format_peleton.setOnClickListener{
            statsService?.setStatsFormat(ECHStatsService.StatsFormat.PELOTON)
        }
        dist_format_miles.setOnClickListener{
            statsService?.setDistFormat(ECHStatsService.DistFormat.MILES)
        }
        dist_format_kilometers.setOnClickListener{
            statsService?.setDistFormat(ECHStatsService.DistFormat.KILOMETERS)
        }
        time_format_elapsed.setOnClickListener{
            statsService?.setTimeFormat(ECHStatsService.TimeFormat.ELAPSED)
        }
        time_format_remaining.setOnClickListener{
            statsService?.setTimeFormat(ECHStatsService.TimeFormat.REMAINING)
        }

        login.setOnClickListener {
            val signInPassword = SignInPassword(Peloton_user.text.toString(), Peloton_pass.text.toString())
            val savePasswordRequest =
                SavePasswordRequest.builder().setSignInPassword(signInPassword).build()
            statsService?.pelotonLogin(Peloton_user.text.toString(),Peloton_pass.text.toString())
        }
    }

    private val broadcastHandler: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            //println("onReceive")
            runOnUiThread {
                cadence.text = intent.getStringExtra("cadence")
                cadence_avg.text = intent.getStringExtra("cadence_avg")
                cadence_max.text = intent.getStringExtra("cadence_max")
                resistance.text = intent.getStringExtra("resistance")
                resistance_avg.text = intent.getStringExtra("resistance_avg")
                resistance_max.text = intent.getStringExtra("resistance_max")
                power.text = intent.getStringExtra("power")
                power_avg.text = intent.getStringExtra("power_avg")
                power_max.text = intent.getStringExtra("power_max")
                time.text = intent.getStringExtra("time")
                kcal.text = intent.getStringExtra("kcal")
                dist.text = intent.getStringExtra("dist")

                val statsFormat = intent.getStringExtra("stats_format")
                if(statsFormat != "") {
                    if(statsFormat == "echelon" && !stats_format_echelon.isChecked) {
                        stats_format_echelon.isChecked = true
                        stats_format_peleton.isChecked = false
                    }
                    if(statsFormat == "peloton" && !stats_format_peleton.isChecked) {
                        stats_format_echelon.isChecked = false
                        stats_format_peleton.isChecked = true
                    }
                }
                val distFormat = intent.getStringExtra("dist_format")
                if(distFormat != "") {
                    if(distFormat == "miles" && !dist_format_miles.isChecked) {
                        dist_format_miles.isChecked = true
                        dist_format_kilometers.isChecked = false
                    }
                    if(distFormat == "kilometers" && !dist_format_kilometers.isChecked) {
                        dist_format_miles.isChecked = false
                        dist_format_kilometers.isChecked = true
                    }
                }
            }
        }
    }

    override fun onBackPressed() {
        statsService?.shutdown()
        super.onBackPressed() // Don't call this
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(echStatsServiceConnection)
        if (receiverRegistered) {
            unregisterReceiver(broadcastHandler)
            receiverRegistered = false
        }
    }

    override fun onPause() {
        super.onPause()
        if (receiverRegistered) {
            unregisterReceiver(broadcastHandler)
            receiverRegistered = false
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter()
        filter.addAction("com.prozach.echbt.android.stats")
        if (!receiverRegistered) {
            registerReceiver(broadcastHandler, filter)
            receiverRegistered = true
        }

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_CODE_DRAW_OVERLAY_PERMISSION -> {
                if (canDrawOverlays) {
                    echStatsFloating.show()
                    floatingWindowShown = true
                    finish()
                    // Return to home screen
                    val startMain = Intent(Intent.ACTION_MAIN)
                    startMain.addCategory(Intent.CATEGORY_HOME)
                    startMain.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(startMain)
                } else {
                    showToast("Permission is not granted!")
                }
            }
        }
    }

    private fun startManageDrawOverlaysPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${applicationContext.packageName}")
            ).let {
                startActivityForResult(it, REQUEST_CODE_DRAW_OVERLAY_PERMISSION)
            }
        }
    }
}

