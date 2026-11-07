package com.sandianshui.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.sandianshui.player.ui.player.PlayerScreen
import com.sandianshui.player.ui.theme.SandianshuiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SandianshuiTheme {
                PlayerScreen()
            }
        }
    }
}
