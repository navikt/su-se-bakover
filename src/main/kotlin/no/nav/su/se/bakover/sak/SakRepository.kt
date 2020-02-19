package no.nav.su.se.bakover.sak

import com.google.gson.JsonObject
import kotliquery.Row
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Søknad
import javax.sql.DataSource

interface SakRepo {
    fun opprettSak(fnr: String): Long
    fun hentSak(fnr: String): Sak?
    fun hentSak(id: Long): Sak?
    fun hentAlleSaker(): List<Sak>
    fun hentSoknadForPerson(fnr: String): Søknad?
    fun hentSøknad(søknadId: Long): Søknad?
    fun lagreSøknad(søknadJson: JsonObject, sakId: Long): Long?
    fun hentSøknaderForSak(sakId: Long): List<Søknad>
}

internal class SakRepository(
        private val dataSource: DataSource
) : SakRepo {
    private fun String.oppdatering(params: Map<String, Any>):Long? =
        using(sessionOf(dataSource, returnGeneratedKey = true)) {
            it.run(queryOf(this, params).asUpdateAndReturnGeneratedKey)
        }

    private fun <T> String.hent(params: Map<String, Any> = emptyMap(), rowMapping: (Row) -> T): T? = using(sessionOf(dataSource)) {
        it.run(queryOf(this, params).map { row -> rowMapping(row) }.asSingle)
    }

    private fun <T> String.hentListe(params: Map<String, Any> = emptyMap(), rowMapping: (Row) -> T): List<T> = using(sessionOf(dataSource)) {
        it.run(queryOf(this, params).map { row -> rowMapping(row) }.asList)
    }

    override fun hentSak(fnr: String): Sak? = "select * from sak where fnr=:fnr".hent(mapOf("fnr" to fnr)) { toSak(it) }
    override fun hentSak(id: Long): Sak? = "select * from sak where id=:id".hent(mapOf("id" to id)) { toSak(it) }
    override fun hentAlleSaker(): List<Sak> = "select * from sak".hentListe { toSak(it) }
    override fun opprettSak(fnr: String): Long = "insert into sak (fnr) values (:fnr::varchar)".oppdatering(mapOf("fnr" to fnr))!!
    // TODO: List not single
    override fun hentSoknadForPerson(fnr: String): Søknad? = "SELECT * FROM søknad WHERE json#>>'{personopplysninger,fnr}'=:fnr".hent(mapOf("fnr" to fnr)) { toSøknad(it) }
    override fun hentSøknaderForSak(sakId: Long): List<Søknad> = "SELECT * FROM søknad WHERE sakId=:sakId".hentListe(mapOf("sakId" to sakId)) { toSøknad(it) }
    override fun hentSøknad(søknadId: Long): Søknad? = "SELECT * FROM søknad WHERE id=:id".hent(mapOf("id" to søknadId)) { toSøknad(it) }
    override fun lagreSøknad(søknadJson: JsonObject, sakId: Long): Long? = "INSERT INTO søknad (json, sakId) VALUES (to_json(:soknad::json), :sakId)".oppdatering(mapOf("soknad" to søknadJson.toString(), "sakId" to sakId))
}

private fun toSak(row: Row): Sak {
    return Sak(row.long("id"), row.string("fnr"))
}

private fun toSøknad(row: Row) = Søknad(
    row.long("id"),
    row.string("json"),
    row.long("sakId")
)