package com.broadbandlifestyle.common

import com.google.gson.annotations.SerializedName

// ==================== DRIVER AUTH MODELS ====================
data class GenericResponse(
    val status: String? = null,
    val message: String? = null,
    val error: String? = null
)

data class LoginResponse(
    @SerializedName("token") val token: String?,
    val status: String?,
    @SerializedName("role") val role: String?,
    @SerializedName("driver_id") val driverId: Int?,
    @SerializedName("full_name") val fullName: String?
) {
    val userId: Int? get() = driverId

    // Added for deep debugging
    override fun toString(): String {
        return "LoginResponse(status=$status, role=$role, driverId=$driverId, name=$fullName)"
    }
}

// ==================== DRIVER ORDER MODELS ====================
data class CompletedOrder(
    @SerializedName("order_number") val orderNumber: String?,
    val amount: Double?,
    val date: String?,
    val restaurant: String?
)

data class OrderResponse(
    val id: Int,
    @SerializedName("order_number") val orderNumber: String,
    @SerializedName("delivery_address") val deliveryAddress: String?,
    @SerializedName("restaurant_address") val restaurantAddress: String?,
    @SerializedName("restaurant_name") val restaurantName: String?,
    val status: String,
    val lat: Double?,
    val lng: Double?,
    @SerializedName("res_lat") val resLat: Double?,
    @SerializedName("res_lng") val resLng: Double?,
    @SerializedName("is_offer") val isOffer: Boolean,
    @SerializedName("remaining_seconds") val remainingSeconds: Int?
)

data class DispatchedOrder(
    val id: Int,
    @SerializedName("order_number") val orderNumber: String,
    val status: String,
    @SerializedName("final_amount") val finalAmount: Double?,
    @SerializedName("delivery_address") val deliveryAddress: String?,
    @SerializedName("restaurant_name") val restaurantName: String,
    @SerializedName("restaurant_phone") val restaurantPhone: String? = null,
    @SerializedName("items_summary") val itemsSummary: String? = null,
    val lat: Double? = null,
    val lng: Double? = null
)

// ==================== DRIVER ACTION MODELS ====================
data class AcceptOrderRequest(
    @SerializedName("driver_id") val driverId: Int
)

data class UpdateProfileRequest(
    @SerializedName("driver_id") val driverId: Int,
    val phone: String? = null,
    @SerializedName("is_available") val isAvailable: Boolean? = null
)

data class LocationUpdateRequest(
    @SerializedName("driver_id") val driverId: Int,
    val latitude: Double,
    val longitude: Double
)

// ==================== EARNINGS MODELS ====================
data class DeliveryEarning(
    val id: Int,
    @SerializedName("order_id") val orderId: Int,
    @SerializedName("delivery_fee") val deliveryFee: Double,
    @SerializedName("total_earned") val totalEarned: Double,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("order_number") val orderNumber: String?,
    @SerializedName("delivery_address") val deliveryAddress: String?,
    @SerializedName("restaurant_name") val restaurantName: String?
)

data class WeeklyEarning(
    val year: Int,
    val week: Int,
    @SerializedName("week_start") val weekStart: String?,
    @SerializedName("week_end") val weekEnd: String?,
    @SerializedName("delivery_count") val deliveryCount: Int,
    @SerializedName("week_total") val weekTotal: Double,
    @SerializedName("total_delivery_fees") val totalDeliveryFees: Double,
    val deliveries: List<DeliveryEarning>
)

data class EarningsResponse(
    @SerializedName("current_balance") val currentBalance: Double,
    @SerializedName("total_earnings_all_time") val totalEarningsAllTime: Double,
    @SerializedName("last_7_days") val last7Days: Double,
    @SerializedName("last_30_days") val last30Days: Double,
    @SerializedName("deliveries_7_days") val deliveries7Days: Int,
    @SerializedName("deliveries_30_days") val deliveries30Days: Int,
    @SerializedName("weekly_earnings") val weeklyEarnings: List<WeeklyEarning>
)

// ==================== DRIVER PROFILE MODELS ====================
// ==================== DRIVER PROFILE MODELS ====================
data class DriverProfileResponse(
    @SerializedName("full_name") val fullName: String,
    @SerializedName("completed_deliveries") val completedDeliveries: Int,
    val rating: Double,
    @SerializedName("is_available") val isAvailable: Boolean,
    @SerializedName("wallet_balance") val walletBalance: Double,
    @SerializedName("phone") val phone: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("profile_image") val profileImage: String? = null,
    @SerializedName("license_number") val licenseNumber: String? = null,
    @SerializedName("vehicle_type") val vehicleType: String? = null,
    @SerializedName("acceptance_rate") val acceptanceRate: Double? = null
)

// ==================== WITHDRAWAL & BALANCE MODELS ====================
data class BalanceResponse(
    @SerializedName("current_balance") val currentBalance: Double,
    @SerializedName("available_balance") val availableBalance: Double,
    @SerializedName("pending_withdrawals") val pendingWithdrawals: Int,
    @SerializedName("pending_amount") val pendingAmount: Double,
    @SerializedName("last_withdrawal") val lastWithdrawal: String?,
    @SerializedName("is_thursday") val isThursday: Boolean,
    @SerializedName("instant_fee") val instantFee: InstantFee,
    @SerializedName("min_withdrawal") val minWithdrawal: Double,
    @SerializedName("max_withdrawal") val maxWithdrawal: Double
)

data class InstantFee(
    @SerializedName("fee_amount") val feeAmount: Double,
    @SerializedName("fee_percentage") val feePercentage: Double,
    @SerializedName("min_fee") val minFee: Double,
    @SerializedName("max_fee") val maxFee: Double
)

data class WithdrawalRequest(
    @SerializedName("driver_id") val driverId: Int,
    val amount: Double,
    @SerializedName("withdrawal_type") val withdrawalType: String,
    @SerializedName("payment_method") val paymentMethod: String,
    @SerializedName("account_details") val accountDetails: String? = null
)

data class WithdrawalResponse(
    val status: String,
    @SerializedName("withdrawal_id") val withdrawalId: Int,
    val amount: Double,
    val fee: Double,
    @SerializedName("net_amount") val netAmount: Double,
    val message: String
)

data class WithdrawalHistory(
    val id: Int,
    val amount: Double,
    @SerializedName("withdrawal_type") val withdrawalType: String,
    val fee: Double,
    @SerializedName("net_amount") val netAmount: Double,
    val status: String,
    @SerializedName("requested_at") val requestedAt: String?,
    @SerializedName("processed_at") val processedAt: String?,
    @SerializedName("completed_at") val completedAt: String?
)

// ==================== CUSTOMER & SHOP MODELS ====================

data class Product(
    val id: Int,
    val name: String,
    val description: String?, // Changed to nullable to prevent crashes if DB description is empty
    val price: Double,
    @SerializedName("image_url") val imageUrl: String?,
    @SerializedName("category_id") val categoryId: Int = 0 // Default to 0 if missing
)

data class CartItem(
    @SerializedName("product_id") val product_id: Int,
    val quantity: Int
)

data class CreateOrderRequest(
    @SerializedName("user_id") val user_id: Int,
    @SerializedName("delivery_address") val delivery_address: String,
    @SerializedName("latitude") val latitude: Double?,
    @SerializedName("longitude") val longitude: Double?,
    @SerializedName("instructions") val instructions: String,
    @SerializedName("order_type") val order_type: String = "delivery",
    @SerializedName("cart") val cart: List<CartItem>
)

// Add to Models.kt with your other data classes

data class CartCalculationResponse(
    val success: Boolean,
    val restaurant: RestaurantInfo? = null,
    val items: List<CartItemInfo>? = null,
    val subtotal: Double,
    @SerializedName("delivery_fee") val deliveryFee: Double,
    @SerializedName("platform_fee") val platformFee: Double,
    val total: Double,
    @SerializedName("meets_minimum") val meetsMinimum: Boolean,
    val message: String? = null
)

data class RestaurantInfo(
    val id: Int,
    val name: String,
    @SerializedName("min_order_amount") val minOrderAmount: Double
)

data class CartItemInfo(
    @SerializedName("product_id") val productId: Int,
    val name: String,
    val quantity: Int,
    val price: Double,
    val subtotal: Double
)

data class CartCalculationRequest(
    val cart: List<CartItem>,
    @SerializedName("order_type") val orderType: String = "delivery"
)

data class DriverStatusResponse(
    val is_online: Boolean,
    val is_available: Boolean,
    val is_manual_offline: Boolean,
    val last_heartbeat: String?,
    val minutes_since_heartbeat: Int,
    val current_location: LocationData?,
    val session: SessionData?,
    val has_active_order: Boolean,
    val has_pending_offer: Boolean
)

data class LocationData(
    val lat: Double,
    val lng: Double
)

data class SessionData(
    val active: Boolean,
    val start_time: String?,
    val online_minutes: Int
)