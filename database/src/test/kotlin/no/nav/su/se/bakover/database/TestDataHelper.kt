package no.nav.su.se.bakover.database

import arrow.core.NonEmptyList
import arrow.core.getOrHandle
import arrow.core.nonEmptyListOf
import arrow.core.right
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.dokument.DokumentPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.BosituasjongrunnlagPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.FormueVilkårsvurderingPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.FormuegrunnlagPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.FradragsgrunnlagPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.GrunnlagPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.UføreVilkårsvurderingPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.UføregrunnlagPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.UtenlandsoppholdVilkårsvurderingPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.UtenlandsoppholdgrunnlagPostgresRepo
import no.nav.su.se.bakover.database.hendelse.PersonhendelsePostgresRepo
import no.nav.su.se.bakover.database.hendelseslogg.HendelsesloggPostgresRepo
import no.nav.su.se.bakover.database.nøkkeltall.NøkkeltallPostgresRepo
import no.nav.su.se.bakover.database.person.PersonPostgresRepo
import no.nav.su.se.bakover.database.revurdering.RevurderingPostgresRepo
import no.nav.su.se.bakover.database.sak.SakPostgresRepo
import no.nav.su.se.bakover.database.søknad.SøknadPostgresRepo
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingPostgresRepo
import no.nav.su.se.bakover.database.utbetaling.UtbetalingPostgresRepo
import no.nav.su.se.bakover.database.vedtak.VedtakPostgresRepo
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.NySak
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.SakFactory
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnhold
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.behandling.withAvslåttFlyktning
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.hendelseslogg.Hendelseslogg
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Forhåndsvarsel
import no.nav.su.se.bakover.domain.revurdering.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.revurdering.UnderkjentRevurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.LukketSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.NySøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.domain.vilkår.UtenlandsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.test.behandlingsinformasjonAlleVilkårInnvilget
import no.nav.su.se.bakover.test.beregningAvslagForHøyInntekt
import no.nav.su.se.bakover.test.bosituasjongrunnlagEnslig
import no.nav.su.se.bakover.test.bosituasjongrunnlagEpsUførFlyktning
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.innvilgetUførevilkår
import no.nav.su.se.bakover.test.innvilgetUførevilkårForventetInntekt0
import no.nav.su.se.bakover.test.uføregrunnlagForventetInntekt0
import no.nav.su.se.bakover.test.utlandsoppholdInnvilget
import no.nav.su.se.bakover.test.vilkårsvurderingerInnvilget
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import javax.sql.DataSource

internal val stønadsperiode = Stønadsperiode.create(Periode.create(1.januar(2021), 31.desember(2021)))
internal val tomBehandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon()
internal val behandlingsinformasjonMedAlleVilkårOppfylt =
    Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt()
internal val behandlingsinformasjonMedAvslag =
    Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAvslåttFlyktning()

internal val oppgaveId = OppgaveId("oppgaveId")
internal val journalpostId = JournalpostId("journalpostId")

internal fun innvilgetBeregning(periode: Periode = stønadsperiode.periode) =
    no.nav.su.se.bakover.test.beregning(periode)

internal val avslåttBeregning: Beregning = beregningAvslagForHøyInntekt()

internal fun simulering(fnr: Fnr) = Simulering(
    gjelderId = fnr,
    gjelderNavn = "gjelderNavn",
    datoBeregnet = fixedLocalDate,
    nettoBeløp = 100,
    periodeList = emptyList(),
)

internal val saksbehandler = NavIdentBruker.Saksbehandler("saksbehandler")
internal val attestant = NavIdentBruker.Attestant("attestant")
internal val underkjentAttestering =
    Attestering.Underkjent(
        attestant = attestant,
        grunn = Attestering.Underkjent.Grunn.ANDRE_FORHOLD,
        kommentar = "kommentar",
        opprettet = fixedTidspunkt,
    )
internal val iverksattAttestering = Attestering.Iverksatt(attestant, fixedTidspunkt)
internal val avstemmingsnøkkel = Avstemmingsnøkkel(fixedTidspunkt)
internal fun utbetalingslinje(periode: Periode = stønadsperiode.periode) =
    Utbetalingslinje.Ny(
        opprettet = fixedTidspunkt,
        fraOgMed = periode.fraOgMed,
        tilOgMed = periode.tilOgMed,
        forrigeUtbetalingslinjeId = null,
        beløp = 25000,
        uføregrad = Uføregrad.parse(50),
    )

internal fun oversendtUtbetalingUtenKvittering(
    søknadsbehandling: Søknadsbehandling.Iverksatt.Innvilget,
    avstemmingsnøkkel: Avstemmingsnøkkel = no.nav.su.se.bakover.database.avstemmingsnøkkel,
    utbetalingslinjer: NonEmptyList<Utbetalingslinje> = nonEmptyListOf(utbetalingslinje(søknadsbehandling.periode)),
) = oversendtUtbetalingUtenKvittering(
    søknadsbehandling.fnr,
    søknadsbehandling.sakId,
    søknadsbehandling.saksnummer,
    utbetalingslinjer,
    avstemmingsnøkkel,
)

internal fun oversendtUtbetalingUtenKvittering(
    revurdering: RevurderingTilAttestering,
    avstemmingsnøkkel: Avstemmingsnøkkel = no.nav.su.se.bakover.database.avstemmingsnøkkel,
    utbetalingslinjer: NonEmptyList<Utbetalingslinje> = nonEmptyListOf(utbetalingslinje(revurdering.periode)),
) = oversendtUtbetalingUtenKvittering(
    revurdering.fnr,
    revurdering.sakId,
    revurdering.saksnummer,
    utbetalingslinjer,
    avstemmingsnøkkel,
)

internal fun oversendtUtbetalingUtenKvittering(
    fnr: Fnr,
    sakId: UUID,
    saksnummer: Saksnummer,
    utbetalingslinjer: NonEmptyList<Utbetalingslinje> = nonEmptyListOf(utbetalingslinje()),
    avstemmingsnøkkel: Avstemmingsnøkkel = no.nav.su.se.bakover.database.avstemmingsnøkkel,
) = Utbetaling.OversendtUtbetaling.UtenKvittering(
    id = UUID30.randomUUID(),
    opprettet = fixedTidspunkt,
    sakId = sakId,
    saksnummer = saksnummer,
    fnr = fnr,
    utbetalingslinjer = utbetalingslinjer,
    type = Utbetaling.UtbetalingsType.NY,
    behandler = attestant,
    avstemmingsnøkkel = avstemmingsnøkkel,
    simulering = simulering(fnr),
    utbetalingsrequest = Utbetalingsrequest("<xml></xml>"),
)

internal val kvitteringOk = Kvittering(
    utbetalingsstatus = Kvittering.Utbetalingsstatus.OK,
    originalKvittering = "hallo",
    mottattTidspunkt = fixedTidspunkt,
)

internal val dbMetricsStub: DbMetrics = object : DbMetrics {
    override fun <T> timeQuery(label: String, block: () -> T): T {
        return block()
    }
}

internal class TestDataHelper(
    internal val dataSource: DataSource,
    dbMetrics: DbMetrics = dbMetricsStub,
    private val clock: Clock = fixedClock,
) {
    internal val sessionFactory = PostgresSessionFactory(dataSource)
    internal val utbetalingRepo = UtbetalingPostgresRepo(
        dataSource = dataSource,
        dbMetrics = dbMetrics,
    )
    internal val hendelsesloggRepo = HendelsesloggPostgresRepo(dataSource)
    internal val søknadRepo = SøknadPostgresRepo(
        dataSource = dataSource,
        dbMetrics = dbMetrics,
        postgresSessionFactory = sessionFactory,
    )
    internal val uføregrunnlagPostgresRepo = UføregrunnlagPostgresRepo()
    internal val utenlandsoppholdgrunnlagPostgresRepo = UtenlandsoppholdgrunnlagPostgresRepo()
    internal val fradragsgrunnlagPostgresRepo = FradragsgrunnlagPostgresRepo(
        dataSource = dataSource,
        dbMetrics = dbMetrics,
    )
    internal val bosituasjongrunnlagPostgresRepo = BosituasjongrunnlagPostgresRepo(
        dataSource = dataSource,
        dbMetrics = dbMetrics,
    )
    internal val grunnlagRepo = GrunnlagPostgresRepo(
        fradragsgrunnlagRepo = fradragsgrunnlagPostgresRepo,
        bosituasjongrunnlagRepo = bosituasjongrunnlagPostgresRepo,
    )
    internal val uføreVilkårsvurderingRepo = UføreVilkårsvurderingPostgresRepo(
        dataSource = dataSource,
        uføregrunnlagRepo = uføregrunnlagPostgresRepo,
        dbMetrics = dbMetrics,
    )
    internal val utenlandsoppholdVilkårsvurderingRepo = UtenlandsoppholdVilkårsvurderingPostgresRepo(
        utenlandsoppholdgrunnlagRepo = utenlandsoppholdgrunnlagPostgresRepo,
        dbMetrics = dbMetrics,
    )
    internal val avkortingsvarselRepo = AvkortingsvarselPostgresRepo(
        dataSource = dataSource,
    )
    internal val formuegrunnlagPostgresRepo = FormuegrunnlagPostgresRepo()
    internal val formueVilkårsvurderingPostgresRepo = FormueVilkårsvurderingPostgresRepo(
        dataSource = dataSource,
        formuegrunnlagPostgresRepo = formuegrunnlagPostgresRepo,
        dbMetrics = dbMetrics,
    )
    internal val søknadsbehandlingRepo = SøknadsbehandlingPostgresRepo(
        dataSource = dataSource,
        fradragsgrunnlagPostgresRepo = fradragsgrunnlagPostgresRepo,
        bosituasjongrunnlagRepo = bosituasjongrunnlagPostgresRepo,
        uføreVilkårsvurderingRepo = uføreVilkårsvurderingRepo,
        dbMetrics = dbMetrics,
        sessionFactory = sessionFactory,
        utenlandsoppholdVilkårsvurderingRepo = utenlandsoppholdVilkårsvurderingRepo,
    )
    internal val revurderingRepo = RevurderingPostgresRepo(
        dataSource = dataSource,
        fradragsgrunnlagPostgresRepo = fradragsgrunnlagPostgresRepo,
        bosituasjonsgrunnlagPostgresRepo = bosituasjongrunnlagPostgresRepo,
        uføreVilkårsvurderingRepo = uføreVilkårsvurderingRepo,
        utlandsoppholdVilkårsvurderingRepo = utenlandsoppholdVilkårsvurderingRepo,
        formueVilkårsvurderingRepo = formueVilkårsvurderingPostgresRepo,
        søknadsbehandlingRepo = søknadsbehandlingRepo,
        dbMetrics = dbMetrics,
        sessionFactory = sessionFactory,
        avkortingsvarselRepo = avkortingsvarselRepo,
    )
    internal val vedtakRepo = VedtakPostgresRepo(
        dataSource = dataSource,
        søknadsbehandlingRepo = søknadsbehandlingRepo,
        revurderingRepo = revurderingRepo,
        dbMetrics = dbMetrics,
        sessionFactory = sessionFactory,
    )
    internal val sakRepo = SakPostgresRepo(
        sessionFactory = sessionFactory,
        søknadsbehandlingRepo = søknadsbehandlingRepo,
        revurderingRepo = revurderingRepo,
        vedtakPostgresRepo = vedtakRepo,
        dbMetrics = dbMetrics,
    )
    internal val personRepo = PersonPostgresRepo(
        dataSource = dataSource,
        dbMetrics = dbMetrics,
    )
    internal val nøkkeltallRepo = NøkkeltallPostgresRepo(dataSource = dataSource, fixedClock)
    internal val dokumentRepo = DokumentPostgresRepo(dataSource, sessionFactory)
    internal val hendelsePostgresRepo = PersonhendelsePostgresRepo(dataSource, fixedClock)

    fun nySakMedNySøknad(
        fnr: Fnr = Fnr.generer(),
        søknadInnhold: SøknadInnhold = SøknadInnholdTestdataBuilder.build(),
    ): NySak {
        return SakFactory(clock = clock).nySakMedNySøknad(fnr, søknadInnhold).also {
            sakRepo.opprettSak(it)
        }
    }

    /**
     * Ny søknad som _ikke_ er journalført eller har oppgave
     */
    fun nySøknadForEksisterendeSak(
        sakId: UUID,
        søknadInnhold: SøknadInnhold = SøknadInnholdTestdataBuilder.build(),
    ): Søknad.Ny {
        return Søknad.Ny(
            sakId = sakId,
            id = UUID.randomUUID(),
            søknadInnhold = søknadInnhold,
            opprettet = fixedTidspunkt,
        ).also { søknadRepo.opprettSøknad(it) }
    }

    fun nyLukketSøknadForEksisterendeSak(
        sakId: UUID,
        søknadInnhold: SøknadInnhold = SøknadInnholdTestdataBuilder.build(),
    ): Søknad.Journalført.MedOppgave.Lukket {
        return nySøknadForEksisterendeSak(sakId = sakId, søknadInnhold = søknadInnhold)
            .journalfør(journalpostId).also {
                søknadRepo.oppdaterjournalpostId(it)
            }
            .medOppgave(oppgaveId).also {
                søknadRepo.oppdaterOppgaveId(it)
            }
            .let {
                it.lukk(
                    lukketAv = NavIdentBruker.Saksbehandler("saksbehandler"),
                    type = Søknad.Journalført.MedOppgave.Lukket.LukketType.TRUKKET,
                    lukketTidspunkt = fixedTidspunkt,
                ).also { lukketSøknad ->
                    søknadRepo.lukkSøknad(lukketSøknad)
                }
            }
    }

    fun nyLukketSøknadsbehandlingOgSøknadForEksisterendeSak(sak: Sak): LukketSøknadsbehandling {
        return nySøknadsbehandling(
            sak = sak,
            søknad = nyLukketSøknadForEksisterendeSak(sakId = sak.id),
        ).let {
            it.lukkSøknadsbehandling().orNull()!!.also {
                søknadsbehandlingRepo.lagre(it)
            }
        }
    }

    fun nySakMedJournalførtSøknad(
        fnr: Fnr = Fnr.generer(),
        journalpostId: JournalpostId = no.nav.su.se.bakover.database.journalpostId,
    ): Sak {
        val nySak: NySak = nySakMedNySøknad(fnr)
        nySak.søknad.journalfør(journalpostId).also { journalførtSøknad ->
            søknadRepo.oppdaterjournalpostId(journalførtSøknad)
        }
        return sakRepo.hentSak(nySak.id)
            ?: throw java.lang.IllegalStateException("Fant ikke sak rett etter vi opprettet den.")
    }

    fun journalførtSøknadForEksisterendeSak(
        sakId: UUID,
        journalpostId: JournalpostId = no.nav.su.se.bakover.database.journalpostId,
    ): Søknad.Journalført.UtenOppgave {
        return nySøknadForEksisterendeSak(sakId).journalfør(journalpostId).also {
            søknadRepo.oppdaterjournalpostId(it)
        }
    }

    fun nySakMedJournalførtSøknadOgOppgave(
        fnr: Fnr = Fnr.generer(),
        oppgaveId: OppgaveId = no.nav.su.se.bakover.database.oppgaveId,
        journalpostId: JournalpostId = no.nav.su.se.bakover.database.journalpostId,
    ): Sak {
        val sak: Sak = nySakMedJournalførtSøknad(fnr, journalpostId)
        sak.journalførtSøknad().medOppgave(oppgaveId).also {
            søknadRepo.oppdaterOppgaveId(it)
        }
        return sakRepo.hentSak(sak.id)
            ?: throw java.lang.IllegalStateException("Fant ikke sak rett etter vi opprettet den.")
    }

    fun journalførtSøknadMedOppgaveForEksisterendeSak(
        sakId: UUID,
        journalpostId: JournalpostId = no.nav.su.se.bakover.database.journalpostId,
        oppgaveId: OppgaveId = no.nav.su.se.bakover.database.oppgaveId,
    ): Søknad.Journalført.MedOppgave.IkkeLukket {
        return journalførtSøknadForEksisterendeSak(sakId, journalpostId).medOppgave(oppgaveId).also {
            søknadRepo.oppdaterOppgaveId(it)
        }
    }

    fun nyOversendtUtbetalingMedKvittering(
        periode: Periode = stønadsperiode.periode,
        avstemmingsnøkkel: Avstemmingsnøkkel = no.nav.su.se.bakover.database.avstemmingsnøkkel,
        utbetalingslinjer: NonEmptyList<Utbetalingslinje> = nonEmptyListOf(utbetalingslinje(periode)),
    ): Pair<Søknadsbehandling.Iverksatt.Innvilget, Utbetaling.OversendtUtbetaling.MedKvittering> {
        val utenKvittering = nyIverksattInnvilget(
            periode = periode,
            avstemmingsnøkkel = avstemmingsnøkkel,
            utbetalingslinjer = utbetalingslinjer,
        )
        return utenKvittering.first to utenKvittering.second.toKvittertUtbetaling(kvitteringOk).also {
            utbetalingRepo.oppdaterMedKvittering(it)
        }
    }

    fun oppdaterHendelseslogg(hendelseslogg: Hendelseslogg) = hendelsesloggRepo.oppdaterHendelseslogg(hendelseslogg)

    fun vedtakForSøknadsbehandlingOgUtbetalingId(
        søknadsbehandling: Søknadsbehandling.Iverksatt.Innvilget,
        utbetalingId: UUID30,
    ) =
        Vedtak.fromSøknadsbehandling(søknadsbehandling, utbetalingId, fixedClock).also {
            vedtakRepo.lagre(it)
        }

    fun vedtakMedInnvilgetSøknadsbehandling(
        periode: Periode = stønadsperiode.periode,
    ): Pair<Vedtak.EndringIYtelse.InnvilgetSøknadsbehandling, Utbetaling> {
        val (søknadsbehandling, utbetaling) = nyOversendtUtbetalingMedKvittering(periode)
        return Pair(
            Vedtak.fromSøknadsbehandling(søknadsbehandling, utbetaling.id, fixedClock).also {
                vedtakRepo.lagre(it)
            },
            utbetaling,
        )
    }

    fun vedtakForRevurdering(revurdering: RevurderingTilAttestering.Innvilget): Pair<Vedtak.EndringIYtelse, Utbetaling> {
        val utbetalingId = UUID30.randomUUID()

        val utbetaling = oversendtUtbetalingUtenKvittering(
            revurdering = revurdering,
            avstemmingsnøkkel = avstemmingsnøkkel,
            utbetalingslinjer = nonEmptyListOf(utbetalingslinje()),
        ).copy(id = utbetalingId)

        utbetalingRepo.opprettUtbetaling(utbetaling)

        return Pair(
            Vedtak.from(
                revurdering = revurdering.tilIverksatt(
                    attestant = attestant,
                ) { utbetaling.id.right() }.orNull()!!,
                utbetalingId = utbetaling.id,
                fixedClock,
            ).also {
                vedtakRepo.lagre(it)
            },
            utbetaling,
        )
    }

    fun nyRevurdering(
        innvilget: Vedtak.EndringIYtelse,
        periode: Periode,
        epsFnr: Fnr? = null,
        grunnlagsdata: Grunnlagsdata = innvilgetGrunnlagsdataRevurdering(periode, epsFnr),
        vilkårsvurderinger: Vilkårsvurderinger.Revurdering = innvilgetVilkårsvurderingerSøknadsbehandling(
            periode = periode,
        ).tilVilkårsvurderingerRevurdering(),
    ): OpprettetRevurdering {
        return OpprettetRevurdering(
            id = UUID.randomUUID(),
            periode = periode,
            tilRevurdering = innvilget,
            opprettet = fixedTidspunkt,
            saksbehandler = saksbehandler,
            oppgaveId = OppgaveId("oppgaveid"),
            fritekstTilBrev = "",
            revurderingsårsak = Revurderingsårsak(
                Revurderingsårsak.Årsak.MELDING_FRA_BRUKER,
                Revurderingsårsak.Begrunnelse.create("Ny informasjon"),
            ),
            forhåndsvarsel = null,
            grunnlagsdata = grunnlagsdata,
            vilkårsvurderinger = vilkårsvurderinger,
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
            attesteringer = Attesteringshistorikk.empty(),
        ).also {
            lagreVilkårOgGrunnlag(
                behandlingId = it.id,
                vilkårsvurderinger = vilkårsvurderinger,
                grunnlagsdata = grunnlagsdata,
            )
            revurderingRepo.lagre(it)
        }
    }

    private fun beregnetRevurdering(): BeregnetRevurdering.Innvilget {
        val vedtak = vedtakMedInnvilgetSøknadsbehandling()
        return nyRevurdering(
            innvilget = vedtak.first,
            periode = stønadsperiode.periode,
            epsFnr = null,
        ).beregn(
            eksisterendeUtbetalinger = listOf(vedtak.second),
            clock = clock,
        ).getOrFail().let {
            revurderingRepo.lagre(it)
            it as BeregnetRevurdering.Innvilget
        }
    }

    private fun simulertRevurdering(): SimulertRevurdering {
        return beregnetRevurdering().toSimulert(simulering(Fnr.generer())).also {
            revurderingRepo.lagre(it)
        }
    }

    fun tilAttesteringRevurdering(): RevurderingTilAttestering {
        val simulert = simulertRevurdering()
        return when (simulert) {
            is SimulertRevurdering.Innvilget -> simulert.tilAttestering(
                attesteringsoppgaveId = oppgaveId,
                saksbehandler = saksbehandler,
                fritekstTilBrev = "",
                forhåndsvarsel = Forhåndsvarsel.IngenForhåndsvarsel,
            )
            is SimulertRevurdering.Opphørt -> simulert.tilAttestering(
                attesteringsoppgaveId = oppgaveId,
                saksbehandler = saksbehandler,
                fritekstTilBrev = "",
                forhåndsvarsel = Forhåndsvarsel.IngenForhåndsvarsel,
            ).getOrHandle {
                throw java.lang.IllegalStateException("Her skal vi ha en revurdering som er til attestering")
            }
        }.also {
            revurderingRepo.lagre(it)
        }
    }

    fun tilIverksattRevurdering(): IverksattRevurdering {
        return when (val tilAttestering = tilAttesteringRevurdering()) {
            is RevurderingTilAttestering.IngenEndring -> tilAttestering.tilIverksatt(
                attestant,
            ).getOrHandle {
                throw IllegalStateException("Her skulle vi ha hatt en iverksatt revurdering")
            }
            is RevurderingTilAttestering.Innvilget -> tilAttestering.tilIverksatt(
                attestant = attestant,
            ) {
                UUID30.randomUUID().right()
            }.getOrHandle {
                throw IllegalStateException("Her skulle vi ha hatt en iverksatt revurdering")
            }
            is RevurderingTilAttestering.Opphørt -> tilAttestering.tilIverksatt(
                attestant,
            ) {
                _: UUID, _: NavIdentBruker.Attestant, _: LocalDate, _: Simulering ->
                UUID30.randomUUID().right()
            }.getOrHandle {
                throw IllegalStateException("Her skulle vi ha hatt en iverksatt revurdering")
            }
        }.also {
            revurderingRepo.lagre(it)
        }
    }

    fun underkjentRevurdering(): UnderkjentRevurdering {
        return tilAttesteringRevurdering().underkjenn(underkjentAttestering, OppgaveId("oppgaveid")).also {
            revurderingRepo.lagre(it)
        }
    }

    /* Kaller lagreNySøknadsbehandling (insert) */
    fun nySøknadsbehandling(
        sak: Sak = nySakMedJournalførtSøknadOgOppgave(),
        søknad: Søknad.Journalført.MedOppgave = sak.journalførtSøknadMedOppgave(),
        behandlingsinformasjon: Behandlingsinformasjon = tomBehandlingsinformasjon,
        stønadsperiode: Stønadsperiode? = no.nav.su.se.bakover.database.stønadsperiode,
    ): Søknadsbehandling.Vilkårsvurdert.Uavklart {

        return NySøknadsbehandling(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            sakId = sak.id,
            søknad = søknad,
            oppgaveId = søknad.oppgaveId,
            behandlingsinformasjon = behandlingsinformasjon,
            fnr = sak.fnr,
        ).let {
            søknadsbehandlingRepo.lagreNySøknadsbehandling(it)
            (søknadsbehandlingRepo.hent(it.id)!! as Søknadsbehandling.Vilkårsvurdert.Uavklart).copy(
                stønadsperiode = stønadsperiode,
            ).also {
                søknadsbehandlingRepo.lagre(it)
            }
        }
    }

    private fun innvilgetGrunnlagsdataSøknadsbehandling(
        periode: Periode = stønadsperiode.periode,
        epsFnr: Fnr? = null,
    ) = Grunnlagsdata.create(
        bosituasjon = listOf(
            when (epsFnr == null) {
                true -> bosituasjongrunnlagEnslig(id = UUID.randomUUID(), periode = periode)
                false -> bosituasjongrunnlagEpsUførFlyktning(id = UUID.randomUUID(), periode = periode, epsFnr = epsFnr)
            },
        ),
        // søknadsbehandling benytter enn så lenge fradrag rett fra beregning
        fradragsgrunnlag = emptyList(),
    )

    private fun innvilgetGrunnlagsdataRevurdering(
        periode: Periode = stønadsperiode.periode,
        epsFnr: Fnr? = null,
    ) =
        innvilgetGrunnlagsdataSøknadsbehandling(periode, epsFnr)

    internal fun nyInnvilgetVilkårsvurdering(
        periode: Periode = stønadsperiode.periode,
        behandlingsinformasjon: Behandlingsinformasjon = behandlingsinformasjonMedAlleVilkårOppfylt,
        vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling = innvilgetVilkårsvurderingerSøknadsbehandling(periode = periode),
        grunnlagsdata: Grunnlagsdata = innvilgetGrunnlagsdataSøknadsbehandling(periode = periode),
    ): Søknadsbehandling.Vilkårsvurdert.Innvilget {
        return nySøknadsbehandling(
            stønadsperiode = Stønadsperiode.create(periode),
            behandlingsinformasjon = behandlingsinformasjon,
        ).copy(
            vilkårsvurderinger = vilkårsvurderinger,
            grunnlagsdata = grunnlagsdata,
        ).tilVilkårsvurdert(
            behandlingsinformasjon,
            clock = fixedClock,
        ).also {
            lagreVilkårOgGrunnlag(
                behandlingId = it.id,
                vilkårsvurderinger = vilkårsvurderinger,
                grunnlagsdata = grunnlagsdata,
            )
            søknadsbehandlingRepo.lagre(it)
        } as Søknadsbehandling.Vilkårsvurdert.Innvilget
    }

    fun innvilgetVilkårsvurderingerSøknadsbehandling(
        periode: Periode = stønadsperiode.periode,
        uføre: Vilkår.Uførhet = innvilgetUførevilkårForventetInntekt0(
            id = UUID.randomUUID(),
            periode = periode,
            uføregrunnlag = uføregrunnlagForventetInntekt0(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = periode,
            ),
        ),
        bosituasjon: Grunnlag.Bosituasjon.Fullstendig = bosituasjongrunnlagEnslig(
            id = UUID.randomUUID(),
            periode = periode,
        ),
        behandlingsinformasjon: Behandlingsinformasjon = behandlingsinformasjonAlleVilkårInnvilget,
        utenlandsopphold: UtenlandsoppholdVilkår = utlandsoppholdInnvilget(
            id = UUID.randomUUID(),
            periode = periode,
        ),
    ): Vilkårsvurderinger.Søknadsbehandling {
        return vilkårsvurderingerInnvilget(
            periode = periode,
            uføre = uføre,
            bosituasjon = bosituasjon,
            behandlingsinformasjon = behandlingsinformasjon,
            utenlandsopphold = utenlandsopphold,
        )
    }

    internal fun lagreVilkårOgGrunnlag(
        behandlingId: UUID,
        vilkårsvurderinger: Vilkårsvurderinger,
        grunnlagsdata: Grunnlagsdata,
    ) {
        bosituasjongrunnlagPostgresRepo.lagreBosituasjongrunnlag(behandlingId, grunnlagsdata.bosituasjon)
        fradragsgrunnlagPostgresRepo.lagreFradragsgrunnlag(behandlingId, grunnlagsdata.fradragsgrunnlag)
        when (vilkårsvurderinger) {
            is Vilkårsvurderinger.Revurdering -> {
                uføreVilkårsvurderingRepo.lagre(behandlingId, vilkårsvurderinger.uføre)
                formueVilkårsvurderingPostgresRepo.lagre(behandlingId, vilkårsvurderinger.formue)
            }
            is Vilkårsvurderinger.Søknadsbehandling -> {
                formueVilkårsvurderingPostgresRepo.lagre(behandlingId, vilkårsvurderinger.formue)
            }
        }
    }

    internal fun nyAvslåttVilkårsvurdering(
        grunnlagsdata: Grunnlagsdata = Grunnlagsdata.create(
            bosituasjon = listOf(bosituasjongrunnlagEnslig(periode = stønadsperiode.periode)),
        ),
        vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling = Vilkårsvurderinger.Søknadsbehandling(
            uføre = innvilgetUførevilkår(periode = stønadsperiode.periode),
        ),
    ): Søknadsbehandling.Vilkårsvurdert.Avslag {
        return nySøknadsbehandling()
            .copy(
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
            ).tilVilkårsvurdert(
                behandlingsinformasjon = behandlingsinformasjonMedAvslag,
                clock = fixedClock,
            )
            .also {
                søknadsbehandlingRepo.lagre(it)
                lagreVilkårOgGrunnlag(it.id, vilkårsvurderinger, grunnlagsdata)
            } as Søknadsbehandling.Vilkårsvurdert.Avslag
    }

    private fun nyInnvilgetBeregning(
        periode: Periode = stønadsperiode.periode,
        behandlingsinformasjon: Behandlingsinformasjon = behandlingsinformasjonMedAlleVilkårOppfylt,
        vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling = innvilgetVilkårsvurderingerSøknadsbehandling(periode),
        grunnlagsdata: Grunnlagsdata = innvilgetGrunnlagsdataSøknadsbehandling(periode),
    ): Søknadsbehandling.Beregnet.Innvilget {
        return nyInnvilgetVilkårsvurdering(
            periode,
            behandlingsinformasjon,
            vilkårsvurderinger,
            grunnlagsdata,
        ).tilBeregnet(
            innvilgetBeregning(),
        ).also {
            søknadsbehandlingRepo.lagre(it)
        } as Søknadsbehandling.Beregnet.Innvilget
    }

    internal fun nyAvslåttBeregning(): Søknadsbehandling.Beregnet.Avslag {
        return nyInnvilgetVilkårsvurdering().tilBeregnet(
            avslåttBeregning,
        ).also {
            søknadsbehandlingRepo.lagre(it)
        } as Søknadsbehandling.Beregnet.Avslag
    }

    private fun nySimulering(
        periode: Periode = stønadsperiode.periode,
        behandlingsinformasjon: Behandlingsinformasjon = behandlingsinformasjonMedAlleVilkårOppfylt,
        vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling = innvilgetVilkårsvurderingerSøknadsbehandling(periode),
        grunnlagsdata: Grunnlagsdata = innvilgetGrunnlagsdataSøknadsbehandling(periode),
    ): Søknadsbehandling.Simulert {
        return nyInnvilgetBeregning(
            periode,
            behandlingsinformasjon,
            vilkårsvurderinger,
            grunnlagsdata,
        ).let {
            it.tilSimulert(simulering(it.fnr))
        }.also {
            søknadsbehandlingRepo.lagre(it)
        }
    }

    internal fun nyTilInnvilgetAttestering(
        periode: Periode = stønadsperiode.periode,
        behandlingsinformasjon: Behandlingsinformasjon = behandlingsinformasjonMedAlleVilkårOppfylt,
        vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling = innvilgetVilkårsvurderingerSøknadsbehandling(periode),
        grunnlagsdata: Grunnlagsdata = innvilgetGrunnlagsdataSøknadsbehandling(periode),
        fritekstTilBrev: String = "",
    ): Søknadsbehandling.TilAttestering.Innvilget {
        return nySimulering(
            periode,
            behandlingsinformasjon,
            vilkårsvurderinger,
            grunnlagsdata,
        ).tilAttestering(
            saksbehandler,
            fritekstTilBrev,
        ).also {
            søknadsbehandlingRepo.lagre(it)
        }
    }

    internal fun tilAvslåttAttesteringMedBeregning(fritekstTilBrev: String = ""): Søknadsbehandling.TilAttestering.Avslag.MedBeregning {
        return nyAvslåttBeregning().tilAttestering(
            saksbehandler,
            fritekstTilBrev,
        ).also {
            søknadsbehandlingRepo.lagre(it)
        }
    }

    internal fun nyTilAvslåttAttesteringUtenBeregning(
        fritekstTilBrev: String = "",
    ): Søknadsbehandling.TilAttestering.Avslag.UtenBeregning {
        return nyAvslåttVilkårsvurdering().tilAttestering(
            saksbehandler,
            fritekstTilBrev = fritekstTilBrev,
        ).also {
            søknadsbehandlingRepo.lagre(it)
        }
    }

    internal fun nyInnvilgetUnderkjenning(): Søknadsbehandling.Underkjent.Innvilget {
        return nyTilInnvilgetAttestering().tilUnderkjent(
            underkjentAttestering,
        ).also {
            søknadsbehandlingRepo.lagre(it)
        }
    }

    internal fun nyUnderkjenningUtenBeregning(): Søknadsbehandling.Underkjent.Avslag.UtenBeregning {
        return nyTilAvslåttAttesteringUtenBeregning().tilUnderkjent(
            underkjentAttestering,
        ).also {
            søknadsbehandlingRepo.lagre(it)
        }
    }

    internal fun nyUnderkjenningMedBeregning(): Søknadsbehandling.Underkjent.Avslag.MedBeregning {
        return tilAvslåttAttesteringMedBeregning().tilUnderkjent(
            underkjentAttestering,
        ).also {
            søknadsbehandlingRepo.lagre(it)
        }
    }

    internal fun nyIverksattInnvilget(
        periode: Periode = stønadsperiode.periode,
        behandlingsinformasjon: Behandlingsinformasjon = behandlingsinformasjonMedAlleVilkårOppfylt,
        vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling = innvilgetVilkårsvurderingerSøknadsbehandling(periode),
        epsFnr: Fnr? = null,
        grunnlagsdata: Grunnlagsdata = innvilgetGrunnlagsdataSøknadsbehandling(periode, epsFnr),
        avstemmingsnøkkel: Avstemmingsnøkkel = no.nav.su.se.bakover.database.avstemmingsnøkkel,
        utbetalingslinjer: NonEmptyList<Utbetalingslinje> = nonEmptyListOf(utbetalingslinje(periode)),
    ): Pair<Søknadsbehandling.Iverksatt.Innvilget, Utbetaling.OversendtUtbetaling.UtenKvittering> {
        val utbetalingId = UUID30.randomUUID()
        val innvilget =
            nyTilInnvilgetAttestering(
                periode = periode,
                behandlingsinformasjon,
                vilkårsvurderinger,
                grunnlagsdata,
            ).tilIverksatt(
                iverksattAttestering,
            )
        val utbetaling = oversendtUtbetalingUtenKvittering(
            søknadsbehandling = innvilget,
            avstemmingsnøkkel = avstemmingsnøkkel,
            utbetalingslinjer = utbetalingslinjer,
        ).copy(id = utbetalingId)
        utbetalingRepo.opprettUtbetaling(utbetaling)
        søknadsbehandlingRepo.lagre(innvilget)
        vedtakRepo.lagre(Vedtak.fromSøknadsbehandling(innvilget, utbetalingId, fixedClock))
        return innvilget to utbetaling
    }

    internal fun nyUtbetalingUtenKvittering(
        revurderingTilAttestering: RevurderingTilAttestering,
    ): Utbetaling.OversendtUtbetaling.UtenKvittering {
        val utbetaling = oversendtUtbetalingUtenKvittering(
            revurdering = revurderingTilAttestering,
            avstemmingsnøkkel = avstemmingsnøkkel,
            utbetalingslinjer = nonEmptyListOf(utbetalingslinje()),
        ).copy(id = UUID30.randomUUID())

        utbetalingRepo.opprettUtbetaling(utbetaling)
        return utbetaling
    }

    internal fun nyIverksattAvslagUtenBeregning(
        fritekstTilBrev: String = "",
    ): Søknadsbehandling.Iverksatt.Avslag.UtenBeregning {
        return nyTilAvslåttAttesteringUtenBeregning(
            fritekstTilBrev = fritekstTilBrev,
        ).tilIverksatt(
            iverksattAttestering,
        ).also {
            søknadsbehandlingRepo.lagre(it)
        }
    }

    internal fun nyIverksattAvslagMedBeregning(): Søknadsbehandling.Iverksatt.Avslag.MedBeregning {
        return tilAvslåttAttesteringMedBeregning().tilIverksatt(
            iverksattAttestering,
        ).also {
            søknadsbehandlingRepo.lagre(it)
        }
    }

    companion object {
        /** Kaster hvis size != 1 */
        fun Sak.journalførtSøknadMedOppgave(): Søknad.Journalført.MedOppgave.IkkeLukket {
            kastDersomSøknadErUlikEn()
            return søknader.first() as Søknad.Journalført.MedOppgave.IkkeLukket
        }

        /** Kaster hvis size != 1 */
        fun Sak.journalførtSøknad(): Søknad.Journalført.UtenOppgave {
            kastDersomSøknadErUlikEn()
            return søknader.first() as Søknad.Journalført.UtenOppgave
        }

        /** Kaster hvis size != 1 */
        fun Sak.søknadNy(): Søknad.Ny {
            kastDersomSøknadErUlikEn()
            return søknader.first() as Søknad.Ny
        }

        private fun Sak.kastDersomSøknadErUlikEn() {
            if (søknader.size != 1) throw IllegalStateException("Var ferre/fler enn 1 søknad. Testen bør spesifisere dersom fler. Antall: ${søknader.size}")
        }
    }
}
