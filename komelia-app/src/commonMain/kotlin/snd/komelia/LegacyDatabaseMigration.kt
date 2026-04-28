package snd.komelia

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import snd.komelia.db.GlobalDatabase
import snd.komelia.db.KomeliaDatabase
import snd.komelia.db.settings.ExposedServerProfileRepository
import snd.komelia.db.settings.ExposedSettingsRepository
import snd.komelia.settings.model.ServerProfile
import java.io.File

private val logger = KotlinLogging.logger {}

class LegacyDatabaseMigration(
    private val databaseDir: String
) {
    suspend fun runMigrationIfNeeded() {
        val legacyAppDbFile = File(databaseDir, "komelia.sqlite")

        if (legacyAppDbFile.exists()) {
            logger.info { "Legacy database found, migrating to multi-server support" }
            migrate()
        }
    }

    private suspend fun migrate() {
        val legacyDatabase = KomeliaDatabase(databaseDir, null)
        val settingsRepository = ExposedSettingsRepository(legacyDatabase.app)
        val settings = settingsRepository.get()
        legacyDatabase.close()

        if (settings != null) {
            val globalDatabase = GlobalDatabase(databaseDir)
            val serverProfileRepository = ExposedServerProfileRepository(globalDatabase.database)
            val profile = ServerProfile(
                name = "Default Server",
                url = settings.serverUrl,
                username = settings.username,
                lastActive = Clock.System.now()
            )
            val inserted = serverProfileRepository.insert(profile)
            val serverId = inserted.id

            renameFile(File(databaseDir, "komelia.sqlite"), File(databaseDir, "server_${serverId}_komelia.sqlite"))
            renameFile(File(databaseDir, "komelia.sqlite-wal"), File(databaseDir, "server_${serverId}_komelia.sqlite-wal"))
            renameFile(File(databaseDir, "komelia.sqlite-shm"), File(databaseDir, "server_${serverId}_komelia.sqlite-shm"))

            renameFile(File(databaseDir, "offline.sqlite"), File(databaseDir, "server_${serverId}_offline.sqlite"))
            renameFile(File(databaseDir, "offline.sqlite-wal"), File(databaseDir, "server_${serverId}_offline.sqlite-wal"))
            renameFile(File(databaseDir, "offline.sqlite-shm"), File(databaseDir, "server_${serverId}_offline.sqlite-shm"))

            val datastoreDir = File(databaseDir, "datastore")
            if (datastoreDir.exists()) {
                renameFile(File(datastoreDir, "settings.pb"), File(datastoreDir, "server_${serverId}_settings.pb"))
            }
        }
    }

    private fun renameFile(from: File, to: File) {
        if (from.exists()) {
            if (!from.renameTo(to)) {
                logger.error { "Failed to rename ${from.absolutePath} to ${to.absolutePath}" }
            }
        }
    }
}
