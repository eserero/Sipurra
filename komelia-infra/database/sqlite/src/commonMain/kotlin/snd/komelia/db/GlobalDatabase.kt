package snd.komelia.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import org.sqlite.SQLiteConfig
import org.sqlite.SQLiteDataSource
import snd.komelia.db.migrations.GlobalMigrations
import snd.komelia.db.migrations.MigrationResourcesProvider
import javax.sql.DataSource


class GlobalDatabase(databaseDir: String) {
    val database: Database

    init {
        val url = "jdbc:sqlite:${databaseDir}/global.sqlite"
        val config = SQLiteConfig().apply {
            setJournalMode(SQLiteConfig.JournalMode.WAL)
            enforceForeignKeys(true)
            busyTimeout = 5_000
        }
        config.newConnectionConfig()
        val datasource = HikariDataSource(
            HikariConfig().apply {
                dataSource = SQLiteDataSource(config).apply { this.url = url }
                poolName = "DB global pool"
                maximumPoolSize = 1
            }
        )

        flywayMigrate(datasource, GlobalMigrations())
        database = Database.connect(datasource)
    }

    private fun flywayMigrate(datasource: DataSource, resourcesProvider: MigrationResourcesProvider) {
        Flyway(
            Flyway.configure()
                .loggers("slf4j")
                .dataSource(datasource)
                .resourceProvider(resourcesProvider)
                .javaMigrationClassProvider(resourcesProvider)
        ).migrate()
    }
}
