package org.prozach.echbt.android

import android.content.*
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.identity.SavePasswordRequest
import com.google.android.gms.auth.api.identity.SignInPassword
import org.prozach.echbt.android.databinding.ActivityEchStatsBinding
import timber.log.Timber
import androidx.core.net.toUri

class ECHStatsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEchStatsBinding
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
        binding = ActivityEchStatsBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        val intent = Intent(this, ECHStatsService::class.java)
        bindService(intent, echStatsServiceConnection, BIND_AUTO_CREATE)

        val filter = IntentFilter()
        filter.addAction("com.prozach.echbt.android.stats")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerReceiver(broadcastHandler, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(broadcastHandler, filter)
        }
        receiverRegistered = true

        echStatsFloating = ECHStatsFloating(applicationContext)

        binding.pipButton.setOnClickListener {
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
            binding.pipHelp.visibility = View.INVISIBLE
        } else {
            binding.pipHelp.visibility = View.VISIBLE
        }

        binding.icResetStats.setOnClickListener{
            statsService?.clearStats()
            statsService?.forceResistance(32u)
        }
        binding.resetStats.setOnClickListener{
            statsService?.clearStats()
            statsService?.forceResistance(32u)
        }
        binding.icResetTime.setOnClickListener{
            statsService?.clearStats()
            statsService?.forceResistance(1u)
        }
        binding.resetTime.setOnClickListener{
            statsService?.clearTime()
            statsService?.forceResistance(1u)
        }
        binding.statsFormatEchelon.setOnClickListener{
            statsService?.setStatsFormat(ECHStatsService.StatsFormat.ECHELON)
        }
        binding.statsFormatPeloton.setOnClickListener{
            statsService?.setStatsFormat(ECHStatsService.StatsFormat.PELOTON)
        }
        binding.distFormatMiles.setOnClickListener{
            statsService?.setDistFormat(ECHStatsService.DistFormat.MILES)
        }
        binding.distFormatKilometers.setOnClickListener{
            statsService?.setDistFormat(ECHStatsService.DistFormat.KILOMETERS)
        }
        binding.timeFormatElapsed.setOnClickListener{
            statsService?.setTimeFormat(ECHStatsService.TimeFormat.ELAPSED)
        }
        binding.timeFormatRemaining.setOnClickListener{
            statsService?.setTimeFormat(ECHStatsService.TimeFormat.REMAINING)
        }

        binding.login.setOnClickListener {
            val signInPassword = SignInPassword(binding.PelotonUser.text.toString(), binding.PelotonPass.text.toString())
            val savePasswordRequest =
                SavePasswordRequest.builder().setSignInPassword(signInPassword).build()
            statsService?.pelotonLogin(binding.PelotonUser.text.toString(),binding.PelotonPass.text.toString())
        }
    }

    private val broadcastHandler: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            //println("onReceive")
            runOnUiThread {
                binding.cadence.text = intent.getStringExtra("cadence")
                binding.cadenceAvg.text = intent.getStringExtra("cadence_avg")
                binding.cadenceMax.text = intent.getStringExtra("cadence_max")
                binding.resistance.text = intent.getStringExtra("resistance")
                binding.resistanceAvg.text = intent.getStringExtra("resistance_avg")
                binding.resistanceMax.text = intent.getStringExtra("resistance_max")
                binding.power.text = intent.getStringExtra("power")
                binding.powerAvg.text = intent.getStringExtra("power_avg")
                binding.powerMax.text = intent.getStringExtra("power_max")
                binding.time.text = intent.getStringExtra("time")
                binding.kcal.text = intent.getStringExtra("kcal")
                binding.dist.text = intent.getStringExtra("dist")

                val statsFormat = intent.getStringExtra("stats_format")
                if(statsFormat != "") {
                    if(statsFormat == "echelon" && !binding.statsFormatEchelon.isChecked) {
                        binding.statsFormatEchelon.isChecked = true
                        binding.statsFormatPeloton.isChecked = false
                    }
                    if(statsFormat == "peloton" && !binding.statsFormatPeloton.isChecked) {
                        binding.statsFormatEchelon.isChecked = false
                        binding.statsFormatPeloton.isChecked = true
                    }
                }
                val distFormat = intent.getStringExtra("dist_format")
                if(distFormat != "") {
                    if(distFormat == "miles" && !binding.distFormatMiles.isChecked) {
                        binding.distFormatMiles.isChecked = true
                        binding.distFormatKilometers.isChecked = false
                    }
                    if(distFormat == "kilometers" && !binding.distFormatKilometers.isChecked) {
                        binding.distFormatMiles.isChecked = false
                        binding.distFormatKilometers.isChecked = true
                    }
                }
                binding.loginStatus.text = intent.getStringExtra("login_status")
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                registerReceiver(broadcastHandler, filter, RECEIVER_EXPORTED)
            } else {
                registerReceiver(broadcastHandler, filter)
            }
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
                }
            }
        }
    }

    private fun startManageDrawOverlaysPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:${applicationContext.packageName}".toUri()
            ).let {
                startActivityForResult(it, REQUEST_CODE_DRAW_OVERLAY_PERMISSION)
            }
        }
    }
}
