package me.rerere.rikkahub.ui.pages.setting

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.rerere.rikkahub.utils.WebDavUtils
import java.io.File
import javax.inject.Inject

// Corrected database name based on grep result
const val DATABASE_NAME = "rikka_hub"
const val DATASTORE_PREFERENCES_NAME = "settings" // from DataStoreModule.kt: preferencesDataStore(name = "settings")
const val DATASTORE_FILE_NAME = "${DATASTORE_PREFERENCES_NAME}.preferences_pb"


// Sealed interface for status, defined outside or in a common place if used by multiple ViewModels
sealed class BackupRestoreStatus {
    object Idle : BackupRestoreStatus()
    data class RestoredNeedRestart(val message: String) : BackupRestoreStatus() // Added for restore
    data class InProgress(val message: String) : BackupRestoreStatus()
    data class Success(val message: String) : BackupRestoreStatus()
    data class Error(val message: String) : BackupRestoreStatus()
}

sealed class TestConnectionStatus {
    object Idle : TestConnectionStatus()
    object Testing : TestConnectionStatus()
    data class Success(val message: String) : TestConnectionStatus()
    data class Error(val message: String) : TestConnectionStatus()
}

@HiltViewModel
class WebDavViewModel @Inject constructor(
    private val application: Application,
    private val appDatabase: me.rerere.rikkahub.data.db.AppDatabase // Injected AppDatabase
    // private val settingsStore: SettingsStore // Example, not used for now
) : ViewModel() {

    private val _backupStatus = MutableStateFlow<BackupRestoreStatus>(BackupRestoreStatus.Idle)
    val backupStatus: StateFlow<BackupRestoreStatus> = _backupStatus.asStateFlow()

    private val _testConnectionStatus = MutableStateFlow<TestConnectionStatus>(TestConnectionStatus.Idle)
    val testConnectionStatus: StateFlow<TestConnectionStatus> = _testConnectionStatus.asStateFlow()

    // TODO: Add LiveData/StateFlow for lastBackupTime and load it from preferences

    private fun getDatabasePath(): String {
        return application.getDatabasePath(DATABASE_NAME).absolutePath
    }

    private fun getDataStorePath(): String {
        // Path for DataStore preferences is typically context.filesDir + "datastore/" + PREFERENCES_NAME + ".preferences_pb"
        return File(application.filesDir, "datastore/$DATASTORE_FILE_NAME").absolutePath
    }

    fun backupData(serverUrl: String, username: String, password: String, remoteDirName: String = "RikkaHubBackup") {
        viewModelScope.launch(Dispatchers.IO) {
            _backupStatus.value = BackupRestoreStatus.InProgress("Starting backup...")
            Log.d("WebDavViewModel", "Backup started. Server: $serverUrl, User: $username, RemoteDir: $remoteDirName")

            try {
                val webDavUtils = WebDavUtils(serverUrl, username, password)

                // Construct the full URL for the remote directory.
                // Sardine methods operate on URLs. If serverUrl is "https://host.com/dav/",
                // and remoteDirName is "RikkaHubBackup", then target dir is "https://host.com/dav/RikkaHubBackup/"
                val fullRemoteDirPath = buildFullUrl(serverUrl, remoteDirName) + "/" // Ensure trailing slash for directory

                _backupStatus.value = BackupRestoreStatus.InProgress("Checking/Creating remote directory: $remoteDirName")
                Log.d("WebDavViewModel", "Attempting to create remote directory: $fullRemoteDirPath")
                if (!webDavUtils.sardine.exists(fullRemoteDirPath)) {
                    webDavUtils.sardine.createDirectory(fullRemoteDirPath)
                    Log.i("WebDavViewModel", "Remote directory created: $fullRemoteDirPath")
                } else {
                    Log.i("WebDavViewModel", "Remote directory already exists: $fullRemoteDirPath")
                }

                val dbPath = getDatabasePath()
                val dataStorePath = getDataStorePath()

                Log.d("WebDavViewModel", "Database path: $dbPath")
                Log.d("WebDavViewModel", "DataStore path: $dataStorePath")

                val dbFile = File(dbPath)
                if (dbFile.exists()) {
                    _backupStatus.value = BackupRestoreStatus.InProgress("Backing up database (${dbFile.name})...")
                    Log.d("WebDavViewModel", "Uploading database...")
                    val dbUploadSuccess = webDavUtils.uploadFile(dbPath, "$remoteDirName/${dbFile.name}")
                    if (!dbUploadSuccess) {
                        _backupStatus.value = BackupRestoreStatus.Error("Failed to upload database.")
                        Log.e("WebDavViewModel", "Database upload failed.")
                        return@launch
                    }
                    Log.i("WebDavViewModel", "Database uploaded successfully.")
                } else {
                    _backupStatus.value = BackupRestoreStatus.Error("Database file not found at: $dbPath")
                    Log.e("WebDavViewModel", "Database file not found: $dbPath")
                    return@launch
                }

                val dataStoreFile = File(dataStorePath)
                if (dataStoreFile.exists()) {
                    _backupStatus.value = BackupRestoreStatus.InProgress("Backing up settings (${dataStoreFile.name})...")
                    Log.d("WebDavViewModel", "Uploading settings...")
                    val settingsUploadSuccess = webDavUtils.uploadFile(dataStorePath, "$remoteDirName/${dataStoreFile.name}")
                    if (!settingsUploadSuccess) {
                        _backupStatus.value = BackupRestoreStatus.Error("Failed to upload settings.")
                        Log.e("WebDavViewModel", "Settings upload failed.")
                        return@launch
                    }
                    Log.i("WebDavViewModel", "Settings uploaded successfully.")
                } else {
                    _backupStatus.value = BackupRestoreStatus.Error("Settings file not found at: $dataStorePath")
                    Log.e("WebDavViewModel", "Settings file not found: $dataStorePath")
                    return@launch
                }

                _backupStatus.value = BackupRestoreStatus.Success("Backup completed successfully!")
                Log.i("WebDavViewModel", "Backup completed successfully.")
                // TODO: Update lastBackupTime here

            } catch (e: Exception) {
                _backupStatus.value = BackupRestoreStatus.Error("Backup failed: ${e.message}")
                Log.e("WebDavViewModel", "Backup error", e)
            }
        }
    }

    private fun buildFullUrl(serverBaseUrl: String, relativePath: String): String {
        val base = if (serverBaseUrl.endsWith("/")) serverBaseUrl else "$serverBaseUrl/"
        val relative = if (relativePath.startsWith("/")) relativePath.substring(1) else relativePath
        return base + relative
    }

    // TODO: Implement restoreData function
    // TODO: Implement testConnection function (maybe call from UI and update status here)

    fun restoreData(serverUrl: String, username: String, password: String, remoteDirName: String = "RikkaHubBackup") {
        viewModelScope.launch(Dispatchers.IO) {
            _backupStatus.value = BackupRestoreStatus.InProgress("Starting restore...")
            Log.d("WebDavViewModel", "Restore started. Server: $serverUrl, User: $username, RemoteDir: $remoteDirName")

            try {
                val webDavUtils = WebDavUtils(serverUrl, username, password)

                val dbFileName = application.getDatabasePath(DATABASE_NAME).name
                val dataStoreFileName = File(application.filesDir, "datastore/$DATASTORE_FILE_NAME").name

                val localDbPath = getDatabasePath()
                val localDataStorePath = getDataStorePath()

                // Remote paths for download
                val remoteDbDownloadPath = "$remoteDirName/$dbFileName"
                val remoteDataStoreDownloadPath = "$remoteDirName/$dataStoreFileName"

                // Optional: Check if files exist on server before attempting download
                val fullRemoteDbUrl = buildFullUrl(serverUrl, remoteDbDownloadPath)
                val fullRemoteDsUrl = buildFullUrl(serverUrl, remoteDataStoreDownloadPath)

                Log.d("WebDavViewModel", "Checking server for database file: $fullRemoteDbUrl")
                if (!webDavUtils.sardine.exists(fullRemoteDbUrl)) {
                    _backupStatus.value = BackupRestoreStatus.Error("Database backup file not found on server at: $remoteDbDownloadPath")
                    Log.e("WebDavViewModel", "Remote database file not found: $fullRemoteDbUrl")
                    return@launch
                }
                Log.d("WebDavViewModel", "Checking server for settings file: $fullRemoteDsUrl")
                if (!webDavUtils.sardine.exists(fullRemoteDsUrl)) {
                    _backupStatus.value = BackupRestoreStatus.Error("Settings backup file not found on server at: $remoteDataStoreDownloadPath")
                    Log.e("WebDavViewModel", "Remote settings file not found: $fullRemoteDsUrl")
                    return@launch
                }
                Log.i("WebDavViewModel", "Backup files found on server.")

                // Close the database BEFORE replacing its file
                _backupStatus.value = BackupRestoreStatus.InProgress("Closing local database...")
                Log.d("WebDavViewModel", "Closing local database...")
                if (appDatabase.isOpen) {
                    appDatabase.close()
                    Log.i("WebDavViewModel", "Local database closed.")
                } else {
                    Log.i("WebDavViewModel", "Local database was already closed.")
                }
                // DataStore does not have a direct close method for file manipulation.
                // Overwriting the file is generally handled by the OS, changes picked up on next DataStore read/app restart.

                _backupStatus.value = BackupRestoreStatus.InProgress("Downloading database ($dbFileName)...")
                Log.d("WebDavViewModel", "Downloading database from $remoteDbDownloadPath to $localDbPath")
                val dbDownloadSuccess = webDavUtils.downloadFile(remoteDbDownloadPath, localDbPath)
                if (!dbDownloadSuccess) {
                    _backupStatus.value = BackupRestoreStatus.Error("Failed to download database.")
                    Log.e("WebDavViewModel", "Database download failed from $remoteDbDownloadPath")
                    // Attempt to re-open DB if it was closed? Or just let user restart.
                    return@launch
                }
                Log.i("WebDavViewModel", "Database downloaded successfully.")

                _backupStatus.value = BackupRestoreStatus.InProgress("Downloading settings ($dataStoreFileName)...")
                Log.d("WebDavViewModel", "Downloading settings from $remoteDataStoreDownloadPath to $localDataStorePath")
                val settingsDownloadSuccess = webDavUtils.downloadFile(remoteDataStoreDownloadPath, localDataStorePath)
                if (!settingsDownloadSuccess) {
                    _backupStatus.value = BackupRestoreStatus.Error("Failed to download settings.")
                    Log.e("WebDavViewModel", "Settings download failed from $remoteDataStoreDownloadPath")
                    return@launch
                }
                Log.i("WebDavViewModel", "Settings downloaded successfully.")

                _backupStatus.value = BackupRestoreStatus.RestoredNeedRestart("Restore completed successfully! Please restart the app.")
                Log.i("WebDavViewModel", "Restore completed. App restart needed.")

            } catch (e: Exception) {
                _backupStatus.value = BackupRestoreStatus.Error("Restore failed: ${e.message}")
                Log.e("WebDavViewModel", "Restore error", e)
                // If DB was closed and restore failed, it remains closed. App restart will handle re-initialization.
            }
        }
    }
}
