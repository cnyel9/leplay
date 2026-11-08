package com.leplay.app

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Voice search via the system speech recognizer (ACTION_RECOGNIZE_SPEECH).
 * The recognizer app owns the microphone and its RECORD_AUDIO permission,
 * so LePlay needs neither the permission nor a speech dependency. When no
 * recognizer is installed (common on de-Googled TVs) the returned action
 * shows a toast instead of failing silently.
 *
 * Returns a start action; the recognized text is delivered to [onResult].
 */
@Composable
fun rememberVoiceSearchLauncher(onResult: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull { it.isNotBlank() }
                ?.let(onResult)
        }
    }
    return {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
            putExtra(RecognizerIntent.EXTRA_PROMPT, I18n.t("voice_search_prompt", "Speak to search"))
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            launcher.launch(intent)
        } else {
            Toast.makeText(
                context,
                I18n.t("voice_search_unavailable", "Voice search is not available on this device"),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
}
