package com.max.pandaai.ai

import com.max.pandaai.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

private const val OPENAI_BASE_URL = "https://api.openai.com/"

// Retrofits OpenAI's Chat Completions endpoint.
interface OpenAIApi {
    @Headers("Content-Type: application/json")
    @POST("v1/chat/completions")
    suspend fun createChatCompletion(@Body request: ChatCompletionRequest): ChatCompletionResponse
}

data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessageDto>,
    val temperature: Double = 0.7
)

data class ChatMessageDto(
    val role: String,
    val content: String
)

data class ChatCompletionResponse(
    val choices: List<ChatChoice>
)

data class ChatChoice(
    val index: Int,
    val message: ChatMessageDto
)

// Wraps network calls so the rest of the app can stay coroutine-friendly.
class AIService {

    // Insert your real API key before release. Prefer BuildConfig.OPENAI_API_KEY supplied via gradle.
    private val apiKey: String

    init {
        val envKey = System.getenv("OPENAI_API_KEY")
        apiKey = when {
            BuildConfig.OPENAI_API_KEY.isNotBlank() -> BuildConfig.OPENAI_API_KEY
            !envKey.isNullOrBlank() -> envKey
            else -> PLACEHOLDER_KEY
        }
    }

    private val api: OpenAIApi by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.BASIC
            }
        }
        val authInterceptor = Interceptor { chain ->
            val original = chain.request()
            val builder = original.newBuilder()
                .header("Authorization", "Bearer $apiKey")
            chain.proceed(builder.build())
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor(authInterceptor)
            .build()

        Retrofit.Builder()
            .baseUrl(OPENAI_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenAIApi::class.java)
    }

    suspend fun requestResponse(
        userPrompt: String,
        assistantName: String
    ): String = withContext(Dispatchers.IO) {
        if (apiKey == PLACEHOLDER_KEY) {
            return@withContext "Please add your AI API key in AIService.kt or via BuildConfig.OPENAI_API_KEY before using cloud responses."
        }
        return@withContext try {
            val response = api.createChatCompletion(
                ChatCompletionRequest(
                    model = "gpt-3.5-turbo",
                    messages = listOf(
                        ChatMessageDto("system", "You are $assistantName, a friendly helpful voice assistant created by Max."),
                        ChatMessageDto("user", userPrompt)
                    ),
                    temperature = 0.7
                )
            )
            response.choices.firstOrNull()?.message?.content?.trim()
                ?: "I'm here, but I couldn't understand that. Could you rephrase?"
        } catch (ex: Exception) {
            "I'm having trouble reaching the AI service right now. Let's try again soon."
        }
    }

    companion object {
        private const val PLACEHOLDER_KEY = "REPLACE_WITH_REAL_KEY"
    }
}
