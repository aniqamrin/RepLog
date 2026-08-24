package com.replog.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val name: String
)

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class AuthResponse(
    val token: String = "",
    @SerialName("refresh_token") val refreshToken: String? = null,
    val user: RemoteUser? = null
)

@Serializable
data class RemoteUser(val id: String = "", val email: String = "", val name: String = "")

@Serializable
data class SyncBatchRequest(
    val device_id: String,
    val food_entries: List<SyncFoodEntry> = emptyList(),
    val workouts: List<SyncWorkout> = emptyList(),
    val weight_entries: List<SyncWeight> = emptyList()
)

@Serializable
data class SyncFoodEntry(
    val client_id: Long,
    val date: String,
    val name: String,
    val meal_type: String,
    val calories: Int,
    val protein_g: Double,
    val carbs_g: Double,
    val fat_g: Double,
    val fiber_g: Double
)

@Serializable
data class SyncWorkout(
    val client_id: Long,
    val date: String,
    val name: String,
    val type: String,
    val duration_min: Int,
    val notes: String? = null,
    val volume_kg: Double = 0.0
)

@Serializable
data class SyncWeight(
    val date: String,
    val weight_kg: Double,
    val body_fat_pct: Double? = null
)

@Serializable
data class SyncAck(val accepted: Int = 0, val server_time: String = "")

@Serializable
data class ChatMessageDto(val role: String, val content: String)

@Serializable
data class ChatRequest(
    val messages: List<ChatMessageDto>,
    val context: String,
    val mode: String = "coach"
)

@Serializable
data class ChatResponse(val reply: String = "", val model: String = "")

@Serializable
data class HealthResponse(val status: String = "unknown", val ai_configured: Boolean = false)

@Serializable
data class VisionRequest(val image_base64: String, val prompt: String)

@Serializable
data class VisionResponseDto(val raw: String = "", val model: String = "")

@Serializable
data class FriendDto(
    val id: Long,
    val email: String,
    val name: String,
    val trained_today: Boolean = false,
    val volume_today: Double = 0.0,
    val week_volume: Double = 0.0
)

@Serializable
data class FriendsResponse(
    val friends: List<FriendDto> = emptyList(),
    val incoming: List<FriendDto> = emptyList(),
    val outgoing: List<FriendDto> = emptyList()
)

@Serializable
data class FriendRequestRequest(val email: String)

@Serializable
data class FriendRespondRequest(val user_id: Long, val action: String)

@Serializable
data class FriendStatusResponse(val status: String = "")

@Serializable
data class LeaderboardEntryDto(
    val id: Long,
    val name: String,
    val email: String,
    val is_me: Boolean = false,
    val trained_today: Boolean = false,
    val volume_today: Double = 0.0,
    val week_volume: Double = 0.0
)

@Serializable
data class LeaderboardResponse(val entries: List<LeaderboardEntryDto> = emptyList())

interface ApiService {

    @POST("api/auth/register")
    suspend fun register(@Body body: RegisterRequest): AuthResponse

    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): AuthResponse

    @GET("api/health")
    suspend fun health(): HealthResponse

    @POST("api/sync")
    suspend fun sync(@Header("Authorization") token: String, @Body body: SyncBatchRequest): SyncAck

    @POST("api/ai/chat")
    suspend fun chat(@Body body: ChatRequest): ChatResponse

    @POST("api/ai/vision")
    suspend fun analyzeFood(@Body body: VisionRequest): VisionResponseDto

    @GET("api/friends")
    suspend fun friends(
        @Header("Authorization") token: String,
        @Query("date") date: String,
        @Query("week_start") weekStart: String
    ): FriendsResponse

    @POST("api/friends/request")
    suspend fun friendRequest(
        @Header("Authorization") token: String,
        @Body body: FriendRequestRequest
    ): FriendStatusResponse

    @POST("api/friends/respond")
    suspend fun friendRespond(
        @Header("Authorization") token: String,
        @Body body: FriendRespondRequest
    ): FriendStatusResponse

    @POST("api/friends/remove")
    suspend fun friendRemove(
        @Header("Authorization") token: String,
        @Body body: FriendRespondRequest
    ): FriendStatusResponse

    @GET("api/friends/leaderboard")
    suspend fun leaderboard(
        @Header("Authorization") token: String,
        @Query("date") date: String,
        @Query("week_start") weekStart: String
    ): LeaderboardResponse

    companion object {
        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
            coerceInputValues = true
        }

        fun create(baseUrl: String, tokenProvider: () -> String?): ApiService {
            val client = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val token = tokenProvider()
                    val req = if (token != null) {
                        chain.request().newBuilder().header("Authorization", "Bearer $token").build()
                    } else chain.request()
                    chain.proceed(req)
                }
                .build()
            return Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(ApiService::class.java)
        }
    }
}
