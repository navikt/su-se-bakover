package no.nav.su.se.bakover.database

import kotliquery.Row
import kotliquery.Session
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.su.meldinger.kafka.soknad.SøknadInnhold
import no.nav.su.se.bakover.domain.Behandling
import no.nav.su.se.bakover.domain.BehandlingPersistenceObserver
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.SakPersistenceObserver
import no.nav.su.se.bakover.domain.Stønadsperiode
import no.nav.su.se.bakover.domain.StønadsperiodePersistenceObserver
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.Vilkår
import no.nav.su.se.bakover.domain.Vilkårsvurdering
import no.nav.su.se.bakover.domain.VilkårsvurderingPersistenceObserver
import org.json.JSONObject
import javax.sql.DataSource

internal class DatabaseRepo(
    private val dataSource: DataSource
) : ObjectRepo, SakPersistenceObserver, StønadsperiodePersistenceObserver, BehandlingPersistenceObserver,
    VilkårsvurderingPersistenceObserver {

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

    private fun opprettStønadsperiode(sakId: Long, søknadId: Long): Long =
        "insert into stønadsperiode (sakId, søknadId) values (:sakId, :soknadId)"
            .oppdatering(mapOf("sakId" to sakId, "soknadId" to søknadId))!!

    private fun opprettSøknad(søknadInnhold: SøknadInnhold): Long =
        "insert into søknad (json) values (to_json(:soknad::json))".oppdatering(mapOf("soknad" to søknadInnhold.toJson()))!!

    override fun opprettSak(fnr: Fnr): Sak {
        val sakId = opprettSak(fnr.toString())
        return hentSak(sakId)!!
    }

    private fun opprettSak(fnr: String): Long = "insert into sak (fnr) values (:fnr)".oppdatering(mapOf("fnr" to fnr))!!

    private fun Row.toStønadsperiode(session: Session) = Stønadsperiode(
        id = long("id"),
        søknad = hentSøknad(long("søknadId"), session)!!
    )

    override fun hentStønadsperioder(sakId: Long): MutableList<Stønadsperiode> =
        using(sessionOf(dataSource)) { hentStønadsperioder(sakId, it) }

    private fun hentStønadsperioder(sakId: Long, session: Session): MutableList<Stønadsperiode> =
        "select * from stønadsperiode where sakId=:sakId"
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

    override fun hentBehandling(behandlingId: Long): Behandling? =
        using(sessionOf(dataSource)) { hentBehandling(behandlingId, it) }

    private fun hentBehandling(behandlingId: Long, session: Session): Behandling? =
        "select * from behandling where id=:id".hent(mapOf("id" to behandlingId), session) { row ->
            row.toBehandling(session).also {
                it.addObserver(this)
            }
        }

    private fun Row.toBehandling(session: Session) = Behandling(
        id = long("id"),
        vilkårsvurderinger = hentVilkårsvurderinger(long("id"), session)
    )

    override fun hentStønadsperiode(stønadsperiodeId: Long): Stønadsperiode? =
        using(sessionOf(dataSource)) { hentStønadsperiode(stønadsperiodeId, it) }

    private fun hentStønadsperiode(stønadsperiodeId: Long, session: Session): Stønadsperiode? =
        "select * from stønadsperiode where id=:id"
            .hent(mapOf("id" to stønadsperiodeId), session) { row ->
                row.toStønadsperiode(session).also {
                    it.addObserver(this)
                }
            }

    override fun nyBehandling(stønadsperiodeId: Long): Behandling {
        val behandlingId = opprettBehandling(stønadsperiodeId)
        return hentBehandling(behandlingId)!!
    }

    private fun opprettBehandling(stønadsperiodeId: Long) =
        "insert into behandling (stønadsperiodeId) values (:id)".oppdatering(mapOf("id" to stønadsperiodeId))!!

    override fun opprettVilkårsvurderinger(behandlingId: Long, vilkår: List<Vilkår>): List<Vilkårsvurdering> {
        vilkår.forEach { opprettVilkårsvurdering(behandlingId, it) }
        return hentVilkårsvurderinger(behandlingId)
    }

    override fun hentVilkårsvurderinger(behandlingId: Long): MutableList<Vilkårsvurdering> =
        using(sessionOf(dataSource)) { hentVilkårsvurderinger(behandlingId, it) }

    private fun hentVilkårsvurderinger(behandlingId: Long, session: Session): MutableList<Vilkårsvurdering> =
        "select * from vilkårsvurdering where behandlingId=:behandlingId".hentListe(
            mapOf("behandlingId" to behandlingId),
            session
        ) { row ->
            row.toVilkårsvurdering(session).also {
                it.addObserver(this)
            }
        }.toMutableList()

    private fun Row.toVilkårsvurdering(session: Session) = Vilkårsvurdering(
        id = long("id"),
        vilkår = Vilkår.valueOf(string("vilkår")),
        begrunnelse = string("begrunnelse"),
        status = Vilkårsvurdering.Status.valueOf(string("status"))
    )

    private fun opprettVilkårsvurdering(behandlingId: Long, vilkår: Vilkår) {
        "insert into vilkårsvurdering (behandlingId, vilkår, begrunnelse, status) values (:behandlingId,CAST(:vilkar AS VILKÅR),'',CAST('IKKE_VURDERT' AS VILKÅR_VURDERING_STATUS))"
            .oppdatering(
                mapOf(
                    "behandlingId" to behandlingId,
                    "vilkar" to vilkår.name
                )
            )!!
    }

    private fun String.oppdatering(params: Map<String, Any>): Long? =
        using(sessionOf(dataSource, returnGeneratedKey = true)) {
            it.run(
                queryOf(
                    this,
                    params
                ).asUpdateAndReturnGeneratedKey
            )
        }
//    private fun <T> String.hent(params: Map<String, Any> = emptyMap(), rowMapping: (Row) -> T): T? = using(sessionOf(dataSource)) { it.run(queryOf(this, params).map { row -> rowMapping(row) }.asSingle) }
//    private fun <T> String.hentListe(params: Map<String, Any> = emptyMap(), rowMapping: (Row) -> T): List<T> = using(sessionOf(dataSource)) { it.run(queryOf(this, params).map { row -> rowMapping(row) }.asList) }

    private fun <T> String.hent(params: Map<String, Any> = emptyMap(), session: Session, rowMapping: (Row) -> T): T? =
        session.run(queryOf(this, params).map { row -> rowMapping(row) }.asSingle)

    private fun <T> String.hentListe(
        params: Map<String, Any> = emptyMap(),
        session: Session,
        rowMapping: (Row) -> T
    ): List<T> = session.run(queryOf(this, params).map { row -> rowMapping(row) }.asList)

    private fun oppdaterVilkår(id: Long, begrunnelse: String, status: Vilkårsvurdering.Status) =
        "update vilkårsvurdering set begrunnelse = :begrunnelse, status = CAST(:status AS VILKÅR_VURDERING_STATUS) where id = :id"
            .oppdatering(mapOf("id" to id, "begrunnelse" to begrunnelse, "status" to status.name))!!

    override fun oppdaterVilkårsvurdering(
        vilkårsvurderingsId: Long,
        begrunnelse: String,
        status: Vilkårsvurdering.Status
    ): Vilkårsvurdering {
        val id = oppdaterVilkår(vilkårsvurderingsId, begrunnelse, status)
        return hentVilkårsvurdering(id)!!
    }

    override fun hentVilkårsvurdering(id: Long): Vilkårsvurdering? = using(sessionOf(dataSource)) {
        hentVilkårsvurdering(id, it)
    }

    private fun hentVilkårsvurdering(id: Long, session: Session) =
        "select * from vilkårsvurdering where id = :id".hent(mapOf("id" to id), session) { row ->
            row.toVilkårsvurdering(session).also {
                it.addObserver(this)
            }
        }
}
