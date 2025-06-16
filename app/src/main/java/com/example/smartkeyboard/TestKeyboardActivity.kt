package com.example.smartkeyboard

import android.os.Bundle
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity

class TestKeyboardActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test_keyboard)
        
        // Focus on the text field to show keyboard
        val editText = findViewById<EditText>(R.id.et_test_input)
        editText.requestFocus()
    }
}
