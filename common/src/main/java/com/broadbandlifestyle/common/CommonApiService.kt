package com.broadbandlifestyle.common

import retrofit2.Response
import retrofit2.http.*

interface CommonApiService {

    @POST("login_mobile")
    suspend fun login(@Body credentials: Map<String, String>): Response<LoginResponse>
}