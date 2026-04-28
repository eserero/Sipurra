package snd.komelia.ui.settings.servers

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import snd.komelia.settings.model.ServerProfile
import snd.komelia.ui.LocalViewModelFactory
import snd.komelia.ui.dialogs.ConfirmationDialog
import snd.komelia.ui.login.LoginScreen
import snd.komelia.ui.settings.SettingsScreenContainer

class AppServerManagementScreen : Screen {

    @Composable
    override fun Content() {
        val rootNavigator = LocalNavigator.currentOrThrow.parent ?: LocalNavigator.currentOrThrow
        val viewModelFactory = LocalViewModelFactory.current
        val vm = rememberScreenModel { viewModelFactory.getAppServerManagementViewModel() }
        val serverProfiles by vm.serverProfiles.collectAsState(emptyList())
        val currentServer by vm.currentServer.collectAsState()

        SettingsScreenContainer(title = "Manage Connected Servers") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                serverProfiles.forEach { profile ->
                    ServerProfileItem(
                        profile = profile,
                        isCurrent = profile.id == currentServer?.id,
                        onDelete = { vm.deleteServer(profile) },
                        onSwitch = { vm.switchServer(profile) }
                    )
                    HorizontalDivider()
                }

                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = { vm.addNewServer() },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("Connect to a new server")
                }
            }
        }
    }

    @Composable
    private fun ServerProfileItem(
        profile: ServerProfile,
        isCurrent: Boolean,
        onDelete: () -> Unit,
        onSwitch: () -> Unit
    ) {
        var showDeleteConfirmation by remember { mutableStateOf(false) }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(profile.name, style = MaterialTheme.typography.titleMedium)
                Text(profile.url, style = MaterialTheme.typography.bodyMedium)
                Text("User: ${profile.username}", style = MaterialTheme.typography.bodySmall)
            }

            if (isCurrent) {
                SuggestionChip(
                    onClick = {},
                    label = { Text("Current") },
                    enabled = false
                )
            } else {
                Button(onClick = onSwitch) {
                    Text("Switch to this server")
                }
            }

            IconButton(onClick = { showDeleteConfirmation = true }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete Server")
            }
        }

        if (showDeleteConfirmation) {
            ConfirmationDialog(
                title = "Delete Server Profile",
                body = "Are you sure you want to delete the profile for ${profile.name}? This will also delete all local settings and offline data associated with this server.",
                buttonConfirm = "Delete",
                buttonConfirmColor = MaterialTheme.colorScheme.error,
                onDialogConfirm = onDelete,
                onDialogDismiss = { showDeleteConfirmation = false }
            )
        }
    }
}
