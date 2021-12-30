package fr.geonature.commons.data.dao

import androidx.room.Dao
import fr.geonature.commons.data.model.DefaultNomenclature

/**
 * Data access object for [DefaultNomenclature].
 *
 * @author S. Grimault
 */
@Dao
abstract class DefaultNomenclatureDao : BaseDao<DefaultNomenclature>()
