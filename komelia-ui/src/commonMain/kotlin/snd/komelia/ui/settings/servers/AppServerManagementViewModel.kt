package snd.komelia.ui.settings.servers

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.launch
import snd.komelia.settings.model.ServerProfile
import snd.komelia.ui.session.ServerSessionManager

class AppServerManagementViewModel(
    private val sessionManager: ServerSessionManager
) : ScreenModel {
    val serverProfiles = sessionManager.serverProfiles
    val currentServer = sessionManager.currentServerProfile

    fun deleteServer(profile: ServerProfile) {
        screenModelScope.launch {
            sessionManager.deleteServer(profile)
        }
    }

    fun switchServer(profile: ServerProfile) {
        sessionManager.switchServer(profile)
    }

    fun addNewServer() {
        sessionManager.switchServer(null)
    }
}
