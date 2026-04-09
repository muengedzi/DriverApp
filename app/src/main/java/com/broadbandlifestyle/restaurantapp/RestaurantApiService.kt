package com.broadbandlifestyle.restaurantapp

import com.broadbandlifestyle.common.*
import retrofit2.Response
import retrofit2.http.*

interface RestaurantApiService {

    @GET("restaurant/{restaurant_id}/orders")
    suspend fun getOrders(@Path("restaurant_id") restaurantId: Int): Response<RestaurantOrdersResponse>

    @GET("restaurant/orders/{order_id}/details")
    suspend fun getOrderDetails(
        @Path("order_id") orderId: Int,
        @Query("restaurant_id") restaurantId: Int
    ): Response<OrderDetailResponse>

    @PUT("restaurant/orders/{order_id}/status")
    suspend fun updateOrderStatus(
        @Path("order_id") orderId: Int,
        @Body request: UpdateOrderStatusRequest
    ): Response<GenericResponse>

    @GET("restaurant/{restaurant_id}/stats")
    suspend fun getStats(@Path("restaurant_id") restaurantId: Int): Response<RestaurantStats>
}