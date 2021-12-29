package fr.geonature.commons.settings

import android.annotation.SuppressLint
import android.app.Application
import android.util.Log
import fr.geonature.commons.data.helper.Provider
import fr.geonature.commons.settings.io.AppSettingsJsonReader
import fr.geonature.mountpoint.model.MountPoint.StorageType.INTERNAL
import fr.geonature.mountpoint.util.FileUtils.getFile
import fr.geonature.mountpoint.util.FileUtils.getRootFolder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileReader

/**
 * Default implementation of [IAppSettingsManager].
 *
 * @author S. Grimault
 */
class AppSettingsManagerImpl<AS : IAppSettings>(
    internal val application: Application,
    onAppSettingsJsonJsonReaderListener: AppSettingsJsonReader.OnAppSettingsJsonReaderListener<AS>,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : IAppSettingsManager<AS> {
    private val appSettingsJsonReader: AppSettingsJsonReader<AS> = AppSettingsJsonReader(onAppSettingsJsonJsonReaderListener)

    override fun getAppSettingsFilename(): String {
        val packageName = application.packageName

        return "settings_${packageName.substring(packageName.lastIndexOf('.') + 1)}.json"
    }

    override suspend fun loadAppSettings(): AS? =
        withContext(dispatcher) {
            val appSettings = loadAppSettingsFromUri()
                ?: loadAppSettingsFromFile()

            if (appSettings == null) {
                Log.w(
                    TAG,
                    "Failed to load '${getAppSettingsFilename()}'"
                )
            }

            appSettings
        }

    internal fun getAppSettingsAsFile(): File {
        return getFile(
            getRootFolder(
                application,
                INTERNAL
            ),
            getAppSettingsFilename()
        )
    }

    @SuppressLint("Recycle")
    private fun loadAppSettingsFromUri(): AS? {
        val appSettingsUri = Provider.buildUri(
            "settings",
            getAppSettingsFilename()
        )

        Log.i(
            TAG,
            "Loading settings from URI '${appSettingsUri}'..."
        )

        return runCatching {
            application.contentResolver
                .acquireContentProviderClient(appSettingsUri)
                ?.let {
                    val appSettings = it
                        .openFile(
                            appSettingsUri,
                            "r"
                        )
                        ?.let { pfd ->
                            val appSettings = runCatching { appSettingsJsonReader.read(FileReader(pfd.fileDescriptor)) }.getOrNull()

                            if (appSettings == null) {
                                Log.w(
                                    TAG,
                                    "failed to load settings from URI '${appSettingsUri}'"
                                )
                            }

                            pfd.close()

                            appSettings
                        }

                    it.close()

                    appSettings
                }
        }.getOrNull()
    }

    private fun loadAppSettingsFromFile(): AS? {
        val appSettingsJsonFile = getAppSettingsAsFile()

        Log.i(
            TAG,
            "Loading settings from '${appSettingsJsonFile.absolutePath}'..."
        )

        if (!appSettingsJsonFile.exists()) {
            Log.w(
                TAG,
                "'${appSettingsJsonFile.absolutePath}' not found"
            )

            return null
        }

        return runCatching { appSettingsJsonReader.read(FileReader(appSettingsJsonFile)) }.getOrNull()
    }

    companion object {
        private val TAG = AppSettingsManagerImpl::class.java.name
    }
}
