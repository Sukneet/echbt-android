package org.prozach.echbt.android

import android.bluetooth.BluetoothGattCharacteristic
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import org.prozach.echbt.android.ble.ConnectionManager
import timber.log.Timber
import kotlin.math.pow

@Serializable
data class Offset(val start: Int, val end: Int)

@Serializable
data class Resistance(val upper: Int, val lower: Int)

@Serializable
data class Cadence(val upper: Int, val lower: Int)

@Serializable
data class InstructorCues(val offsets: Offset? = null, val resistance_range: Resistance? = null, val cadence_range: Cadence? = null)

class FollowRide () {
    private var cues = listOf<InstructorCues>()
    private var stillRunning = false
    private var currentCue = 0
    var startTimeMillis: Long = 0

    fun update(newCues: List<InstructorCues>){
        cues = newCues
        startTimeMillis = System.currentTimeMillis()
        Timber.d("new cues at $startTimeMillis")
        stillRunning = true
    }

    val resistance: Flow<UInt> = flow {
        while (true){
            if (!stillRunning || cues.isEmpty() || currentCue >= cues.size) {
                delay (1000)
            } else {
                val elapsedTime = (System.currentTimeMillis() - startTimeMillis)/ 1000
                Timber.d("Elapsed Time = $elapsedTime")
                Timber.d("Current cue start: " + cues[currentCue].offsets!!.start)
                Timber.d ("Current cue end: " + cues[currentCue].offsets!!.end)

                if (cues[currentCue].offsets!!.start > elapsedTime){ //we're in the wrong place
                    if (currentCue > 0) { // go to earlier cue
                        if (elapsedTime > cues[currentCue-1].offsets!!.start){
                            val waitTime = (cues[currentCue].offsets!!.start - elapsedTime)*1000 - 500 // start 0.5s early
                            Timber.d("<<We're early. Waiting $waitTime")
                            delay(waitTime)
                        } else {
                            Timber.d("<<We're in the wrong place. Going to earlier cue")
                            currentCue--
                        }
                    } else { //should never get here
                        Timber.d("We're in the intro, wait 1s")
                        delay(1000)
                    }
                } else if (cues[currentCue].offsets!!.end < elapsedTime) { //we're in the wrong place
                    if (currentCue < cues.size) { // go to later cue
                        Timber.d(">>We're in the wrong place. Going to later cue")
                        currentCue++
                    } else { // we're at the end...
                        Timber.i("No more cues!! All done!")
                        stillRunning = false
                    }
                } else {
                    Timber.d("We're in the right place!")
                    val targetResistance = cues[currentCue].resistance_range!!.upper.toUInt()
                    Timber.d("Setting Peloton Resistance to $targetResistance")
                    emit(calcResistance(targetResistance))

                    val period = cues[currentCue].offsets!!.end - elapsedTime // cues[currentCue].offsets!!.start
                    Timber.d("Waiting $period s for next cue")
                    delay(period.toLong()*1000)
                    currentCue++
                }
            }
        }
    }

    private fun calcResistance(r: UInt): UInt {
        // https://www.desmos.com/calculator/scqq2p0ayv
        // used http://www.amandasok.com/2021/02/my-peloton-bike-dupe-how-to-use-echelon.html
        // y = ax^3 + bx^2 + cx + d
        return (-0.0000333116 * r.toFloat().pow(3) + //a
                0.00260373 * r.toFloat().pow(2) +    //b
                0.392107 * r.toFloat() +                //c
                -0.0563918).toUInt()                    //d
    }
}