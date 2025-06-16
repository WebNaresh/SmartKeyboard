package com.example.smartkeyboard.ai

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Retrofit interface for OpenAI API
 */
interface OpenAIService {
    
    @POST("v1/chat/completions")
    suspend fun createChatCompletion(
        @Header("Authorization") authorization: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Body request: ChatCompletionRequest
    ): Response<ChatCompletionResponse>
    
    companion object {
        const val BASE_URL = "https://api.openai.com/"
        const val BEARER_PREFIX = "Bearer "
    }
}
