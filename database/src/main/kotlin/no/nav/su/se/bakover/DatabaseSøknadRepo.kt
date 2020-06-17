package no.nav.su.se.bakover

import kotliquery.Row
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.su.meldinger.kafka.soknad.SøknadInnhold
import org.json.JSONObject
import javax.sql.DataSource

class DatabaseSøknadRepo(
        private val dataSource: DataSource
) : ObjectRepo, SakPersistenceObserver, StønadsperiodePersistenceObserver {
    override fun hentSak(fnr: Fødselsnummer): Sak? = "select * from sak where fnr=:fnr"
            .hent(mapOf("fnr" to fnr.toString())) {
                it.toSak().also {
                    it.addObserver(this)
                }
            }

    override fun nySøknad(sakId: Long, søknadInnhold: SøknadInnhold): Stønadsperiode {
        val stønadsperiodeId = opprettStønadsperiode(sakId, opprettSøknad(søknadInnhold))
        return hentStønadsperiode(stønadsperiodeId)!!
    }

    private fun Row.toSak() = Sak(
            id = long("id"),
            fnr = Fødselsnummer(string("fnr")),
            stønadsperioder = hentStønadsperioder(long("id"))
    )

    override fun hentSak(sakId: Long): Sak? = "select * from sak where id=:sakId"
            .hent(mapOf("sakId" to sakId)) { row ->
                row.toSak().also {
                    it.addObserver(this)
                }
            }

    private fun opprettStønadsperiode(sakId: Long, søknadId: Long): Long = "insert into stønadsperiode (sakId, søknadId) values (:sakId, :soknadId)"
            .oppdatering(mapOf("sakId" to sakId, "soknadId" to søknadId))!!

    private fun opprettSøknad(søknadInnhold: SøknadInnhold): Long = "insert into søknad (json) values (to_json(:soknad::json))".oppdatering(mapOf("soknad" to søknadInnhold.toJson()))!!

    override fun opprettSak(fnr: Fødselsnummer): Sak {
        println("OPPRETTER SAK FOR $fnr")
        val sakId = opprettSak(fnr.toString())
        return hentSak(sakId)!!
    }

    private fun opprettSak(fnr: String): Long = "insert into sak (fnr) values (:fnr)".oppdatering(mapOf("fnr" to fnr))!!


    fun Row.toStønadsperiode() = Stønadsperiode(
            id = long("id"),
            søknad = hentSøknad(long("søknadId"))!!
    )

    override fun hentStønadsperioder(sakId: Long): MutableList<Stønadsperiode> = "select * from stønadsperiode where sakId=:sakId"
            .hentListe(mapOf("sakId" to sakId)) { row ->
                row.toStønadsperiode().also {
                    it.addObserver(this)
                }
            }.toMutableList()

    override fun hentSøknad(søknadId: Long): Søknad? = "select * from søknad where id=:id"
            .hent(mapOf("id" to søknadId)) {
                Søknad(
                        id = it.long("id"),
                        søknadInnhold = SøknadInnhold.fromJson(JSONObject(it.string("json")))
                )
            }

    override fun hentBehandling(behandlingId: Long): Behandling? = "select * from behandling where id=:id"
            .hent(mapOf("id" to behandlingId)) {
                Behandling(id = it.long("id"))
            }

    override fun hentStønadsperiode(stønadsperiodeId: Long): Stønadsperiode? = "select * from stønadsperiode where id=:id"
            .hent(mapOf("id" to stønadsperiodeId)) { row ->
                row.toStønadsperiode().also {
                    it.addObserver(this)
                }
            }

    override fun nyBehandling(stønadsperiodeId: Long): Behandling {
        val behandlingId = opprettBehandling(stønadsperiodeId)
        return hentBehandling(behandlingId)!!
    }

    private fun opprettBehandling(stønadsperiodeId: Long) = "insert into behandling (stønadsperiodeId) values (:id)".oppdatering(mapOf("id" to stønadsperiodeId))!!

    private fun String.oppdatering(params: Map<String, Any>): Long? = using(sessionOf(dataSource, returnGeneratedKey = true)) { it.run(queryOf(this, params).asUpdateAndReturnGeneratedKey) }
    private fun <T> String.hent(params: Map<String, Any> = emptyMap(), rowMapping: (Row) -> T): T? = using(sessionOf(dataSource)) { it.run(queryOf(this, params).map { row -> rowMapping(row) }.asSingle) }
    private fun <T> String.hentListe(params: Map<String, Any> = emptyMap(), rowMapping: (Row) -> T): List<T> = using(sessionOf(dataSource)) { it.run(queryOf(this, params).map { row -> rowMapping(row) }.asList) }
}