package org.prozach.echbt.android

import android.content.*
import android.content.Context.WINDOW_SERVICE
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat.startActivity
import kotlinx.android.synthetic.main.activity_ech_stats.view.*
import kotlinx.android.synthetic.main.floating_ech_stats.view.*
//import kotlinx.android.synthetic.main.floating_ech_stats.view.ic_reset_stats
//import kotlinx.android.synthetic.main.floating_ech_stats.view.ic_reset_time
import timber.log.Timber
import kotlin.math.abs


class ECHStatsFloating constructor(private val context: Context) {

    private var windowManager: WindowManager? = null
        get() {
            if (field == null) field = (context.getSystemService(WINDOW_SERVICE) as WindowManager)
            return field
        }

    private var floatView: View =
        LayoutInflater.from(context).inflate(R.layout.floating_ech_stats, null)

    private lateinit var layoutParams: WindowManager.LayoutParams

    var statsService: ECHStatsService? = null
    private val echStatsServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            Timber.i("Floating Window Connected To Service")
            val binder = service as ECHStatsService.ECHStatsBinder
            statsService = binder.getService()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            Timber.i("Floating Window Disconnected From Service")
        }
    }

    private var lastX: Int = 0
    private var lastY: Int = 0
    private var firstX: Int = 0
    private var firstY: Int = 0

    private var isShowing = false
    private var touchConsumedByMove = false

    private val onTouchListener = View.OnTouchListener { view, event ->
        val totalDeltaX = lastX - firstX
        val totalDeltaY = lastY - firstY

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.rawX.toInt()
                lastY = event.rawY.toInt()
                firstX = lastX
                firstY = lastY
            }
            MotionEvent.ACTION_UP -> {
                view.performClick()
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaX = event.rawX.toInt() - lastX
                val deltaY = event.rawY.toInt() - lastY
                lastX = event.rawX.toInt()
                lastY = event.rawY.toInt()
                if (abs(totalDeltaX) >= 5 || abs(totalDeltaY) >= 5) {
                    if (event.pointerCount == 1) {
                        layoutParams.x += deltaX
                        layoutParams.y += deltaY
                        touchConsumedByMove = true
                        windowManager?.apply {
                            updateViewLayout(floatView, layoutParams)
                        }
                    } else {
                        touchConsumedByMove = false
                    }
                } else {
                    touchConsumedByMove = false
                }
            }
            else -> {
            }
        }
        touchConsumedByMove
    }

    init {
        with(floatView) {
            ic_back_float.setOnClickListener {
                val intent = Intent(context, ECHStatsActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(context, intent, null)
                dismiss()
            }

            /*
            ic_reset_stats.setOnClickListener {
                statsService?.clearStats()
            }

            ic_reset_time.setOnClickListener {
                statsService?.clearTime()
            }
            */

            increase_time.setOnClickListener {
                statsService?.increaseTime(1)
            }

            increase_time.setOnLongClickListener {
                statsService?.increaseTime(60)
                false
            }

            decrease_time.setOnClickListener {
                statsService?.decreaseTime(1)
            }

            decrease_time.setOnLongClickListener {
                statsService?.decreaseTime(60)
                false
            }
        }

        floatView.setOnTouchListener(onTouchListener)

        layoutParams = WindowManager.LayoutParams().apply {
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            @Suppress("DEPRECATION")
            type = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ->
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else -> WindowManager.LayoutParams.TYPE_PHONE
            }

            gravity = Gravity.CENTER
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
        }
    }

    fun show() {
        Timber.i("show")
        if (context.canDrawOverlays) {
            dismiss()
            isShowing = true
            windowManager?.addView(floatView, layoutParams)
            val filter = IntentFilter()
            filter.addAction("com.prozach.echbt.android.stats")
            context.registerReceiver(broadcastHandler, filter)
        }
        val intent = Intent(context, ECHStatsService::class.java)
        context.bindService(intent, echStatsServiceConnection, AppCompatActivity.BIND_AUTO_CREATE)
    }

    fun dismiss() {
        if (isShowing) {
            windowManager?.removeView(floatView)
            isShowing = false
            context.unregisterReceiver(broadcastHandler)
        }
    }

    private val broadcastHandler: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            //println("onReceive")
            val floatingEchStats = intent.getStringExtra("floating_ech_stats")
            if(floatingEchStats == "dismiss") {
                Timber.d("Got: $floatingEchStats")
                dismiss()
                return
            }
            with(floatView) {
                cadence_float.text = intent.getStringExtra("cadence")
                avg_cadence_float.text = intent.getStringExtra("cadence_avg")
                max_cadence_float.text = intent.getStringExtra("cadence_max")
                resistance_float.text = intent.getStringExtra("resistance")
                avg_resistance_float.text = intent.getStringExtra("resistance_avg")
                max_resistance_float.text = intent.getStringExtra("resistance_max")
                power_float.text = intent.getStringExtra("power")
                avg_power_float.text = intent.getStringExtra("power_avg")
                max_power_float.text = intent.getStringExtra("power_max")
                time_float.text = intent.getStringExtra("time")
                kcal_float.text = intent.getStringExtra("kcal")
                dist_float.text = intent.getStringExtra("dist")
                max_resistance.text = intent.getStringExtra("resistance_range_upper")
                min_resistance.text = intent.getStringExtra("resistance_range_lower")
                max_cadence.text = intent.getStringExtra("cadence_range_upper")
                min_cadence.text = intent.getStringExtra("cadence_range_lower")

                var cadenceProgress = (((intent.getStringExtra("cadence")!!.toDouble()-intent.getStringExtra("cadence_range_lower")!!.toDouble()) / (intent.getStringExtra("cadence_range_upper")!!.toDouble() - intent.getStringExtra("cadence_range_lower")!!.toDouble())) * 100).toInt()
                if (cadenceProgress > 0) {
                    cadenceProgress = cadenceProgress
                } else {
                    cadenceProgress = 0
                    //set tint color
                }
                //Timber.d("Cadence Progress = $cadenceProgress")
                cadenceBar.progress = cadenceProgress

                var resistanceProgress = (((intent.getStringExtra("resistance")!!.toDouble() - intent.getStringExtra("resistance_range_lower")!!.toDouble()) / (intent.getStringExtra("resistance_range_upper")!!.toDouble() - intent.getStringExtra("resistance_range_lower")!!.toDouble())) * 100).toInt()
                resistanceProgress = if (resistanceProgress > 0) resistanceProgress else  0
                //Timber.d("Resistance Progress = $resistanceProgress")
                resistanceBar.progress = resistanceProgress
            }
        }
    }
}