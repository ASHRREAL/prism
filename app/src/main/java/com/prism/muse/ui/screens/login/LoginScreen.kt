package com.prism.muse.ui.screens.login

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prism.muse.PrismApp
import com.prism.muse.data.prefs.ServerConfig
import com.prism.muse.ui.components.AriaBackground
import com.prism.muse.ui.components.HairlineDivider
import com.prism.muse.ui.theme.HubTitle
import com.prism.muse.ui.theme.LocalPrismAccent
import com.prism.muse.ui.theme.SectionHeader
import com.prism.muse.ui.theme.TextPrimary
import com.prism.muse.ui.theme.TextSecondary
import com.prism.muse.ui.theme.TextTertiary
import com.prism.muse.ui.theme.TrackedLabel
import kotlinx.coroutines.launch

/**
 * Navidrome account setup: server URL + username + password, verified with a
 * ping before saving; the library resyncs on success.
 */
@Composable
fun LoginScreen(onBack: () -> Unit) {
    val graph = PrismApp.graph(LocalContext.current)
    val prefs = graph.prefs
    val accent = LocalPrismAccent.current
    val scope = rememberCoroutineScope()

    val saved by prefs.server.collectAsState()
    var url by remember { mutableStateOf(saved.url) }
    var username by remember { mutableStateOf(saved.username) }
    var password by remember { mutableStateOf(saved.password) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    AriaBackground {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
        ) {
            Row(modifier = Modifier.padding(top = 10.dp)) {
                Text(
                    "‹",
                    style = SectionHeader.copy(fontSize = 30.sp),
                    color = TextSecondary,
                    modifier = Modifier.clickable(onClick = onBack).padding(end = 16.dp)
                )
                Text("account", style = HubTitle.copy(fontSize = 48.sp, lineHeight = 52.sp), color = TextPrimary)
            }

            Text(
                "NAVIDROME SERVER",
                style = TrackedLabel,
                color = TextTertiary,
                modifier = Modifier.padding(top = 30.dp, bottom = 6.dp)
            )

            AriaField("server url", url, { url = it }, keyboard = KeyboardType.Uri, hint = "https://music.example.com")
            AriaField("username", username, { username = it })
            AriaField("password", password, { password = it }, password = true)

            Text(
                text = when {
                    busy -> "connecting…"
                    else -> status ?: ""
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (status?.startsWith("connected") == true) accent else TextTertiary,
                modifier = Modifier.padding(top = 18.dp)
            )

            Row(Modifier.padding(top = 20.dp)) {
                Text(
                    "connect",
                    style = SectionHeader.copy(fontSize = 26.sp),
                    color = accent,
                    modifier = Modifier
                        .clickable(enabled = !busy) {
                            busy = true
                            status = null
                            val config = ServerConfig(url.trim().trimEnd('/'), username.trim(), password)
                            scope.launch {
                                prefs.saveServer(config)
                                runCatching { graph.api.ping() }
                                    .onSuccess {
                                        status = "connected — syncing library"
                                        graph.library.refresh()
                                        status = "connected"
                                    }
                                    .onFailure { status = "failed: ${it.message}" }
                                busy = false
                            }
                        }
                        .padding(end = 32.dp)
                )
                if (saved.isConfigured) {
                    Text(
                        "sign out",
                        style = SectionHeader.copy(fontSize = 26.sp),
                        color = TextTertiary,
                        modifier = Modifier.clickable {
                            prefs.clearServer()
                            url = ""; username = ""; password = ""
                            status = "signed out — demo library"
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AriaField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    keyboard: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    hint: String = ""
) {
    Column(Modifier.padding(top = 14.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextTertiary)
        TextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            placeholder = { Text(hint, color = TextTertiary) },
            visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            textStyle = MaterialTheme.typography.bodyLarge,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                cursorColor = TextPrimary
            ),
            modifier = Modifier.fillMaxWidth()
        )
        HairlineDivider()
    }
}
