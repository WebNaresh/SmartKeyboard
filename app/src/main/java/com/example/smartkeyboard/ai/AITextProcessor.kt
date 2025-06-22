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
     * Enhance text with AI using custom instructions
     */
    suspend fun enhanceTextWithCustomInstructions(
        text: String,
        customInstructions: String,
        context: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (text.isBlank()) {
                return@withContext Result.success(text)
            }

            // Check if we have a real API key
            if (isMockMode || apiKey == "mock-key-for-demo") {
                Log.d(TAG, "No API key provided, using simple fallback for custom mood")
                // For custom moods without API key, just capitalize and add period
                val enhanced = text.replaceFirstChar { it.uppercase() }
                val result = if (!enhanced.endsWith(".") && !enhanced.endsWith("!") && !enhanced.endsWith("?")) {
                    "$enhanced."
                } else {
                    enhanced
                }
                return@withContext Result.success(result)
            }

            // Real AI processing with custom instructions
            val systemPrompt = buildCustomSystemPrompt(customInstructions, context)
            val userPrompt = "Please enhance and improve this message according to the instructions: $text"

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
                    Log.w(TAG, "Empty response from OpenAI API for custom mood")
                    Result.success(text)
                } else {
                    val cleanedText = cleanResponseText(enhancedText)
                    Log.d(TAG, "Custom mood enhancement: '$text' -> '$cleanedText'")
                    Result.success(cleanedText)
                }
            } else {
                val errorMsg = "OpenAI API error for custom mood: ${response.code()} - ${response.message()}"
                Log.e(TAG, errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error enhancing text with custom instructions", e)
            Result.failure(e)
        }
    }

    /**
     * Generate suggestions using OpenAI API for what user might type next
     */
    suspend fun generateSuggestions(text: String, mood: MoodType): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            if (text.isBlank()) {
                return@withContext Result.success(listOf(text))
            }

            // Check if we have a real API key
            if (isMockMode || apiKey == "mock-key-for-demo") {
                Log.d(TAG, "No API key provided (key: '$apiKey'), using simple fallback suggestions for mood: $mood")
                val suggestions = generateSimpleFallback(text, mood)
                Log.d(TAG, "Fallback suggestions generated: $suggestions")
                return@withContext Result.success(suggestions)
            }

            // Real OpenAI API processing for suggestions
            val systemPrompt = buildSuggestionSystemPrompt(mood)
            val userPrompt = """
The user is typing a message and has written: '$text'. Provide 3 different ways to COMPLETE or CONTINUE this message. Do NOT respond to the message - instead suggest how the user can finish typing their own message. Consider the ${mood.name.lowercase()} tone. Return only the complete message suggestions, one per line, without numbers or formatting. If mood is respectful, avoid framing completions as questions and do not start with phrases like 'may I', 'could you', or 'would it be possible'. Keep the language respectful, but clear and assertive.
""".trimIndent()

            val request = ChatCompletionRequest(
                model = "gpt-3.5-turbo",
                messages = listOf(
                    ChatMessage("system", systemPrompt),
                    ChatMessage("user", userPrompt)
                ),
                maxTokens = 150,
                temperature = 0.8 // Higher temperature for more creative suggestions
            )

            val response = openAIService!!.createChatCompletion(
                authorization = "${OpenAIService.BEARER_PREFIX}$apiKey",
                request = request
            )

            if (response.isSuccessful) {
                val chatResponse = response.body()
                val suggestionsText = chatResponse?.choices?.firstOrNull()?.message?.content?.trim()

                if (suggestionsText.isNullOrBlank()) {
                    Log.w(TAG, "Empty suggestions from OpenAI API")
                    Result.success(generateSimpleFallback(text, mood))
                } else {
                    // Parse suggestions from AI response
                    val suggestions = parseSuggestions(suggestionsText, text)
                    Log.d(TAG, "OpenAI API suggestions: $suggestions")
                    Result.success(suggestions)
                }
            } else {
                val errorMsg = "OpenAI API error: ${response.code()} - ${response.message()}"
                Log.e(TAG, errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calling OpenAI API", e)
            Result.failure(e)
        }
    }

    /**
     * Generate suggestions using custom instructions
     */
    suspend fun generateSuggestionsWithCustomInstructions(
        text: String,
        customInstructions: String
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            if (text.isBlank()) {
                return@withContext Result.success(listOf(text))
            }

            // Check if we have a real API key
            if (isMockMode || apiKey == "mock-key-for-demo") {
                Log.d(TAG, "No API key provided, using simple fallback for custom mood suggestions")
                val suggestions = listOf(
                    text,
                    text.replaceFirstChar { it.uppercase() },
                    if (!text.endsWith(".") && !text.endsWith("!") && !text.endsWith("?")) "$text." else text
                ).distinct()
                return@withContext Result.success(suggestions)
            }

            // Real OpenAI API processing with custom instructions
            val systemPrompt = buildCustomSuggestionSystemPrompt(customInstructions)
            val userPrompt = "The user is typing a message and has written: '$text'. Provide 3 different ways to COMPLETE or CONTINUE this message according to the custom instructions. Return only the complete message suggestions, one per line, without numbers or formatting."

            val request = ChatCompletionRequest(
                model = "gpt-3.5-turbo",
                messages = listOf(
                    ChatMessage("system", systemPrompt),
                    ChatMessage("user", userPrompt)
                ),
                maxTokens = 150,
                temperature = 0.8
            )

            val response = openAIService!!.createChatCompletion(
                authorization = "${OpenAIService.BEARER_PREFIX}$apiKey",
                request = request
            )

            if (response.isSuccessful) {
                val chatResponse = response.body()
                val suggestionsText = chatResponse?.choices?.firstOrNull()?.message?.content?.trim()

                if (suggestionsText.isNullOrBlank()) {
                    Log.w(TAG, "Empty suggestions from OpenAI API for custom mood")
                    Result.success(listOf(text))
                } else {
                    val suggestions = parseSuggestions(suggestionsText, text)
                    Log.d(TAG, "Custom mood suggestions: $suggestions")
                    Result.success(suggestions)
                }
            } else {
                val errorMsg = "OpenAI API error for custom mood suggestions: ${response.code()} - ${response.message()}"
                Log.e(TAG, errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating suggestions with custom instructions", e)
            Result.failure(e)
        }
    }

    /**
     * Generate simple fallback suggestions when no API key is available
     */
    private fun generateSimpleFallback(text: String, mood: MoodType): List<String> {
        val suggestions = mutableListOf<String>()

        // Always include original text
        suggestions.add(text)

        // Add simple mood-based variations (no hardcoded responses)
        when (mood) {
            MoodType.RESPECTFUL -> {
                // Make it more polite and respectful
                val respectful = text.replaceFirstChar { it.uppercase() }

                // Add "please" if it's a request-like message
                if (text.contains("can you", ignoreCase = true) ||
                    text.contains("could you", ignoreCase = true) ||
                    text.contains("would you", ignoreCase = true)) {
                    suggestions.add("$respectful, please")
                } else if (!respectful.endsWith(".") && !respectful.endsWith("!") && !respectful.endsWith("?")) {
                    suggestions.add("$respectful.")
                } else {
                    suggestions.add(respectful)
                }

                // Add a more formal version
                if (text.length > 5) {
                    suggestions.add("I would like to say: $respectful")
                }
            }
            MoodType.FUNNY -> {
                suggestions.add("$text 😄")
                if (text.length > 3) {
                    suggestions.add("$text (and I'm having fun!) 🎉")
                }
            }
            MoodType.ANGRY -> {
                suggestions.add(text.uppercase() + "!")
                if (text.length > 3) {
                    suggestions.add("Listen: ${text.uppercase()}!")
                }
            }
            MoodType.NORMAL -> {
                suggestions.add(text.replaceFirstChar { it.uppercase() })
                if (!text.endsWith(".") && !text.endsWith("!") && !text.endsWith("?") && text.length > 3) {
                    suggestions.add("${text.replaceFirstChar { it.uppercase() }}.")
                }
            }
        }

        return suggestions.take(3).distinct() // Remove duplicates and limit to 3
    }

    /**
     * Generate simple enhancement when no API key is available
     */
    private fun generateMockEnhancement(text: String, mood: MoodType): String {
        // For single enhancement, just return the first fallback suggestion
        return generateSimpleFallback(text, mood).firstOrNull() ?: text
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
            MoodType.RESPECTFUL -> "Please enhance and improve this message to be more respectful and polite: $text"
            MoodType.FUNNY -> "Please enhance and improve this message to be more fun and humorous: $text"
            MoodType.ANGRY -> "Please enhance and improve this message to be more assertive and emphatic: $text"
            MoodType.NORMAL -> "Please enhance and improve this message: $text"
        }
    }

    private fun buildSuggestionSystemPrompt(mood: MoodType): String {
        val basePrompt = """
            You are a smart keyboard text completion assistant. Your job is to help users complete their messages.
            When given partial text that a user is typing, suggest 3 different ways to COMPLETE or CONTINUE that text.
            DO NOT respond to the message or have a conversation - only suggest how to finish the user's own message.

            Example:
            User typing: "hi good morning"
            Your suggestions:
            - "hi good morning! how are you today?"
            - "hi good morning, hope you're doing well"
            - "hi good morning! ready for the day?"

            IMPORTANT: Return only the complete suggested messages, one per line, without numbers, bullets, or formatting.
        """.trimIndent()

        val moodPrompt = when (mood) {
            MoodType.RESPECTFUL -> """
                Complete the user's message in a polite, respectful, and professional way.
                Add courteous language and formal tone where appropriate to finish their message.
            """.trimIndent()

            MoodType.FUNNY -> """
                Complete the user's message in a humorous and playful way while keeping it appropriate.
                Add light humor, wordplay, or fun expressions to finish their message.
            """.trimIndent()

            MoodType.ANGRY -> """
                Complete the user's message in a more assertive and emphatic way while keeping it professional.
                Use strong but appropriate language to finish their message with determination.
            """.trimIndent()

            MoodType.NORMAL -> """
                Complete the user's message in a clear, natural, and conversational way.
                Focus on common ways people would finish such messages.
            """.trimIndent()
        }

        return "$basePrompt\n\n$moodPrompt"
    }

    private fun buildCustomSystemPrompt(customInstructions: String, context: String): String {
        val basePrompt = """
            You are a text enhancement assistant for a smart keyboard.
            Your job is to improve the user's text while maintaining their intended meaning.
            Keep responses concise and natural.
            IMPORTANT: Return ONLY the enhanced text without quotes, explanations, or additional formatting.
            Do not wrap the response in quotation marks.
        """.trimIndent()

        return if (context.isNotBlank()) {
            "$basePrompt\n\n$customInstructions\n\nContext: $context"
        } else {
            "$basePrompt\n\n$customInstructions"
        }
    }

    private fun buildCustomSuggestionSystemPrompt(customInstructions: String): String {
        val basePrompt = """
            You are a smart keyboard text completion assistant. Your job is to help users complete their messages.
            When given partial text that a user is typing, suggest 3 different ways to COMPLETE or CONTINUE that text.
            DO NOT respond to the message or have a conversation - only suggest how to finish the user's own message.

            IMPORTANT: Return only the complete suggested messages, one per line, without numbers, bullets, or formatting.
        """.trimIndent()

        return "$basePrompt\n\n$customInstructions"
    }

    private fun parseSuggestions(suggestionsText: String, originalText: String): List<String> {
        val suggestions = mutableListOf<String>()

        // Split by lines and clean up
        val lines = suggestionsText.split("\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { cleanResponseText(it) }

        // Add parsed suggestions
        suggestions.addAll(lines.take(3))

        // Always include original text as fallback if we don't have enough suggestions
        if (!suggestions.contains(originalText)) {
            suggestions.add(originalText)
        }

        return suggestions.take(3) // Limit to 3 suggestions
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
