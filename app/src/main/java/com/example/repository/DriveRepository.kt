package com.example.repository

import android.content.Context
import com.example.BuildConfig
import com.example.model.DriveTrack
import com.example.model.Track
import com.example.service.DriveApiService
import com.squareup.moshi.JsonReader
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Buffer
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

val PROXY_BASE_URL: String = BuildConfig.WAVIFY_PROXY_BASE_URL
val DATABASE_URL: String = "${BuildConfig.GOOGLE_DRIVE_BASE_URL}uc?export=download&id=${BuildConfig.GOOGLE_DRIVE_DATABASE_FILE_ID}"

class DriveRepository(context: Context) {
    private val okHttpClient = cachedOkHttpClient(context)

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val trackListAdapter = moshi.adapter<List<DriveTrack>>(
        Types.newParameterizedType(List::class.java, DriveTrack::class.java)
    )

    private val retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.GOOGLE_DRIVE_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val apiService = retrofit.create(DriveApiService::class.java)

    suspend fun fetchTracks(): Result<List<Track>> = withContext(Dispatchers.IO) {
        try {
            val driveTracks = parseDatabaseJson(apiService.getTracks().string())
            Result.success(driveTracks.map { it.toTrack() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseDatabaseJson(json: String): List<DriveTrack> {
        val reader = JsonReader.of(Buffer().writeUtf8(json))
        return when (reader.peek()) {
            JsonReader.Token.BEGIN_ARRAY -> trackListAdapter.fromJson(reader).orEmpty()
            JsonReader.Token.BEGIN_OBJECT -> readTracksObject(reader)
            else -> emptyList()
        }
    }

    private fun readTracksObject(reader: JsonReader): List<DriveTrack> {
        var tracks = emptyList<DriveTrack>()
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "tracks" -> tracks = trackListAdapter.fromJson(reader).orEmpty()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return tracks
    }
}
