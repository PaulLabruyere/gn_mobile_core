package fr.geonature.sync.api

import fr.geonature.sync.api.model.Taxref
import fr.geonature.sync.api.model.TaxrefArea
import fr.geonature.sync.api.model.User
import retrofit2.Call
import retrofit2.http.GET

/**
 * GeoNature API interface definition.
 *
 * @author [S. Grimault](mailto:sebastien.grimault@gmail.com)
 */
interface GeoNatureService {

    @GET("geonature/api/users/menu/1")
    fun getUsers(): Call<List<User>>

    @GET("taxhub/api/taxref/allnamebylist/100")
    fun getTaxref(): Call<List<Taxref>>

    @GET("geonature/api/synthese/color_taxon")
    fun getTaxrefAreas(): Call<List<TaxrefArea>>
}