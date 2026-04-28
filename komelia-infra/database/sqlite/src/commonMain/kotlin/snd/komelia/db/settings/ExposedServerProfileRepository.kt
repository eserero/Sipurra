package snd.komelia.db.settings

import kotlinx.datetime.Instant
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import snd.komelia.db.ExposedRepository
import snd.komelia.db.tables.ServerProfilesTable
import snd.komelia.settings.model.ServerProfile

class ExposedServerProfileRepository(database: Database) : ExposedRepository(database) {

    suspend fun getAll(): List<ServerProfile> = transaction {
        ServerProfilesTable.selectAll()
            .map {
                ServerProfile(
                    id = it[ServerProfilesTable.id],
                    name = it[ServerProfilesTable.name],
                    url = it[ServerProfilesTable.url],
                    username = it[ServerProfilesTable.username],
                    lastActive = it[ServerProfilesTable.lastActive]?.let { ts -> Instant.parse(ts) }
                )
            }
    }

    suspend fun get(id: Long): ServerProfile? = transaction {
        ServerProfilesTable.selectAll().where { ServerProfilesTable.id eq id }
            .map {
                ServerProfile(
                    id = it[ServerProfilesTable.id],
                    name = it[ServerProfilesTable.name],
                    url = it[ServerProfilesTable.url],
                    username = it[ServerProfilesTable.username],
                    lastActive = it[ServerProfilesTable.lastActive]?.let { ts -> Instant.parse(ts) }
                )
            }.firstOrNull()
    }

    suspend fun insert(profile: ServerProfile): ServerProfile = transaction {
        val statement = ServerProfilesTable.insert {
            it[name] = profile.name
            it[url] = profile.url
            it[username] = profile.username
            it[lastActive] = profile.lastActive?.toString()
        }
        profile.copy(id = statement[ServerProfilesTable.id])
    }

    suspend fun update(profile: ServerProfile) = transaction {
        ServerProfilesTable.update({ ServerProfilesTable.id eq profile.id }) {
            it[name] = profile.name
            it[url] = profile.url
            it[username] = profile.username
            it[lastActive] = profile.lastActive?.toString()
        }
    }

    suspend fun delete(id: Long) = transaction {
        ServerProfilesTable.deleteWhere { ServerProfilesTable.id eq id }
    }
}
