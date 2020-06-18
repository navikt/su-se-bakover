package no.nav.su.se.bakover

import kotliquery.*
import no.nav.su.meldinger.kafka.soknad.SøknadInnhold
import org.json.JSONObject
import javax.sql.DataSource

class DatabaseSøknadRepo(
        private val dataSource: DataSource
) : ObjectRepo, SakPersistenceObserver, StønadsperiodePersistenceObserver {

    override fun hentSak(fnr: Fnr): Sak? = using(sessionOf(dataSource)) { hentSak(fnr, it) }

    private fun hentSak(fnr: Fnr, session: Session): Sak? = "select * from sak where fnr=:fnr"
            .hent(mapOf("fnr" to fnr.toString()), session) { row ->
                row.toSak(session).also {
                    it.addObserver(this)
                }
            }

    override fun nySøknad(sakId: Long, søknadInnhold: SøknadInnhold): Stønadsperiode {
        val stønadsperiodeId = opprettStønadsperiode(sakId, opprettSøknad(søknadInnhold))
        return hentStønadsperiode(stønadsperiodeId)!!
    }

    private fun Row.toSak(session: Session) = Sak(
            id = long("id"),
            fnr = Fnr(string("fnr")),
            stønadsperioder = hentStønadsperioder(long("id"), session)
    )

    override fun hentSak(sakId: Long): Sak? = using(sessionOf(dataSource)) { hentSak(sakId, it) }

    private fun hentSak(sakId: Long, session: Session): Sak? = "select * from sak where id=:sakId"
            .hent(mapOf("sakId" to sakId), session) { row ->
                row.toSak(session).also {
                    it.addObserver(this)
                }
            }

    private fun opprettStønadsperiode(sakId: Long, søknadId: Long): Long = "insert into stønadsperiode (sakId, søknadId) values (:sakId, :soknadId)"
            .oppdatering(mapOf("sakId" to sakId, "soknadId" to søknadId))!!

    private fun opprettSøknad(søknadInnhold: SøknadInnhold): Long = "insert into søknad (json) values (to_json(:soknad::json))".oppdatering(mapOf("soknad" to søknadInnhold.toJson()))!!

    override fun opprettSak(fnr: Fnr): Sak {
        val sakId = opprettSak(fnr.toString())
        return hentSak(sakId)!!
    }

    private fun opprettSak(fnr: String): Long = "insert into sak (fnr) values (:fnr)".oppdatering(mapOf("fnr" to fnr))!!


    private fun Row.toStønadsperiode(session: Session) = Stønadsperiode(
            id = long("id"),
            søknad = hentSøknad(long("søknadId"), session)!!
    )

    override fun hentStønadsperioder(sakId: Long): MutableList<Stønadsperiode> = using(sessionOf(dataSource)) { hentStønadsperioder(sakId, it) }

    private fun hentStønadsperioder(sakId: Long, session: Session): MutableList<Stønadsperiode> = "select * from stønadsperiode where sakId=:sakId"
            .hentListe(mapOf("sakId" to sakId), session) { row ->
                row.toStønadsperiode(session).also {
                    it.addObserver(this)
                }
            }.toMutableList()

    override fun hentSøknad(søknadId: Long): Søknad? = using(sessionOf(dataSource)) { hentSøknad(søknadId, it) }

    private fun hentSøknad(søknadId: Long, session: Session): Søknad? = "select * from søknad where id=:id"
            .hent(mapOf("id" to søknadId), session) {
                Søknad(
                        id = it.long("id"),
                        søknadInnhold = SøknadInnhold.fromJson(JSONObject(it.string("json")))
                )
            }

    override fun hentBehandling(behandlingId: Long): Behandling? = using(sessionOf(dataSource)) { hentBehandling(behandlingId, it) }

    private fun hentBehandling(behandlingId: Long, session: Session): Behandling? = "select * from behandling where id=:id"
            .hent(mapOf("id" to behandlingId), session) {
                Behandling(id = it.long("id"))
            }

    override fun hentStønadsperiode(stønadsperiodeId: Long): Stønadsperiode? = using(sessionOf(dataSource)) { hentStønadsperiode(stønadsperiodeId, it) }

    private fun hentStønadsperiode(stønadsperiodeId: Long, session: Session): Stønadsperiode? = "select * from stønadsperiode where id=:id"
            .hent(mapOf("id" to stønadsperiodeId), session) { row ->
                row.toStønadsperiode(session).also {
                    it.addObserver(this)
                }
            }


    override fun nyBehandling(stønadsperiodeId: Long): Behandling {
        val behandlingId = opprettBehandling(stønadsperiodeId)
        return hentBehandling(behandlingId)!!
    }

    private fun opprettBehandling(stønadsperiodeId: Long) = "insert into behandling (stønadsperiodeId) values (:id)".oppdatering(mapOf("id" to stønadsperiodeId))!!

    private fun String.oppdatering(params: Map<String, Any>): Long? = using(sessionOf(dataSource, returnGeneratedKey = true)) { it.run(queryOf(this, params).asUpdateAndReturnGeneratedKey) }
//    private fun <T> String.hent(params: Map<String, Any> = emptyMap(), rowMapping: (Row) -> T): T? = using(sessionOf(dataSource)) { it.run(queryOf(this, params).map { row -> rowMapping(row) }.asSingle) }
//    private fun <T> String.hentListe(params: Map<String, Any> = emptyMap(), rowMapping: (Row) -> T): List<T> = using(sessionOf(dataSource)) { it.run(queryOf(this, params).map { row -> rowMapping(row) }.asList) }

    private fun <T> String.hent(params: Map<String, Any> = emptyMap(), session: Session, rowMapping: (Row) -> T): T? = session.run(queryOf(this, params).map { row -> rowMapping(row) }.asSingle)
    private fun <T> String.hentListe(params: Map<String, Any> = emptyMap(), session: Session, rowMapping: (Row) -> T): List<T> = session.run(queryOf(this, params).map { row -> rowMapping(row) }.asList)
}