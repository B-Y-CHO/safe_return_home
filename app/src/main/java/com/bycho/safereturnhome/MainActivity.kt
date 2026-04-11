package com.bycho.safereturnhome

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.bycho.safereturnhome.navigation.SafeReturnHomeNavGraph
import com.bycho.safereturnhome.ui.theme.SafeReturnHomeTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SafeReturnHomeAppContent()
        }
    }
}

@Composable
private fun SafeReturnHomeAppContent() {
    SafeReturnHomeTheme {
        SafeReturnHomeNavGraph()
    }
}

@Preview(showBackground = true)
@Composable
private fun SafeReturnHomeAppPreview() {
    SafeReturnHomeAppContent()
}
