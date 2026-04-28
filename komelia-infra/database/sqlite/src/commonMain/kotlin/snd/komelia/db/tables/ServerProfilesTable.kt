package snd.komelia.db.tables

import org.jetbrains.exposed.v1.core.Table

object ServerProfilesTable : Table("ServerProfiles") {
    val id = long("id").autoIncrement()
    val name = text("name")
    val url = text("url")
    val username = text("username")
    val lastActive = text("last_active").nullable()

    override val primaryKey = PrimaryKey(id)
}
