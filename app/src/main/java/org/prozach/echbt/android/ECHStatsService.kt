package org.prozach.echbt.android

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.prozach.echbt.android.ble.ConnectionEventListener
import org.prozach.echbt.android.ble.ConnectionManager
import org.prozach.echbt.android.ble.toHexString
import timber.log.Timber
import java.util.*
import kotlin.math.pow
import kotlin.math.roundToInt


class ECHStatsService : LifecycleService() {

    private val sensorUUID = UUID.fromString("0bf669f4-45f2-11e7-9598-0800200c9a66")
    private val writeUUID = UUID.fromString("0bf669f2-45f2-11e7-9598-0800200c9a66")
    private val channelID = "Echadence"

    // https://www.techotopia.com/index.php/Android_Local_Bound_Services_%E2%80%93_A_Kotlin_Example
    private val binder = ECHStatsBinder()

    private lateinit var device: BluetoothDevice
    private val characteristics by lazy {
        ConnectionManager.servicesOnDevice(device)?.flatMap { service ->
            service.characteristics ?: listOf()
        } ?: listOf()
    }
    private var notifyingCharacteristics = mutableListOf<UUID>()

    private var resistanceVal: UInt = 0u
    private var resistanceMax: UInt = 0u
    private var resistanceTotal: UInt = 0u
    private var cadenceVal: UInt = 0u
    private var cadenceMax: UInt = 0u
    private var cadenceTotal: UInt = 0u
    private var powerMax: UInt = 0u
    private var dist: Long = 0
    private var startTimeMillis: Long = 0
    private var elapsedTimeMillis: Long = 0
    private var statCount: UInt = 0u
    private var distFormat: DistFormat = DistFormat.MILES
    private var statsFormat: StatsFormat = StatsFormat.PELOTON
    private var timeFormat: TimeFormat = TimeFormat.ELAPSED
    private var rideDuration: Long = 60L

    private var resistanceRangeUpper = 1
    private var resistanceRangeLower = 0
    private var cadenceRangeUpper = 1
    private var cadenceRangeLower = 0

    private var pelotonUsername = ""
    private var pelotonPassword = ""
    private val follow = FollowRide()
    private lateinit var peloton:Peloton

    enum class StatsFormat {
        ECHELON, PELOTON
    }

    enum class DistFormat {
        MILES, KILOMETERS
    }

    enum class TimeFormat {
        ELAPSED, REMAINING
    }

    override fun onCreate(){
        super.onCreate()
        peloton = Peloton(java.io.File(filesDir, "cookies.txt"))
    }

    fun setStatsFormat(sf: StatsFormat) {
        statsFormat = sf
        powerMax = 0U
    }

    fun setDistFormat(df: DistFormat) {
        //var intent = Intent("com.prozach.echbt.android.stats")
        distFormat = df
    }

    fun setTimeFormat(tf: TimeFormat) {
        timeFormat = tf
    }


    fun clearStats() {
        resistanceMax = 0U
        resistanceTotal = 0U
        cadenceMax = 0U
        cadenceTotal = 0U
        powerMax = 0U
        statCount = 0U
        dist = 0L
    }

    fun clearTime() {
        startTimeMillis = 0L
        elapsedTimeMillis = 0L
    }

    fun increaseTime(inc:Int) {
        follow.increaseTime(inc)
    }

    fun decreaseTime(dec:Int) {
        follow.decreaseTime(dec)
    }

    fun pelotonLogin(username:String,password:String) {
        pelotonUsername = username
        pelotonPassword = password

        if (pelotonUsername.isNotEmpty() && pelotonPassword.isNotEmpty()) {
            peloton.login(pelotonUsername,pelotonPassword)
        }
    }

    fun shutdown() {
        ConnectionManager.unregisterListener(connectionEventListener)
        ConnectionManager.teardownConnection(device)
        stopForeground(true)
        stopSelf()
    }

    fun floatingWindow(action: String) {
        val intent = Intent("com.prozach.echbt.android.stats")
        intent.putExtra("floating_ech_stats", action)
        Timber.i("Sent:$action")
        sendBroadcast(intent)
    }

    // Helpers to start/stop
    // https://androidwave.com/foreground-service-android-example-in-kotlin/
    companion object {
        fun startService(context: Context, message: String) {
            val startIntent = Intent(context, ECHStatsService::class.java)
            startIntent.putExtra("inputExtra", message)
            ContextCompat.startForegroundService(context, startIntent)
        }

        fun stopService(context: Context) {
            val stopIntent = Intent(context, ECHStatsService::class.java)
            context.stopService(stopIntent)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                channelID, "Foreground Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager!!.createNotificationChannel(serviceChannel)
        }
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Timber.i("onStartCommand")

        // Create a notification to keep it running
        createNotificationChannel()
        val notificationIntent = Intent(this, ECHStatsActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0, notificationIntent, 0
        )
        val notification = NotificationCompat.Builder(this, channelID)
            .setContentTitle(this.resources.getString(R.string.app_name))
            .setContentText("Bike Stats collection running...")
            .setSmallIcon(R.mipmap.ic_cadence_white)
            .setContentIntent(pendingIntent)
            .build()
        startForeground(1, notification)

        ConnectionManager.registerListener(connectionEventListener)
        device = intent?.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            ?: error("Missing BluetoothDevice from MainActivity!")

        for (characteristic in characteristics) {
            when (characteristic.uuid) {
                sensorUUID -> {
                    ConnectionManager.enableNotifications(device, characteristic)
                    Timber.i("Enabling notifications from ${characteristic.uuid}")
                }
                writeUUID -> {
                    ConnectionManager.writeCharacteristic(
                        device,
                        characteristic,
                        byteArrayOfInts(0xF0, 0xB0, 0x01, 0x01, 0xA2)
                    )
                    Timber.i("Writing activation string to ${characteristic.uuid}")
                }
            }
        }

        lifecycleScope.launch{
            startPeloton()
        }
        return START_NOT_STICKY
    }

    private suspend fun startPeloton() {
        Timber.i("Creating Peloton")

        lifecycleScope.launch {
            //peloton.login(pelotonUsername, pelotonPassword)

            launch {
                follow.resistance.collect { followOutput ->
                    resistanceRangeUpper = followOutput.resistanceRangeUpper
                    resistanceRangeLower = followOutput.resistanceRangeLower
                    cadenceRangeUpper = followOutput.cadenceRangeUpper
                    cadenceRangeLower = followOutput.cadenceRangeLower
                    Timber.i("Setting New Resistance to $followOutput.resistance")
                    forceResistance(followOutput.resistance)
                }
            }

            launch {
                peloton.latestWorkout.collect { latestInstructorCues ->
                    //follow.cues = latestInstructorCues
                    Timber.i("New instructor cues")
                    rideDuration = latestInstructorCues.duration
                    follow.update(latestInstructorCues.startTime,latestInstructorCues.instructor_cues!!)
                }
            }


        }
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    inner class ECHStatsBinder : Binder() {
        fun getService(): ECHStatsService {
            return this@ECHStatsService
        }
    }

    override fun onDestroy() {
        //println("onDestroy")
        ConnectionManager.unregisterListener(connectionEventListener)
        ConnectionManager.teardownConnection(device)
        super.onDestroy()
    }

    private val connectionEventListener by lazy {
        ConnectionEventListener().apply {
            onDisconnect = {
                Timber.i("Disconnected")
            }

            onCharacteristicRead = { _, characteristic ->
                Timber.d("Read from ${characteristic.uuid}: ${characteristic.value.toHexString()}")
            }

            onCharacteristicWrite = { _, characteristic ->
                Timber.d("Wrote to ${characteristic.uuid}")
            }

            onCharacteristicChanged = { _, characteristic ->
                when (characteristic.value[1]) {
                    0xD1.toByte() -> {
                        val cadenceTemp =
                            characteristic.value[9].toUInt()
                                .shl(8) + characteristic.value[10].toUInt()
                        //log("Cadence ${cadenceVal}")

                        // Validate the value before using it
                        if(cadenceTemp < 300U) {
                            cadenceVal = cadenceTemp
                            sendLocalBroadcast()
                        }
                    }
                    0xD2.toByte() -> {
                        resistanceVal = characteristic.value[3].toUInt()
                        Timber.i("Resistance $resistanceVal")
                        sendLocalBroadcast()
                    }
                    else -> {
                        Timber.i("Value changed on ${characteristic.uuid}: ${characteristic.value.toHexString()}")
                    }
                }

                //if(cadenceVal > 0.toUInt()) {
                    statCount++
                    cadenceTotal += cadenceVal
                    resistanceTotal += resistanceVal

                    // We just started pedaling
                    if(startTimeMillis == 0L) {
                        startTimeMillis = System.currentTimeMillis()
                    }
                /*
                } else {
                    // We stopped pedaling, stop the clock
                    if(startTimeMillis > 0) {
                        elapsedTimeMillis = System.currentTimeMillis() - startTimeMillis
                        startTimeMillis = 0
                    }
                }
                */
            }

            onNotificationsEnabled = { _, characteristic ->
                Timber.i("Enabled notifications on ${characteristic.uuid}")
                notifyingCharacteristics.add(characteristic.uuid)
            }

            onNotificationsDisabled = { _, characteristic ->
                Timber.i("Disabled notifications on ${characteristic.uuid}")
                notifyingCharacteristics.remove(characteristic.uuid)
            }
        }
    }

    private fun sendLocalBroadcast() {

        val intent = Intent("com.prozach.echbt.android.stats")

        if(cadenceVal > cadenceMax) {
            cadenceMax = cadenceVal
        }
        if(resistanceVal > resistanceMax) {
            resistanceMax = resistanceVal
        }

        // Cadence
        intent.putExtra("cadence", cadenceVal.toString())
        intent.putExtra("cadence_max", cadenceMax.toString())
        var cadenceAverage = 0U
        if(statCount > 0.toUInt()) {
            cadenceAverage = (cadenceTotal / statCount)
            intent.putExtra("cadence_avg", cadenceAverage.toString())
        }

        // Resistance
        intent.putExtra("resistance", calcResistance(resistanceVal).toString())
        if(statCount > 0.toUInt()) {
            intent.putExtra("resistance_avg", calcResistance(resistanceTotal / statCount).toString())
        } else {
            intent.putExtra("resistance_avg", calcResistance(resistanceVal).toString())
        }
        intent.putExtra("resistance_max", calcResistance(resistanceMax).toString())


        var currentElapsedTimeMillis = System.currentTimeMillis() - follow.startTimeMillis
        if (timeFormat == TimeFormat.REMAINING){
            currentElapsedTimeMillis = rideDuration - currentElapsedTimeMillis
        }
        /*
        var currentElapsedTimeMillis = elapsedTimeMillis
        if(startTimeMillis > 0) {
            currentElapsedTimeMillis += System.currentTimeMillis() - follow.startTimeMillis
        }
         */
        val minutes = (currentElapsedTimeMillis / 1000 / 60) - 1
        val seconds = currentElapsedTimeMillis / 1000 % 60
        intent.putExtra("time", minutes.toString()+":"+seconds.toString().padStart(2, '0'))

        // Power
        val power = calcPower(cadenceVal, resistanceVal)
        if(power > powerMax) {
            powerMax = power
        }
        intent.putExtra("power", power.toString())

        var avgPower = 0U
        if(statCount > 0.toUInt()) {
            avgPower = calcPower(cadenceTotal / statCount, resistanceTotal / statCount)
        }
        intent.putExtra("power_avg", avgPower.toString())
        intent.putExtra("power_max", powerMax.toString())
        val kcal = ((avgPower.toFloat() / 0.24) * (currentElapsedTimeMillis.toFloat()/1000.0))/1000.0
        intent.putExtra("kcal", kcal.toUInt().toString())

        if(statsFormat == StatsFormat.ECHELON) {
            intent.putExtra("stats_format", "echelon")
        } else if(statsFormat == StatsFormat.PELOTON) {
            intent.putExtra("stats_format", "peloton")
        }

        // Cadence to kph https://github.com/cagnulein/qdomyos-zwift/issues/62
        val distanceKilometers = (cadenceAverage.toFloat() * 0.37497622F) * (currentElapsedTimeMillis.toFloat()/3600000F)
        if(distFormat == DistFormat.MILES) {
            intent.putExtra("dist_format", "miles")
            intent.putExtra("dist", ((distanceKilometers * 0.621371 * 100.0).roundToInt()/100.0).toString())
        } else if(distFormat == DistFormat.KILOMETERS) {
            intent.putExtra("dist_format", "kilometers")
            intent.putExtra("dist", ((distanceKilometers * 100.0).roundToInt()/100.0).toString())
        }

        // Fellow
        intent.putExtra("resistance_range_upper",resistanceRangeUpper.toString())
        intent.putExtra("resistance_range_lower",resistanceRangeLower.toString())
        intent.putExtra("cadence_range_upper",cadenceRangeUpper.toString())
        intent.putExtra("cadence_range_lower",cadenceRangeLower.toString())

        sendBroadcast(intent)
        /* println("sendLocalBroadcast") */
    }

    fun forceResistance(requestResistance:UInt) {
        val noOpData = byteArrayOfInts(0xF0, 0xB1, 0x01, 0x00, 0xA2)
        noOpData[3] = requestResistance.toByte()
        // OP4 is a checksum = SUM of all other OPs & 0xFF
        noOpData[4] = (requestResistance + noOpData[4].toUInt()).toByte()

        for (characteristic in characteristics) {
            when (characteristic.uuid) {
                writeUUID -> {
                    ConnectionManager.writeCharacteristic(
                        device,
                        characteristic,
                        noOpData
                    )
                }
            }
        }
    }

    private fun calcResistance(r: UInt): UInt {
        val peloResistance = intArrayOf(0,3,6,8,10,12,14,16,18,20,25,28,30,32,33,35,37,38,40,42,45,47,48,50,52,55,60,65,70,75,80,85,90)
        return when (statsFormat) {
            StatsFormat.PELOTON -> {
                peloResistance[r.toInt()].toUInt()
                //((0.0116058 * r.toFloat()
                //    .pow(3)) + (-0.568562 * r.toFloat()
                //    .pow(2)) + (10.4126 * r.toFloat()) - 31.4807).toUInt()
            }
            StatsFormat.ECHELON -> {
                r
            }
        }
    }

    private fun calcPower(c: UInt, r: UInt): UInt {
        if (r == 0u || c == 0u) {
            return 0.toUInt()
        }
        return (1.090112f.pow(r.toFloat()) * 1.015343f.pow(c.toFloat()) * 7.228958).toUInt()
    }

    private fun String.hexToBytes() =
        this.chunked(2).map { it.uppercase(Locale.US).toInt(16).toByte() }.toByteArray()

    private fun byteArrayOfInts(vararg ints: Int) =
        ByteArray(ints.size) { pos -> ints[pos].toByte() }
}
