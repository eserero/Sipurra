# MULTI_SERVER_PLAN.md

## Objective
Introduce multi-server support to Komelia, allowing users to connect to and switch between multiple Komga instances without mixing their offline data, caches, or app settings.

## Background & Motivation
Currently, Komelia is designed to communicate with a single Komga server. All data, settings, and caches are tied to that one connection. Users with multiple Komga servers need the ability to manage separate profiles, databases, and cached assets.

## Scope & Impact
- **Database**: Introduction of a `global.sqlite` to manage the list of known servers. Modifying `KomeliaDatabase` to instantiate `server_<id>.sqlite` and `offline_<id>.sqlite` instead of hardcoded names.
- **Cache**: Update OkHttp, Coil, and Reader caches to be stored in subdirectories keyed by the active server ID.
- **Secrets**: The `SecretsRepository` (cookie store) already keys cookies by URL, so it is inherently compatible.
- **App Lifecycle**: Introduce a mechanism to re-initialize `AppModule` when switching servers, clearing in-memory states and establishing a new dependency graph.
- **UI**: 
  - Revamp the Login screen to support selecting an existing server from a dropdown or entering a new one.
  - Update the Settings screen with a new "Servers" tab for managing existing connections and initiating the addition of new ones.

## Proposed Solution
1. **Global Server Registry (`global.sqlite`)**: A new, lightweight SQLite database storing basic connection profiles (e.g., `id`, `url`, `username`, `last_active`).
2. **Server-Specific Databases**: The existing `komelia.sqlite` and `offline.sqlite` will be renamed dynamically based on the active server's ID (e.g., `server_<id>_komelia.sqlite`).
3. **Server-Specific Caches**: `AppDirectories` and `AppModule` will append the active server ID to all cache paths.
4. **Lifecycle Reboot**: A higher-level state manager will wrap the `AppModule`. When the active server changes, the current `AppModule` will be destroyed (closing DB connections and clearing memory) and a new one instantiated.
5. **UI Requirements (Login)**: 
   - If it is the first login (no servers), show the standard "Connect to Server" fields (URL, username, password).
   - If servers exist, the app should automatically attempt to login to the last active server (preserving existing behavior).
   - If auto-login fails, or if explicitly logged out, show the login screen with a dropdown of available servers (defaulting to the last logged-in server).
   - Add a "Connect to a new server" option in the dropdown that switches the UI back to the input fields.
   - If an existing server is selected, only show the Dropdown, Login button, and Offline Mode button.
6. **UI Requirements (Settings)**:
   - Add a "Servers" tab as the first tab in the Settings screen (specifically within the offline/server settings area).
   - List available servers with a button to "Connect to a new server", which redirects the user to the Login screen with the "Connect to a new server" option pre-selected.

## Alternatives Considered
- **Single Database with `server_id` Foreign Keys**: Rejected due to the massive refactoring required across all domain models, exposed repositories, and SQL migrations.
- **JSON / DataStore Registry**: Rejected in favor of `global.sqlite` to maintain a robust, standard SQL pattern consistent with the rest of the app's infrastructure.
- **Mutable Repositories**: Swapping DB connections underneath existing repositories without a full reboot was rejected due to high risks of race conditions and background task leaks.

## Implementation Plan
### Phase 1: Global Storage & DB Renaming
- Create `GlobalDatabase` with a `Servers` table.
- Update `KomeliaDatabase` to accept a `serverId` parameter and construct file paths accordingly.
- Update `AppModule` cache path initialization to include the `serverId`.

### Phase 2: App Lifecycle Management
- Introduce a top-level component (e.g. `ServerSessionManager`) responsible for managing the lifecycle of `AppModule`.
- Implement the "reboot" mechanism to tear down the old graph and build a new one when the active server changes.

### Phase 3: Login UI Revamp
- Update the Login Screen ViewModel to fetch the list of known servers from `GlobalDatabase`.
- Preserve and adapt the auto-login logic for the last active server.
- Implement the requested dropdown logic:
  - Default: Last logged-in server selected.
  - Option: "Connect to a new server" -> reveals URL/User/Pass fields.
  - Only show Dropdown + Login Button + Offline Mode button if an existing server is selected.

### Phase 4: Settings UI Update
- Add the "Servers" tab to the Settings screen.
- Implement the list of servers and the "Connect to a new server" navigation flow back to the Login screen.

## Verification
- Verify that adding a new server creates dedicated SQLite files and Cache folders.
- Verify that switching servers completely reloads the data (Library, Series, Settings) without bleeding state.
- Verify that the Login UI correctly adapts based on the presence of existing servers.

## Migration & Rollback
- **Migration**: Upon startup, if a `komelia.sqlite` exists but `global.sqlite` does not, generate a default `ServerProfile` using the `AppSettings` from the legacy database and rename the legacy files to match the new `server_<id>` format.




problem analisys: 
✦ I've completed my review of the MULTI_SERVER_PLAN.md implementation. The plan is largely followed, but I've identified several issues: a critical dependency error
  in secret-service, potential migration race conditions in MainActivity, and questionable suspend usage in ExposedServerProfileRepository. I'll now compile a
  detailed report on these findings and the overall implementation status.

  Implementation Status Report: Multi-Server Support

  Overall Status
  The implementation largely follows the MULTI_SERVER_PLAN.md, with core infrastructure for multiple server profiles, dynamic database paths, and lifecycle management
  in place. However, the project is currently in a non-compilable state due to external dependency issues and internal code errors.

  ---

  1. Compilation Failures

  Critical: Dependency Resolution Error
   * Error: Could not resolve all dependencies for configuration ':secret-service:compileClasspath'. > Could not find at.favre.lib:hkdf:.
   * Cause: The third_party/secret-service submodule contains a nested submodule hkdf which is currently empty (uninitialized). Since secret-service uses
     includeBuild("hkdf"), the build fails immediately.
   * Impact: Prevents any module that depends on secret-service from compiling.

  Internal: Exposed Repository Suspend usage
   * File: komelia-infra/database/sqlite/src/commonMain/kotlin/snd/komelia/db/settings/ExposedServerProfileRepository.kt
   * Problem: Methods like getAll(), insert(), etc., are marked as suspend and call transaction { ... }. In the current architecture of Komelia, transaction (from
     ExposedRepository) is typically a blocking call on a specific dispatcher. While not strictly a compiler error if the return types match, it deviates from the
     pattern used in other repositories where transaction handles the dispatching, and might lead to issues if the underlying transaction block doesn't support
     suspension.

  Internal: Illegal runBlocking in commonMain
   * File: komelia-app/src/commonMain/kotlin/snd/komelia/LegacyDatabaseMigration.kt
   * Problem: Uses runBlocking inside commonMain. While this works for JVM/Android, it will fail if the project ever targets Kotlin/Wasm or Kotlin/JS (which Komelia
     partially does for some modules).

  ---

  2. Plan Alignment & Implementation Review

  Phase 1: Global Storage & DB Renaming
   * Success: GlobalDatabase and ServerProfilesTable are implemented. KomeliaDatabase correctly accepts serverId and generates dynamic filenames (e.g.,
     server_1_komelia.sqlite).
   * Success: Cache directories in AndroidAppModule and AppModule are now server-aware.

  Phase 2: App Lifecycle Management
   * Success: DefaultServerSessionManager implemented. It correctly handles appModuleFactory and "reboots" the dependency graph by closing the old module and
     initializing a new one.

  Phase 3: Login UI Revamp
   * Success: LoginViewModel now supports serverProfiles and selectedServerProfile. It includes logic to toggle between existing servers and adding new ones.
   * Observation: Switching a server in LoginViewModel triggers a full graph reboot. This might cause the LoginViewModel itself to be cleared or re-initialized while
     it's still active if not handled carefully by the Navigator.

  Phase 4: Settings UI Update
   * Success: New AppServerManagementViewModel and related UI structures were found in the file list (though not fully read, their presence confirms implementation
     effort).

  ---

  3. Identified Risks & Potential Bugs

   1. Migration Race Condition (Android):
       * In MainActivity.kt, DefaultServerSessionManager is instantiated and manager.loadLastActiveServer() is called before
         LegacyDatabaseMigration(...).runMigrationIfNeeded(). 
       * If the migration hasn't run yet, loadLastActiveServer() will find an empty GlobalDatabase and won't load the legacy data until the next app start or manual
         intervention.

   2. State Leaks:
       * The AppModule has a close() method that shuts down OkHttp and Databases. However, many StateFlows are created using a SupervisorJob in AppModule. If these
         aren't explicitly cancelled when the module is closed, there might be background coroutines leaking from previous server sessions.

   3. Missing Submodule Initialization:
       * The hkdf submodule under secret-service needs to be initialized (git submodule update --init --recursive).

  ---

  Summary of Problems to Fix
   1. Initialize Submodules: Resolve the hkdf dependency.
   2. Fix Repository Methods: Ensure ExposedServerProfileRepository matches the project's transaction pattern.
   3. Reorder Initialization: In MainActivity and main.kt, ensure LegacyDatabaseMigration runs before the SessionManager attempts to load servers.
   4. Refactor runBlocking: Move migration logic out of commonMain or use a more platform-agnostic way to handle the initial one-time migration.
