package me.rerere.rikkahub.utils

import com.thegrizzlylabs.sardineandroid.Sardine
import com.thegrizzlylabs.sardineandroid.impl.okhttp.OkHttpSardine
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

class WebDavUtils(
    private val serverUrl: String, // Should be the full base URL, e.g., "https://dav.example.com/remote.php/dav/files/username/"
    private val username: String,
    private val password: String
) {
    // Made public to allow ViewModel to call createDirectory directly for now
    // Consider adding a dedicated method in WebDavUtils for directory creation if preferred
    val sardine: Sardine = OkHttpSardine()

    init {
        sardine.setCredentials(username, password)
        // It's good practice to ensure the serverUrl ends with a slash if it's a directory path
        // However, sardine.put/get/list will append paths, so it might be okay.
        // For now, assuming serverUrl is correctly formatted by the caller.
    }

    /**
     * Uploads a local file to the WebDAV server.
     *
     * @param localPath The path to the local file to upload.
     * @param remotePath The relative path on the server where the file should be stored.
     *                   For example, if serverUrl is "https://host/dav/" and remotePath is "documents/file.txt",
     *                   the file will be uploaded to "https://host/dav/documents/file.txt".
     * @return True if successful, false otherwise.
     */
    fun uploadFile(localPath: String, remotePath: String): Boolean {
        return try {
            val localFile = File(localPath)
            if (!localFile.exists()) {
                // Log error: Local file does not exist
                return false
            }
            val inputStream = FileInputStream(localFile)
            val fullRemoteUrl = buildFullUrl(remotePath)

            // Ensure parent directories exist (optional, depends on server capabilities and library behavior)
            // MKCOL is the WebDAV method to create collections (directories)
            // For simplicity, we'll assume the library or server handles this, or parent path is pre-existing.
            // Alternatively, one could try to create parent directories:
            // val parent = File(remotePath).parent
            // if (parent != null && parent.isNotEmpty()) {
            //     sardine.createDirectory(buildFullUrl(parent))
            // }

            sardine.put(fullRemoteUrl, inputStream)
            true
        } catch (e: IOException) {
            // Log error (e.g., network issue, file access issue)
            e.printStackTrace() // Replace with proper logging
            false
        } catch (e: Exception) {
            // Log other errors (e.g., SardineException for WebDAV specific errors)
            e.printStackTrace() // Replace with proper logging
            false
        }
    }

    /**
     * Downloads a file from the WebDAV server to a local path.
     *
     * @param remotePath The relative path on the server of the file to download.
     * @param localPath The path where the downloaded file should be saved locally.
     * @return True if successful, false otherwise.
     */
    fun downloadFile(remotePath: String, localPath: String): Boolean {
        return try {
            val fullRemoteUrl = buildFullUrl(remotePath)
            val inputStream = sardine.get(fullRemoteUrl)
            val localFile = File(localPath)

            // Ensure parent directory for local file exists
            localFile.parentFile?.mkdirs()

            FileOutputStream(localFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
            true
        } catch (e: IOException) {
            // Log error
            e.printStackTrace() // Replace with proper logging
            false
        } catch (e: Exception) {
            // Log other errors
            e.printStackTrace() // Replace with proper logging
            false
        }
    }

    /**
     * Tests the connection to the WebDAV server by attempting to list resources.
     *
     * @param path An optional path to list resources from. Defaults to the serverUrl.
     * @return True if the connection and authentication are successful, false otherwise.
     */
    fun testConnection(path: String = ""): Boolean {
        return try {
            val pathToTest = if (path.isEmpty()) serverUrl else buildFullUrl(path)
            sardine.list(pathToTest) // Throws exception on failure (e.g., auth, not found)
            true
        } catch (e: Exception) {
            // Log error (e.g., AuthenticationFailedException, FileNotFoundException, IOException)
            e.printStackTrace() // Replace with proper logging
            false
        }
    }

    /**
     * Helper function to construct the full URL for a remote resource.
     * Ensures that there's exactly one slash between the serverUrl and the relative path.
     */
    private fun buildFullUrl(remotePath: String): String {
        val base = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"
        val relative = if (remotePath.startsWith("/")) remotePath.substring(1) else remotePath
        return base + relative
    }
}
