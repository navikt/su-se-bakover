package no.nav.su.se.bakover.database.søknadsbehandling

import behandling.søknadsbehandling.domain.GrunnlagsdataOgVilkårsvurderingerSøknadsbehandling
import behandling.søknadsbehandling.domain.VilkårsvurderingerSøknadsbehandling
import beregning.domain.Beregning
import beregning.domain.BeregningMedFradragBeregnetMånedsvis
import kotliquery.Row
import no.nav.su.se.bakover.common.deserializeNullable
import no.nav.su.se.bakover.common.domain.BehandlingsId
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.domain.attestering.Attesteringshistorikk
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionContext.Companion.withSession
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresTransactionContext.Companion.withTransaction
import no.nav.su.se.bakover.common.infrastructure.persistence.Session
import no.nav.su.se.bakover.common.infrastructure.persistence.TransactionalSession
import no.nav.su.se.bakover.common.infrastructure.persistence.hent
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.infrastructure.persistence.insert
import no.nav.su.se.bakover.common.infrastructure.persistence.tidspunkt
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.serializeNullable
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.database.attestering.toAttesteringshistorikk
import no.nav.su.se.bakover.database.attestering.toDatabaseJson
import no.nav.su.se.bakover.database.beregning.deserialiserBeregning
import no.nav.su.se.bakover.database.eksternGrunnlag.EksternGrunnlagPostgresRepo
import no.nav.su.se.bakover.database.eksternGrunnlag.IdReferanser
import no.nav.su.se.bakover.database.grunnlag.GrunnlagsdataOgVilkårsvurderingerPostgresRepo
import no.nav.su.se.bakover.database.simulering.deserializeNullableSimulering
import no.nav.su.se.bakover.database.simulering.serializeNullableSimulering
import no.nav.su.se.bakover.database.skatt.Skattereferanser
import no.nav.su.se.bakover.database.søknad.SøknadRepoInternal
import no.nav.su.se.bakover.database.søknadsbehandling.AldersvurderingJson.Companion.toDBJson
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingStatusDB.Companion.status
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingshistorikkJson.Companion.toDbJson
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.søknadsbehandling.BeregnetSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.IverksattSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.LukketSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SimulertSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingId
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingTilAttestering
import no.nav.su.se.bakover.domain.søknadsbehandling.UnderkjentSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.VilkårsvurdertSøknadsbehandling
import satser.domain.supplerendestønad.SatsFactoryForSupplerendeStønad
import vilkår.vurderinger.domain.EksterneGrunnlag
import vilkår.vurderinger.domain.EksterneGrunnlagSkatt
import vilkår.vurderinger.domain.Grunnlagsdata
import vilkår.vurderinger.domain.StøtterHentingAvEksternGrunnlag
import økonomi.domain.simulering.Simulering
import java.util.UUID

internal data class BaseSøknadsbehandlingDb(
    val id: SøknadsbehandlingId,
    val opprettet: Tidspunkt,
    val sakId: UUID,
    val søknad: Søknad.Journalført.MedOppgave,
    val oppgaveId: OppgaveId,
    val fritekstTilBrev: String?,
    val stønadsperiode: Stønadsperiode?,
    val grunnlagsdata: Grunnlagsdata,
    val vilkårsvurderinger: VilkårsvurderingerSøknadsbehandling,
    val eksterneGrunnlag: EksterneGrunnlag,
    val attesteringer: Attesteringshistorikk,
    val sakstype: Sakstype,
    val status: SøknadsbehandlingStatusDB,
    val saksbehandler: String,
    val søknadsbehandlingshistorikk: String,
    val aldersvurdering: String?,
)

internal data class SøknadsbehandlingDb(
    val base: BaseSøknadsbehandlingDb,
    val beregning: Beregning?,
    val simulering: Simulering?,
    val lukket: Boolean,
) {
    val grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderingerSøknadsbehandling(
        grunnlagsdata = base.grunnlagsdata,
        vilkårsvurderinger = base.vilkårsvurderinger,
        eksterneGrunnlag = base.eksterneGrunnlag,
    )
}

internal class SøknadsbehandlingPostgresRepo(
    private val sessionFactory: PostgresSessionFactory,
    private val dbMetrics: DbMetrics,
    private val grunnlagsdataOgVilkårsvurderingerPostgresRepo: GrunnlagsdataOgVilkårsvurderingerPostgresRepo,
    private val satsFactory: SatsFactoryForSupplerendeStønad,
    private val eksterneGrunnlag: EksternGrunnlagPostgresRepo,
) : SøknadsbehandlingRepo {

    override fun lagre(søknadsbehandling: Søknadsbehandling, sessionContext: TransactionContext) {
        dbMetrics.timeQuery("lagreSøknadsbehandling") {
            sessionContext.withTransaction { tx ->
                lagre(søknadsbehandling.toDb(), tx)
            }
        }
    }

    fun Søknadsbehandling.toDb(): SøknadsbehandlingDb {
        val base = this.toBase()
        return when (this) {
            is BeregnetSøknadsbehandling.Avslag -> {
                SøknadsbehandlingDb(
                    base = base,
                    beregning = this.beregning,
                    simulering = null,
                    lukket = this.erLukket,
                )
            }

            is BeregnetSøknadsbehandling.Innvilget -> {
                SøknadsbehandlingDb(
                    base = base,
                    beregning = this.beregning,
                    simulering = null,
                    lukket = this.erLukket,
                )
            }

            is IverksattSøknadsbehandling.Avslag.MedBeregning -> {
                SøknadsbehandlingDb(
                    base = base,
                    beregning = this.beregning,
                    simulering = null,
                    lukket = this.erLukket,
                )
            }

            is IverksattSøknadsbehandling.Avslag.UtenBeregning -> {
                SøknadsbehandlingDb(
                    base = base,
                    beregning = null,
                    simulering = null,
                    lukket = this.erLukket,
                )
            }

            is IverksattSøknadsbehandling.Innvilget -> {
                SøknadsbehandlingDb(
                    base = base,
                    beregning = this.beregning,
                    simulering = this.simulering,
                    lukket = this.erLukket,
                )
            }

            is LukketSøknadsbehandling -> {
                SøknadsbehandlingDb(
                    base = base,
                    beregning = this.beregning,
                    simulering = this.simulering,
                    lukket = this.erLukket,
                )
            }

            is SimulertSøknadsbehandling -> {
                SøknadsbehandlingDb(
                    base = base,
                    beregning = this.beregning,
                    simulering = this.simulering,
                    lukket = this.erLukket,
                )
            }

            is SøknadsbehandlingTilAttestering.Avslag.MedBeregning -> {
                SøknadsbehandlingDb(
                    base = base,
                    beregning = this.beregning,
                    simulering = null,
                    lukket = this.erLukket,
                )
            }

            is SøknadsbehandlingTilAttestering.Avslag.UtenBeregning -> {
                SøknadsbehandlingDb(
                    base = base,
                    beregning = null,
                    simulering = null,
                    lukket = this.erLukket,
                )
            }

            is SøknadsbehandlingTilAttestering.Innvilget -> {
                SøknadsbehandlingDb(
                    base = base,
                    beregning = this.beregning,
                    simulering = this.simulering,
                    lukket = this.erLukket,
                )
            }

            is UnderkjentSøknadsbehandling.Avslag.MedBeregning -> {
                SøknadsbehandlingDb(
                    base = base,
                    beregning = this.beregning,
                    simulering = null,
                    lukket = this.erLukket,
                )
            }

            is UnderkjentSøknadsbehandling.Avslag.UtenBeregning -> {
                SøknadsbehandlingDb(
                    base = base,
                    beregning = null,
                    simulering = null,
                    lukket = this.erLukket,
                )
            }

            is UnderkjentSøknadsbehandling.Innvilget -> {
                SøknadsbehandlingDb(
                    base = base,
                    beregning = this.beregning,
                    simulering = this.simulering,
                    lukket = this.erLukket,
                )
            }

            is VilkårsvurdertSøknadsbehandling.Avslag -> {
                SøknadsbehandlingDb(
                    base = base,
                    beregning = null,
                    simulering = null,
                    lukket = this.erLukket,
                )
            }

            is VilkårsvurdertSøknadsbehandling.Innvilget -> {
                SøknadsbehandlingDb(
                    base = base,
                    beregning = null,
                    simulering = null,
                    lukket = this.erLukket,
                )
            }

            is VilkårsvurdertSøknadsbehandling.Uavklart -> {
                SøknadsbehandlingDb(
                    base = base,
                    beregning = null,
                    simulering = null,
                    lukket = this.erLukket,
                )
            }
        }
    }

    private fun Søknadsbehandling.toBase(): BaseSøknadsbehandlingDb {
        return BaseSøknadsbehandlingDb(
            id = this.id,
            opprettet = this.opprettet,
            sakId = this.sakId,
            søknad = this.søknad,
            oppgaveId = this.oppgaveId,
            fritekstTilBrev = this.fritekstTilBrev,
            stønadsperiode = this.stønadsperiode,
            grunnlagsdata = this.grunnlagsdata,
            vilkårsvurderinger = this.vilkårsvurderinger,
            eksterneGrunnlag = this.eksterneGrunnlag,
            attesteringer = this.attesteringer,
            sakstype = this.sakstype,
            status = this.status(),
            saksbehandler = this.saksbehandler.toString(),
            søknadsbehandlingshistorikk = this.søknadsbehandlingsHistorikk.toDbJson(),
            aldersvurdering = this.aldersvurdering?.toDBJson(),
        )
    }

    private fun hentSkatteIDerForBehandling(
        behandlingId: BehandlingsId,
        session: Session,
    ): Pair<UUID?, UUID?> {
        return "select søkersSkatteId, epsSkatteId from behandling where id=:id"
            .hent(mapOf("id" to behandlingId.value), session) {
                it.uuidOrNull("søkersSkatteId") to it.uuidOrNull("epsSkatteId")
            } ?: Pair(null, null)
    }

    private fun lagre(søknadsbehandling: SøknadsbehandlingDb, tx: TransactionalSession) {
        val (eksisterendeSøkersSkatteId, eksisterendeEpsSkatteId) = hentSkatteIDerForBehandling(
            søknadsbehandling.base.id,
            tx,
        )

        eksterneGrunnlag.lagre(
            søknadsbehandling.base.sakId,
            søknadsbehandling.grunnlagsdataOgVilkårsvurderinger.eksterneGrunnlag,
            tx,
        )

        (
            """
                    insert into behandling (
                        id,
                        sakId,
                        søknadId,
                        opprettet,
                        status,
                        stønadsperiode,
                        oppgaveId,
                        attestering,
                        fritekstTilBrev,
                        saksbehandler,
                        beregning,
                        simulering,
                        lukket,
                        saksbehandling,
                        aldersvurdering,
                        søkersskatteid,
                        epsskatteid
                    ) values (
                        :id,
                        :sakId,
                        :soknadId,
                        :opprettet,
                        :status,
                        to_json(:stonadsperiode::json),
                        :oppgaveId,
                        to_json(:attestering::json),
                        :fritekstTilBrev,
                        :saksbehandler,
                        to_json(:beregning::json),
                        to_json(:simulering::json),
                        :lukket,
                        to_json(:saksbehandling::json),
                        to_json(:aldersvurdering::json),
                        :sokersskatteid,
                        :epsskatteid
                    ) on conflict(id) do update set
                        status = :status,
                        stønadsperiode = to_json(:stonadsperiode::json),
                        oppgaveId = :oppgaveId,
                        attestering = to_json(:attestering::json),
                        saksbehandling = to_json(:saksbehandling::json),
                        fritekstTilBrev = :fritekstTilBrev,
                        saksbehandler = :saksbehandler,
                        beregning = to_json(:beregning::json),
                        simulering =  to_json(:simulering::json),
                        lukket = :lukket,
                        aldersvurdering = to_json(:aldersvurdering::json),
                        søkersskatteid = :sokersskatteid,
                        epsskatteid = :epsskatteid
            """.trimIndent()
            ).insert(
            params = mapOf(
                "id" to søknadsbehandling.base.id.value,
                "sakId" to søknadsbehandling.base.sakId,
                "soknadId" to søknadsbehandling.base.søknad.id,
                "opprettet" to søknadsbehandling.base.opprettet,
                "status" to søknadsbehandling.base.status.toString(),
                "stonadsperiode" to serializeNullable(søknadsbehandling.base.stønadsperiode),
                "oppgaveId" to søknadsbehandling.base.oppgaveId.toString(),
                "attestering" to søknadsbehandling.base.attesteringer.toDatabaseJson(),
                "fritekstTilBrev" to søknadsbehandling.base.fritekstTilBrev,
                "saksbehandler" to søknadsbehandling.base.saksbehandler,
                "beregning" to søknadsbehandling.beregning,
                "simulering" to søknadsbehandling.simulering.serializeNullableSimulering(),
                "lukket" to søknadsbehandling.lukket,
                "saksbehandling" to søknadsbehandling.base.søknadsbehandlingshistorikk,
                "aldersvurdering" to søknadsbehandling.base.aldersvurdering,
                "sokersskatteid" to when (
                    val x =
                        søknadsbehandling.grunnlagsdataOgVilkårsvurderinger.eksterneGrunnlag.skatt
                ) {
                    is EksterneGrunnlagSkatt.Hentet -> x.søkers.id
                    EksterneGrunnlagSkatt.IkkeHentet -> null
                },
                "epsskatteid" to when (
                    val x =
                        søknadsbehandling.grunnlagsdataOgVilkårsvurderinger.eksterneGrunnlag.skatt
                ) {
                    is EksterneGrunnlagSkatt.Hentet -> x.eps?.id
                    EksterneGrunnlagSkatt.IkkeHentet -> null
                },
            ),
            session = tx,
        )

        eksterneGrunnlag.slettEksisterende(
            eksisterendeReferanser = IdReferanser(
                skattereferanser = if (eksisterendeSøkersSkatteId != null) {
                    Skattereferanser(eksisterendeSøkersSkatteId, eksisterendeEpsSkatteId)
                } else {
                    null
                },
            ),
            oppdaterteReferanser = IdReferanser(
                skattereferanser = if (søknadsbehandling.base.eksterneGrunnlag.skatt is EksterneGrunnlagSkatt.Hentet) {
                    Skattereferanser(
                        (søknadsbehandling.base.eksterneGrunnlag.skatt as EksterneGrunnlagSkatt.Hentet).søkers.id,
                        (søknadsbehandling.base.eksterneGrunnlag.skatt as EksterneGrunnlagSkatt.Hentet).eps?.id,
                    )
                } else {
                    null
                },
            ),
            tx,
        )

        grunnlagsdataOgVilkårsvurderingerPostgresRepo.lagre(
            behandlingId = søknadsbehandling.base.id,
            grunnlagsdataOgVilkårsvurderinger = søknadsbehandling.grunnlagsdataOgVilkårsvurderinger,
            tx = tx,
        )
    }

    override fun hent(id: SøknadsbehandlingId): Søknadsbehandling? {
        return dbMetrics.timeQuery("hentSøknadsbehandling") {
            sessionFactory.withSession { session ->
                "select b.*, s.fnr, s.saksnummer, s.type from behandling b inner join sak s on s.id = b.sakId where b.id=:id"
                    .hent(mapOf("id" to id.value), session) { row ->
                        row.toSøknadsbehandling(session)
                    }
            }
        }
    }

    override fun hentForSak(sakId: UUID, sessionContext: SessionContext): List<Søknadsbehandling> {
        return dbMetrics.timeQuery("hentSøknadsbehandlingForSakId") {
            sessionContext.withSession { session ->
                "select b.*, s.fnr, s.saksnummer, s.type from behandling b inner join sak s on s.id = b.sakId where b.sakId=:sakId order by b.opprettet"
                    .hentListe(mapOf("sakId" to sakId), session) {
                        it.toSøknadsbehandling(session)
                    }
            }
        }
    }

    override fun hentForSøknad(søknadId: UUID): Søknadsbehandling? {
        return dbMetrics.timeQuery("hentSøknadsbehandlingForSøknadId") {
            sessionFactory.withSession { session ->
                "select b.*, s.fnr, s.saksnummer, s.type from behandling b inner join sak s on s.id = b.sakId where søknadId=:soknadId".hent(
                    mapOf("soknadId" to søknadId),
                    session,
                ) { it.toSøknadsbehandling(session) }
            }
        }
    }

    override fun defaultTransactionContext(): TransactionContext {
        return sessionFactory.newTransactionContext()
    }

    override fun defaultSessionContext(): SessionContext {
        return sessionFactory.newSessionContext()
    }

    internal fun hent(id: SøknadsbehandlingId, session: Session): Søknadsbehandling? {
        return "select b.*, s.fnr, s.saksnummer, s.type from behandling b inner join sak s on s.id = b.sakId where b.id=:id"
            .hent(mapOf("id" to id.value), session) { row ->
                row.toSøknadsbehandling(session)
            }
    }

    private fun Row.toSøknadsbehandling(session: Session): Søknadsbehandling {
        val behandlingId = SøknadsbehandlingId(uuid("id"))
        val søknad = SøknadRepoInternal.hentSøknadInternal(uuid("søknadId"), session)!!
        if (søknad !is Søknad.Journalført.MedOppgave) {
            throw IllegalStateException("Kunne ikke hente behandling med søknad som ikke er journalført med oppgave.")
        }
        val sakId = uuid("sakId")
        val opprettet = tidspunkt("opprettet")
        val status = SøknadsbehandlingStatusDB.valueOf(string("status"))
        val oppgaveId = OppgaveId(string("oppgaveId"))
        val sakstype = Sakstype.from(string("type"))
        val saksnummer = Saksnummer(long("saksnummer"))
        val erAvbrutt = boolean("lukket")
        val beregning: BeregningMedFradragBeregnetMånedsvis? = stringOrNull("beregning")?.deserialiserBeregning(
            satsFactory = satsFactory,
            sakstype = sakstype,
            saksnummer = saksnummer,
            erAvbrutt = erAvbrutt,
        )
        val simulering = stringOrNull("simulering").deserializeNullableSimulering()
        val attesteringer = string("attestering").toAttesteringshistorikk()
        val søknadsbehandlingHistorikk =
            SøknadsbehandlingshistorikkJson.toSøknadsbehandlingsHistorikk(string("saksbehandling"))
        val saksbehandler = NavIdentBruker.Saksbehandler(string("saksbehandler"))

        val fritekstTilBrev = stringOrNull("fritekstTilBrev") ?: ""
        val stønadsperiode = deserializeNullable<Stønadsperiode>(stringOrNull("stønadsperiode"))

        val aldersvurdering =
            stønadsperiode?.let { AldersvurderingJson.toAldersvurdering(string("aldersvurdering"), it) }

        val fnr = Fnr(string("fnr"))

        val eksterneGrunnlag = StøtterHentingAvEksternGrunnlag(
            skatt = eksterneGrunnlag.hentSkattegrunnlag(
                uuidOrNull("søkersSkatteId"),
                uuidOrNull("epsSkatteId"),
                session,
            ),
        )

        val grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderingerPostgresRepo.hentForSøknadsbehandling(
            søknadsbehandlingId = behandlingId,
            session = session,
            sakstype = Sakstype.from(string("type")),
            eksterneGrunnlag = eksterneGrunnlag,
        )

        fun uavklart(): VilkårsvurdertSøknadsbehandling.Uavklart = VilkårsvurdertSøknadsbehandling.Uavklart(
            id = behandlingId,
            opprettet = opprettet,
            sakId = sakId,
            saksnummer = saksnummer,
            søknad = søknad,
            oppgaveId = oppgaveId,
            fnr = fnr,
            fritekstTilBrev = fritekstTilBrev,
            aldersvurdering = aldersvurdering,
            grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
            attesteringer = attesteringer,
            søknadsbehandlingsHistorikk = søknadsbehandlingHistorikk,
            sakstype = sakstype,
            saksbehandler = saksbehandler,
        )

        val søknadsbehandling = when (status) {
            SøknadsbehandlingStatusDB.OPPRETTET -> uavklart()

            SøknadsbehandlingStatusDB.VILKÅRSVURDERT_INNVILGET -> VilkårsvurdertSøknadsbehandling.Innvilget(
                id = behandlingId,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                søknad = søknad,
                oppgaveId = oppgaveId,
                fnr = fnr,
                fritekstTilBrev = fritekstTilBrev,
                aldersvurdering = aldersvurdering!!,
                grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
                attesteringer = attesteringer,
                søknadsbehandlingsHistorikk = søknadsbehandlingHistorikk,
                sakstype = sakstype,
                saksbehandler = saksbehandler,
            )

            SøknadsbehandlingStatusDB.VILKÅRSVURDERT_AVSLAG -> VilkårsvurdertSøknadsbehandling.Avslag(
                forrigeTilstand = uavklart(),
                fritekstTilBrev = fritekstTilBrev,
                aldersvurdering = aldersvurdering!!,
                grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
                søknadsbehandlingsHistorikk = søknadsbehandlingHistorikk,
                saksbehandler = saksbehandler,
            )

            SøknadsbehandlingStatusDB.BEREGNET_INNVILGET -> BeregnetSøknadsbehandling.Innvilget(
                id = behandlingId,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                søknad = søknad,
                oppgaveId = oppgaveId,
                fnr = fnr,
                beregning = beregning!!,
                fritekstTilBrev = fritekstTilBrev,
                aldersvurdering = aldersvurdering!!,
                grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
                attesteringer = attesteringer,
                søknadsbehandlingsHistorikk = søknadsbehandlingHistorikk,
                sakstype = sakstype,
                saksbehandler = saksbehandler,
            )

            SøknadsbehandlingStatusDB.BEREGNET_AVSLAG -> BeregnetSøknadsbehandling.Avslag(
                id = behandlingId,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                søknad = søknad,
                oppgaveId = oppgaveId,
                fnr = fnr,
                beregning = beregning!!,
                fritekstTilBrev = fritekstTilBrev,
                aldersvurdering = aldersvurdering!!,
                grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
                attesteringer = attesteringer,
                søknadsbehandlingsHistorikk = søknadsbehandlingHistorikk,
                sakstype = sakstype,
                saksbehandler = saksbehandler,
            )

            SøknadsbehandlingStatusDB.SIMULERT -> SimulertSøknadsbehandling(
                id = behandlingId,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                søknad = søknad,
                oppgaveId = oppgaveId,
                fnr = fnr,
                beregning = beregning!!,
                simulering = simulering!!,
                fritekstTilBrev = fritekstTilBrev,
                aldersvurdering = aldersvurdering!!,
                grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
                attesteringer = attesteringer,
                søknadsbehandlingsHistorikk = søknadsbehandlingHistorikk,
                sakstype = sakstype,
                saksbehandler = saksbehandler,
            )

            SøknadsbehandlingStatusDB.TIL_ATTESTERING_INNVILGET -> SøknadsbehandlingTilAttestering.Innvilget(
                id = behandlingId,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                søknad = søknad,
                oppgaveId = oppgaveId,
                fnr = fnr,
                beregning = beregning!!,
                simulering = simulering!!,
                saksbehandler = saksbehandler,
                fritekstTilBrev = fritekstTilBrev,
                aldersvurdering = aldersvurdering!!,
                grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
                attesteringer = attesteringer,
                søknadsbehandlingsHistorikk = søknadsbehandlingHistorikk,
                sakstype = sakstype,
            )

            SøknadsbehandlingStatusDB.TIL_ATTESTERING_AVSLAG -> when (beregning) {
                null -> SøknadsbehandlingTilAttestering.Avslag.UtenBeregning(
                    id = behandlingId,
                    opprettet = opprettet,
                    sakId = sakId,
                    saksnummer = saksnummer,
                    søknad = søknad,
                    oppgaveId = oppgaveId,
                    fnr = fnr,
                    saksbehandler = saksbehandler,
                    fritekstTilBrev = fritekstTilBrev,
                    aldersvurdering = aldersvurdering!!,
                    grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
                    attesteringer = attesteringer,
                    søknadsbehandlingsHistorikk = søknadsbehandlingHistorikk,
                    sakstype = sakstype,
                )

                else -> SøknadsbehandlingTilAttestering.Avslag.MedBeregning(
                    id = behandlingId,
                    opprettet = opprettet,
                    sakId = sakId,
                    saksnummer = saksnummer,
                    søknad = søknad,
                    oppgaveId = oppgaveId,
                    fnr = fnr,
                    beregning = beregning,
                    saksbehandler = saksbehandler,
                    fritekstTilBrev = fritekstTilBrev,
                    aldersvurdering = aldersvurdering!!,
                    grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
                    attesteringer = attesteringer,
                    søknadsbehandlingsHistorikk = søknadsbehandlingHistorikk,
                    sakstype = sakstype,
                )
            }

            SøknadsbehandlingStatusDB.UNDERKJENT_INNVILGET -> UnderkjentSøknadsbehandling.Innvilget(
                id = behandlingId,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                søknad = søknad,
                oppgaveId = oppgaveId,
                fnr = fnr,
                beregning = beregning!!,
                simulering = simulering!!,
                saksbehandler = saksbehandler,
                attesteringer = attesteringer,
                søknadsbehandlingsHistorikk = søknadsbehandlingHistorikk,
                fritekstTilBrev = fritekstTilBrev,
                aldersvurdering = aldersvurdering!!,
                grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
                sakstype = sakstype,
            )

            SøknadsbehandlingStatusDB.UNDERKJENT_AVSLAG -> when (beregning) {
                null -> UnderkjentSøknadsbehandling.Avslag.UtenBeregning(
                    id = behandlingId,
                    opprettet = opprettet,
                    sakId = sakId,
                    saksnummer = saksnummer,
                    søknad = søknad,
                    oppgaveId = oppgaveId,
                    fnr = fnr,
                    saksbehandler = saksbehandler,
                    attesteringer = attesteringer,
                    søknadsbehandlingsHistorikk = søknadsbehandlingHistorikk,
                    fritekstTilBrev = fritekstTilBrev,
                    aldersvurdering = aldersvurdering!!,
                    grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
                    sakstype = sakstype,
                )

                else -> UnderkjentSøknadsbehandling.Avslag.MedBeregning(
                    id = behandlingId,
                    opprettet = opprettet,
                    sakId = sakId,
                    saksnummer = saksnummer,
                    søknad = søknad,
                    oppgaveId = oppgaveId,
                    fnr = fnr,
                    beregning = beregning,
                    saksbehandler = saksbehandler,
                    attesteringer = attesteringer,
                    søknadsbehandlingsHistorikk = søknadsbehandlingHistorikk,
                    fritekstTilBrev = fritekstTilBrev,
                    aldersvurdering = aldersvurdering!!,
                    grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
                    sakstype = sakstype,
                )
            }

            SøknadsbehandlingStatusDB.IVERKSATT_INNVILGET -> {
                IverksattSøknadsbehandling.Innvilget(
                    id = behandlingId,
                    opprettet = opprettet,
                    sakId = sakId,
                    saksnummer = saksnummer,
                    søknad = søknad,
                    oppgaveId = oppgaveId,
                    fnr = fnr,
                    beregning = beregning!!,
                    simulering = simulering!!,
                    saksbehandler = saksbehandler,
                    attesteringer = attesteringer,
                    søknadsbehandlingsHistorikk = søknadsbehandlingHistorikk,
                    fritekstTilBrev = fritekstTilBrev,
                    aldersvurdering = aldersvurdering!!,
                    grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
                    sakstype = sakstype,
                )
            }

            SøknadsbehandlingStatusDB.IVERKSATT_AVSLAG -> {
                when (beregning) {
                    null -> IverksattSøknadsbehandling.Avslag.UtenBeregning(
                        id = behandlingId,
                        opprettet = opprettet,
                        sakId = sakId,
                        saksnummer = saksnummer,
                        søknad = søknad,
                        oppgaveId = oppgaveId,
                        fnr = fnr,
                        saksbehandler = saksbehandler,
                        attesteringer = attesteringer,
                        søknadsbehandlingsHistorikk = søknadsbehandlingHistorikk,
                        fritekstTilBrev = fritekstTilBrev,
                        aldersvurdering = aldersvurdering!!,
                        grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
                        sakstype = sakstype,
                    )

                    else -> IverksattSøknadsbehandling.Avslag.MedBeregning(
                        id = behandlingId,
                        opprettet = opprettet,
                        sakId = sakId,
                        saksnummer = saksnummer,
                        søknad = søknad,
                        oppgaveId = oppgaveId,
                        fnr = fnr,
                        beregning = beregning,
                        saksbehandler = saksbehandler,
                        attesteringer = attesteringer,
                        søknadsbehandlingsHistorikk = søknadsbehandlingHistorikk,
                        fritekstTilBrev = fritekstTilBrev,
                        aldersvurdering = aldersvurdering!!,
                        grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
                        sakstype = sakstype,
                    )
                }
            }
        }
        if (erAvbrutt) {
            return LukketSøknadsbehandling.createFromPersistedState(
                søknadsbehandling = søknadsbehandling,
            )
        }
        return søknadsbehandling
    }
}

internal enum class SøknadsbehandlingStatusDB {
    OPPRETTET,
    VILKÅRSVURDERT_INNVILGET,
    VILKÅRSVURDERT_AVSLAG,
    BEREGNET_INNVILGET,
    BEREGNET_AVSLAG,
    SIMULERT,
    TIL_ATTESTERING_INNVILGET,
    TIL_ATTESTERING_AVSLAG,
    UNDERKJENT_INNVILGET,
    UNDERKJENT_AVSLAG,
    IVERKSATT_INNVILGET,
    IVERKSATT_AVSLAG,
    ;

    companion object {
        @Suppress("MemberVisibilityCanBePrivate")
        fun åpneBeregnetSøknadsbehandlinger() = listOf(
            BEREGNET_INNVILGET,
            BEREGNET_AVSLAG,
            SIMULERT,
            TIL_ATTESTERING_INNVILGET,
            TIL_ATTESTERING_AVSLAG,
            UNDERKJENT_INNVILGET,
            UNDERKJENT_AVSLAG,
        )

        fun åpneBeregnetSøknadsbehandlingerKommaseparert(): String =
            åpneBeregnetSøknadsbehandlinger().joinToString(",") { "'$it'" }

        fun Søknadsbehandling.status(): SøknadsbehandlingStatusDB {
            return when (this) {
                is BeregnetSøknadsbehandling.Avslag -> BEREGNET_AVSLAG
                is BeregnetSøknadsbehandling.Innvilget -> BEREGNET_INNVILGET
                is IverksattSøknadsbehandling.Avslag.MedBeregning -> IVERKSATT_AVSLAG
                is IverksattSøknadsbehandling.Avslag.UtenBeregning -> IVERKSATT_AVSLAG
                is IverksattSøknadsbehandling.Innvilget -> IVERKSATT_INNVILGET
                is LukketSøknadsbehandling -> underliggendeSøknadsbehandling.status()
                is SimulertSøknadsbehandling -> SIMULERT
                is SøknadsbehandlingTilAttestering.Avslag.MedBeregning -> TIL_ATTESTERING_AVSLAG
                is SøknadsbehandlingTilAttestering.Avslag.UtenBeregning -> TIL_ATTESTERING_AVSLAG
                is SøknadsbehandlingTilAttestering.Innvilget -> TIL_ATTESTERING_INNVILGET
                is UnderkjentSøknadsbehandling.Avslag.MedBeregning -> UNDERKJENT_AVSLAG
                is UnderkjentSøknadsbehandling.Avslag.UtenBeregning -> UNDERKJENT_AVSLAG
                is UnderkjentSøknadsbehandling.Innvilget -> UNDERKJENT_INNVILGET
                is VilkårsvurdertSøknadsbehandling.Avslag -> VILKÅRSVURDERT_AVSLAG
                is VilkårsvurdertSøknadsbehandling.Innvilget -> VILKÅRSVURDERT_INNVILGET
                is VilkårsvurdertSøknadsbehandling.Uavklart -> OPPRETTET
            }
        }
    }
}
