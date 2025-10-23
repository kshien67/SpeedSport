package com.speedsport.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.speedsport.app.ui.AppRoot
import com.speedsport.app.ui.theme.SpeedSportTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SpeedSportTheme {
                AppRoot()
            }
        }
    }
}
