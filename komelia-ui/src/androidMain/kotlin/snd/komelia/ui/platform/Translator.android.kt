package snd.komelia.ui.platform

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberTranslator(): (String) -> Unit {
    val context = LocalContext.current
    return { text ->
        context.openGoogleTranslate(text)
    }
}

private fun Context.findGoogleTranslateActivity(): ActivityInfo? {
    val intent = Intent(Intent.ACTION_PROCESS_TEXT).apply {
        type = "text/plain"
    }
    return packageManager
        .queryIntentActivities(intent, 0)
        .firstOrNull { it.activityInfo.packageName == "com.google.android.apps.translate" }
        ?.activityInfo
}

private fun Context.openGoogleTranslate(text: String) {
    val activity = findGoogleTranslateActivity()
    val intent = Intent(Intent.ACTION_PROCESS_TEXT).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_PROCESS_TEXT, text)
        putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
        if (activity != null) {
            setClassName(activity.packageName, activity.name)
        }
    }
    startActivity(intent)
}
