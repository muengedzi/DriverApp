package com.broadbandlifestyle.customerapp

import com.broadbandlifestyle.common.*
import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.*

// ONLY classes that DO NOT exist in your common models.kt go here
data class Category(
    val id: Int,
    val name: String,
    val image_url: String?
)

data class CustomerOrderResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String?,
    @SerializedName("order_id") val order_id: Int?,
    @SerializedName("order_number") val order_number: String?,
    @SerializedName("final_amount") val final_amount: Double?
)

data class TrackingData(
    val status: String,
    val driver_lat: Double?,
    val driver_lng: Double?,
    val driver_name: String?,
    val driver_phone: String?
)

// ==================== INTERFACE ====================
interface CustomerApiService {

    @POST("login_mobile")
    suspend fun login(@Body credentials: Map<String, String>): Response<LoginResponse>

    @POST("mobile/customer/register")
    suspend fun register(@Body details: Map<String, String>): Response<GenericResponse>

    @GET("mobile/categories")
    suspend fun getCategories(): Response<List<Category>>

    @GET("mobile/products")
    suspend fun getProducts(
        @Query("category_id") categoryId: Int? = null,
        @Query("search") query: String? = null
    ): Response<List<Product>>

    @POST("api/checkout")
    suspend fun placeOrder(@Body request: CreateOrderRequest): Response<CustomerOrderResponse>

    @GET("mobile/customer/{customer_id}/orders")
    suspend fun getOrderHistory(@Path("customer_id") customerId: Int): Response<List<CompletedOrder>>

    @GET("mobile/orders/{order_id}/tracking")
    suspend fun getOrderTracking(@Path("order_id") orderId: Int): Response<TrackingData>

    @GET("mobile/customer/{customer_id}/profile")
    suspend fun getCustomerProfile(@Path("customer_id") customerId: Int): Response<DriverProfileResponse>

    @POST("api/calculate_cart")
    suspend fun calculateCart(@Body request: CartCalculationRequest): Response<CartCalculationResponse>
}