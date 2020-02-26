package no.nav.su.se.bakover.db

import kotliquery.Row
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import javax.sql.DataSource

internal class DatabaseRepository(private val dataSource: DataSource): Repository {
    override fun nySak(fnr: String): Long = "insert into sak (fnr) values (:fnr::varchar)".oppdatering(mapOf("fnr" to fnr))!!
    override fun nySøknad(sakId: Long, json: String): Long = "insert into søknad (json, sakId) values (to_json(:soknad::json), :sakId)".oppdatering(mapOf("soknad" to json, "sakId" to sakId))!!
    override fun sakIdForFnr(fnr: String): Long? = "select id from sak where fnr=:fnr".hent(mapOf("fnr" to fnr)) { row -> row.long("id") }
    override fun fnrForSakId(sakId: Long): String? = "select fnr from sak where id=:id".hent(mapOf("id" to sakId)) { row -> row.string("fnr") }
    override fun søknaderForSak(sakId: Long): List<Pair<Long, String>> = "select id, json from søknad where sakId=:sakId".hentListe(mapOf("sakId" to sakId)) { row -> Pair(row.long("id"), row.string("json")) }
    override fun alleSaker(): List<Pair<Long, String>> = "select id, fnr from sak".hentListe { row -> Pair(row.long("id"), row.string("fnr")) }

    private fun String.oppdatering(params: Map<String, Any>):Long? = using(sessionOf(dataSource, returnGeneratedKey = true)) { it.run(queryOf(this, params).asUpdateAndReturnGeneratedKey) }
    private fun <T> String.hent(params: Map<String, Any> = emptyMap(), rowMapping: (Row) -> T): T? = using(sessionOf(dataSource)) { it.run(queryOf(this, params).map { row -> rowMapping(row) }.asSingle) }
    private fun <T> String.hentListe(params: Map<String, Any> = emptyMap(), rowMapping: (Row) -> T): List<T> = using(sessionOf(dataSource)) { it.run(queryOf(this, params).map { row -> rowMapping(row) }.asList) }
}