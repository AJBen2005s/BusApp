package com.example.myapplication

import com.example.myapplication.model.BusResponse
import retrofit2.http.GET

interface BusApiService {
    @GET("hrmbuses")
    suspend fun getBuses(): BusResponse
}