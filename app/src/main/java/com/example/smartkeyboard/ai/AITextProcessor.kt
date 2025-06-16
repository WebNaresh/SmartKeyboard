package com.example.smartkeyboard.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * AI Text Processor for handling OpenAI API calls
 */
class AITextProcessor(private val apiKey: String) {
    
    private val openAIService: OpenAIService
    
    init {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
        
        val retrofit = Retrofit.Builder()
            .baseUrl(OpenAIService.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        
        openAIService = retrofit.create(OpenAIService::class.java)
    }
    
    /**
     * Enhance text with AI based on mood and context
     */
    suspend fun enhanceText(
        text: String,
        mood: MoodType,
        context: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (text.isBlank()) {
                return@withContext Result.success(text)
            }
            
            val systemPrompt = buildSystemPrompt(mood, context)
            val userPrompt = buildUserPrompt(text, mood)
            
            val request = ChatCompletionRequest(
                model = "gpt-3.5-turbo",
                messages = listOf(
                    ChatMessage("system", systemPrompt),
                    ChatMessage("user", userPrompt)
                ),
                maxTokens = 150,
                temperature = 0.7
            )
            
            val response = openAIService.createChatCompletion(
                authorization = "${OpenAIService.BEARER_PREFIX}$apiKey",
                request = request
            )
            
            if (response.isSuccessful) {
                val chatResponse = response.body()
                val enhancedText = chatResponse?.choices?.firstOrNull()?.message?.content?.trim()
                
                if (enhancedText.isNullOrBlank()) {
                    Log.w(TAG, "Empty response from AI")
                    Result.success(text) // Return original text if AI response is empty
                } else {
                    // Clean up any quotes that might be in the response
                    val cleanedText = cleanResponseText(enhancedText)
                    Log.d(TAG, "AI enhanced text: $cleanedText")
                    Result.success(cleanedText)
                }
            } else {
                val errorMsg = "AI API error: ${response.code()} - ${response.message()}"
                Log.e(TAG, errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error enhancing text with AI", e)
            Result.failure(e)
        }
    }
    
    /**
     * Correct grammar and spelling
     */
    suspend fun correctGrammar(text: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (text.isBlank()) {
                return@withContext Result.success(text)
            }
            
            val systemPrompt = """
                You are a grammar and spelling correction assistant.
                Correct any grammar, spelling, or punctuation errors in the given text.
                Maintain the original tone and meaning.
                If the text is already correct, return it unchanged.
                IMPORTANT: Return ONLY the corrected text without quotes, explanations, or additional formatting.
                Do not wrap the response in quotation marks.
            """.trimIndent()
            
            val request = ChatCompletionRequest(
                model = "gpt-3.5-turbo",
                messages = listOf(
                    ChatMessage("system", systemPrompt),
                    ChatMessage("user", text)
                ),
                maxTokens = 150,
                temperature = 0.3 // Lower temperature for more consistent corrections
            )
            
            val response = openAIService.createChatCompletion(
                authorization = "${OpenAIService.BEARER_PREFIX}$apiKey",
                request = request
            )
            
            if (response.isSuccessful) {
                val chatResponse = response.body()
                val correctedText = chatResponse?.choices?.firstOrNull()?.message?.content?.trim()
                
                if (correctedText.isNullOrBlank()) {
                    Result.success(text)
                } else {
                    // Clean up any quotes that might be in the response
                    val cleanedText = cleanResponseText(correctedText)
                    Result.success(cleanedText)
                }
            } else {
                val errorMsg = "Grammar correction error: ${response.code()}"
                Log.e(TAG, errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error correcting grammar", e)
            Result.failure(e)
        }
    }
    
    private fun buildSystemPrompt(mood: MoodType, context: String): String {
        val basePrompt = """
            You are a text enhancement assistant for a smart keyboard.
            Your job is to improve the user's text while maintaining their intended meaning.
            Keep responses concise and natural.
            IMPORTANT: Return ONLY the enhanced text without quotes, explanations, or additional formatting.
            Do not wrap the response in quotation marks.
        """.trimIndent()
        
        val moodPrompt = when (mood) {
            MoodType.RESPECTFUL -> """
                Make the text more polite, respectful, and professional.
                Use courteous language and formal tone where appropriate.
            """.trimIndent()
            
            MoodType.FUNNY -> """
                Make the text more humorous and playful while keeping it appropriate.
                Add light humor, wordplay, or fun expressions where suitable.
            """.trimIndent()
            
            MoodType.ANGRY -> """
                Make the text more assertive and emphatic while keeping it professional.
                Use strong but appropriate language to convey determination.
            """.trimIndent()
            
            MoodType.NORMAL -> """
                Improve clarity, grammar, and flow while maintaining the original tone.
            """.trimIndent()
        }
        
        return if (context.isNotBlank()) {
            "$basePrompt\n\n$moodPrompt\n\nContext: $context"
        } else {
            "$basePrompt\n\n$moodPrompt"
        }
    }
    
    private fun buildUserPrompt(text: String, mood: MoodType): String {
        return when (mood) {
            MoodType.RESPECTFUL -> "Please make this text more respectful and polite: $text"
            MoodType.FUNNY -> "Please make this text more fun and humorous: $text"
            MoodType.ANGRY -> "Please make this text more assertive and emphatic: $text"
            MoodType.NORMAL -> "Please improve this text: $text"
        }
    }

    /**
     * Clean up AI response text by removing unwanted quotes and formatting
     */
    private fun cleanResponseText(text: String): String {
        var cleaned = text.trim()

        // Remove surrounding quotes if present
        if ((cleaned.startsWith("\"") && cleaned.endsWith("\"")) ||
            (cleaned.startsWith("'") && cleaned.endsWith("'"))) {
            cleaned = cleaned.substring(1, cleaned.length - 1)
        }

        // Remove any leading/trailing quotes that might remain
        cleaned = cleaned.trim('"', '\'', ' ')

        return cleaned
    }
    
    enum class MoodType {
        NORMAL, RESPECTFUL, FUNNY, ANGRY
    }
    
    companion object {
        private const val TAG = "AITextProcessor"
    }
}
