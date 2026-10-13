package com.example.machinetimeline

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: TimelineViewModel = viewModel()
            val state by vm.uiState.collectAsStateWithLifecycle()
            val base = LocalDensity.current
            // Scale toàn bộ chữ (sp) mà không đổi kích thước dp / pixel của Canvas
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, base.fontScale * state.textScale)
            ) {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    TimelineScreen(vm)
                }
            }
        }
    }
}
