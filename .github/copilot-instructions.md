# QuillPad AI Coding Instructions

## Architecture Overview
QuillPad is an Android notes app with Markdown support, task lists, notebooks, tags, reminders, and attachments. It uses MVVM architecture with Room database, Koin dependency injection, and Navigation Component.

- **Data Layer**: Room entities (NoteEntity, Notebook, Tag, etc.) with relations via Junction tables. Domain models (Note) include relations loaded eagerly.
- **UI Layer**: Fragments with ViewModels, Navigation Drawer for main navigation. Editor fragment for note creation/editing.
- **Sync**: Supports filesystem and Nextcloud backends via BackendProvider interface.
- **Key Components**: AppDatabase (Room), Repositories (NoteRepository, etc.), Koin modules for DI.

## Key Patterns
- **Models**: Separate Entity (Room) and domain models. Use `toEntity()` to convert domain to storable. Relations loaded via `@Relation` in domain models.
- **DI**: Koin modules in `di/` package. Inject ViewModels with `by viewModel()`, services with `by inject()`.
- **Navigation**: Use NavController with `navigateSafely()` extension. Pass bundles for arguments.
- **Markdown**: Markwon library for rendering. Convert task lists between Markdown (`- [x] item`) and NoteTask objects.
- **Attachments**: Store in app's private storage under `media/` folder. Use Attachment model with URI and metadata.
- **Workers**: WorkManager for background tasks (BinCleaningWorker, SyncWorker). Enqueue in App.kt.

## Development Workflow
- **Build**: `./gradlew assembleDebug` or `build` task. Release builds require keystore properties.
- **Test**: `./gradlew test` for unit tests, `connectedAndroidTest` for instrumented. Schemas exported to `schemas/` for testing.
- **Debug**: StrictMode enabled in debug builds. ACRA for crash reporting (disabled by default).
- **Sync Testing**: Use filesystem sync for local testing. Nextcloud requires server setup.

## Conventions
- **Naming**: PascalCase for classes, camelCase for variables. Fragments prefixed with `Fragment_`.
- **Database**: Migrations in AppDatabase.kt. Export schema with `exportSchema = true`.
- **Strings**: All UI text in `strings.xml`, support for 20+ languages.
- **Colors/Themes**: Custom NoteColor enum for note backgrounds. Dark/Light themes supported.
- **Intents**: Handle ACTION_SEND for sharing text/media. Create notes from shared content.

## Examples
- **Create Note**: `Note(title="Test", content="Content", isList=false).toEntity()` then insert via repository.
- **Task List**: Parse Markdown with `mdToTaskList()` method on Note. Render with `taskListToMd()`.
- **Navigation**: `navController.navigateSafely(R.id.fragment_editor, bundleOf("noteId" to note.id))`
- **Sync**: Implement BackendProvider for new sync methods. Use Converters.kt for serialization.

Focus on Room relations, Koin injection, and Navigation safety. Preserve existing patterns for consistency.

## Security & Hardening 🔒
- Android backups: **`android:allowBackup` was set to true**. This exposes app data via device backups; set it to `false` (changed in `AndroidManifest.xml`).
- Network/TLS:
  - The app previously allowed user-installed CAs globally (`res/xml/network_security_config.xml` included user certs). This is a high-risk MITM vector; we removed global user cert trust and documented the need for domain-scoped policies or certificate pinning.
  - The Nextcloud client (`data/sync/nextcloud/NextcloudAPIProvider.kt`) implements an *opt-in* "trust self-signed" mode that currently accepts all certificates and disables hostname verification. This is insecure; prefer certificate pinning or a TOFU workflow. We reduced logging in release builds and added an explicit warning in the code. (Action: replace the trust-all approach with pinning/TOFU.)
- Credentials storage: Nextcloud credentials are stored using `EncryptedSharedPreferences` (see `di/PreferencesModule.kt`) — good. The helper `PreferenceRepository.getEncryptedString` reads from FlowSharedPreferences wrapping the encrypted shared prefs.
- Logging: Sensitive fields (Authorization / Cookie) are redacted; logging levels are now disabled in release builds (see Nextcloud provider changes). Avoid logging filenames, URIs, or credential details in release builds.
- Shared media & attachments:
  - Incoming shared media copies are now size-limited to 10 MB (see `App.MAX_SHARED_ATTACHMENT_SIZE_BYTES` and `MainActivity.copySharedMedia`) to prevent resource-exhaustion attacks.
  - Attachment filenames are sanitized when creating files in the filesystem-backed sync (`StorageBackend.kt`) — we added sanitization and length limits to prevent accidental path issues.
- Backups & restore:
  - Backup manager unpacks ZIPs to app-private storage and maps filenames safely by taking the last path segment; still, we recommend adding size and entry-count limits to avoid zip-bomb attacks.
- CI & Dependency hygiene:
  - Run automated dependency scanning (OWASP Dependency-Check or Gradle plugin) and keep `libs.versions.toml` up-to-date (notable deps: OkHttp, ACRA, security-crypto).
  - Ensure release builds do not enable verbose logging or include debug-only libraries.

Quick actionable checklist (priorities):
1. Replace "trust-all" TLS fallback with certificate pinning or a TOFU flow for Nextcloud (High). Reference: `NextcloudAPIProvider.kt` and `res/xml/network_security_config.xml`. ✅ (documented)
   - Note: A basic certificate-pinning scaffold has been added: the app reads `PreferenceRepository.NEXTCLOUD_CERT_FINGERPRINT` and will use a pinned client if present. A TOFU (trust-on-first-use) UI has been added to `SyncSettingsFragment` to fetch a server certificate fingerprint and store it in encrypted prefs. Tests were added (scaffold) in `app/src/test/java/org/qosp/notes/data/sync/nextcloud/NextcloudAPIFingerprintTest.kt`. Next steps: add integration tests and UI tests to validate the end-to-end TOFU flow.
2. Keep `android:allowBackup=false` (done). ✅
3. Enforce attachment size limits, sanitize filenames, and limit backup zip sizes/entry counts (Medium) — some fixes implemented (file size, sanitization), add zip limits.
3. Add a CI job to run dependency vulnerability scans and fail on critical CVEs (Medium).  
   - A workflow `./github/workflows/dependency-scan.yml` was added to run OWASP Dependency-Check on PRs; consider failing the PR on critical findings and uploading SARIF for GitHub code scanning integration.
5. Audit logs for any remaining PII or secrets and gate them behind `BuildConfig.DEBUG` (Low) — started in a few places.

If you want, I can open PR(s) with: 1) the manifest + attachment/file hardenings (already applied); 2) a follow-up that implements a TOFU cert acceptance workflow and a CI dependency scan and tests for zip limits.

---
**Files worth reviewing for security context:**
- `app/src/main/AndroidManifest.xml` (backup, exported components)
- `app/src/main/res/xml/network_security_config.xml` (trust anchors)
- `app/src/main/java/org/qosp/notes/data/sync/nextcloud/NextcloudAPIProvider.kt` (TLS client)
- `app/src/main/java/org/qosp/notes/di/PreferencesModule.kt` (EncryptedSharedPreferences)
- `app/src/main/java/org/qosp/notes/data/sync/fs/StorageBackend.kt` (filename handling, filesystem sync)
- `app/src/main/java/org/qosp/notes/components/backup/BackupManager.kt` (backup zip handling)
- `app/src/main/java/org/qosp/notes/ui/MainActivity.kt` (shared media input handling)

Please tell me which of the follow-ups you'd like me to implement first (TOFU / pinning, CI scan, zip limits, or audit logs), and I’ll proceed.</content>
<parameter name="filePath">d:\jaspe\OneDrive\Coding\quillpad\.github\copilot-instructions.md