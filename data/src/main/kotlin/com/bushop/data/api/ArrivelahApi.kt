package com.bushop.data.api


/**
 * ┌─ ArrivelahApi ───────────────────────────────────┐
 * │  data/ layer · Retrofit API interface            │
 * │                                                   │
 * │  GET /?id={busStopCode} ─→ ArrivelahResponse     │
 * │  Returns next 3 buses per service + metadata     │
 * │  Used by RetrofitBusArrivalDataSource            │
 * └───────────────────────────────────────────────────┘
 */

import retrofit2.http.GET
import retrofit2.http.Query

interface ArrivelahApi {
    @GET("/")
    suspend fun getBusArrivals(
        @Query("id") busStopCode: String,
    ): ArrivelahResponse
}
