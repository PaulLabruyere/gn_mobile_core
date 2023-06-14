package fr.geonature.datasync.sync.usecase

import android.app.Application
import android.text.TextUtils
import androidx.room.withTransaction
import androidx.work.WorkInfo
import fr.geonature.commons.data.GeoNatureModuleName
import fr.geonature.commons.data.LocalDatabase
import fr.geonature.commons.data.entity.DefaultNomenclature
import fr.geonature.commons.data.entity.InputObserver
import fr.geonature.commons.data.entity.Nomenclature
import fr.geonature.commons.data.entity.NomenclatureTaxonomy
import fr.geonature.commons.data.entity.NomenclatureType
import fr.geonature.commons.data.entity.Taxon
import fr.geonature.commons.data.entity.TaxonArea
import fr.geonature.commons.data.entity.Taxonomy
import fr.geonature.commons.interactor.BaseFlowUseCase
import fr.geonature.datasync.R
import fr.geonature.datasync.api.IGeoNatureAPIClient
import fr.geonature.datasync.api.error.BaseApiException
import fr.geonature.datasync.settings.DataSyncSettings
import fr.geonature.datasync.sync.DataSyncStatus
import fr.geonature.datasync.sync.ServerStatus
import fr.geonature.datasync.sync.io.DatasetJsonReader
import fr.geonature.datasync.sync.io.TaxonomyJsonReader
import fr.geonature.datasync.sync.repository.ISynchronizeAdditionalDataRepository
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import org.json.JSONObject
import org.tinylog.Logger
import retrofit2.await
import java.io.BufferedReader
import java.util.Locale
import javax.inject.Inject

/**
 * Synchronize all local data from _GeoNature_ APIs.
 *
 * @author S. Grimault
 */
class DataSyncUseCase @Inject constructor(
    private val application: Application,
    @GeoNatureModuleName private val moduleName: String,
    private val geoNatureAPIClient: IGeoNatureAPIClient,
    private val database: LocalDatabase,
    private val synchronizeAdditionalDataRepository: ISynchronizeAdditionalDataRepository
) : BaseFlowUseCase<DataSyncStatus, DataSyncUseCase.Params>() {

    override suspend fun run(params: Params): Flow<DataSyncStatus> =
        channelFlow {
            database.withTransaction {
                checkServerUrls(params.withAdditionalData).collect {
                    sendOrThrow(
                        this,
                        it
                    )
                }
                synchronizeDataset().collect {
                    sendOrThrow(
                        this,
                        it
                    )
                }
                synchronizeObservers(params.usersMenuId).collect {
                    sendOrThrow(
                        this,
                        it
                    )
                }
                synchronizeTaxonomyRanks().collect {
                    sendOrThrow(
                        this,
                        it
                    )
                }
                synchronizeNomenclature().collect {
                    sendOrThrow(
                        this,
                        it
                    )
                }
                synchronizeTaxa(
                    params.taxRefListId,
                    params.codeAreaType,
                    params.pageSize,
                    params.withAdditionalData
                ).collect {
                    sendOrThrow(
                        this,
                        it
                    )
                }
                synchronizeAdditionalDataRepository().collect {
                    sendOrThrow(
                        this,
                        it
                    )
                }
            }
        }

    private fun checkServerUrls(withAdditionalData: Boolean = true): Flow<DataSyncStatus> =
        flow {
            runCatching { geoNatureAPIClient.getBaseUrls() }.fold(
                onSuccess = {
                    if (geoNatureAPIClient.checkSettings()) {
                        Logger.info {
                            "starting local data synchronization from '${it.geoNatureBaseUrl}' (with additional data: $withAdditionalData)..."
                        }

                        emit(DataSyncStatus(state = WorkInfo.State.SUCCEEDED))
                    } else {
                        emit(
                            DataSyncStatus(
                                state = WorkInfo.State.FAILED,
                                syncMessage = application.getString(R.string.sync_error_server_url_configuration),
                                serverStatus = ServerStatus.INTERNAL_SERVER_ERROR
                            )
                        )
                    }
                },
                onFailure = {
                    emit(
                        DataSyncStatus(
                            state = WorkInfo.State.FAILED,
                            syncMessage = application.getString(R.string.sync_error_server_url_configuration),
                            serverStatus = ServerStatus.INTERNAL_SERVER_ERROR
                        )
                    )
                },
            )
        }

    private suspend fun synchronizeDataset(): Flow<DataSyncStatus> =
        flow {
            Logger.info { "synchronize dataset..." }

            val response = runCatching {
                geoNatureAPIClient
                    .getMetaDatasets()
                    .await()
            }
                .onFailure {
                    emit(
                        onFailure(
                            it,
                            application.getString(R.string.sync_data_dataset_error)
                        )
                    )
                }
                .getOrNull()
                ?: return@flow

            val dataset = runCatching {
                DatasetJsonReader().read(
                    response
                        .byteStream()
                        .bufferedReader()
                        .use(BufferedReader::readText)
                )
            }.getOrElse { emptyList() }

            Logger.info { "dataset to update: ${dataset.size}" }

            if (dataset.isEmpty()) {
                emit(DataSyncStatus(state = WorkInfo.State.SUCCEEDED))

                return@flow
            }

            runCatching {
                database
                    .datasetDao()
                    .run {
                        deleteAll()
                        insert(*dataset.toTypedArray())
                    }
            }.onFailure {
                emit(
                    onFailure(
                        it,
                        application.getString(R.string.sync_data_dataset_error)
                    )
                )
            }

            emit(
                DataSyncStatus(
                    state = WorkInfo.State.SUCCEEDED,
                    syncMessage = application.getString(
                        R.string.sync_data_dataset,
                        dataset.size
                    )
                )
            )
        }

    private suspend fun synchronizeObservers(usersMenuId: Int = 0): Flow<DataSyncStatus> =
        flow {
            Logger.info { "synchronize users..." }

            val response = runCatching {
                geoNatureAPIClient
                    .getUsers(usersMenuId)
                    .await()
            }
                .onFailure {
                    emit(
                        onFailure(
                            it,
                            application.getString(R.string.sync_data_observers_error)
                        )
                    )
                }
                .getOrNull()
                ?: return@flow

            val inputObservers = response
                .asSequence()
                .map {
                    InputObserver(
                        it.id,
                        it.lastname,
                        it.firstname
                    )
                }
                .toList()
                .toTypedArray()

            Logger.info { "users to update: ${inputObservers.size}" }

            if (inputObservers.isEmpty()) {
                emit(DataSyncStatus(state = WorkInfo.State.SUCCEEDED))

                return@flow
            }

            runCatching {
                database
                    .inputObserverDao()
                    .run {
                        deleteAll()
                        insert(*inputObservers)
                    }
            }.onFailure {
                emit(
                    onFailure(
                        it,
                        application.getString(R.string.sync_data_observers_error)
                    )
                )
            }

            emit(
                DataSyncStatus(
                    state = WorkInfo.State.SUCCEEDED,
                    syncMessage = application.getString(
                        R.string.sync_data_observers,
                        inputObservers.size
                    )
                )
            )
        }

    private suspend fun synchronizeTaxonomyRanks(): Flow<DataSyncStatus> =
        flow {
            Logger.info { "synchronize taxonomy ranks..." }

            val response = runCatching {
                geoNatureAPIClient
                    .getTaxonomyRanks()
                    .await()
            }
                .onFailure {
                    emit(
                        onFailure(
                            it,
                            application.getString(R.string.sync_data_taxonomy_ranks_error)
                        )
                    )
                }
                .getOrNull()
                ?: return@flow

            val taxonomyRanks = runCatching {
                TaxonomyJsonReader().read(
                    response
                        .byteStream()
                        .bufferedReader()
                        .use(BufferedReader::readText)
                )
            }.getOrElse { emptyList() }

            Logger.info { "taxonomy ranks to update: ${taxonomyRanks.size}" }

            if (taxonomyRanks.isEmpty()) {
                emit(DataSyncStatus(state = WorkInfo.State.SUCCEEDED))

                return@flow
            }

            runCatching {
                database
                    .taxonomyDao()
                    .run {
                        deleteAll()
                        insert(*taxonomyRanks.toTypedArray())
                    }
            }.onFailure {
                emit(
                    onFailure(
                        it,
                        application.getString(R.string.sync_data_taxonomy_ranks_error)
                    )
                )
            }

            emit(
                DataSyncStatus(
                    state = WorkInfo.State.SUCCEEDED,
                    syncMessage = application.getString(
                        R.string.sync_data_taxonomy_ranks,
                        taxonomyRanks.size
                    )
                )
            )
        }

    private suspend fun synchronizeNomenclature(): Flow<DataSyncStatus> =
        flow {
            Logger.info { "synchronize nomenclature types..." }

            val nomenclaturesResponse = runCatching {
                geoNatureAPIClient
                    .getNomenclatures()
                    .await()
            }
                .onFailure {
                    emit(
                        onFailure(
                            it,
                            application.getString(R.string.sync_data_nomenclature_type_error)
                        )
                    )
                }
                .getOrNull()
                ?: return@flow

            val validNomenclatureTypesToUpdate = nomenclaturesResponse
                .asSequence()
                .filter { it.id > 0 }
                .filter { it.nomenclatures.isNotEmpty() }

            val nomenclatureTypesToUpdate = validNomenclatureTypesToUpdate
                .map {
                    NomenclatureType(
                        it.id,
                        it.mnemonic,
                        it.defaultLabel
                    )
                }
                .toList()
                .toTypedArray()

            Logger.info { "nomenclature types to update: ${nomenclatureTypesToUpdate.size}" }

            if (nomenclatureTypesToUpdate.isEmpty()) {
                emit(DataSyncStatus(state = WorkInfo.State.SUCCEEDED))
                return@flow
            }

            emit(
                DataSyncStatus(
                    state = WorkInfo.State.SUCCEEDED,
                    syncMessage = application.getString(
                        R.string.sync_data_nomenclature_type,
                        nomenclatureTypesToUpdate.size
                    )
                )
            )

            val nomenclaturesToUpdate = validNomenclatureTypesToUpdate
                .map { nomenclatureType ->
                    nomenclatureType.nomenclatures
                        .asSequence()
                        .filter { it.id > 0 }
                        .map {
                            Nomenclature(
                                it.id,
                                it.code,
                                if (TextUtils.isEmpty(it.hierarchy)) nomenclatureType.id.toString() else it.hierarchy!!,
                                it.defaultLabel,
                                nomenclatureType.id
                            )
                        }
                }
                .flatMap { it }
                .toList()
                .toTypedArray()

            val nomenclaturesTaxonomyToUpdate = validNomenclatureTypesToUpdate
                .map { it.nomenclatures }
                .flatMap { it.asSequence() }
                .filter { it.id > 0 }
                .map { nomenclature ->
                    (if (nomenclature.taxref.isEmpty()) arrayOf(
                        fr.geonature.datasync.api.model.NomenclatureTaxonomy(
                            Taxonomy.ANY,
                            Taxonomy.ANY
                        )
                    )
                    else nomenclature.taxref.toTypedArray())
                        .asSequence()
                        .map {
                            Taxonomy(
                                it.kingdom,
                                it.group
                            )
                        }
                        .distinct()
                        .map {
                            NomenclatureTaxonomy(
                                nomenclature.id,
                                it
                            )
                        }
                }
                .flatMap { it }
                .toList()
                .toTypedArray()

            Logger.info { "nomenclature to update: ${nomenclaturesToUpdate.size}" }

            val taxonomyToUpdate = nomenclaturesTaxonomyToUpdate
                .asSequence()
                .map { it.taxonomy }
                .toList()
                .toTypedArray()

            emit(
                DataSyncStatus(
                    state = WorkInfo.State.SUCCEEDED,
                    syncMessage = application.getString(
                        R.string.sync_data_nomenclature,
                        nomenclaturesToUpdate.size
                    )
                )
            )

            Logger.info { "synchronize nomenclature default values..." }

            val defaultNomenclatureResponse = runCatching {
                geoNatureAPIClient
                    .getDefaultNomenclaturesValues(moduleName)
                    .await()
            }
                .onFailure {
                    emit(
                        onFailure(
                            it,
                            application.getString(R.string.sync_data_nomenclature_default_error)
                        )
                    )
                }
                .getOrNull()
                ?: return@flow

            val defaultNomenclatureAsJson = runCatching {
                JSONObject(
                    defaultNomenclatureResponse
                        .byteStream()
                        .bufferedReader()
                        .use(BufferedReader::readText)
                )
            }.getOrNull()
                ?: JSONObject()

            val defaultNomenclaturesToUpdate = defaultNomenclatureAsJson
                .keys()
                .asSequence()
                .filter { mnemonic ->
                    val nomenclatureType =
                        nomenclatureTypesToUpdate.find { it.mnemonic == mnemonic }
                            ?: return@filter run {
                                Logger.warn { "no such nomenclature type '$mnemonic' with default value" }
                                false
                            }
                    val defaultNomenclatureId = defaultNomenclatureAsJson.getLong(mnemonic)

                    run {
                        val predicate =
                            nomenclaturesToUpdate.any { it.typeId == nomenclatureType.id && it.id == defaultNomenclatureId }

                        if (!predicate) {
                            Logger.warn { "no default nomenclature value found with mnemonic '$mnemonic'" }
                        }

                        predicate
                    }
                }
                .map {
                    DefaultNomenclature(
                        moduleName,
                        defaultNomenclatureAsJson.getLong(it)
                    )
                }
                .toList()
                .toTypedArray()

            Logger.info { "nomenclature default values to update: ${defaultNomenclaturesToUpdate.size}" }

            emit(
                DataSyncStatus(
                    state = WorkInfo.State.SUCCEEDED,
                    syncMessage = application.getString(
                        R.string.sync_data_nomenclature_default,
                        defaultNomenclaturesToUpdate.size
                    )
                )
            )

            runCatching {
                database
                    .nomenclatureTypeDao()
                    .run {
                        deleteAll()
                        insert(*nomenclatureTypesToUpdate)
                    }
            }.onFailure {
                emit(
                    onFailure(
                        it,
                        application.getString(R.string.sync_data_nomenclature_type_error)
                    )
                )
            }

            runCatching {
                database
                    .nomenclatureDao()
                    .run {
                        if (nomenclaturesToUpdate.isNotEmpty()) {
                            deleteAll()
                            insert(*nomenclaturesToUpdate)
                        }
                    }
            }.onFailure {
                emit(
                    onFailure(
                        it,
                        application.getString(R.string.sync_data_nomenclature_error)
                    )
                )
            }

            runCatching {
                database
                    .taxonomyDao()
                    .insertOrIgnore(*taxonomyToUpdate)
            }.onFailure {
                emit(
                    onFailure(
                        it,
                        application.getString(R.string.sync_data_taxonomy_ranks_error)
                    )
                )
            }

            runCatching {
                database
                    .nomenclatureTaxonomyDao()
                    .insert(*nomenclaturesTaxonomyToUpdate)
            }.onFailure {
                emit(
                    onFailure(
                        it,
                        application.getString(R.string.sync_data_nomenclature_error)
                    )
                )
            }

            runCatching {
                database
                    .defaultNomenclatureDao()
                    .insert(*defaultNomenclaturesToUpdate)
            }.onFailure {
                emit(
                    onFailure(
                        it,
                        application.getString(R.string.sync_data_nomenclature_default_error)
                    )
                )
            }
        }

    private suspend fun synchronizeTaxa(
        taxRefListId: Int,
        codeAreaType: String?,
        pageSize: Int,
        withAdditionalData: Boolean = true
    ): Flow<DataSyncStatus> =
        flow {
            Logger.info { "synchronize taxa..." }

            runCatching {
                database
                    .taxonDao()
                    .deleteAll()
            }.onFailure { Logger.warn(it) { "failed to deleting existing taxa" } }

            var hasNext: Boolean
            var offset = 0

            val validTaxaIds = mutableSetOf<Long>()

            // fetch all taxa from paginated list
            do {
                val taxrefResponse = runCatching {
                    geoNatureAPIClient
                        .getTaxref(
                            taxRefListId,
                            pageSize,
                            offset
                        )
                        .await()
                }
                    .onFailure {
                        Logger.warn { "taxa synchronization finished with errors" }

                        emit(
                            onFailure(
                                it,
                                application.getString(R.string.sync_data_taxa_with_errors)
                            )
                        )
                    }
                    .getOrNull()
                    ?: return@flow

                if (taxrefResponse.isEmpty()) {
                    hasNext = false
                    continue
                }

                val taxa = taxrefResponse
                    .asSequence()
                    .map { taxRef ->
                        // check if this taxon as a valid taxonomy definition
                        if (taxRef.kingdom.isNullOrBlank() || taxRef.group.isNullOrBlank()) {
                            Logger.warn { "invalid taxon with ID '${taxRef.id}' found: no taxonomy defined" }

                            return@map null
                        }

                        Taxon(
                            id = taxRef.id,
                            name = taxRef.name.trim(),
                            taxonomy = Taxonomy(
                                taxRef.kingdom,
                                taxRef.group
                            ),
                            commonName = taxRef.commonName?.trim(),
                            description = taxRef.fullName.trim(),
                            rank = ".+\\[(\\w+) - \\d+]"
                                .toRegex()
                                .find(taxRef.description)?.groupValues
                                ?.elementAtOrNull(1)
                                ?.let { "${it.uppercase(Locale.ROOT)} - ${taxRef.id}" },
                        )
                    }
                    .filterNotNull()
                    .onEach {
                        validTaxaIds.add(it.id)
                    }
                    .toList()
                    .toTypedArray()

                runCatching {
                    database
                        .taxonDao()
                        .insert(*taxa)
                }.onFailure { Logger.warn(it) { "failed to update taxa (offset: $offset)" } }

                Logger.info { "taxa to update: ${offset + taxa.size}" }

                emit(
                    DataSyncStatus(
                        state = WorkInfo.State.SUCCEEDED,
                        syncMessage = application.getString(
                            R.string.sync_data_taxa,
                            (offset + taxa.size)
                        )
                    )
                )

                offset += pageSize
                hasNext = taxrefResponse.size == pageSize
            } while (hasNext)

            delay(1000)

            if (withAdditionalData) {
                Logger.info { "synchronize taxa additional data..." }

                offset = 0

                // fetch all taxa metadata from paginated list
                do {
                    val taxrefAreasResponse = runCatching {
                        geoNatureAPIClient
                            .getTaxrefAreas(
                                codeAreaType,
                                pageSize,
                                offset
                            )
                            .await()
                    }
                        .onFailure {
                            Logger.warn { "taxa by area synchronization finished with errors" }
                            emit(
                                onFailure(
                                    it,
                                    application.getString(R.string.sync_data_taxa_areas_with_errors)
                                )
                            )
                        }
                        .getOrNull()
                        ?: return@flow

                    if (taxrefAreasResponse.isEmpty()) {
                        hasNext = false
                        continue
                    }

                    Logger.info { "found ${taxrefAreasResponse.size} taxa with areas from offset $offset" }

                    emit(
                        DataSyncStatus(
                            state = WorkInfo.State.SUCCEEDED,
                            syncMessage = application.getString(
                                R.string.sync_data_taxa_areas,
                                (offset + taxrefAreasResponse.size)
                            )
                        )
                    )

                    val taxonAreas = taxrefAreasResponse
                        .asSequence()
                        .filter { taxrefArea -> validTaxaIds.any { it == taxrefArea.taxrefId } }
                        .map {
                            TaxonArea(
                                it.taxrefId,
                                it.areaId,
                                it.color,
                                it.numberOfObservers,
                                it.lastUpdatedAt
                            )
                        }
                        .toList()
                        .toTypedArray()

                    runCatching {
                        database
                            .taxonAreaDao()
                            .insert(*taxonAreas)
                    }.onFailure { Logger.warn(it) { "failed to update taxa with areas (offset: $offset)" } }

                    Logger.info { "updating ${taxonAreas.size} taxa with areas from offset $offset" }

                    offset += pageSize
                    hasNext = taxrefAreasResponse.size == pageSize
                } while (hasNext)
            }
        }

    private fun onFailure(
        throwable: Throwable,
        errorMessage: String? = null
    ): DataSyncStatus {
        return when (throwable) {
            is BaseApiException.UnauthorizedException -> {
                DataSyncStatus(
                    state = WorkInfo.State.FAILED,
                    syncMessage = application.getString(R.string.sync_error_server_not_connected),
                    serverStatus = ServerStatus.UNAUTHORIZED
                )
            }
            is BaseApiException.InternalServerException -> {
                DataSyncStatus(
                    state = WorkInfo.State.FAILED,
                    syncMessage = application.getString(R.string.sync_error_server_error),
                    serverStatus = ServerStatus.INTERNAL_SERVER_ERROR
                )
            }
            else -> {
                DataSyncStatus(
                    state = WorkInfo.State.FAILED,
                    syncMessage = errorMessage
                )
            }
        }
    }

    /**
     * Sends synchronization status to the given channel and throw exception if its current status
     * is [WorkInfo.State.FAILED] to cancel the current transaction.
     */
    private suspend fun sendOrThrow(
        channel: SendChannel<DataSyncStatus>,
        dataSyncStatus: DataSyncStatus
    ) {
        channel.send(dataSyncStatus)

        if (dataSyncStatus.state == WorkInfo.State.FAILED) {
            delay(500)
            throw java.lang.Exception(dataSyncStatus.syncMessage)
        }
    }

    data class Params(
        val withAdditionalData: Boolean = true,
        val usersMenuId: Int = 0,
        val taxRefListId: Int = 0,
        val codeAreaType: String?,
        val pageSize: Int = DataSyncSettings.Builder.DEFAULT_PAGE_SIZE
    )
}