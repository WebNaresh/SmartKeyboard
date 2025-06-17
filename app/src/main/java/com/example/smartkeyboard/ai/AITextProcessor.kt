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

    private val openAIService: OpenAIService?
    private val isMockMode = apiKey == "mock-key-for-demo"
    
    init {
        if (isMockMode) {
            // Mock mode - no API calls needed
            openAIService = null
            Log.d(TAG, "AITextProcessor initialized in mock mode")
        } else {
            // Real API mode
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
            Log.d(TAG, "AITextProcessor initialized with real API")
        }
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

            // Use mock AI if in demo mode
            if (isMockMode) {
                val enhancedText = generateMockEnhancement(text, mood)
                Log.d(TAG, "Mock AI enhanced text: $enhancedText")
                return@withContext Result.success(enhancedText)
            }

            // Real AI processing
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

            val response = openAIService!!.createChatCompletion(
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

            // Use mock AI if in demo mode
            if (isMockMode) {
                val correctedText = generateMockCorrection(text)
                Log.d(TAG, "Mock AI corrected text: $correctedText")
                return@withContext Result.success(correctedText)
            }

            // Real AI processing
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

            val response = openAIService!!.createChatCompletion(
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

    /**
     * Generate mock AI enhancement for demo purposes
     */
    private fun generateMockEnhancement(text: String, mood: MoodType): String {
        val words = text.trim().split("\\s+".toRegex())

        return when (mood) {
            MoodType.RESPECTFUL -> {
                when {
                    text.contains("hi", ignoreCase = true) && text.contains("want", ignoreCase = true) ->
                        "Hello! I would be grateful if you could share your contact number with me."
                    text.contains("hi", ignoreCase = true) && text.contains("sir", ignoreCase = true) && text.contains("good", ignoreCase = true) ->
                        "Good morning, sir! I hope you're having a wonderful day."
                    text.contains("hi", ignoreCase = true) ->
                        "Hello! How are you doing today?"
                    text.contains("hello", ignoreCase = true) ->
                        "Good day! I hope you're doing well."
                    text.contains("thanks", ignoreCase = true) ->
                        "Thank you very much for your assistance."
                    text.contains("please", ignoreCase = true) ->
                        "I would be most grateful if you could help with: $text"
                    text.contains("need", ignoreCase = true) ->
                        text.replace("need", "would appreciate", ignoreCase = true)
                    text.contains("want", ignoreCase = true) ->
                        text.replace("want", "would like", ignoreCase = true)
                    text.contains("good", ignoreCase = true) ->
                        "Good day to you!"
                    words.size <= 2 -> text.replaceFirstChar { it.uppercase() }
                    else -> "I would like to respectfully say: $text"
                }
            }
            MoodType.FUNNY -> {
                when {
                    text.contains("hi", ignoreCase = true) ->
                        "Hey there! 😄 What's cooking?"
                    text.contains("hello", ignoreCase = true) ->
                        "Well hello there, sunshine! ☀️"
                    text.contains("thanks", ignoreCase = true) ->
                        "Thanks a million! You're awesome! 🎉"
                    text.contains("good", ignoreCase = true) ->
                        text.replace("good", "absolutely fantastic", ignoreCase = true) + " 😊"
                    text.contains("ok", ignoreCase = true) ->
                        "Okey dokey! 👍"
                    words.size <= 2 -> "$text 😄"
                    else -> "$text (and I'm not even kidding!) 😉"
                }
            }
            MoodType.ANGRY -> {
                when {
                    text.contains("please", ignoreCase = true) ->
                        text.replace("please", "I NEED you to", ignoreCase = true)
                    text.contains("want", ignoreCase = true) ->
                        text.replace("want", "DEMAND", ignoreCase = true)
                    text.contains("need", ignoreCase = true) ->
                        text.replace("need", "REQUIRE", ignoreCase = true).uppercase()
                    words.size <= 2 -> text.uppercase() + "!"
                    else -> "Listen up: $text!"
                }
            }
            MoodType.NORMAL -> {
                when {
                    text.contains("hi", ignoreCase = true) && text.contains("want", ignoreCase = true) ->
                        "Hi there! Could I get your number please?"
                    text.contains("u", ignoreCase = true) ->
                        text.replace("u", "you", ignoreCase = true)
                    text.contains("ur", ignoreCase = true) ->
                        text.replace("ur", "your", ignoreCase = true)
                    text.contains("thx", ignoreCase = true) ->
                        text.replace("thx", "thanks", ignoreCase = true)
                    !text.endsWith(".") && !text.endsWith("!") && !text.endsWith("?") ->
                        "$text."
                    else -> text
                }
            }
        }
    }

    /**
     * Generate mock grammar correction for demo purposes
     */
    private fun generateMockCorrection(text: String): String {
        var corrected = text

        // Common corrections
        corrected = corrected.replace("i ", "I ", ignoreCase = false)
        corrected = corrected.replace("u ", "you ", ignoreCase = true)
        corrected = corrected.replace("ur ", "your ", ignoreCase = true)
        corrected = corrected.replace("thx", "thanks", ignoreCase = true)
        corrected = corrected.replace("plz", "please", ignoreCase = true)
        corrected = corrected.replace("cant", "can't", ignoreCase = true)
        corrected = corrected.replace("wont", "won't", ignoreCase = true)
        corrected = corrected.replace("dont", "don't", ignoreCase = true)

        // Capitalize first letter
        if (corrected.isNotEmpty()) {
            corrected = corrected.first().uppercase() + corrected.drop(1)
        }

        // Add period if missing
        if (corrected.isNotEmpty() && !corrected.endsWith(".") && !corrected.endsWith("!") && !corrected.endsWith("?")) {
            corrected += "."
        }

        return corrected
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
