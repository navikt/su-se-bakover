package no.nav.su.se.bakover.database.søknadsbehandling

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.database.DbMetrics
import no.nav.su.se.bakover.database.PostgresSessionContext.Companion.withSession
import no.nav.su.se.bakover.database.PostgresSessionFactory
import no.nav.su.se.bakover.database.PostgresTransactionContext.Companion.withTransaction
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.TransactionalSession
import no.nav.su.se.bakover.database.avkorting.AvkortingVedSøknadsbehandlingDb
import no.nav.su.se.bakover.database.avkorting.AvkortingsvarselPostgresRepo
import no.nav.su.se.bakover.database.avkorting.toDb
import no.nav.su.se.bakover.database.avkorting.toDomain
import no.nav.su.se.bakover.database.beregning.deserialiserBeregning
import no.nav.su.se.bakover.database.grunnlag.GrunnlagsdataOgVilkårsvurderingerPostgresRepo
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.database.insert
import no.nav.su.se.bakover.database.oppdatering
import no.nav.su.se.bakover.database.søknad.SøknadRepoInternal
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Sakstype
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.avslag.AvslagManglendeDokumentasjon
import no.nav.su.se.bakover.domain.beregning.BeregningMedFradragBeregnetMånedsvis
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.søknadsbehandling.BehandlingsStatus
import no.nav.su.se.bakover.domain.søknadsbehandling.LukketSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.NySøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingRepo
import java.time.Clock
import java.util.UUID

internal class SøknadsbehandlingPostgresRepo(
    private val sessionFactory: PostgresSessionFactory,
    private val dbMetrics: DbMetrics,
    private val grunnlagsdataOgVilkårsvurderingerPostgresRepo: GrunnlagsdataOgVilkårsvurderingerPostgresRepo,
    private val avkortingsvarselRepo: AvkortingsvarselPostgresRepo,
    private val clock: Clock,
    private val satsFactory: SatsFactory,
) : SøknadsbehandlingRepo {

    override fun lagre(søknadsbehandling: Søknadsbehandling, sessionContext: TransactionContext) {
        dbMetrics.timeQuery("lagreSøknadsbehandling") {
            sessionContext.withTransaction { tx ->
                return@withTransaction when (søknadsbehandling) {
                    is Søknadsbehandling.Vilkårsvurdert -> lagre(søknadsbehandling, tx)
                    is Søknadsbehandling.Beregnet -> lagre(søknadsbehandling, tx)
                    is Søknadsbehandling.Simulert -> lagre(søknadsbehandling, tx)
                    is Søknadsbehandling.TilAttestering -> lagre(søknadsbehandling, tx)
                    is Søknadsbehandling.Underkjent -> lagre(søknadsbehandling, tx)
                    is Søknadsbehandling.Iverksatt -> lagre(søknadsbehandling, tx)
                    is LukketSøknadsbehandling -> lagre(søknadsbehandling, tx)
                }.also {
                    grunnlagsdataOgVilkårsvurderingerPostgresRepo.lagre(
                        behandlingId = søknadsbehandling.id,
                        grunnlagsdataOgVilkårsvurderinger = søknadsbehandling.grunnlagsdataOgVilkårsvurderinger,
                        tx = tx,
                    )
                }
            }
        }
    }

    override fun lagreNySøknadsbehandling(søknadsbehandling: NySøknadsbehandling) {
        dbMetrics.timeQuery("lagreNySøknadsbehandling") {
            sessionFactory.withSession { session ->
                (
                    """
                    insert into behandling (
                        id,
                        sakId,
                        søknadId,
                        opprettet,
                        status,
                        behandlingsinformasjon,
                        oppgaveId,
                        attestering,
                        avkorting
                    ) values (
                        :id,
                        :sakId,
                        :soknadId,
                        :opprettet,
                        :status,
                        to_json(:behandlingsinformasjon::json),
                        :oppgaveId,
                        jsonb_build_array(),
                        to_json(:avkorting::json)
                    )
                    """.trimIndent()
                    ).insert(
                    params = mapOf(
                        "id" to søknadsbehandling.id,
                        "sakId" to søknadsbehandling.sakId,
                        "soknadId" to søknadsbehandling.søknad.id,
                        "opprettet" to søknadsbehandling.opprettet,
                        "status" to BehandlingsStatus.OPPRETTET.toString(),
                        "behandlingsinformasjon" to objectMapper.writeValueAsString(søknadsbehandling.behandlingsinformasjon),
                        "oppgaveId" to søknadsbehandling.oppgaveId.toString(),
                        "avkorting" to objectMapper.writeValueAsString(søknadsbehandling.avkorting.toDb()),
                    ),
                    session = session,
                )
            }
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
                        behandlingsinformasjon = to_json(:behandlingsinformasjon::json),
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
                            "behandlingsinformasjon" to objectMapper.writeValueAsString(it.behandlingsinformasjon),
                            "saksbehandler" to it.saksbehandler,
                            "attestering" to it.attesteringer.serialize(),
                            "fritekstTilBrev" to it.fritekstTilBrev,
                            "stonadsperiode" to objectMapper.writeValueAsString(it.stønadsperiode),
                            "status" to it.status.toString(),
                            "id" to it.id,
                            "beregning" to null,
                            "simulering" to null,
                            "avkorting" to objectMapper.writeValueAsString(it.avkorting.toDb()),
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
                    mapOf("soknadId" to søknadId), session,
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
        val behandlingsinformasjon = objectMapper.readValue<Behandlingsinformasjon>(string("behandlingsinformasjon"))
        val status = BehandlingsStatus.valueOf(string("status"))
        val oppgaveId = OppgaveId(string("oppgaveId"))
        val beregning: BeregningMedFradragBeregnetMånedsvis? = stringOrNull("beregning")?.deserialiserBeregning(satsFactory)
        val simulering = stringOrNull("simulering")?.let { objectMapper.readValue<Simulering>(it) }
        val attesteringer = Attesteringshistorikk.create(objectMapper.readValue(string("attestering")))
        val saksbehandler = stringOrNull("saksbehandler")?.let { NavIdentBruker.Saksbehandler(it) }
        val saksnummer = Saksnummer(long("saksnummer"))
        val fritekstTilBrev = stringOrNull("fritekstTilBrev") ?: ""
        val stønadsperiode = stringOrNull("stønadsperiode")?.let { objectMapper.readValue<Stønadsperiode>(it) }
        val sakstype = Sakstype.from(string("type"))

        val fnr = Fnr(string("fnr"))
        val (grunnlagsdata, vilkårsvurderinger) = grunnlagsdataOgVilkårsvurderingerPostgresRepo.hentForSøknadsbehandling(
            behandlingId,
            session,
        ).let { grunnlagsdataOgVilkårsvurderinger ->
            stønadsperiode?.let {
                grunnlagsdataOgVilkårsvurderinger.copy(
                    vilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger.oppdater(
                        stønadsperiode = stønadsperiode,
                        behandlingsinformasjon = behandlingsinformasjon,
                        clock = clock,
                    ),
                )
            } ?: grunnlagsdataOgVilkårsvurderinger
        }

        val avkorting = stringOrNull("avkorting")?.let {
            objectMapper.readValue<AvkortingVedSøknadsbehandlingDb>(it).toDomain()
        }

        val søknadsbehandling = when (status) {
            BehandlingsStatus.OPPRETTET -> Søknadsbehandling.Vilkårsvurdert.Uavklart(
                id = behandlingId,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                søknad = søknad,
                oppgaveId = oppgaveId,
                behandlingsinformasjon = behandlingsinformasjon,
                fnr = fnr,
                fritekstTilBrev = fritekstTilBrev,
                stønadsperiode = stønadsperiode,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                attesteringer = attesteringer,
                avkorting = avkorting as AvkortingVedSøknadsbehandling.Uhåndtert.KanIkkeHåndtere,
                sakstype = sakstype
            )
            BehandlingsStatus.VILKÅRSVURDERT_INNVILGET -> Søknadsbehandling.Vilkårsvurdert.Innvilget(
                id = behandlingId,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                søknad = søknad,
                oppgaveId = oppgaveId,
                behandlingsinformasjon = behandlingsinformasjon,
                fnr = fnr,
                fritekstTilBrev = fritekstTilBrev,
                stønadsperiode = stønadsperiode!!,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                attesteringer = attesteringer,
                avkorting = avkorting as AvkortingVedSøknadsbehandling.Uhåndtert,
                sakstype = sakstype
            )
            BehandlingsStatus.VILKÅRSVURDERT_AVSLAG -> Søknadsbehandling.Vilkårsvurdert.Avslag(
                id = behandlingId,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                søknad = søknad,
                oppgaveId = oppgaveId,
                behandlingsinformasjon = behandlingsinformasjon,
                fnr = fnr,
                fritekstTilBrev = fritekstTilBrev,
                stønadsperiode = stønadsperiode!!,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                attesteringer = attesteringer,
                avkorting = avkorting as AvkortingVedSøknadsbehandling.Uhåndtert.KanIkkeHåndtere,
                sakstype = sakstype
            )
            BehandlingsStatus.BEREGNET_INNVILGET -> Søknadsbehandling.Beregnet.Innvilget(
                id = behandlingId,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                søknad = søknad,
                oppgaveId = oppgaveId,
                behandlingsinformasjon = behandlingsinformasjon,
                fnr = fnr,
                beregning = beregning!!,
                fritekstTilBrev = fritekstTilBrev,
                stønadsperiode = stønadsperiode!!,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                attesteringer = attesteringer,
                avkorting = avkorting as AvkortingVedSøknadsbehandling.Håndtert,
                sakstype = sakstype
            )
            BehandlingsStatus.BEREGNET_AVSLAG -> Søknadsbehandling.Beregnet.Avslag(
                id = behandlingId,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                søknad = søknad,
                oppgaveId = oppgaveId,
                behandlingsinformasjon = behandlingsinformasjon,
                fnr = fnr,
                beregning = beregning!!,
                fritekstTilBrev = fritekstTilBrev,
                stønadsperiode = stønadsperiode!!,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                attesteringer = attesteringer,
                avkorting = avkorting as AvkortingVedSøknadsbehandling.Håndtert.KanIkkeHåndtere,
                sakstype = sakstype
            )
            BehandlingsStatus.SIMULERT -> Søknadsbehandling.Simulert(
                id = behandlingId,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                søknad = søknad,
                oppgaveId = oppgaveId,
                behandlingsinformasjon = behandlingsinformasjon,
                fnr = fnr,
                beregning = beregning!!,
                simulering = simulering!!,
                fritekstTilBrev = fritekstTilBrev,
                stønadsperiode = stønadsperiode!!,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                attesteringer = attesteringer,
                avkorting = avkorting as AvkortingVedSøknadsbehandling.Håndtert,
                sakstype = sakstype
            )
            BehandlingsStatus.TIL_ATTESTERING_INNVILGET -> Søknadsbehandling.TilAttestering.Innvilget(
                id = behandlingId,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                søknad = søknad,
                oppgaveId = oppgaveId,
                behandlingsinformasjon = behandlingsinformasjon,
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
                sakstype = sakstype
            )
            BehandlingsStatus.TIL_ATTESTERING_AVSLAG -> when (beregning) {
                null -> Søknadsbehandling.TilAttestering.Avslag.UtenBeregning(
                    id = behandlingId,
                    opprettet = opprettet,
                    sakId = sakId,
                    saksnummer = saksnummer,
                    søknad = søknad,
                    oppgaveId = oppgaveId,
                    behandlingsinformasjon = behandlingsinformasjon,
                    fnr = fnr,
                    saksbehandler = saksbehandler!!,
                    fritekstTilBrev = fritekstTilBrev,
                    stønadsperiode = stønadsperiode!!,
                    grunnlagsdata = grunnlagsdata,
                    vilkårsvurderinger = vilkårsvurderinger,
                    attesteringer = attesteringer,
                    avkorting = avkorting as AvkortingVedSøknadsbehandling.Håndtert.KanIkkeHåndtere,
                    sakstype = sakstype
                )
                else -> Søknadsbehandling.TilAttestering.Avslag.MedBeregning(
                    id = behandlingId,
                    opprettet = opprettet,
                    sakId = sakId,
                    saksnummer = saksnummer,
                    søknad = søknad,
                    oppgaveId = oppgaveId,
                    behandlingsinformasjon = behandlingsinformasjon,
                    fnr = fnr,
                    beregning = beregning,
                    saksbehandler = saksbehandler!!,
                    fritekstTilBrev = fritekstTilBrev,
                    stønadsperiode = stønadsperiode!!,
                    grunnlagsdata = grunnlagsdata,
                    vilkårsvurderinger = vilkårsvurderinger,
                    attesteringer = attesteringer,
                    avkorting = avkorting as AvkortingVedSøknadsbehandling.Håndtert.KanIkkeHåndtere,
                    sakstype = sakstype
                )
            }
            BehandlingsStatus.UNDERKJENT_INNVILGET -> Søknadsbehandling.Underkjent.Innvilget(
                id = behandlingId,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                søknad = søknad,
                oppgaveId = oppgaveId,
                behandlingsinformasjon = behandlingsinformasjon,
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
                sakstype = sakstype
            )
            BehandlingsStatus.UNDERKJENT_AVSLAG -> when (beregning) {
                null -> Søknadsbehandling.Underkjent.Avslag.UtenBeregning(
                    id = behandlingId,
                    opprettet = opprettet,
                    sakId = sakId,
                    saksnummer = saksnummer,
                    søknad = søknad,
                    oppgaveId = oppgaveId,
                    behandlingsinformasjon = behandlingsinformasjon,
                    fnr = fnr,
                    saksbehandler = saksbehandler!!,
                    attesteringer = attesteringer,
                    fritekstTilBrev = fritekstTilBrev,
                    stønadsperiode = stønadsperiode!!,
                    grunnlagsdata = grunnlagsdata,
                    vilkårsvurderinger = vilkårsvurderinger,
                    avkorting = avkorting as AvkortingVedSøknadsbehandling.Håndtert.KanIkkeHåndtere,
                    sakstype = sakstype
                )
                else -> Søknadsbehandling.Underkjent.Avslag.MedBeregning(
                    id = behandlingId,
                    opprettet = opprettet,
                    sakId = sakId,
                    saksnummer = saksnummer,
                    søknad = søknad,
                    oppgaveId = oppgaveId,
                    behandlingsinformasjon = behandlingsinformasjon,
                    fnr = fnr,
                    beregning = beregning,
                    saksbehandler = saksbehandler!!,
                    attesteringer = attesteringer,
                    fritekstTilBrev = fritekstTilBrev,
                    stønadsperiode = stønadsperiode!!,
                    grunnlagsdata = grunnlagsdata,
                    vilkårsvurderinger = vilkårsvurderinger,
                    avkorting = avkorting as AvkortingVedSøknadsbehandling.Håndtert.KanIkkeHåndtere,
                    sakstype = sakstype
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
                    behandlingsinformasjon = behandlingsinformasjon,
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
                    sakstype = sakstype
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
                        behandlingsinformasjon = behandlingsinformasjon,
                        fnr = fnr,
                        saksbehandler = saksbehandler!!,
                        attesteringer = attesteringer,
                        fritekstTilBrev = fritekstTilBrev,
                        stønadsperiode = stønadsperiode!!,
                        grunnlagsdata = grunnlagsdata,
                        vilkårsvurderinger = vilkårsvurderinger,
                        avkorting = avkorting as AvkortingVedSøknadsbehandling.Iverksatt.KanIkkeHåndtere,
                        sakstype = sakstype
                    )
                    else -> Søknadsbehandling.Iverksatt.Avslag.MedBeregning(
                        id = behandlingId,
                        opprettet = opprettet,
                        sakId = sakId,
                        saksnummer = saksnummer,
                        søknad = søknad,
                        oppgaveId = oppgaveId,
                        behandlingsinformasjon = behandlingsinformasjon,
                        fnr = fnr,
                        beregning = beregning,
                        saksbehandler = saksbehandler!!,
                        attesteringer = attesteringer,
                        fritekstTilBrev = fritekstTilBrev,
                        stønadsperiode = stønadsperiode!!,
                        grunnlagsdata = grunnlagsdata,
                        vilkårsvurderinger = vilkårsvurderinger,
                        avkorting = avkorting as AvkortingVedSøknadsbehandling.Iverksatt.KanIkkeHåndtere,
                        sakstype = sakstype
                    )
                }
            }
        }

        if (boolean("lukket")) {
            return LukketSøknadsbehandling.create(søknadsbehandling)
        }
        return søknadsbehandling
    }

    private fun lagre(søknadsbehandling: Søknadsbehandling.Vilkårsvurdert, tx: TransactionalSession) {
        """
                    update behandling set
                        status = :status,
                        behandlingsinformasjon = to_json(:behandlingsinformasjon::json),
                        beregning = null,
                        simulering = null,
                        stønadsperiode = to_json(:stonadsperiode::json),
                        avkorting = to_json(:avkorting::json)
                    where id = :id    
        """.trimIndent()
            .oppdatering(defaultParams(søknadsbehandling), tx)
    }

    private fun lagre(søknadsbehandling: Søknadsbehandling.Beregnet, tx: TransactionalSession) {
        """
                   update behandling set 
                       status = :status, 
                       beregning = to_json(:beregning::json), 
                       simulering = null,
                       avkorting = to_json(:avkorting::json)
                   where id = :id
        """.trimIndent()
            .oppdatering(
                params = defaultParams(søknadsbehandling).plus(
                    "beregning" to søknadsbehandling.beregning,
                ),
                session = tx,
            )
    }

    private fun lagre(søknadsbehandling: Søknadsbehandling.Simulert, tx: TransactionalSession) {
        """
                   update behandling set 
                       status = :status, 
                       beregning = to_json(:beregning::json), 
                       simulering = to_json(:simulering::json),
                       avkorting = to_json(:avkorting::json)
                   where id = :id
        """.trimIndent()
            .oppdatering(
                defaultParams(søknadsbehandling).plus(
                    listOf(
                        "beregning" to søknadsbehandling.beregning,
                        "simulering" to objectMapper.writeValueAsString(søknadsbehandling.simulering),
                    ),
                ),
                tx,
            )
    }

    private fun lagre(søknadsbehandling: Søknadsbehandling.TilAttestering, tx: TransactionalSession) {
        """
                    update behandling set
                        status = :status,
                        saksbehandler = :saksbehandler,
                        oppgaveId = :oppgaveId,
                        fritekstTilBrev = :fritekstTilBrev,
                        avkorting = to_json(:avkorting::json)
                    where id = :id
        """.trimIndent()
            .oppdatering(
                defaultParams(søknadsbehandling).plus(
                    listOf(
                        "saksbehandler" to søknadsbehandling.saksbehandler.navIdent,
                        "fritekstTilBrev" to søknadsbehandling.fritekstTilBrev,
                    ),
                ),
                tx,
            )
    }

    private fun lagre(søknadsbehandling: Søknadsbehandling.Underkjent, tx: TransactionalSession) {
        """
                    update behandling set 
                        status = :status, 
                        attestering = to_json(:attestering::json), 
                        oppgaveId = :oppgaveId,
                        avkorting = to_json(:avkorting::json)
                    where id = :id
        """.trimIndent()
            .oppdatering(
                params = defaultParams(søknadsbehandling).plus(
                    "attestering" to søknadsbehandling.attesteringer.serialize(),
                ),
                session = tx,
            )
    }

    private fun lagre(søknadsbehandling: Søknadsbehandling.Iverksatt, tx: TransactionalSession) {
        when (søknadsbehandling) {
            is Søknadsbehandling.Iverksatt.Innvilget -> {
                """
                       update behandling set 
                           status = :status, 
                           attestering = to_json(:attestering::json),
                           avkorting = to_json(:avkorting::json)
                       where id = :id
                """.trimIndent()
                    .oppdatering(
                        params = defaultParams(søknadsbehandling).plus(
                            listOf(
                                "attestering" to søknadsbehandling.attesteringer.serialize(),
                            ),
                        ),
                        session = tx,
                    )

                when (val avkort = søknadsbehandling.avkorting) {
                    is AvkortingVedSøknadsbehandling.Iverksatt.AvkortUtestående -> {
                        avkortingsvarselRepo.lagre(
                            avkortingsvarsel = avkort.avkortingsvarsel,
                            tx = tx,
                        )
                    }
                    is AvkortingVedSøknadsbehandling.Iverksatt.IngenUtestående -> {
                        // noop
                    }
                    is AvkortingVedSøknadsbehandling.Iverksatt.KanIkkeHåndtere -> {
                        throw IllegalStateException("Innvilget søknadsbehandling:${søknadsbehandling.id} bør håndtere avkorting")
                    }
                }
            }
            is Søknadsbehandling.Iverksatt.Avslag -> {
                """
                       update behandling set 
                           status = :status, 
                           attestering = to_json(:attestering::json),
                           avkorting = to_json(:avkorting::json) 
                       where id = :id
                """.trimIndent()
                    .oppdatering(
                        params = defaultParams(søknadsbehandling).plus(
                            listOf(
                                "attestering" to søknadsbehandling.attesteringer.serialize(),
                            ),
                        ),
                        session = tx,
                    )
            }
        }
    }

    private fun lagre(søknadsbehandling: LukketSøknadsbehandling, tx: TransactionalSession) {
        """
            update behandling set 
                lukket = true,
                avkorting = to_json(:avkorting::json)
            where id = :id
        """.trimIndent()
            .oppdatering(
                params = mapOf(
                    "id" to søknadsbehandling.lukketSøknadsbehandling.id,
                    "avkorting" to objectMapper.writeValueAsString(søknadsbehandling.avkorting.toDb()),
                ),
                session = tx,
            )
    }

    private fun defaultParams(søknadsbehandling: Søknadsbehandling): Map<String, Any> {
        return mapOf(
            "id" to søknadsbehandling.id,
            "sakId" to søknadsbehandling.sakId,
            "soknadId" to søknadsbehandling.søknad.id,
            "opprettet" to søknadsbehandling.opprettet,
            "status" to søknadsbehandling.status.name,
            "behandlingsinformasjon" to objectMapper.writeValueAsString(søknadsbehandling.behandlingsinformasjon),
            "oppgaveId" to søknadsbehandling.oppgaveId.toString(),
            "stonadsperiode" to objectMapper.writeValueAsString(søknadsbehandling.stønadsperiode),
            "avkorting" to objectMapper.writeValueAsString(søknadsbehandling.avkorting.toDb()),
        )
    }
}
