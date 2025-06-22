package com.example.smartkeyboard

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.smartkeyboard.adapter.CustomMoodAdapter
import com.example.smartkeyboard.data.CustomMood
import com.example.smartkeyboard.manager.CustomMoodManager

class SettingsActivity : AppCompatActivity() {

    private lateinit var etApiKey: EditText
    private lateinit var btnSaveApiKey: Button
    private lateinit var btnTestKeyboard: Button
    private lateinit var sharedPreferences: SharedPreferences

    // Custom Mood Creator components
    private lateinit var etMoodTitle: EditText
    private lateinit var etMoodInstructions: EditText
    private lateinit var btnCreateMood: Button
    private lateinit var tvCustomMoodsHeader: TextView
    private lateinit var tvNoCustomMoods: TextView
    private lateinit var rvCustomMoods: RecyclerView
    private lateinit var customMoodManager: CustomMoodManager
    private lateinit var customMoodAdapter: CustomMoodAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        customMoodManager = CustomMoodManager(this)

        initViews()
        setupClickListeners()
        setupCustomMoodRecyclerView()
        loadSavedApiKey()
        loadCustomMoods()
    }
    
    private fun initViews() {
        etApiKey = findViewById(R.id.et_api_key)
        btnSaveApiKey = findViewById(R.id.btn_save_api_key)
        btnTestKeyboard = findViewById(R.id.btn_test_keyboard)

        // Custom Mood Creator views
        etMoodTitle = findViewById(R.id.et_mood_title)
        etMoodInstructions = findViewById(R.id.et_mood_instructions)
        btnCreateMood = findViewById(R.id.btn_create_mood)
        tvCustomMoodsHeader = findViewById(R.id.tv_custom_moods_header)
        tvNoCustomMoods = findViewById(R.id.tv_no_custom_moods)
        rvCustomMoods = findViewById(R.id.rv_custom_moods)
    }
    
    private fun setupClickListeners() {
        btnSaveApiKey.setOnClickListener {
            saveApiKey()
        }

        btnTestKeyboard.setOnClickListener {
            // Open a simple text field to test the keyboard
            val intent = android.content.Intent(this, TestKeyboardActivity::class.java)
            startActivity(intent)
        }

        btnCreateMood.setOnClickListener {
            createCustomMood()
        }
    }
    
    private fun loadSavedApiKey() {
        val savedApiKey = sharedPreferences.getString(API_KEY_PREF, "")
        etApiKey.setText(savedApiKey)
    }
    
    private fun saveApiKey() {
        val apiKey = etApiKey.text.toString().trim()
        
        if (apiKey.isEmpty()) {
            Toast.makeText(this, "Please enter an API key", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (!apiKey.startsWith("sk-")) {
            Toast.makeText(this, "Invalid OpenAI API key format", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Save API key
        sharedPreferences.edit()
            .putString(API_KEY_PREF, apiKey)
            .apply()
        
        Toast.makeText(this, "API key saved successfully!", Toast.LENGTH_SHORT).show()
    }

    private fun setupCustomMoodRecyclerView() {
        customMoodAdapter = CustomMoodAdapter(
            moods = mutableListOf(),
            onEditClick = { mood -> editCustomMood(mood) },
            onDeleteClick = { mood -> deleteCustomMood(mood) }
        )

        rvCustomMoods.layoutManager = LinearLayoutManager(this)
        rvCustomMoods.adapter = customMoodAdapter
    }

    private fun loadCustomMoods() {
        val customMoods = customMoodManager.getCustomMoods()
        customMoodAdapter.updateMoods(customMoods)

        // Always show the custom moods section, but show different content based on whether there are moods
        tvCustomMoodsHeader.visibility = View.VISIBLE

        if (customMoods.isNotEmpty()) {
            // Show the list of custom moods
            rvCustomMoods.visibility = View.VISIBLE
            tvNoCustomMoods.visibility = View.GONE

            // Update header text to show count
            tvCustomMoodsHeader.text = "Your Custom Moods (${customMoods.size})"
        } else {
            // Show empty state message
            rvCustomMoods.visibility = View.GONE
            tvNoCustomMoods.visibility = View.VISIBLE
            tvCustomMoodsHeader.text = "Your Custom Moods"
        }
    }

    private fun createCustomMood() {
        val title = etMoodTitle.text.toString().trim()
        val instructions = etMoodInstructions.text.toString().trim()

        // Validation
        if (title.isEmpty()) {
            Toast.makeText(this, "Please enter a mood title", Toast.LENGTH_SHORT).show()
            return
        }

        if (instructions.isEmpty()) {
            Toast.makeText(this, "Please enter AI instructions", Toast.LENGTH_SHORT).show()
            return
        }

        if (title.length < 2) {
            Toast.makeText(this, "Mood title must be at least 2 characters", Toast.LENGTH_SHORT).show()
            return
        }

        if (instructions.length < 10) {
            Toast.makeText(this, "Instructions must be at least 10 characters", Toast.LENGTH_SHORT).show()
            return
        }

        // Check if title already exists
        if (customMoodManager.isTitleExists(title)) {
            Toast.makeText(this, "A mood with this title already exists", Toast.LENGTH_SHORT).show()
            return
        }

        // Create new custom mood
        val customMood = CustomMood(
            id = CustomMood.generateId(),
            title = title,
            instructions = instructions
        )

        // Save the mood
        if (customMoodManager.saveCustomMood(customMood)) {
            Toast.makeText(this, "✅ Custom mood '$title' created successfully!", Toast.LENGTH_LONG).show()

            // Clear input fields
            etMoodTitle.text.clear()
            etMoodInstructions.text.clear()

            // Refresh the list to show the new mood
            loadCustomMoods()

            // Scroll to show the new mood if there are many
            rvCustomMoods.post {
                rvCustomMoods.smoothScrollToPosition(customMoodAdapter.itemCount - 1)
            }
        } else {
            Toast.makeText(this, "❌ Failed to create custom mood", Toast.LENGTH_SHORT).show()
        }
    }

    private fun editCustomMood(mood: CustomMood) {
        // Create dialog for editing
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_mood, null)
        val etEditTitle = dialogView.findViewById<EditText>(R.id.et_edit_mood_title)
        val etEditInstructions = dialogView.findViewById<EditText>(R.id.et_edit_mood_instructions)

        // Pre-fill with current values
        etEditTitle.setText(mood.title)
        etEditInstructions.setText(mood.instructions)

        AlertDialog.Builder(this)
            .setTitle("Edit Custom Mood")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val newTitle = etEditTitle.text.toString().trim()
                val newInstructions = etEditInstructions.text.toString().trim()

                // Validation
                if (newTitle.isEmpty() || newInstructions.isEmpty()) {
                    Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (newTitle.length < 2 || newInstructions.length < 10) {
                    Toast.makeText(this, "Title must be at least 2 characters, instructions at least 10", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                // Check if new title conflicts with other moods
                if (customMoodManager.isTitleExists(newTitle, mood.id)) {
                    Toast.makeText(this, "A mood with this title already exists", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                // Update mood
                val updatedMood = mood.copy(title = newTitle, instructions = newInstructions)
                if (customMoodManager.updateCustomMood(mood, updatedMood)) {
                    Toast.makeText(this, "✅ Mood '$newTitle' updated successfully!", Toast.LENGTH_LONG).show()
                    loadCustomMoods()
                } else {
                    Toast.makeText(this, "❌ Failed to update mood", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteCustomMood(mood: CustomMood) {
        AlertDialog.Builder(this)
            .setTitle("Delete Custom Mood")
            .setMessage("Are you sure you want to delete the '${mood.title}' mood? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                if (customMoodManager.deleteCustomMood(mood)) {
                    Toast.makeText(this, "🗑️ Mood '${mood.title}' deleted successfully!", Toast.LENGTH_LONG).show()
                    loadCustomMoods()
                } else {
                    Toast.makeText(this, "❌ Failed to delete mood", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    companion object {
        const val PREFS_NAME = "SmartKeyboardPrefs"
        const val API_KEY_PREF = "openai_api_key"

        fun getApiKey(context: Context): String? {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(API_KEY_PREF, null)
        }
    }
}
