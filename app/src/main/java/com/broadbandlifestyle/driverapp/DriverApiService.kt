package com.broadbandlifestyle.driverapp

import com.broadbandlifestyle.common.* import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface DriverApiService {

    // 1. AUTHENTICATION
    @POST("mobile/driver/logout")
    suspend fun driverLogout(@Body driverIdMap: Map<String, Int>): Response<GenericResponse>

    @POST("mobile/register")
    suspend fun register(@Body data: Map<String, String>): Response<GenericResponse>

    @POST("mobile/forgot-password")
    suspend fun forgotPassword(@Body data: Map<String, String>): Response<GenericResponse>

    // 2. PROFILE & SETTINGS
    @GET("mobile/driver/{driver_id}/profile")
    suspend fun getDriverProfile(@Path("driver_id") driverId: Int): Response<DriverProfileResponse>

    @GET("driver/completed-orders/{driver_id}")
    suspend fun getCompletedOrders(@Path("driver_id") driverId: Int): Response<List<CompletedOrder>>

    @PUT("mobile/driver/update")
    suspend fun updateProfile(@Body request: UpdateProfileRequest): Response<GenericResponse>

    // 3. ORDER MANAGEMENT
    @GET("mobile/driver/active_order/{driver_id}")
    suspend fun getMyCurrentAssignment(@Path("driver_id") id: Int): Response<OrderResponse>

    @GET("mobile/driver/{id}/dispatched_orders")
    suspend fun getDispatchedOrders(@Path("id") id: Int): Response<List<DispatchedOrder>>

    // Consolidated Accept Order logic
    @PUT("mobile/orders/{order_id}/accept")
    suspend fun acceptOrder(
        @Path("order_id") orderId: Int,
        @Body request: AcceptOrderRequest,
        @Header("X-User-Action") userAction: String = "true"
    ): Response<GenericResponse>

    @PUT("mobile/orders/{order_id}/reject")
    suspend fun rejectOrder(
        @Path("order_id") orderId: Int,
        @Body request: AcceptOrderRequest
    ): Response<GenericResponse>

    @PUT("mobile/orders/{orderId}/deliver")
    suspend fun deliverOrder(
        @Path("orderId") orderId: Int,
        @Body request: AcceptOrderRequest
    ): Response<GenericResponse>

    // 4. TELEMETRY (GPS)
    @POST("mobile/driver/update_location")
    suspend fun updateLocation(
        @Body locationRequest: LocationUpdateRequest
    ): Response<GenericResponse>

    // 5. FINANCIALS
    @GET("mobile/driver/{driver_id}/earnings")
    suspend fun getDriverEarnings(@Path("driver_id") driverId: Int): Response<EarningsResponse>

    @GET("mobile/driver/{driver_id}/balance")
    suspend fun getDriverBalance(@Path("driver_id") driverId: Int): Response<BalanceResponse>

    @POST("mobile/driver/withdraw")
    suspend fun requestWithdrawal(@Body request: WithdrawalRequest): Response<WithdrawalResponse>

    @GET("mobile/driver/{driver_id}/withdrawals")
    suspend fun getWithdrawalHistory(@Path("driver_id") driverId: Int): Response<List<WithdrawalHistory>>

    @PUT("mobile/orders/{orderId}/picked_up")
    suspend fun pickedUpOrder(
        @Path("orderId") orderId: Int,
        @Body request: AcceptOrderRequest
    ): Response<GenericResponse>

    @POST("mobile/driver/heartbeat")
    suspend fun sendHeartbeat(@Body heartbeatData: Map<String, Any>): Response<GenericResponse>

    @POST("mobile/driver/toggle_online")
    suspend fun toggleOnline(@Body toggleData: Map<String, Any>): Response<GenericResponse>

    @GET("mobile/driver/status")
    suspend fun getDriverStatus(@Query("driver_id") driverId: Int): Response<DriverStatusResponse>
}