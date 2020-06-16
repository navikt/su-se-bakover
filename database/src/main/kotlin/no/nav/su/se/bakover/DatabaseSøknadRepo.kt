package no.nav.su.se.bakover

import kotliquery.Row
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import javax.sql.DataSource

class DatabaseSøknadRepo(private val dataSource: DataSource) :
    SøknadRepo, StønadsperiodeRepo, SakRepo, BehandlingRepo {
    override fun nySak(fnr: Fødselsnummer): Long = "insert into sak (fnr) values (:fnr::varchar)"
            .oppdatering(mapOf("fnr" to fnr.toString()))!!

    override fun lagreSøknad(json: String): Long = "insert into søknad (json) values (to_json(:soknad::json))"
            .oppdatering(mapOf("soknad" to json))!!

    override fun sakIdForFnr(fnr: Fødselsnummer): Long? = "select id from sak where fnr=:fnr"
            .hent(mapOf("fnr" to fnr.toString())) { row -> row.long("id") }

    override fun fnrForSakId(sakId: Long): Fødselsnummer? = "select fnr from sak where id=:id"
            .hent(mapOf("id" to sakId)) { row -> Fødselsnummer.fraString(row.string("fnr")).fold(left = { null }, right = { it }) }

    override fun søknadForStønadsperiode(stønadsperiodeId: Long): Pair<Long, String>? =
            "select s.id, s.json from søknad s join stønadsperiode sp on sp.søknadId = s.id where sp.id=:stonadsperiodeId"
            .hent(mapOf("stonadsperiodeId" to stønadsperiodeId)) { row -> Pair(row.long("id"), row.string("json")) }

    override fun alleSaker(): List<Pair<Long, Fødselsnummer>> = "select id, fnr from sak"
            .hentListe { row -> Pair(row.long("id"), Fødselsnummer(row.string("fnr"))) }

    override fun søknadForId(id: Long): Pair<Long, String>? = "select id, json from søknad where id=:id"
            .hent(mapOf("id" to id)) { row -> Pair(row.long("id"), row.string("json")) }

    override fun lagreStønadsperiode(sakId: Long, søknadId: Long): Long = "insert into stønadsperiode (sakId, søknadId) values (:sakId, :soknadId)"
            .oppdatering(mapOf("sakId" to sakId, "soknadId" to søknadId))!!

    override fun stønadsperioderForSak(sakId: Long) = "select id from stønadsperiode where sakId=:sakId"
            .hentListe(mapOf("sakId" to sakId)) { row -> row.long("id") }

    override fun nyBehandling(stønadsperiodeId: Long) = "insert into behandling (stønadsperiodeId) values (:periodeId)"
            .oppdatering(mapOf("periodeId" to stønadsperiodeId))!!

    override fun hentBehandling(id: Long): Long = "select id from behandling where id=:id"
            .hent(mapOf("id" to id)) { row -> row.long("id") }!!

    private fun String.oppdatering(params: Map<String, Any>): Long? = using(sessionOf(dataSource, returnGeneratedKey = true)) { it.run(queryOf(this, params).asUpdateAndReturnGeneratedKey) }
    private fun <T> String.hent(params: Map<String, Any> = emptyMap(), rowMapping: (Row) -> T): T? = using(sessionOf(dataSource)) { it.run(queryOf(this, params).map { row -> rowMapping(row) }.asSingle) }
    private fun <T> String.hentListe(params: Map<String, Any> = emptyMap(), rowMapping: (Row) -> T): List<T> = using(sessionOf(dataSource)) { it.run(queryOf(this, params).map { row -> rowMapping(row) }.asList) }
}