package fr.geonature.sync.sync.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import fr.geonature.sync.api.GeoNatureAPIClient
import fr.geonature.sync.sync.PackageInfoManager
import retrofit2.Response
import retrofit2.awaitResponse

/**
 * Checks all application packages to update.
 *
 * @author [S. Grimault](mailto:sebastien.grimault@gmail.com)
 */
class CheckAppPackagesToUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(
    appContext,
    workerParams
) {
    private val packageInfoManager =
        PackageInfoManager.getInstance(applicationContext)

    override suspend fun doWork(): Result {
        val geoNatureAPIClient = GeoNatureAPIClient.instance(applicationContext)
            ?: return Result.failure()

        Log.i(
            TAG,
            "check available applications from '${geoNatureAPIClient.geoNatureBaseUrl}'..."
        )

        return try {
            val response = geoNatureAPIClient.getApplications()
                .awaitResponse()

            checkResponse(response).run {
                if (this is Result.Failure) {
                    return this
                }
            }

            val appPackages = response.body() ?: return Result.failure()

            packageInfoManager.setAppPackagesToUpdate(appPackages)

            Log.i(
                TAG,
                "available applications to update: ${appPackages.size}"
            )

            Result.success()
        } catch (e: Exception) {
            Log.w(
                TAG,
                e
            )

            Result.failure()
        }
    }

    private fun checkResponse(response: Response<*>): Result {
        if (!response.isSuccessful) {
            return Result.failure()
        }

        return Result.success()
    }

    companion object {
        private val TAG = CheckAppPackagesToUpdateWorker::class.java.name

        // The name of the synchronization work
        const val CHECK_APP_PACKAGES_WORKER = "check_app_packages_worker"
        const val CHECK_APP_PACKAGES_WORKER_TAG = "check_app_packages_worker_tag"
    }
}