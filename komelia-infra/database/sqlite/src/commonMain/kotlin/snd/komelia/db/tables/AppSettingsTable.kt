package snd.komelia.db.tables

import org.jetbrains.exposed.v1.core.Table

object AppSettingsTable : Table("AppSettings") {
    val version = integer("version")
    val username = text("username")
    val serverUrl = text("serverUrl")
    val cardWidth = integer("card_width")
    val seriesPageLoadSize = integer("series_page_load_size")

    val bookPageLoadSize = integer("book_page_load_size")
    val bookListLayout = text("book_list_layout")
    val appTheme = text("app_theme")

    val checkForUpdatesOnStartup = bool("check_for_updates_on_startup")

    //FIXME Android doesn't support JDBC 4.1.
    // timestamp field type uses java.sql.ResultSet.getObject(int columnIndex, Class<T> type)
    // which does not exist on Android. why???
    val updateLastCheckedTimestamp = text("update_last_checked_timestamp").nullable()
    val updateLastCheckedReleaseVersion = text("update_last_checked_release_version").nullable()
    val updateDismissedVersion = text("update_dismissed_version").nullable()

    val navBarColor = text("nav_bar_color").nullable()
    val accentColor = text("accent_color").nullable()
    val useNewLibraryUI = bool("use_new_library_ui").default(true)
    val cardLayoutBelow = bool("card_layout_below").default(false)
    val immersiveColorEnabled = bool("immersive_color_enabled").default(true)
    val immersiveColorAlpha = float("immersive_color_alpha").default(0.12f)
    val lastSelectedLibraryId = text("last_selected_library_id").nullable()

    override val primaryKey = PrimaryKey(version)
    }