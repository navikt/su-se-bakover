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
import no.nav.su.se.bakover.database.simulering.deserializeNullableSimulering
import no.nav.su.se.bakover.database.simulering.serializeNullableSimulering
import no.nav.su.se.bakover.database.skatt.SkattPostgresRepo
import no.nav.su.se.bakover.database.søknad.SøknadRepoInternal
import no.nav.su.se.bakover.database.søknadsbehandling.AldersvurderingJson.Companion.toDBJson
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingStatusDB.Companion.status
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingshistorikkJson.Companion.toDbJson
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.BeregningMedFradragBeregnetMånedsvis
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.sak.Saksnummer
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.satser.SatsFactoryForSupplerendeStønad
import no.nav.su.se.bakover.domain.skatt.Skattereferanser
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.søknadsbehandling.BeregnetSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.IverksattSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.LukketSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SimulertSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingMedSkattegrunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingTilAttestering
import no.nav.su.se.bakover.domain.søknadsbehandling.UnderkjentSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.VilkårsvurdertSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.opprett.NySøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Stønadsperiode
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import java.util.UUID

internal data class BaseSøknadsbehandlingDb(
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
    private val skattRepo: SkattPostgresRepo,
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
                status = SøknadsbehandlingStatusDB.OPPRETTET,
                saksbehandler = saksbehandler.navIdent,
                søknadsbehandlingshistorikk = this.søknadsbehandlingsHistorikk.toDbJson(),
                aldersvurdering = null,
            ),
            beregning = null,
            simulering = null,
            lukket = false,
        )
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
            attesteringer = this.attesteringer,
            avkorting = this.avkorting,
            sakstype = this.sakstype,
            status = this.status(),
            saksbehandler = this.saksbehandler.toString(),
            søknadsbehandlingshistorikk = this.søknadsbehandlingsHistorikk.toDbJson(),
            aldersvurdering = this.aldersvurdering?.toDBJson(),
        )
    }

    private fun lagre(søknadsbehandling: SøknadsbehandlingDb, tx: TransactionalSession) {
        val (eksisterendeSøkerId, eksisterendeEpsId) = hentSkatteIDerForBehandling(søknadsbehandling.base.id, tx)
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
                        to_json(:avkorting::json),
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
                        avkorting = to_json(:avkorting::json),
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
                "simulering" to søknadsbehandling.simulering.serializeNullableSimulering(),
                "lukket" to søknadsbehandling.lukket,
                "saksbehandling" to søknadsbehandling.base.søknadsbehandlingshistorikk,
                "aldersvurdering" to søknadsbehandling.base.aldersvurdering,
                "sokersskatteid" to søknadsbehandling.grunnlagsdataOgVilkårsvurderinger.grunnlagsdata.skattereferanser?.søkers,
                "epsskatteid" to søknadsbehandling.grunnlagsdataOgVilkårsvurderinger.grunnlagsdata.skattereferanser?.eps,
            ),
            session = tx,
        )

        grunnlagsdataOgVilkårsvurderingerPostgresRepo.lagre(
            behandlingId = søknadsbehandling.base.id,
            grunnlagsdataOgVilkårsvurderinger = søknadsbehandling.grunnlagsdataOgVilkårsvurderinger,
            tx = tx,
        )

        fjernSkattedataDersomSkattereferanserIkkeErIHenholdTilBehandling(
            eksisterendeEpsId = eksisterendeEpsId,
            eksisterendeSøkersId = eksisterendeSøkerId,
            søknadsbehandling = søknadsbehandling,
            tx = tx,
        )

        when (val avkort = søknadsbehandling.base.avkorting) {
            is AvkortingVedSøknadsbehandling.Iverksatt.AvkortUtestående -> {
                avkortingsvarselRepo.lagre(
                    avkortingsvarsel = avkort.avkortingsvarsel,
                    tx = tx,
                )
            }

            else -> { /*noop*/
            }
        }
    }

    private fun fjernSkattedataDersomSkattereferanserIkkeErIHenholdTilBehandling(
        eksisterendeSøkersId: UUID?,
        eksisterendeEpsId: UUID?,
        søknadsbehandling: SøknadsbehandlingDb,
        tx: TransactionalSession,
    ) {
        if (eksisterendeSøkersId != null && eksisterendeSøkersId != søknadsbehandling.grunnlagsdataOgVilkårsvurderinger.grunnlagsdata.skattereferanser?.søkers) {
            TODO("Vi har ikke støtte for å fjerne søkers skattegrunnlag gjennom endring av søknadsbehandling. behandling id ${søknadsbehandling.base.id}")
        }
        if (eksisterendeEpsId != null && eksisterendeEpsId != søknadsbehandling.grunnlagsdataOgVilkårsvurderinger.grunnlagsdata.skattereferanser?.eps) {
            skattRepo.slettSkattegrunnlag(eksisterendeEpsId, tx)
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

    /**
     * obs: Kan ikke slette søkers skattemelding, men kun oppdatere den til non-null.
     *
     * 1) hent skatteIDer på behandling (søker + eps)
     * 2) lagre ny(e) skattegrunnlag
     * 3) lagre behandling i sin helhet med skattegrunnlag
     * 4) Slett overflødige skattegrunnlag
     */
    override fun lagreMedSkattegrunnlag(skattegrunnlagForSøknadsbehandling: SøknadsbehandlingMedSkattegrunnlag) {
        dbMetrics.timeQuery("lagreMedSkattegrunnlag") {
            sessionFactory.withTransaction { tx ->
                val (eksisterendeSøker: UUID?, eksisterendeEps: UUID?) = hentSkatteIDerForBehandling(
                    skattegrunnlagForSøknadsbehandling.søknadsbehandling.id,
                    tx,
                ).let { Pair(it.first, it.second) }

                val skalOppdatereSøker = eksisterendeSøker != skattegrunnlagForSøknadsbehandling.søkersSkatteId

                val skalSletteSøker =
                    eksisterendeSøker != null && eksisterendeSøker != skattegrunnlagForSøknadsbehandling.søkersSkatteId

                val skalOppdatereEps =
                    skattegrunnlagForSøknadsbehandling.eps != null && eksisterendeEps != skattegrunnlagForSøknadsbehandling.epsSkatteId

                val skalSletteEps =
                    eksisterendeEps != null && eksisterendeEps != skattegrunnlagForSøknadsbehandling.epsSkatteId

                if (!skalOppdatereSøker && !skalOppdatereEps && !skalSletteSøker && !skalSletteEps) {
                    return@withTransaction
                }

                if (skalOppdatereSøker) {
                    skattRepo.lagreSkattegrunnlag(
                        id = skattegrunnlagForSøknadsbehandling.søkersSkatteId,
                        sakId = skattegrunnlagForSøknadsbehandling.sakId,
                        fnr = skattegrunnlagForSøknadsbehandling.søker.fnr,
                        erEps = false,
                        opprettet = skattegrunnlagForSøknadsbehandling.opprettet,
                        data = skattegrunnlagForSøknadsbehandling.søker,
                        saksbehandler = skattegrunnlagForSøknadsbehandling.søker.saksbehandler,
                        årSpurtFor = skattegrunnlagForSøknadsbehandling.søker.årSpurtFor,
                        session = tx,
                    )
                }

                if (skalOppdatereEps) {
                    skattRepo.lagreSkattegrunnlag(
                        id = skattegrunnlagForSøknadsbehandling.epsSkatteId!!,
                        sakId = skattegrunnlagForSøknadsbehandling.sakId,
                        fnr = skattegrunnlagForSøknadsbehandling.eps!!.fnr,
                        erEps = true,
                        opprettet = skattegrunnlagForSøknadsbehandling.opprettet,
                        data = skattegrunnlagForSøknadsbehandling.eps!!,
                        saksbehandler = skattegrunnlagForSøknadsbehandling.eps!!.saksbehandler,
                        årSpurtFor = skattegrunnlagForSøknadsbehandling.eps!!.årSpurtFor,
                        session = tx,
                    )
                }
                lagre(skattegrunnlagForSøknadsbehandling.søknadsbehandling.toDb(), tx)

                if (skalSletteSøker) {
                    skattRepo.slettSkattegrunnlag(eksisterendeSøker!!, tx)
                }
                if (skalSletteEps) {
                    skattRepo.slettSkattegrunnlag(eksisterendeEps!!, tx)
                }
            }
        }
    }

    private fun hentSkatteIDerForBehandling(
        behandlingId: UUID,
        session: Session,
    ): Pair<UUID?, UUID?> {
        return "select søkersSkatteId, epsSkatteId from behandling where id=:id"
            .hent(mapOf("id" to behandlingId), session) {
                it.uuidOrNull("søkersSkatteId") to it.uuidOrNull("epsSkatteId")
            } ?: Pair(null, null)
    }

    override fun hentSkattegrunnlag(behandlingId: UUID): SøknadsbehandlingMedSkattegrunnlag? {
        return dbMetrics.timeQuery("hentSkattegrunnlagForSøknadsbehandling") {
            sessionFactory.withSession { session ->
                internalHentSkattegrunnlag(behandlingId, session)
            }
        }
    }

    private fun internalHentSkattegrunnlag(behandlingId: UUID, session: Session): SøknadsbehandlingMedSkattegrunnlag? {
        return hentSkatteIDerForBehandling(behandlingId, session).let {
            val søkers = it.first?.let { skattRepo.hent(it, session) }
            val eps = it.second?.let { skattRepo.hent(it, session) }

            if (søkers == null) {
                null
            } else {
                val søknadsbehandling = hent(behandlingId, session)
                SøknadsbehandlingMedSkattegrunnlag(
                    søknadsbehandling = søknadsbehandling
                        ?: throw IllegalStateException("Fant ikke søknadsbehandling for id $behandlingId. Denne har eksistert tidligere?"),
                    opprettet = søkers.hentetTidspunkt,
                    søker = søkers,
                    eps = eps,
                )
            }
        }
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
        val status = SøknadsbehandlingStatusDB.valueOf(string("status"))
        val oppgaveId = OppgaveId(string("oppgaveId"))
        val sakstype = Sakstype.from(string("type"))
        val beregning: BeregningMedFradragBeregnetMånedsvis? = stringOrNull("beregning")?.deserialiserBeregning(
            satsFactory = satsFactory.gjeldende(opprettet),
            sakstype = sakstype,
        )
        val simulering = stringOrNull("simulering").deserializeNullableSimulering()
        val attesteringer = Attesteringshistorikk.create(deserializeList((string("attestering"))))
        val søknadsbehandlingHistorikk =
            SøknadsbehandlingshistorikkJson.toSøknadsbehandlingsHistorikk(string("saksbehandling"))
        val saksbehandler = NavIdentBruker.Saksbehandler(string("saksbehandler"))
        val saksnummer = Saksnummer(long("saksnummer"))
        val fritekstTilBrev = stringOrNull("fritekstTilBrev") ?: ""
        val stønadsperiode = deserializeNullable<Stønadsperiode>(stringOrNull("stønadsperiode"))

        val aldersvurdering =
            stønadsperiode?.let { AldersvurderingJson.toAldersvurdering(string("aldersvurdering"), it) }

        val fnr = Fnr(string("fnr"))

        val skattereferanser = uuidOrNull("søkersSkatteId")?.let {
            Skattereferanser(it, uuidOrNull("epsSkatteId"))
        }

        val (grunnlagsdata, vilkårsvurderinger) = grunnlagsdataOgVilkårsvurderingerPostgresRepo.hentForSøknadsbehandling(
            behandlingId = behandlingId,
            session = session,
            sakstype = Sakstype.from(string("type")),
            skattereferanser = skattereferanser,
        )

        val avkorting = deserializeNullable<AvkortingVedSøknadsbehandlingDb>(stringOrNull("avkorting"))?.toDomain()

        val søknadsbehandling = when (status) {
            SøknadsbehandlingStatusDB.OPPRETTET -> VilkårsvurdertSøknadsbehandling.Uavklart(
                id = behandlingId,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                søknad = søknad,
                oppgaveId = oppgaveId,
                fnr = fnr,
                fritekstTilBrev = fritekstTilBrev,
                aldersvurdering = aldersvurdering,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                attesteringer = attesteringer,
                søknadsbehandlingsHistorikk = søknadsbehandlingHistorikk,
                avkorting = avkorting as AvkortingVedSøknadsbehandling.Uhåndtert.KanIkkeHåndtere,
                sakstype = sakstype,
                saksbehandler = saksbehandler,
            )

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
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                attesteringer = attesteringer,
                søknadsbehandlingsHistorikk = søknadsbehandlingHistorikk,
                avkorting = avkorting as AvkortingVedSøknadsbehandling.Uhåndtert,
                sakstype = sakstype,
                saksbehandler = saksbehandler,
            )

            SøknadsbehandlingStatusDB.VILKÅRSVURDERT_AVSLAG -> VilkårsvurdertSøknadsbehandling.Avslag(
                id = behandlingId,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                søknad = søknad,
                oppgaveId = oppgaveId,
                fnr = fnr,
                fritekstTilBrev = fritekstTilBrev,
                aldersvurdering = aldersvurdering!!,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                attesteringer = attesteringer,
                søknadsbehandlingsHistorikk = søknadsbehandlingHistorikk,
                avkorting = avkorting as AvkortingVedSøknadsbehandling.Uhåndtert.KanIkkeHåndtere,
                sakstype = sakstype,
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
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                attesteringer = attesteringer,
                søknadsbehandlingsHistorikk = søknadsbehandlingHistorikk,
                avkorting = avkorting as AvkortingVedSøknadsbehandling.Håndtert,
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
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                attesteringer = attesteringer,
                søknadsbehandlingsHistorikk = søknadsbehandlingHistorikk,
                avkorting = avkorting as AvkortingVedSøknadsbehandling.Håndtert.KanIkkeHåndtere,
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
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                attesteringer = attesteringer,
                søknadsbehandlingsHistorikk = søknadsbehandlingHistorikk,
                avkorting = avkorting as AvkortingVedSøknadsbehandling.Håndtert,
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
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                attesteringer = attesteringer,
                søknadsbehandlingsHistorikk = søknadsbehandlingHistorikk,
                avkorting = avkorting as AvkortingVedSøknadsbehandling.Håndtert,
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
                    grunnlagsdata = grunnlagsdata,
                    vilkårsvurderinger = vilkårsvurderinger,
                    attesteringer = attesteringer,
                    søknadsbehandlingsHistorikk = søknadsbehandlingHistorikk,
                    avkorting = avkorting as AvkortingVedSøknadsbehandling.Håndtert.KanIkkeHåndtere,
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
                    grunnlagsdata = grunnlagsdata,
                    vilkårsvurderinger = vilkårsvurderinger,
                    attesteringer = attesteringer,
                    søknadsbehandlingsHistorikk = søknadsbehandlingHistorikk,
                    avkorting = avkorting as AvkortingVedSøknadsbehandling.Håndtert.KanIkkeHåndtere,
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
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                avkorting = avkorting as AvkortingVedSøknadsbehandling.Håndtert,
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
                    grunnlagsdata = grunnlagsdata,
                    vilkårsvurderinger = vilkårsvurderinger,
                    avkorting = avkorting as AvkortingVedSøknadsbehandling.Håndtert.KanIkkeHåndtere,
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
                    grunnlagsdata = grunnlagsdata,
                    vilkårsvurderinger = vilkårsvurderinger,
                    avkorting = avkorting as AvkortingVedSøknadsbehandling.Håndtert.KanIkkeHåndtere,
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
                    grunnlagsdata = grunnlagsdata,
                    vilkårsvurderinger = vilkårsvurderinger,
                    avkorting = avkorting as AvkortingVedSøknadsbehandling.Iverksatt,
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
                        grunnlagsdata = grunnlagsdata,
                        vilkårsvurderinger = vilkårsvurderinger,
                        avkorting = avkorting as AvkortingVedSøknadsbehandling.Iverksatt.KanIkkeHåndtere,
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
