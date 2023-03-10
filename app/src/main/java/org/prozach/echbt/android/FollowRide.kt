package org.prozach.echbt.android

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.serialization.Serializable
import timber.log.Timber

@Serializable
data class Offset(val start: Int, val end: Int)

@Serializable
data class Resistance(val upper: Int, val lower: Int)

@Serializable
data class Cadence(val upper: Int, val lower: Int)

@Serializable
data class InstructorCues(val offsets: Offset? = null, val resistance_range: Resistance? = null, val cadence_range: Cadence? = null)

data class FollowOut (
    var resistanceRangeUpper:Int = 0,
    var resistanceRangeLower:Int = 0,
    var cadenceRangeUpper:Int = 0,
    var cadenceRangeLower:Int = 0,
    var resistance:UInt = 0u
)

class FollowRide {
    private var cues = listOf<InstructorCues>()
    private var stillRunning = false
    private var currentCue = 0
    var startTimeMillis: Long = 0

    fun update(startTime: Long, newCues: List<InstructorCues>){
        cues = newCues
        startTimeMillis = startTime *1000//System.currentTimeMillis()
        Timber.d("new cues at $startTimeMillis")
        currentCue = 0
        stillRunning = true
    }

    val resistance: Flow<FollowOut> = flow {
        var updateResistance = true
        val output = FollowOut(0,0,0,0,0u)
        while (currentCoroutineContext().isActive){
            if (!stillRunning || cues.isEmpty() || currentCue >= cues.size) {
                delay (1000)
            } else {
                val elapsedTime = (System.currentTimeMillis() - startTimeMillis)/ 1000
                Timber.d("Elapsed Time = $elapsedTime")
                Timber.d("Current cue start: %s", cues[currentCue].offsets!!.start)
                Timber.d ("Current cue end: %s", cues[currentCue].offsets!!.end)

                if (cues[currentCue].offsets!!.start > elapsedTime){ //we're in the wrong place
                    if (currentCue > 0) { // go to earlier cue
                        if (elapsedTime > cues[currentCue-1].offsets!!.start){
                            val waitTime = (cues[currentCue].offsets!!.start - elapsedTime)*1000 - 500 // start 0.5s early
                            Timber.d("<<We're early. Waiting $waitTime")
                            delay(waitTime)
                        } else {
                            Timber.d("<<We're in the wrong place. Going to earlier cue")
                            currentCue--
                            updateResistance = true
                        }
                    } else { //should never get here
                        Timber.d("We're in the intro, wait 1s")
                        delay(1000)
                    }
                } else if (cues[currentCue].offsets!!.end < elapsedTime) { //we're in the wrong place
                    if (currentCue < cues.size) { // go to later cue
                        Timber.d(">>We're in the wrong place. Going to later cue")
                        currentCue++
                        updateResistance = true
                    } else { // we're at the end...
                        Timber.i("No more cues!! All done!")
                        output.resistance = 32u
                        emit(output)
                        stillRunning = false
                    }
                } else {
                    //Timber.d("We're in the right place!")
                    if (updateResistance) {
                        val targetResistance = cues[currentCue].resistance_range!!.upper
                        Timber.d("Setting Peloton Resistance to $targetResistance")
                        output.resistanceRangeUpper = cues[currentCue].resistance_range!!.upper
                        output.resistanceRangeLower = cues[currentCue].resistance_range!!.lower
                        output.cadenceRangeUpper =cues[currentCue].cadence_range!!.upper
                        output.cadenceRangeLower =cues[currentCue].cadence_range!!.lower
                        output.resistance = calcResistance(targetResistance)
                        emit(output)
                        updateResistance = false
                    }
                    delay(1000)

                    // this is more efficient but doesn't allow us to dynamically change time
                    //val period = cues[currentCue].offsets!!.end - elapsedTime
                    //Timber.d("Waiting $period s for next cue")
                    //delay(period * 1000)
                    //currentCue++
                }
            }
        }
    }

    private fun calcResistance(r: Int): UInt {
        val echResistance = intArrayOf(0,1,1,1,1,1,2,2,3,3,4,4,5,5,6,6,7,7,8,8,9,9,9,9,9,10,10,10,11,11,12,12,13,14,14,15,15,16,17,17,18,18,19,19,19,20,20,21,22,22,23,23,24,24,24,25,25,25,25,25,26,26,26,26,26,27,27,27,27,27,28,28,28,28,28,29,29,29,29,29,30,30,30,30,30,31,31,31,31,31,32,32,32,32,32,32,32,32,32,32,32)
        return echResistance[r].toUInt()
        // https://www.desmos.com/calculator/scqq2p0ayv
        // used http://www.amandasok.com/2021/02/my-peloton-bike-dupe-how-to-use-echelon.html
        // y = ax^3 + bx^2 + cx + d
        // return (-0.0000333116 * r.toFloat().pow(3) + //a
        //        0.00260373 * r.toFloat().pow(2) +    //b
        //        0.392107 * r.toFloat() +                //c
        //        -0.0563918).toUInt()                    //d
    }

    fun increaseTime(inc:Int){
        Timber.d("Increase Time")
        startTimeMillis -= inc * 1000
    }

    fun decreaseTime(dec:Int){
        Timber.d("Decrease Time")
        startTimeMillis += dec * 1000
    }
}