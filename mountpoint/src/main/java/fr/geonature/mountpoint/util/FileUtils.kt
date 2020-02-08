package fr.geonature.mountpoint.util

import android.content.Context
import android.os.Environment
import android.util.Log
import fr.geonature.mountpoint.model.MountPoint
import fr.geonature.mountpoint.util.MountPointUtils.getExternalStorage
import fr.geonature.mountpoint.util.MountPointUtils.getInternalStorage
import java.io.File

/**
 * Helpers for [File] utilities.
 *
 * @author [S. Grimault](mailto:sebastien.grimault@gmail.com)
 */
object FileUtils {

    private val TAG = FileUtils::class.java.name

    /**
     * Construct a file from the set of name elements.
     *
     * @param directory the parent directory
     * @param names the name elements
     *
     * @return the corresponding file
     */
    fun getFile(
        directory: File,
        vararg names: String
    ): File {

        var file = directory

        for (name in names) {
            file = File(
                file,
                name
            )
        }

        return file
    }

    /**
     * Gets the relative path used by given package ID.
     *
     * @param packageId the package ID
     *
     * @return the relative path
     */
    fun getRelativeSharedPath(packageId: String): String {
        return arrayOf(
            "Android",
            "data",
            packageId
        ).joinToString(
            File.separator,
            "",
            File.separator
        )
    }

    /**
     * Tries to find the mount point used by external storage as `File`.
     * If not, returns the default `Environment.getExternalStorageDirectory()`.
     *
     * @param context the current `Context`
     *
     * @return the mount point as `File` used by external storage if available
     */
    fun getExternalStorageDirectory(context: Context): File {

        val externalMountPoint = getExternalStorage(
            context,
            Environment.MEDIA_MOUNTED,
            Environment.MEDIA_MOUNTED_READ_ONLY
        )

        if (externalMountPoint == null) {
            Log.w(
                TAG,
                "getExternalStorageDirectory: external mount point is not available. Use default: " + getInternalStorage()
            )

            return getInternalStorage().mountPath
        }

        return externalMountPoint.mountPath
    }

    /**
     * Gets the root folder as `File` used by this context.
     *
     * @param context the current `Context`
     * @param storageType the [MountPoint.StorageType] to use
     *
     * @return the root folder as `File`
     */
    fun getRootFolder(
        context: Context,
        storageType: MountPoint.StorageType
    ): File {

        return getFile(
            if (storageType === MountPoint.StorageType.EXTERNAL) getExternalStorageDirectory(context)
            else getInternalStorage().mountPath,
            getRelativeSharedPath(context.packageName)
        )
    }
}