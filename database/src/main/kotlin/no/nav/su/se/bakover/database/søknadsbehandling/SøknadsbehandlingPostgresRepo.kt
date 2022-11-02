package no.nav.su.se.bakover.database.søknadsbehandling

import kotliquery.Row
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.deserializeList
import no.nav.su.se.bakover.common.deserializeNullable
import no.nav.su.se.bakover.common.persistence.DbMetrics
import no.nav.su.se.bakover.common.persistence.PostgresSessionContext.Companion.withSession
import no.nav.su.se.bakover.common.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.persistence.PostgresTransactionContext.Companion.withTransaction
import no.nav.su.se.bakover.common.persistence.Session
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.common.persistence.TransactionalSession
import no.nav.su.se.bakover.common.persistence.hent
import no.nav.su.se.bakover.common.persistence.hentListe
import no.nav.su.se.bakover.common.persistence.insert
import no.nav.su.se.bakover.common.persistence.tidspunkt
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.serializeNullable
import no.nav.su.se.bakover.database.avkorting.AvkortingVedSøknadsbehandlingDb
import no.nav.su.se.bakover.database.avkorting.AvkortingsvarselPostgresRepo
import no.nav.su.se.bakover.database.avkorting.toDb
import no.nav.su.se.bakover.database.avkorting.toDomain
import no.nav.su.se.bakover.database.beregning.deserialiserBeregning
import no.nav.su.se.bakover.database.grunnlag.GrunnlagsdataOgVilkårsvurderingerPostgresRepo
import no.nav.su.se.bakover.database.søknad.SøknadRepoInternal
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.behandling.avslag.AvslagManglendeDokumentasjon
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.BeregningMedFradragBeregnetMånedsvis
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.sak.Saksnummer
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.satser.SatsFactoryForSupplerendeStønad
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.søknadsbehandling.BehandlingsStatus
import no.nav.su.se.bakover.domain.søknadsbehandling.LukketSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.NySøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import java.util.UUID

data class BaseSøknadsbehandlingDb(
    val id: UUID,
    val opprettet: Tidspunkt,
    val sakId: UUID,
    val søknad: Søknad.Journalført.MedOppgave,
    val oppgaveId: OppgaveId,
    val fritekstTilBrev: String?,
    val stønadsperiode: Stønadsperiode?,
    val grunnlagsdata: Grunnlagsdata,
    val vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling,
    val attesteringer: Attesteringshistorikk,
    val avkorting: AvkortingVedSøknadsbehandling,
    val sakstype: Sakstype,
    val status: BehandlingsStatus,
    val saksbehandler: String?,
)
data class SøknadsbehandlingDb(
    val base: BaseSøknadsbehandlingDb,
    val beregning: Beregning?,
    val simulering: Simulering?,
    val lukket: Boolean,
) {
    val grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling(
        grunnlagsdata = base.grunnlagsdata,
        vilkårsvurderinger = base.vilkårsvurderinger,
    )
}

internal class SøknadsbehandlingPostgresRepo(
    private val sessionFactory: PostgresSessionFactory,
    private val dbMetrics: DbMetrics,
    private val grunnlagsdataOgVilkårsvurderingerPostgresRepo: GrunnlagsdataOgVilkårsvurderingerPostgresRepo,
    private val avkortingsvarselRepo: AvkortingsvarselPostgresRepo,
    private val satsFactory: SatsFactoryForSupplerendeStønad,
) : SøknadsbehandlingRepo {

    override fun lagre(søknadsbehandling: Søknadsbehandling, sessionContext: TransactionContext) {
        dbMetrics.timeQuery("lagreSøknadsbehandling") {
            sessionContext.withTransaction { tx ->
                lagre(søknadsbehandling.toDb(), tx)
            }
        }
    }

    override fun lagreNySøknadsbehandling(søknadsbehandling: NySøknadsbehandling) {
        dbMetrics.timeQuery("lagreNySøknadsbehandling") {
            sessionFactory.withTransaction { tx ->
                lagre(søknadsbehandling.toDb(), tx)
            }
        }
    }

    fun NySøknadsbehandling.toDb(): SøknadsbehandlingDb {
        return SøknadsbehandlingDb(
            base = BaseSøknadsbehandlingDb(
                id = this.id,
                opprettet = this.opprettet,
                sakId = this.sakId,
                søknad = this.søknad,
                oppgaveId = this.oppgaveId,
                fritekstTilBrev = null,
                stønadsperiode = null,
                grunnlagsdata = Grunnlagsdata.IkkeVurdert,
                vilkårsvurderinger = Vilkårsvurderinger.Søknadsbehandling.Uføre.ikkeVurdert(),
                attesteringer = Attesteringshistorikk.empty(),
                avkorting = this.avkorting,
                sakstype = this.sakstype,
                status = BehandlingsStatus.OPPRETTET,
                saksbehandler = null,
            ),
            beregning = null,
            simulering = null,
            lukket = false,
        )
    }

    fun Søknadsbehandling.toDb(): SøknadsbehandlingDb {
        val base = this.toBase()
        return when (this) {
            is Søknadsbehandling.Beregnet.Avslag -> {
                SøknadsbehandlingDb(
                    base = base,
                    beregning = this.beregning,
                    simulering = null,
                    lukket = this.erLukket,
                )
            }
            is Søknadsbehandling.Beregnet.Innvilget -> {
                SøknadsbehandlingDb(
                    base = base,
                    beregning = this.beregning,
                    simulering = null,
                    lukket = this.erLukket,
                )
            }
            is Søknadsbehandling.Iverksatt.Avslag.MedBeregning -> {
                SøknadsbehandlingDb(
                    base = base,
                    beregning = this.beregning,
                    simulering = null,
                    lukket = this.erLukket,
                )
            }
            is Søknadsbehandling.Iverksatt.Avslag.UtenBeregning -> {
                SøknadsbehandlingDb(
                    base = base,
                    beregning = null,
                    simulering = null,
                    lukket = this.erLukket,
                )
            }
            is Søknadsbehandling.Iverksatt.Innvilget -> {
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
            is Søknadsbehandling.Simulert -> {
                SøknadsbehandlingDb(
                    base = base,
                    beregning = this.beregning,
                    simulering = this.simulering,
                    lukket = this.erLukket,
                )
            }
            is Søknadsbehandling.TilAttestering.Avslag.MedBeregning -> {
                SøknadsbehandlingDb(
                    base = base,
                    beregning = this.beregning,
                    simulering = null,
                    lukket = this.erLukket,
                )
            }
            is Søknadsbehandling.TilAttestering.Avslag.UtenBeregning -> {
                SøknadsbehandlingDb(
                    base = base,
                    beregning = null,
                    simulering = null,
                    lukket = this.erLukket,
                )
            }
            is Søknadsbehandling.TilAttestering.Innvilget -> {
                SøknadsbehandlingDb(
                    base = base,
                    beregning = this.beregning,
                    simulering = this.simulering,
                    lukket = this.erLukket,
                )
            }
            is Søknadsbehandling.Underkjent.Avslag.MedBeregning -> {
                SøknadsbehandlingDb(
                    base = base,
                    beregning = this.beregning,
                    simulering = null,
                    lukket = this.erLukket,
                )
            }
            is Søknadsbehandling.Underkjent.Avslag.UtenBeregning -> {
                SøknadsbehandlingDb(
                    base = base,
                    beregning = null,
                    simulering = null,
                    lukket = this.erLukket,
                )
            }
            is Søknadsbehandling.Underkjent.Innvilget -> {
                SøknadsbehandlingDb(
                    base = base,
                    beregning = this.beregning,
                    simulering = this.simulering,
                    lukket = this.erLukket,
                )
            }
            is Søknadsbehandling.Vilkårsvurdert.Avslag -> {
                SøknadsbehandlingDb(
                    base = base,
                    beregning = null,
                    simulering = null,
                    lukket = this.erLukket,
                )
            }
            is Søknadsbehandling.Vilkårsvurdert.Innvilget -> {
                SøknadsbehandlingDb(
                    base = base,
                    beregning = null,
                    simulering = null,
                    lukket = this.erLukket,
                )
            }
            is Søknadsbehandling.Vilkårsvurdert.Uavklart -> {
                SøknadsbehandlingDb(
                    base = base,
                    beregning = null,
                    simulering = null,
                    lukket = this.erLukket,
                )
            }
        }
    }

    fun Søknadsbehandling.toBase(): BaseSøknadsbehandlingDb {
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
            attesteringer = this.attesteringer,
            avkorting = this.avkorting,
            sakstype = this.sakstype,
            status = this.status,
            saksbehandler = this.saksbehandler?.toString(),
        )
    }

    private fun lagre(søknadsbehandling: SøknadsbehandlingDb, tx: TransactionalSession) {
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
                        avkorting,
                        fritekstTilBrev,
                        saksbehandler,
                        beregning,
                        simulering,
                        lukket
                    ) values (
                        :id,
                        :sakId,
                        :soknadId,
                        :opprettet,
                        :status,
                        to_json(:stonadsperiode::json),
                        :oppgaveId,
                        to_json(:attestering::json),
                        to_json(:avkorting::json),
                        :fritekstTilBrev,
                        :saksbehandler,
                        to_json(:beregning::json),
                        to_json(:simulering::json),
                        :lukket
                    ) on conflict(id) do update set
                        status = :status,
                        stønadsperiode = to_json(:stonadsperiode::json),
                        oppgaveId = :oppgaveId,
                        attestering = to_json(:attestering::json),
                        avkorting = to_json(:avkorting::json),
                        fritekstTilBrev = :fritekstTilBrev,
                        saksbehandler = :saksbehandler,
                        beregning = to_json(:beregning::json),
                        simulering =  to_json(:simulering::json),
                        lukket = :lukket                    
            """.trimIndent()
            ).insert(
            params = mapOf(
                "id" to søknadsbehandling.base.id,
                "sakId" to søknadsbehandling.base.sakId,
                "soknadId" to søknadsbehandling.base.søknad.id,
                "opprettet" to søknadsbehandling.base.opprettet,
                "status" to søknadsbehandling.base.status.toString(),
                "stonadsperiode" to serializeNullable(søknadsbehandling.base.stønadsperiode),
                "oppgaveId" to søknadsbehandling.base.oppgaveId.toString(),
                "attestering" to søknadsbehandling.base.attesteringer.serialize(),
                "avkorting" to serialize(søknadsbehandling.base.avkorting.toDb()),
                "fritekstTilBrev" to søknadsbehandling.base.fritekstTilBrev,
                "saksbehandler" to søknadsbehandling.base.saksbehandler,
                "beregning" to søknadsbehandling.beregning,
                "simulering" to serializeNullable(søknadsbehandling.simulering),
                "lukket" to søknadsbehandling.lukket,
            ),
            session = tx,
        )

        grunnlagsdataOgVilkårsvurderingerPostgresRepo.lagre(
            behandlingId = søknadsbehandling.base.id,
            grunnlagsdataOgVilkårsvurderinger = søknadsbehandling.grunnlagsdataOgVilkårsvurderinger,
            tx = tx,
        )

        when (val avkort = søknadsbehandling.base.avkorting) {
            is AvkortingVedSøknadsbehandling.Iverksatt.AvkortUtestående -> {
                avkortingsvarselRepo.lagre(
                    avkortingsvarsel = avkort.avkortingsvarsel,
                    tx = tx,
                )
            }
            else -> { /*noop*/ }
        }
    }

    override fun lagreAvslagManglendeDokumentasjon(
        avslag: AvslagManglendeDokumentasjon,
        sessionContext: TransactionContext,
    ) {
        dbMetrics.timeQuery("lagreAvslagManglendeDokumentasjon") {
            sessionContext.withTransaction { tx ->
                (
                    """
                    update behandling set
                        saksbehandler = :saksbehandler,
                        attestering = to_json(:attestering::json),
                        fritekstTilBrev = :fritekstTilBrev,
                        stønadsperiode = to_json(:stonadsperiode::json),
                        status = :status,
                        beregning = :beregning,
                        simulering = :simulering,
                        avkorting = to_json(:avkorting::json)
                    where id = :id
                    """.trimIndent()
                    ).insert(
                    params = avslag.søknadsbehandling.let {
                        mapOf(
                            "saksbehandler" to it.saksbehandler,
                            "attestering" to it.attesteringer.serialize(),
                            "fritekstTilBrev" to it.fritekstTilBrev,
                            "stonadsperiode" to serialize(it.stønadsperiode),
                            "status" to it.status.toString(),
                            "id" to it.id,
                            "beregning" to null,
                            "simulering" to null,
                            "avkorting" to serialize(it.avkorting.toDb()),
                        )
                    },
                    session = tx,
                )

                grunnlagsdataOgVilkårsvurderingerPostgresRepo.lagre(
                    behandlingId = avslag.søknadsbehandling.id,
                    grunnlagsdataOgVilkårsvurderinger = avslag.søknadsbehandling.grunnlagsdataOgVilkårsvurderinger,
                    tx = tx,
                )
            }
        }
    }

    override fun hent(id: UUID): Søknadsbehandling? {
        return dbMetrics.timeQuery("hentSøknadsbehandling") {
            sessionFactory.withSession { session ->
                "select b.*, s.fnr, s.saksnummer, s.type from behandling b inner join sak s on s.id = b.sakId where b.id=:id"
                    .hent(mapOf("id" to id), session) { row ->
                        row.toSøknadsbehandling(session)
                    }
            }
        }
    }

    override fun hentForSak(sakId: UUID, sessionContext: SessionContext): List<Søknadsbehandling> {
        return dbMetrics.timeQuery("hentSøknadsbehandlingForSakId") {
            sessionContext.withSession { session ->
                "select b.*, s.fnr, s.saksnummer, s.type from behandling b inner join sak s on s.id = b.sakId where b.sakId=:sakId"
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

    internal fun hent(id: UUID, session: Session): Søknadsbehandling? {
        return "select b.*, s.fnr, s.saksnummer, s.type from behandling b inner join sak s on s.id = b.sakId where b.id=:id"
            .hent(mapOf("id" to id), session) { row ->
                row.toSøknadsbehandling(session)
            }
    }

    private fun Row.toSøknadsbehandling(session: Session): Søknadsbehandling {
        val behandlingId = uuid("id")
        val søknad = SøknadRepoInternal.hentSøknadInternal(uuid("søknadId"), session)!!
        if (søknad !is Søknad.Journalført.MedOppgave) {
            throw IllegalStateException("Kunne ikke hente behandling med søknad som ikke er journalført med oppgave.")
        }
        val sakId = uuid("sakId")
        val opprettet = tidspunkt("opprettet")
        val status = BehandlingsStatus.valueOf(string("status"))
        val oppgaveId = OppgaveId(string("oppgaveId"))
        val sakstype = Sakstype.from(string("type"))
        val beregning: BeregningMedFradragBeregnetMånedsvis? = stringOrNull("beregning")?.deserialiserBeregning(
            satsFactory = satsFactory.gjeldende(opprettet),
            sakstype = sakstype,
        )
        val simulering = deserializeNullable<Simulering>(stringOrNull("simulering"))
        val attesteringer = Attesteringshistorikk.create(deserializeList((string("attestering"))))
        val saksbehandler = stringOrNull("saksbehandler")?.let { NavIdentBruker.Saksbehandler(it) }
        val saksnummer = Saksnummer(long("saksnummer"))
        val fritekstTilBrev = stringOrNull("fritekstTilBrev") ?: ""
        val stønadsperiode = deserializeNullable<Stønadsperiode>(stringOrNull("stønadsperiode"))

        val fnr = Fnr(string("fnr"))
        val (grunnlagsdata, vilkårsvurderinger) = grunnlagsdataOgVilkårsvurderingerPostgresRepo.hentForSøknadsbehandling(
            behandlingId = behandlingId,
            session = session,
            sakstype = Sakstype.from(string("type")),
        )

        val avkorting = deserializeNullable<AvkortingVedSøknadsbehandlingDb>(stringOrNull("avkorting"))?.toDomain()

        val søknadsbehandling = when (status) {
            BehandlingsStatus.OPPRETTET -> Søknadsbehandling.Vilkårsvurdert.Uavklart(
                id = behandlingId,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                søknad = søknad,
                oppgaveId = oppgaveId,
                fnr = fnr,
                fritekstTilBrev = fritekstTilBrev,
                stønadsperiode = stønadsperiode,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                attesteringer = attesteringer,
                avkorting = avkorting as AvkortingVedSøknadsbehandling.Uhåndtert.KanIkkeHåndtere,
                sakstype = sakstype,
                saksbehandler = saksbehandler,
            )
            BehandlingsStatus.VILKÅRSVURDERT_INNVILGET -> Søknadsbehandling.Vilkårsvurdert.Innvilget(
                id = behandlingId,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                søknad = søknad,
                oppgaveId = oppgaveId,
                fnr = fnr,
                fritekstTilBrev = fritekstTilBrev,
                stønadsperiode = stønadsperiode!!,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                attesteringer = attesteringer,
                avkorting = avkorting as AvkortingVedSøknadsbehandling.Uhåndtert,
                sakstype = sakstype,
                saksbehandler = saksbehandler,
            )
            BehandlingsStatus.VILKÅRSVURDERT_AVSLAG -> Søknadsbehandling.Vilkårsvurdert.Avslag(
                id = behandlingId,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                søknad = søknad,
                oppgaveId = oppgaveId,
                fnr = fnr,
                fritekstTilBrev = fritekstTilBrev,
                stønadsperiode = stønadsperiode!!,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                attesteringer = attesteringer,
                avkorting = avkorting as AvkortingVedSøknadsbehandling.Uhåndtert.KanIkkeHåndtere,
                sakstype = sakstype,
                saksbehandler = saksbehandler,
            )
            BehandlingsStatus.BEREGNET_INNVILGET -> Søknadsbehandling.Beregnet.Innvilget(
                id = behandlingId,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                søknad = søknad,
                oppgaveId = oppgaveId,
                fnr = fnr,
                beregning = beregning!!,
                fritekstTilBrev = fritekstTilBrev,
                stønadsperiode = stønadsperiode!!,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                attesteringer = attesteringer,
                avkorting = avkorting as AvkortingVedSøknadsbehandling.Håndtert,
                sakstype = sakstype,
                saksbehandler = saksbehandler,
            )
            BehandlingsStatus.BEREGNET_AVSLAG -> Søknadsbehandling.Beregnet.Avslag(
                id = behandlingId,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                søknad = søknad,
                oppgaveId = oppgaveId,
                fnr = fnr,
                beregning = beregning!!,
                fritekstTilBrev = fritekstTilBrev,
                stønadsperiode = stønadsperiode!!,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                attesteringer = attesteringer,
                avkorting = avkorting as AvkortingVedSøknadsbehandling.Håndtert.KanIkkeHåndtere,
                sakstype = sakstype,
                saksbehandler = saksbehandler,
            )
            BehandlingsStatus.SIMULERT -> Søknadsbehandling.Simulert(
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
                stønadsperiode = stønadsperiode!!,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                attesteringer = attesteringer,
                avkorting = avkorting as AvkortingVedSøknadsbehandling.Håndtert,
                sakstype = sakstype,
                saksbehandler = saksbehandler,
            )
            BehandlingsStatus.TIL_ATTESTERING_INNVILGET -> Søknadsbehandling.TilAttestering.Innvilget(
                id = behandlingId,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                søknad = søknad,
                oppgaveId = oppgaveId,
                fnr = fnr,
                beregning = beregning!!,
                simulering = simulering!!,
                saksbehandler = saksbehandler!!,
                fritekstTilBrev = fritekstTilBrev,
                stønadsperiode = stønadsperiode!!,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                attesteringer = attesteringer,
                avkorting = avkorting as AvkortingVedSøknadsbehandling.Håndtert,
                sakstype = sakstype,
            )
            BehandlingsStatus.TIL_ATTESTERING_AVSLAG -> when (beregning) {
                null -> Søknadsbehandling.TilAttestering.Avslag.UtenBeregning(
                    id = behandlingId,
                    opprettet = opprettet,
                    sakId = sakId,
                    saksnummer = saksnummer,
                    søknad = søknad,
                    oppgaveId = oppgaveId,
                    fnr = fnr,
                    saksbehandler = saksbehandler!!,
                    fritekstTilBrev = fritekstTilBrev,
                    stønadsperiode = stønadsperiode!!,
                    grunnlagsdata = grunnlagsdata,
                    vilkårsvurderinger = vilkårsvurderinger,
                    attesteringer = attesteringer,
                    avkorting = avkorting as AvkortingVedSøknadsbehandling.Håndtert.KanIkkeHåndtere,
                    sakstype = sakstype,
                )
                else -> Søknadsbehandling.TilAttestering.Avslag.MedBeregning(
                    id = behandlingId,
                    opprettet = opprettet,
                    sakId = sakId,
                    saksnummer = saksnummer,
                    søknad = søknad,
                    oppgaveId = oppgaveId,
                    fnr = fnr,
                    beregning = beregning,
                    saksbehandler = saksbehandler!!,
                    fritekstTilBrev = fritekstTilBrev,
                    stønadsperiode = stønadsperiode!!,
                    grunnlagsdata = grunnlagsdata,
                    vilkårsvurderinger = vilkårsvurderinger,
                    attesteringer = attesteringer,
                    avkorting = avkorting as AvkortingVedSøknadsbehandling.Håndtert.KanIkkeHåndtere,
                    sakstype = sakstype,
                )
            }
            BehandlingsStatus.UNDERKJENT_INNVILGET -> Søknadsbehandling.Underkjent.Innvilget(
                id = behandlingId,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                søknad = søknad,
                oppgaveId = oppgaveId,
                fnr = fnr,
                beregning = beregning!!,
                simulering = simulering!!,
                saksbehandler = saksbehandler!!,
                attesteringer = attesteringer,
                fritekstTilBrev = fritekstTilBrev,
                stønadsperiode = stønadsperiode!!,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                avkorting = avkorting as AvkortingVedSøknadsbehandling.Håndtert,
                sakstype = sakstype,
            )
            BehandlingsStatus.UNDERKJENT_AVSLAG -> when (beregning) {
                null -> Søknadsbehandling.Underkjent.Avslag.UtenBeregning(
                    id = behandlingId,
                    opprettet = opprettet,
                    sakId = sakId,
                    saksnummer = saksnummer,
                    søknad = søknad,
                    oppgaveId = oppgaveId,
                    fnr = fnr,
                    saksbehandler = saksbehandler!!,
                    attesteringer = attesteringer,
                    fritekstTilBrev = fritekstTilBrev,
                    stønadsperiode = stønadsperiode!!,
                    grunnlagsdata = grunnlagsdata,
                    vilkårsvurderinger = vilkårsvurderinger,
                    avkorting = avkorting as AvkortingVedSøknadsbehandling.Håndtert.KanIkkeHåndtere,
                    sakstype = sakstype,
                )
                else -> Søknadsbehandling.Underkjent.Avslag.MedBeregning(
                    id = behandlingId,
                    opprettet = opprettet,
                    sakId = sakId,
                    saksnummer = saksnummer,
                    søknad = søknad,
                    oppgaveId = oppgaveId,
                    fnr = fnr,
                    beregning = beregning,
                    saksbehandler = saksbehandler!!,
                    attesteringer = attesteringer,
                    fritekstTilBrev = fritekstTilBrev,
                    stønadsperiode = stønadsperiode!!,
                    grunnlagsdata = grunnlagsdata,
                    vilkårsvurderinger = vilkårsvurderinger,
                    avkorting = avkorting as AvkortingVedSøknadsbehandling.Håndtert.KanIkkeHåndtere,
                    sakstype = sakstype,
                )
            }
            BehandlingsStatus.IVERKSATT_INNVILGET -> {
                Søknadsbehandling.Iverksatt.Innvilget(
                    id = behandlingId,
                    opprettet = opprettet,
                    sakId = sakId,
                    saksnummer = saksnummer,
                    søknad = søknad,
                    oppgaveId = oppgaveId,
                    fnr = fnr,
                    beregning = beregning!!,
                    simulering = simulering!!,
                    saksbehandler = saksbehandler!!,
                    attesteringer = attesteringer,
                    fritekstTilBrev = fritekstTilBrev,
                    stønadsperiode = stønadsperiode!!,
                    grunnlagsdata = grunnlagsdata,
                    vilkårsvurderinger = vilkårsvurderinger,
                    avkorting = avkorting as AvkortingVedSøknadsbehandling.Iverksatt,
                    sakstype = sakstype,
                )
            }
            BehandlingsStatus.IVERKSATT_AVSLAG -> {
                when (beregning) {
                    null -> Søknadsbehandling.Iverksatt.Avslag.UtenBeregning(
                        id = behandlingId,
                        opprettet = opprettet,
                        sakId = sakId,
                        saksnummer = saksnummer,
                        søknad = søknad,
                        oppgaveId = oppgaveId,
                        fnr = fnr,
                        saksbehandler = saksbehandler!!,
                        attesteringer = attesteringer,
                        fritekstTilBrev = fritekstTilBrev,
                        stønadsperiode = stønadsperiode!!,
                        grunnlagsdata = grunnlagsdata,
                        vilkårsvurderinger = vilkårsvurderinger,
                        avkorting = avkorting as AvkortingVedSøknadsbehandling.Iverksatt.KanIkkeHåndtere,
                        sakstype = sakstype,
                    )
                    else -> Søknadsbehandling.Iverksatt.Avslag.MedBeregning(
                        id = behandlingId,
                        opprettet = opprettet,
                        sakId = sakId,
                        saksnummer = saksnummer,
                        søknad = søknad,
                        oppgaveId = oppgaveId,
                        fnr = fnr,
                        beregning = beregning,
                        saksbehandler = saksbehandler!!,
                        attesteringer = attesteringer,
                        fritekstTilBrev = fritekstTilBrev,
                        stønadsperiode = stønadsperiode!!,
                        grunnlagsdata = grunnlagsdata,
                        vilkårsvurderinger = vilkårsvurderinger,
                        avkorting = avkorting as AvkortingVedSøknadsbehandling.Iverksatt.KanIkkeHåndtere,
                        sakstype = sakstype,
                    )
                }
            }
        }

        if (boolean("lukket")) {
            return LukketSøknadsbehandling.createFromPersistedState(
                søknadsbehandling = søknadsbehandling,
            )
        }
        return søknadsbehandling
    }
}
