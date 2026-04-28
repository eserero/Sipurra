package snd.komelia.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.sqlite.SQLiteConfig
import org.sqlite.SQLiteDataSource
import snd.komelia.db.migrations.AppMigrations
import snd.komelia.db.migrations.MigrationResourcesProvider
import snd.komelia.db.migrations.OfflineMigrations
import javax.sql.DataSource


class KomeliaDatabase(databaseDir: String, serverId: Long? = null) {
    val app: Database
    val offline: Database
    val offlineReadOnly: Database

    private val appDatasource: HikariDataSource
    private val offlineWriteDatasource: HikariDataSource

    init {
        val appFileName = if (serverId != null) "server_${serverId}_komelia.sqlite" else "komelia.sqlite"
        val offlineFileName = if (serverId != null) "server_${serverId}_offline.sqlite" else "offline.sqlite"

        val appUrl = "jdbc:sqlite:${databaseDir}/$appFileName"
        val appConfig = SQLiteConfig().apply {
            setJournalMode(SQLiteConfig.JournalMode.WAL)
            enforceForeignKeys(true)
            busyTimeout = 5_000
        }
        appConfig.newConnectionConfig()
        appDatasource = HikariDataSource(
            HikariConfig().apply {
                dataSource = SQLiteDataSource(appConfig).apply { url = appUrl }
                poolName = "DB app pool"
                maximumPoolSize = 1
            }
        )

        val offlineUrl = "jdbc:sqlite:${databaseDir}/$offlineFileName"
        val offlineWriteConfig = SQLiteConfig().apply {
            setJournalMode(SQLiteConfig.JournalMode.WAL)
            enforceForeignKeys(true)
            transactionMode= SQLiteConfig.TransactionMode.IMMEDIATE
            busyTimeout = 5_000
        }
        offlineWriteDatasource = HikariDataSource(
            HikariConfig().apply {
                dataSource = SQLiteDataSource(offlineWriteConfig)
                    .apply { url = offlineUrl }
                poolName = "DB offline pool"
                maximumPoolSize = 1
            }
        )

        val offlineReadOnlyConfig = SQLiteConfig().apply {
            setJournalMode(SQLiteConfig.JournalMode.WAL)
            enforceForeignKeys(true)
            setReadOnly(true)
        }
        val offlineReadOnlyDatasource = SQLiteDataSource(offlineReadOnlyConfig)
            .apply { url = offlineUrl }

        flywayMigrate(appDatasource, AppMigrations())
        flywayMigrate(offlineWriteDatasource, OfflineMigrations())

        app = Database.connect(appDatasource)
        offline = Database.connect(offlineWriteDatasource)
        offlineReadOnly = Database.connect(
            datasource = offlineReadOnlyDatasource,
            databaseConfig = DatabaseConfig { defaultReadOnly = true }
        )

        TransactionManager.defaultDatabase = app
    }

    fun close() {
        appDatasource.close()
        offlineWriteDatasource.close()
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
