package no.nav.su.se.bakover.database.søknadsbehandling

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.database.DbMetrics
import no.nav.su.se.bakover.database.PostgresSessionContext.Companion.withSession
import no.nav.su.se.bakover.database.PostgresSessionFactory
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.beregning.PersistertBeregning
import no.nav.su.se.bakover.database.beregning.toSnapshot
import no.nav.su.se.bakover.database.grunnlag.BosituasjongrunnlagPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.FradragsgrunnlagPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.UføreVilkårsvurderingPostgresRepo
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.database.insert
import no.nav.su.se.bakover.database.oppdatering
import no.nav.su.se.bakover.database.søknad.SøknadRepoInternal
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.database.uuid
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.avslag.AvslagManglendeDokumentasjon
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.søknadsbehandling.BehandlingsStatus
import no.nav.su.se.bakover.domain.søknadsbehandling.LukketSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.NySøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import java.util.UUID
import javax.sql.DataSource

internal class SøknadsbehandlingPostgresRepo(
    private val dataSource: DataSource,
    private val fradragsgrunnlagPostgresRepo: FradragsgrunnlagPostgresRepo,
    private val bosituasjongrunnlagRepo: BosituasjongrunnlagPostgresRepo,
    private val uføreVilkårsvurderingRepo: UføreVilkårsvurderingPostgresRepo,
    private val dbMetrics: DbMetrics,
    private val sessionFactory: PostgresSessionFactory,
) : SøknadsbehandlingRepo {
    override fun lagre(søknadsbehandling: Søknadsbehandling, sessionContext: SessionContext) {
        sessionContext.withSession { session ->
            return@withSession when (søknadsbehandling) {
                is Søknadsbehandling.Vilkårsvurdert -> lagre(søknadsbehandling, session)
                is Søknadsbehandling.Beregnet -> lagre(søknadsbehandling, session)
                is Søknadsbehandling.Simulert -> lagre(søknadsbehandling, session)
                is Søknadsbehandling.TilAttestering -> lagre(søknadsbehandling, session)
                is Søknadsbehandling.Underkjent -> lagre(søknadsbehandling, session)
                is Søknadsbehandling.Iverksatt -> lagre(søknadsbehandling, session)
                is LukketSøknadsbehandling -> lagre(søknadsbehandling, session)
            }
        }
    }

    override fun lagreNySøknadsbehandling(søknadsbehandling: NySøknadsbehandling) {
        dataSource.withSession { session ->
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
                        attestering
                    ) values (
                        :id,
                        :sakId,
                        :soknadId,
                        :opprettet,
                        :status,
                        to_json(:behandlingsinformasjon::json),
                        :oppgaveId,
                        jsonb_build_array()
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
                ),
                session = session,
            )
        }
    }

    override fun lagreAvslagManglendeDokumentasjon(avslag: AvslagManglendeDokumentasjon) {
        dataSource.withSession { session ->
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
                        simulering = :simulering
                    where id = :id
                """.trimIndent()
                ).insert(
                params = avslag.søknadsbehandling.let {
                    mapOf(
                        "behandlingsinformasjon" to objectMapper.writeValueAsString(it.behandlingsinformasjon),
                        "saksbehandler" to it.saksbehandler,
                        "attestering" to it.attesteringer.hentAttesteringer().serialize(),
                        "fritekstTilBrev" to it.fritekstTilBrev,
                        "stonadsperiode" to objectMapper.writeValueAsString(it.stønadsperiode),
                        "status" to it.status.toString(),
                        "id" to it.id,
                        "beregning" to null,
                        "simulering" to null,
                    )
                },
                session = session,
            )
        }
    }

    override fun hentEventuellTidligereAttestering(id: UUID): Attestering? {
        // henter ut siste elementet (seneste attestering) i attesteringslisten
        return dataSource.withSession { session ->
            "select b.attestering from behandling b where b.id=:id"
                .hent(mapOf("id" to id), session) { row ->
                    row.stringOrNull("attestering")?.let {
                        val attesteringer = Attesteringshistorikk(objectMapper.readValue(it))
                        attesteringer.hentAttesteringer().lastOrNull()
                    }
                }
        }
    }

    override fun hent(id: UUID): Søknadsbehandling? {
        return dbMetrics.timeQuery("hentSøknadsbehandling") {
            dataSource.withSession { session ->
                "select b.*, s.fnr, s.saksnummer from behandling b inner join sak s on s.id = b.sakId where b.id=:id"
                    .hent(mapOf("id" to id), session) { row ->
                        row.toSøknadsbehandling(session)
                    }
            }
        }
    }

    override fun hentForSak(sakId: UUID, sessionContext: SessionContext): List<Søknadsbehandling> {
        return sessionContext.withSession { session ->
            "select b.*, s.fnr, s.saksnummer from behandling b inner join sak s on s.id = b.sakId where b.sakId=:sakId"
                .hentListe(mapOf("sakId" to sakId), session) {
                    it.toSøknadsbehandling(session)
                }
        }
    }

    override fun hentForSøknad(søknadId: UUID): Søknadsbehandling? {
        return dataSource.withSession { session ->
            "select b.*, s.fnr, s.saksnummer from behandling b inner join sak s on s.id = b.sakId where søknadId=:soknadId".hent(
                mapOf("soknadId" to søknadId), session,
            ) { it.toSøknadsbehandling(session) }
        }
    }

    override fun defaultSessionContext(): SessionContext {
        return sessionFactory.newSessionContext()
    }

    internal fun hent(id: UUID, session: Session): Søknadsbehandling? {
        return "select b.*, s.fnr, s.saksnummer from behandling b inner join sak s on s.id = b.sakId where b.id=:id"
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
        val beregning = stringOrNull("beregning")?.let { objectMapper.readValue<PersistertBeregning>(it) }
        val simulering = stringOrNull("simulering")?.let { objectMapper.readValue<Simulering>(it) }
        val attesteringer = Attesteringshistorikk(objectMapper.readValue(string("attestering")))
        val saksbehandler = stringOrNull("saksbehandler")?.let { NavIdentBruker.Saksbehandler(it) }
        val saksnummer = Saksnummer(long("saksnummer"))
        val fritekstTilBrev = stringOrNull("fritekstTilBrev") ?: ""
        val stønadsperiode = stringOrNull("stønadsperiode")?.let { objectMapper.readValue<Stønadsperiode>(it) }

        val fnr = Fnr(string("fnr"))
        val grunnlagsdata = Grunnlagsdata.create(
            fradragsgrunnlag = fradragsgrunnlagPostgresRepo.hentFradragsgrunnlag(behandlingId, session),
            bosituasjon = bosituasjongrunnlagRepo.hentBosituasjongrunnlag(behandlingId, session),
        )

        val vilkårsvurderinger = Vilkårsvurderinger(
            uføre = uføreVilkårsvurderingRepo.hent(behandlingId, session),
        )

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
                    )
                }
            }
        }

        if (boolean("lukket")) {
            return LukketSøknadsbehandling.create(søknadsbehandling)
        }
        return søknadsbehandling
    }

    private fun lagre(søknadsbehandling: Søknadsbehandling.Vilkårsvurdert, session: Session) {
        """
                    update behandling set
                        status = :status,
                        behandlingsinformasjon = to_json(:behandlingsinformasjon::json),
                        beregning = null,
                        simulering = null,
                        stønadsperiode = to_json(:stonadsperiode::json)
                    where id = :id    
        """.trimIndent()
            .oppdatering(
                defaultParams(søknadsbehandling), session,
            )
    }

    private fun lagre(søknadsbehandling: Søknadsbehandling.Beregnet, session: Session) {
        """
                   update behandling set status = :status, beregning = to_json(:beregning::json), simulering = null where id = :id
        """.trimIndent()
            .oppdatering(
                params = defaultParams(søknadsbehandling).plus(
                    "beregning" to objectMapper.writeValueAsString(søknadsbehandling.beregning.toSnapshot()),
                ),
                session = session,
            )
    }

    private fun lagre(søknadsbehandling: Søknadsbehandling.Simulert, session: Session) {
        """
                   update behandling set status = :status, beregning = to_json(:beregning::json), simulering = to_json(:simulering::json)  where id = :id
        """.trimIndent()
            .oppdatering(
                defaultParams(søknadsbehandling).plus(
                    listOf(
                        "beregning" to objectMapper.writeValueAsString(søknadsbehandling.beregning.toSnapshot()),
                        "simulering" to objectMapper.writeValueAsString(søknadsbehandling.simulering),
                    ),
                ),
                session,
            )
    }

    private fun lagre(søknadsbehandling: Søknadsbehandling.TilAttestering, session: Session) {
        """
                    update behandling set
                        status = :status,
                        saksbehandler = :saksbehandler,
                        oppgaveId = :oppgaveId,
                        fritekstTilBrev = :fritekstTilBrev
                    where id = :id
        """.trimIndent()
            .oppdatering(
                defaultParams(søknadsbehandling).plus(
                    listOf(
                        "saksbehandler" to søknadsbehandling.saksbehandler.navIdent,
                        "fritekstTilBrev" to søknadsbehandling.fritekstTilBrev,
                    ),
                ),
                session,
            )
    }

    private fun lagre(søknadsbehandling: Søknadsbehandling.Underkjent, session: Session) {
        """
                    update behandling set status = :status, attestering = to_json(:attestering::json), oppgaveId = :oppgaveId  where id = :id
        """.trimIndent()
            .oppdatering(
                params = defaultParams(søknadsbehandling).plus(
                    "attestering" to søknadsbehandling.attesteringer.hentAttesteringer().serialize(),
                ),
                session = session,
            )
    }

    // TODO ai: Se over lagring for nye attesteringer (Attestering -> AttesteringHistorik)
    private fun lagre(søknadsbehandling: Søknadsbehandling.Iverksatt, session: Session) {
        when (søknadsbehandling) {
            is Søknadsbehandling.Iverksatt.Innvilget -> {
                """
                       update behandling set status = :status, attestering = to_json(:attestering::json)  where id = :id
                """.trimIndent()
                    .oppdatering(
                        params = defaultParams(søknadsbehandling).plus(
                            listOf(
                                "attestering" to søknadsbehandling.attesteringer.hentAttesteringer().serialize(),
                            ),
                        ),
                        session = session,
                    )
            }
            is Søknadsbehandling.Iverksatt.Avslag -> {
                """
                       update behandling set status = :status, attestering = to_json(:attestering::json) where id = :id
                """.trimIndent()
                    .oppdatering(
                        params = defaultParams(søknadsbehandling).plus(
                            listOf(
                                "attestering" to søknadsbehandling.attesteringer.hentAttesteringer().serialize(),
                            ),
                        ),
                        session = session,
                    )
            }
        }
    }

    private fun lagre(søknadsbehandling: LukketSøknadsbehandling, session: Session) {
        """
            update behandling set lukket = true where id = :id
        """.trimIndent()
            .oppdatering(
                params = mapOf("id" to søknadsbehandling.lukketSøknadsbehandling.id),
                session = session,
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
        )
    }
}
