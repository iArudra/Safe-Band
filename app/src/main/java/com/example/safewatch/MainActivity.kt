package com.example.safewatch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.safewatch.ui.SafeWatchApp
import com.example.safewatch.ui.theme.SafeWatchTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SafeWatchTheme {
                SafeWatchApp()
            }
        }
    }
}
