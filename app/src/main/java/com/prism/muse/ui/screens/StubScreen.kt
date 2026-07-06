package com.prism.muse.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.prism.muse.ui.components.WaveBackground
import com.prism.muse.ui.theme.DefaultAccent
import com.prism.muse.ui.theme.TextSecondary

/** Placeholder for a section not yet built out in this UI prototype pass. */
@Composable
fun StubScreen(title: String) {
    WaveBackground(accent = DefaultAccent, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("$title — coming soon", style = MaterialTheme.typography.titleLarge, color = TextSecondary)
        }
    }
}
