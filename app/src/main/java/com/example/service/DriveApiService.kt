package com.example.service

import com.example.BuildConfig
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Query

interface DriveApiService {
    @GET("uc")
    suspend fun getTracks(
        @Query("export") export: String = "download",
        @Query("id") id: String = BuildConfig.GOOGLE_DRIVE_DATABASE_FILE_ID
    ): ResponseBody
}
