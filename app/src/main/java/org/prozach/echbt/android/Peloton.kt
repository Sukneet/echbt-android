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
import java.io.File


@Serializable
data class User(val username_or_email: String, val password: String)

@Serializable
data class LoginResponse(val status: Int = 0, val error_code: Int = 0, val message: String = "", val session_id: String = "", val user_id: String = "")

@Serializable
data class WorkoutItem(val id: String = "", val status: String = "", val start_time: Long)

@Serializable
data class WorkoutResponse(val data: List<WorkoutItem>? = null)

@Serializable
data class Ride(val id: String = "", val title: String = "")

@Serializable
data class Workout(val ride: Ride? = null)

@Serializable
data class RideResponse(val instructor_cues: List<InstructorCues>? = null)

data class FlowOutput(val startTime:Long = 0L, val instructor_cues: List<InstructorCues>? = null)

class Peloton(storageFile: File) {
    private var auth = LoginResponse(status=-1)
    private var workoutID = ""
    private var rideID = ""
    private var workoutStartTime = 0L

    //private val storageF = FileStorage(storageFile)
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
            })
        }
        install(HttpCookies)//{
        //    storage = storageF
        //}
    }

    fun login(user:String,pass:String){
        runBlocking {
            val response: HttpResponse = client.post("https://api.onepeloton.com/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(User(user, pass))
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

    val latestWorkout: Flow<FlowOutput> = flow {
        while(currentCoroutineContext().isActive) {
            if (auth.status == 0) {
                Timber.d("Checking workoutlist")
                val update = getWorkoutList(1)
                if (update) {
                    Timber.i("New Workout!!")
                    getWorkout()
                    val latestInstructorCues = getRide(rideID)
                    if (latestInstructorCues != null) {
                        emit(FlowOutput(workoutStartTime,latestInstructorCues))
                    } // Emits the result of the request to the flow
                }
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
                workoutStartTime = workout.data[0].start_time
            }
        }
        Timber.d("Workout : $workoutID")
        return (oldWorkout != workoutID)
    }

    private fun getWorkout(){
        runBlocking {
            val response: HttpResponse = client.get("https://api.onepeloton.com/api/workout/$workoutID")
            val workout:Workout = response.body()

            Timber.d("Ride : %s", workout.ride?.id.orEmpty())
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

class FileStorage(val file: File) : CookiesStorage {
    init {
        //require(file.isFile)
        file.createNewFile()
    }

    override suspend fun addCookie(requestUrl: Url, cookie: Cookie) {
        // either append a new cookie at the end, or sort it and put new entries at the correct line (faster search) or create a new file for each domain
        file.appendText("${requestUrl.host}:${cookie.name}:${cookie.value}")
    }

    override fun close() {
        // file is already closed after write
    }

    override suspend fun get(requestUrl: Url): List<Cookie> {
        return file.readLines().mapNotNull {
                val (host, name, value) = it.split(":")
                if (host == requestUrl.host) {
                    Cookie(name = name, value = value)
                } else {
                    null
                }
            }
    }
}