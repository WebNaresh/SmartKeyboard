package com.example.smartkeyboard

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var etApiKey: EditText
    private lateinit var btnSaveApiKey: Button
    private lateinit var btnTestKeyboard: Button
    private lateinit var sharedPreferences: SharedPreferences
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        initViews()
        setupClickListeners()
        loadSavedApiKey()
    }
    
    private fun initViews() {
        etApiKey = findViewById(R.id.et_api_key)
        btnSaveApiKey = findViewById(R.id.btn_save_api_key)
        btnTestKeyboard = findViewById(R.id.btn_test_keyboard)
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
    
    companion object {
        const val PREFS_NAME = "SmartKeyboardPrefs"
        const val API_KEY_PREF = "openai_api_key"
        
        fun getApiKey(context: Context): String? {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(API_KEY_PREF, null)
        }
    }
}
