package com.example.smartkeyboard.ai

import com.google.gson.annotations.SerializedName

/**
 * Data models for OpenAI API integration
 */

data class ChatCompletionRequest(
    @SerializedName("model")
    val model: String = "gpt-3.5-turbo",
    
    @SerializedName("messages")
    val messages: List<ChatMessage>,
    
    @SerializedName("max_tokens")
    val maxTokens: Int = 150,
    
    @SerializedName("temperature")
    val temperature: Double = 0.7,
    
    @SerializedName("top_p")
    val topP: Double = 1.0,
    
    @SerializedName("frequency_penalty")
    val frequencyPenalty: Double = 0.0,
    
    @SerializedName("presence_penalty")
    val presencePenalty: Double = 0.0
)

data class ChatMessage(
    @SerializedName("role")
    val role: String, // "system", "user", or "assistant"
    
    @SerializedName("content")
    val content: String
)

data class ChatCompletionResponse(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("object")
    val objectType: String,
    
    @SerializedName("created")
    val created: Long,
    
    @SerializedName("model")
    val model: String,
    
    @SerializedName("choices")
    val choices: List<ChatChoice>,
    
    @SerializedName("usage")
    val usage: Usage?
)

data class ChatChoice(
    @SerializedName("index")
    val index: Int,
    
    @SerializedName("message")
    val message: ChatMessage,
    
    @SerializedName("finish_reason")
    val finishReason: String?
)

data class Usage(
    @SerializedName("prompt_tokens")
    val promptTokens: Int,
    
    @SerializedName("completion_tokens")
    val completionTokens: Int,
    
    @SerializedName("total_tokens")
    val totalTokens: Int
)

data class ErrorResponse(
    @SerializedName("error")
    val error: ErrorDetail
)

data class ErrorDetail(
    @SerializedName("message")
    val message: String,
    
    @SerializedName("type")
    val type: String,
    
    @SerializedName("code")
    val code: String?
)
