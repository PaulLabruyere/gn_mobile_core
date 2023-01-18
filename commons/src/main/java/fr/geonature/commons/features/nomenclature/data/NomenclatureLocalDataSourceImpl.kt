package fr.geonature.commons.features.nomenclature.data

import fr.geonature.commons.data.dao.NomenclatureDao
import fr.geonature.commons.data.dao.NomenclatureTypeDao
import fr.geonature.commons.data.entity.Nomenclature
import fr.geonature.commons.data.entity.NomenclatureType
import fr.geonature.commons.data.entity.Taxonomy
import fr.geonature.commons.features.nomenclature.error.NomenclatureException

/**
 * Default implementation of [INomenclatureLocalDataSource] using local database.
 *
 * @author S. Grimault
 */
class NomenclatureLocalDataSourceImpl(
    private val moduleName: String,
    private val nomenclatureTypeDao: NomenclatureTypeDao,
    private val nomenclatureDao: NomenclatureDao
) : INomenclatureLocalDataSource {

    override suspend fun getAllNomenclatureTypes(): List<NomenclatureType> {
        return nomenclatureTypeDao
            .findAll()
            .also {
                if (it.isEmpty()) throw NomenclatureException.NoNomenclatureTypeFoundException
            }
    }

    override suspend fun getAllDefaultNomenclatureValues(): List<Nomenclature> {
        return nomenclatureDao.findAllDefaultNomenclatureValues(moduleName)
    }

    override suspend fun getNomenclatureValuesByTypeAndTaxonomy(
        mnemonic: String,
        taxonomy: Taxonomy?
    ): List<Nomenclature> {
        return nomenclatureDao
            .findAllByNomenclatureTypeAndByTaxonomy(
                mnemonic,
                taxonomy?.kingdom,
                taxonomy?.group
            )
            .also {
                if (it.isEmpty()) throw NomenclatureException.NoNomenclatureValuesFoundException(mnemonic)
            }
    }
}