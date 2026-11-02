package com.meshpay.link.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshpay.link.LinkConfiguration
import com.meshpay.link.LinkContract
import com.meshpay.link.LinkEnvironment
import com.meshpay.link.LinkOptions
import com.meshpay.link.LinkResult
import com.meshpay.link.LinkTheme

/** The Mesh wordmark yellow — the single brand accent in the example app. */
private val MeshYellow = Color(0xFFF8FF4D)
private val Background = Color(0xFF0E0D0D)
private val Surface = Color(0xFF1B1A1A)

/**
 * Pasted-token sample (§2.14). The client secret never lives in the app: mint the
 * session token on a backend or dev machine and paste it here. Dark-mode only,
 * Mesh-yellow accent; the Open button stays disabled until both the client ID and
 * token are non-blank.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = MeshYellow,
                    background = Background,
                    surface = Surface,
                ),
            ) {
                LinkDemoScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LinkDemoScreen() {
    var clientId by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var environment by remember { mutableStateOf(LinkEnvironment.PROD) }
    var theme by remember { mutableStateOf<LinkTheme?>(null) }
    var language by remember { mutableStateOf("") }
    var fiat by remember { mutableStateOf("") }
    var integrationId by remember { mutableStateOf("") }
    var linkUrl by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<LinkResult?>(null) }

    val launcher = rememberLauncherForActivityResult(LinkContract()) { result = it }

    Scaffold(
        topBar = { TopAppBar(title = { Text("MeshPay Link") }) },
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionHeader("Credentials")
            ClearableField("Client ID", clientId) { clientId = it }
            ClearableField("Session token", token) { token = it }

            SectionHeader("Options")
            PickerField(
                label = "Environment",
                options = listOf(
                    "Sandbox" to LinkEnvironment.SBX,
                    "Production" to LinkEnvironment.PROD,
                    "Dev" to LinkEnvironment.DEV,
                ),
                selectedLabel = environmentLabel(environment),
                onSelect = { environment = it },
            )
            PickerField(
                label = "Theme",
                options = listOf(
                    "Not set" to null,
                    "System" to LinkTheme.SYSTEM,
                    "Light" to LinkTheme.LIGHT,
                    "Dark" to LinkTheme.DARK,
                ),
                selectedLabel = themeLabel(theme),
                onSelect = { theme = it },
            )
            ClearableField("Language (e.g. en, es)", language) { language = it }
            ClearableField("Display fiat currency (e.g. USD, DKK)", fiat) { fiat = it }
            ClearableField("Integration ID (e.g. metamask)", integrationId) { integrationId = it }
            ClearableField("Link URL (LocalCan tunnel — testing)", linkUrl, KeyboardType.Uri) { linkUrl = it }

            result?.let {
                SectionHeader("Link result")
                Text(
                    text = resultLine(it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val canOpen = clientId.isNotBlank() && token.isNotBlank()
            Button(
                onClick = {
                    result = null
                    launcher.launch(
                        LinkConfiguration(
                            token = token.trim(),
                            clientId = clientId.trim(),
                            options = LinkOptions(
                                environment = environment,
                                theme = theme,
                                language = language.trim().ifBlank { null },
                                integrationId = integrationId.trim().ifBlank { null },
                                displayFiatCurrency = fiat.trim().ifBlank { null },
                                linkUrl = linkUrl.trim().ifBlank { null },
                            ),
                        ),
                    )
                },
                enabled = canOpen,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Surface,
                    contentColor = MeshYellow,
                    disabledContainerColor = Surface,
                    disabledContentColor = MeshYellow.copy(alpha = 0.38f),
                ),
            ) {
                Text("Open Link", fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun ClearableField(
    label: String,
    value: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrect = false,
            keyboardType = keyboardType,
        ),
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(
                    onClick = { onValueChange("") },
                    modifier = Modifier.semantics { contentDescription = "Clear $label" },
                ) {
                    Text("✕", color = MeshYellow.copy(alpha = 0.7f), fontSize = 18.sp)
                }
            }
        },
        colors = OutlinedTextFieldDefaults.colors(cursorColor = MeshYellow),
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> PickerField(
    label: String,
    options: List<Pair<String, T>>,
    selectedLabel: String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = OutlinedTextFieldDefaults.colors(cursorColor = MeshYellow),
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (text, value) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        onSelect(value)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun environmentLabel(environment: LinkEnvironment): String = when (environment) {
    LinkEnvironment.SBX -> "Sandbox"
    LinkEnvironment.PROD -> "Production"
    LinkEnvironment.DEV -> "Dev"
}

private fun themeLabel(theme: LinkTheme?): String = when (theme) {
    null -> "Not set"
    LinkTheme.SYSTEM -> "System"
    LinkTheme.LIGHT -> "Light"
    LinkTheme.DARK -> "Dark"
}

private fun resultLine(result: LinkResult): String = when (result) {
    is LinkResult.Exit -> "Exit: ${result.status.value}"
    is LinkResult.Error -> "Error: ${result.error.code.value} — ${result.error.message}"
}
