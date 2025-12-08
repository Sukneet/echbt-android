package org.prozach.echbt.android

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Context.RECEIVER_EXPORTED
import android.content.Context.WINDOW_SERVICE
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection // <-- FIX: This import was missing
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat.startActivity
import org.prozach.echbt.android.databinding.FloatingEchStatsBinding
import timber.log.Timber
import kotlin.math.abs

class ECHStatsFloating constructor(private val context: Context) {

    private var windowManager: WindowManager? = null
        get() {
            if (field == null) field = (context.getSystemService(WINDOW_SERVICE) as WindowManager)
            return field
        }

    private var _binding: FloatingEchStatsBinding? = FloatingEchStatsBinding.inflate(LayoutInflater.from(context), null, false)
    private val binding get() = _binding!!
    private var floatView = binding.root

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
                touchConsumedByMove = false // Reset move flag
            }
            MotionEvent.ACTION_UP -> {
                if (!touchConsumedByMove) {
                    view.performClick()
                }
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
                    }
                }
            }
            else -> {
            }
        }
        touchConsumedByMove
    }

    init {
        with(binding) { // Use binding directly
            /*
            icBackFloat.setOnClickListener {
                val intent = Intent(context, ECHStatsActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(context, intent, null)
                dismiss()
            }


            increaseTime.setOnClickListener {
                statsService?.increaseTime(1)
            }

            increaseTime.setOnLongClickListener {
                statsService?.increaseTime(60)
                true // Return true for long click
            }

            decreaseTime.setOnClickListener {
                statsService?.decreaseTime(1)
            }

            decreaseTime.setOnLongClickListener {
                statsService?.decreaseTime(60)
                true // Return true for long click
            }

             */
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
            if(isShowing) return // Don't add view if already showing
            isShowing = true
            windowManager?.addView(floatView, layoutParams)
            val filter = IntentFilter()
            filter.addAction("com.prozach.echbt.android.stats")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(broadcastHandler, filter, RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(broadcastHandler, filter)
            }
        }
        val intent = Intent(context, ECHStatsService::class.java)
        context.bindService(intent, echStatsServiceConnection, AppCompatActivity.BIND_AUTO_CREATE)
    }

    fun dismiss() {
        if (isShowing) {
            try {
                windowManager?.removeView(floatView)
                context.unregisterReceiver(broadcastHandler)
            } catch (e: Exception) {
                Timber.e(e, "Error during dismiss")
            } finally {
                isShowing = false
            }
        }
    }

    private val broadcastHandler: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val floatingEchStats = intent.getStringExtra("floating_ech_stats")
            if(floatingEchStats == "dismiss") {
                Timber.d("Got: $floatingEchStats")
                dismiss()
                return
            }
            with(binding) { // Use binding directly
                /*
                cadenceFloat.text = intent.getStringExtra("cadence")
                avgCadenceFloat.text = intent.getStringExtra("cadence_avg")
                maxCadenceFloat.text = intent.getStringExtra("cadence_max")
                resistanceFloat.text = intent.getStringExtra("resistance")
                avgResistanceFloat.text = intent.getStringExtra("resistance_avg")
                maxResistanceFloat.text = intent.getStringExtra("resistance_max")
                powerFloat.text = intent.getStringExtra("power")
                avgPowerFloat.text = intent.getStringExtra("power_avg")
                maxPowerFloat.text = intent.getStringExtra("power_max")
                timeFloat.text = intent.getStringExtra("time")
                kcalFloat.text = intent.getStringExtra("kcal")
                distFloat.text = intent.getStringExtra("dist")
                maxResistance.text = intent.getStringExtra("resistance_range_upper")
                minResistance.text = intent.getStringExtra("resistance_range_lower")
                maxCadence.text = intent.getStringExtra("cadence_range_upper")
                minCadence.text = intent.getStringExtra("cadence_range_lower")
                 */

                val cadenceValue = intent.getStringExtra("cadence")?.toDoubleOrNull() ?: 0.0
                val cadenceLower = intent.getStringExtra("cadence_range_lower")?.toDoubleOrNull() ?: 0.0
                val cadenceUpper = intent.getStringExtra("cadence_range_upper")?.toDoubleOrNull() ?: 1.0 // Avoid division by zero

                var cadenceProgress = if (cadenceUpper > cadenceLower) {
                    (((cadenceValue - cadenceLower) / (cadenceUpper - cadenceLower)) * 100).toInt()
                } else {
                    0
                }
                if (cadenceProgress < 0) cadenceProgress = 0
                //cadenceBar.progress = cadenceProgress

                val resistanceValue = intent.getStringExtra("resistance")?.toDoubleOrNull() ?: 0.0
                val resistanceLower = intent.getStringExtra("resistance_range_lower")?.toDoubleOrNull() ?: 0.0
                val resistanceUpper = intent.getStringExtra("resistance_range_upper")?.toDoubleOrNull() ?: 1.0 // Avoid division by zero

                var resistanceProgress = if (resistanceUpper > resistanceLower) {
                    (((resistanceValue - resistanceLower) / (resistanceUpper - resistanceLower)) * 100).toInt()
                } else {
                    0
                }
                if (resistanceProgress < 0) resistanceProgress = 0
                //resistanceBar.progress = resistanceProgress
            }
        }
    }
}