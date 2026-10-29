package com.meshpay.link.example

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.meshpay.link.LinkConfiguration
import com.meshpay.link.LinkContract
import com.meshpay.link.LinkOptions
import com.meshpay.link.LinkResult

/**
 * Pasted-token sample (§2.14). The client secret never lives in the app: mint the
 * session token on a backend or dev machine with `POST /v2/sessions` and paste it
 * here. The Open button stays disabled until both the client ID and token are set.
 */
class MainActivity : ComponentActivity() {

    private val linkLauncher = registerForActivityResult(LinkContract()) { result ->
        val text = when (result) {
            is LinkResult.Exit -> "Exit: ${result.status.value}"
            is LinkResult.Error -> "Error: ${result.error.code.value}"
        }
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val clientId = editText("Client ID")
        val token = editText("Session token")
        val language = editText("Language (e.g. en, es) — optional")

        val openButton = Button(this).apply {
            text = "Open Link"
            isEnabled = false
            setOnClickListener {
                linkLauncher.launch(
                    LinkConfiguration(
                        token = token.text.toString().trim(),
                        clientId = clientId.text.toString().trim(),
                        options = LinkOptions(
                            language = language.text.toString().trim().ifEmpty { null },
                        ),
                    ),
                )
            }
        }

        val enableWhenReady = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                openButton.isEnabled = clientId.text.isNotBlank() && token.text.isNotBlank()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        }
        clientId.addTextChangedListener(enableWhenReady)
        token.addTextChangedListener(enableWhenReady)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(48, 48, 48, 48)
            addView(title("Meshpay Link"))
            addView(clientId)
            addView(token)
            addView(language)
            addView(openButton)
        }
        setContentView(layout)
    }

    private fun editText(hint: String): EditText = EditText(this).apply {
        this.hint = hint
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }

    private fun title(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 24f
        setPadding(0, 0, 0, 32)
    }
}
