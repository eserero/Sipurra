package snd.komelia.ui.platform

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun LockScreenOrientation(locked: Boolean) {
    val context = LocalContext.current
    DisposableEffect(locked) {
        val activity = context as? Activity ?: return@DisposableEffect onDispose {}
        val originalOrientation = activity.requestedOrientation
        
        if (locked) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
        } else {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        
        onDispose {
            // Restore original orientation when disposed if we had locked it
            // Or just leave it as is to allow the user to keep the state across navigation.
            // Actually, since the setting is global, we should just let it persist until locked changes
        }
    }
}
