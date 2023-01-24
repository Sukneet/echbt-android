/*
 * Copyright 2022 Punch Through Design LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.prozach.echbt.android

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import timber.log.Timber


@Serializable
data class User(val username_or_email: String, val password: String)

@Serializable
data class LoginResponse(val status: Int = 0, val error_code: Int = 0, val message: String = "", val session_id: String = "", val user_id: String = "")

@Serializable
data class WorkoutItem(val id: String = "", val status: String = "", val start_time: UInt)

@Serializable
data class WorkoutResponse(val data: List<WorkoutItem>? = null)

@Serializable
data class Ride(val id: String = "", val title: String = "")

@Serializable
data class Workout(val ride: Ride? = null)

@Serializable
data class RideResponse(val instructor_cues: List<InstructorCues>? = null)

class Peloton(username: String, password: String) {
    private var auth:LoginResponse
    private var workoutID = ""
    private var rideID = ""

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
            })
        }
        install(HttpCookies)
    }

    init {
        runBlocking {
            val response: HttpResponse = client.post("https://api.onepeloton.com/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(User(username, password))
            }
            auth = response.body()
            //println("Peloton Response:")
            //println(response.bodyAsText())
        }

        if (auth.status == 0){
            Timber.i("Peloton Auth Succeeded")
            //getWorkoutList(1)
        } else {
            Timber.e("Peloton Auth failed")
        }

    }

    val latestWorkout: Flow<List<InstructorCues>> = flow {
        while(auth.status == 0) {
            Timber.d("Checking workoutlist")
            val update = getWorkoutList(1)
            if (update) {
                Timber.i("New Workout!!")
                getWorkout()
                val latestInstructorCues = getRide(rideID)
                if (latestInstructorCues != null) {
                    emit(latestInstructorCues)
                } // Emits the result of the request to the flow
            }

            delay(10000) // Suspends the coroutine for some time
        }
    }


    private fun getWorkoutList(limit:Int): Boolean {
        val oldWorkout = workoutID
        runBlocking {
            val response: HttpResponse = client.get("https://api.onepeloton.com/api/user/" + auth.user_id + "/workouts?sort_by=-created&page=0&limit=$limit")
            val workout:WorkoutResponse = response.body()
            //println(response.bodyAsText())

            if (workout.data?.get(0)?.status == "IN_PROGRESS"){
                workoutID = workout.data[0].id
            }
            workoutID = workout.data?.get(0)?.id.orEmpty()
        }
        Timber.d("Workout : $workoutID")
        return (oldWorkout != workoutID)
    }

    private fun getWorkout(){
        runBlocking {
            val response: HttpResponse = client.get("https://api.onepeloton.com/api/workout/$workoutID")
            val workout:Workout = response.body()

            Timber.d("Ride : " + workout.ride?.id.orEmpty())
            rideID = workout.ride?.id.orEmpty()
        }
    }

    private fun getRide(id:String): List<InstructorCues>? {
        var ride:RideResponse
        runBlocking {
            val response: HttpResponse = client.get("https://api.onepeloton.com/api/ride/$id/details?stream_source=multichannel")
            ride = response.body()
        }
        return ride.instructor_cues
    }
}