package no.nav.su.se.bakover.database

import arrow.core.nonEmptyListOf
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.beregning.PersistertBeregning
import no.nav.su.se.bakover.database.beregning.PersistertMånedsberegning
import no.nav.su.se.bakover.database.beregning.TestBeregning
import no.nav.su.se.bakover.database.beregning.toSnapshot
import no.nav.su.se.bakover.database.grunnlag.BosituasjongrunnlagPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.FormueVilkårsvurderingPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.FormuegrunnlagPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.FradragsgrunnlagPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.GrunnlagPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.UføreVilkårsvurderingPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.UføregrunnlagPostgresRepo
import no.nav.su.se.bakover.database.hendelseslogg.HendelsesloggPostgresRepo
import no.nav.su.se.bakover.database.revurdering.RevurderingPostgresRepo
import no.nav.su.se.bakover.database.sak.SakPostgresRepo
import no.nav.su.se.bakover.database.søknad.SøknadPostgresRepo
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingPostgresRepo
import no.nav.su.se.bakover.database.utbetaling.UtbetalingPostgresRepo
import no.nav.su.se.bakover.database.vedtak.VedtakPosgresRepo
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.NySak
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.SakFactory
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.behandling.withVilkårAvslått
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
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
import no.nav.su.se.bakover.domain.revurdering.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.søknadsbehandling.NySøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.domain.vilkår.Vurderingsperiode
import no.nav.su.se.bakover.test.create
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import javax.sql.DataSource

internal val fixedClock: Clock =
    Clock.fixed(1.januar(2021).atTime(1, 2, 3, 456789000).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
internal val fixedTidspunkt: Tidspunkt = Tidspunkt.now(fixedClock)
internal val fixedLocalDate: LocalDate = fixedTidspunkt.toLocalDate(ZoneOffset.UTC)
internal val stønadsperiode = Stønadsperiode.create(Periode.create(1.januar(2021), 31.januar(2021)))
internal val tomBehandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon()
internal val behandlingsinformasjonMedAlleVilkårOppfylt =
    Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt()
internal val behandlingsinformasjonMedAvslag =
    Behandlingsinformasjon.lagTomBehandlingsinformasjon().withVilkårAvslått()

internal val oppgaveId = OppgaveId("oppgaveId")
internal val journalpostId = JournalpostId("journalpostId")
internal fun beregning(periode: Periode = stønadsperiode.periode) =
    TestBeregning.toSnapshot().copy(periode = periode)

internal val persistertMånedsberegning = PersistertMånedsberegning(
    sumYtelse = 0,
    sumFradrag = 0.0,
    benyttetGrunnbeløp = 0,
    sats = Sats.ORDINÆR,
    satsbeløp = 0.0,
    fradrag = listOf(),
    periode = Periode.create(1.januar(2020), 31.desember(2020)),
    fribeløpForEps = 0.0,
)
internal val avslåttBeregning: PersistertBeregning = beregning().copy(
    månedsberegninger = listOf(
        persistertMånedsberegning,
    ),
)

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
        opprettet = fixedTidspunkt
    )
internal val iverksattAttestering = Attestering.Iverksatt(attestant, fixedTidspunkt)
internal val iverksattJournalpostId = JournalpostId("iverksattJournalpostId")
internal val iverksattBrevbestillingId = BrevbestillingId("iverksattBrevbestillingId")
internal val avstemmingsnøkkel = Avstemmingsnøkkel()
internal fun utbetalingslinje() = Utbetalingslinje.Ny(
    fraOgMed = 1.januar(2020),
    tilOgMed = 31.desember(2020),
    forrigeUtbetalingslinjeId = null,
    beløp = 25000,
)

internal fun oversendtUtbetalingUtenKvittering(
    søknadsbehandling: Søknadsbehandling.Iverksatt.Innvilget,
    avstemmingsnøkkel: Avstemmingsnøkkel = no.nav.su.se.bakover.database.avstemmingsnøkkel,
    utbetalingslinjer: List<Utbetalingslinje> = listOf(utbetalingslinje()),
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
    utbetalingslinjer: List<Utbetalingslinje> = listOf(utbetalingslinje()),
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
    utbetalingslinjer: List<Utbetalingslinje> = listOf(utbetalingslinje()),
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

internal class TestDataHelper(
    internal val dataSource: DataSource = EmbeddedDatabase.instance(),
    private val clock: Clock = fixedClock,
) {
    internal val utbetalingRepo = UtbetalingPostgresRepo(dataSource)
    private val hendelsesloggRepo = HendelsesloggPostgresRepo(dataSource)
    internal val søknadRepo = SøknadPostgresRepo(dataSource)
    internal val uføregrunnlagPostgresRepo = UføregrunnlagPostgresRepo()
    private val fradragsgrunnlagPostgresRepo = FradragsgrunnlagPostgresRepo(dataSource)
    private val bosituasjongrunnlagPostgresRepo = BosituasjongrunnlagPostgresRepo(dataSource)
    internal val grunnlagRepo = GrunnlagPostgresRepo(fradragsgrunnlagPostgresRepo, bosituasjongrunnlagPostgresRepo)
    internal val uføreVilkårsvurderingRepo = UføreVilkårsvurderingPostgresRepo(dataSource, uføregrunnlagPostgresRepo)
    private val formuegrunnlagPostgresRepo = FormuegrunnlagPostgresRepo()
    internal val formueVilkårsvurderingPostgresRepo =
        FormueVilkårsvurderingPostgresRepo(dataSource, formuegrunnlagPostgresRepo)
    internal val søknadsbehandlingRepo =
        SøknadsbehandlingPostgresRepo(
            dataSource,
            uføregrunnlagPostgresRepo,
            fradragsgrunnlagPostgresRepo,
            bosituasjongrunnlagPostgresRepo,
            uføreVilkårsvurderingRepo,
        )
    internal val revurderingRepo = RevurderingPostgresRepo(
        dataSource,
        uføregrunnlagPostgresRepo,
        fradragsgrunnlagPostgresRepo,
        bosituasjongrunnlagPostgresRepo,
        uføreVilkårsvurderingRepo,
        formueVilkårsvurderingPostgresRepo,
        søknadsbehandlingRepo,
    )
    internal val vedtakRepo = VedtakPosgresRepo(dataSource, søknadsbehandlingRepo, revurderingRepo)
    internal val sakRepo = SakPostgresRepo(dataSource, søknadsbehandlingRepo, revurderingRepo, vedtakRepo)

    fun nySakMedNySøknad(fnr: Fnr = FnrGenerator.random()): NySak {
        return SakFactory(clock = clock).nySak(fnr, SøknadInnholdTestdataBuilder.build()).also {
            sakRepo.opprettSak(it)
        }
    }

    fun nySøknadForEksisterendeSak(sakId: UUID): Søknad.Ny {
        return Søknad.Ny(
            sakId = sakId,
            id = UUID.randomUUID(),
            søknadInnhold = SøknadInnholdTestdataBuilder.build(),
            opprettet = fixedTidspunkt,
        ).also { søknadRepo.opprettSøknad(it) }
    }

    fun nyLukketSøknadForEksisterendeSak(sakId: UUID): Søknad.Lukket {
        return Søknad.Ny(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            sakId = sakId,
            søknadInnhold = SøknadInnholdTestdataBuilder.build(),
        ).let { nySøknad ->
            søknadRepo.opprettSøknad(nySøknad)
            nySøknad.lukk(
                lukketAv = NavIdentBruker.Saksbehandler("saksbehandler"),
                type = Søknad.Lukket.LukketType.TRUKKET,
                lukketTidspunkt = fixedTidspunkt,
            ).also { lukketSøknad ->
                søknadRepo.oppdaterSøknad(lukketSøknad)
            }
        }
    }

    fun nySakMedJournalførtSøknad(
        fnr: Fnr = FnrGenerator.random(),
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
        fnr: Fnr = FnrGenerator.random(),
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
    ): Søknad.Journalført.MedOppgave {
        return journalførtSøknadForEksisterendeSak(sakId, journalpostId).medOppgave(oppgaveId).also {
            søknadRepo.oppdaterOppgaveId(it)
        }
    }

    fun nyOversendtUtbetalingMedKvittering(
        avstemmingsnøkkel: Avstemmingsnøkkel = no.nav.su.se.bakover.database.avstemmingsnøkkel,
    ): Pair<Søknadsbehandling.Iverksatt.Innvilget, Utbetaling.OversendtUtbetaling.MedKvittering> {
        val utenKvittering = nyIverksattInnvilget(avstemmingsnøkkel = avstemmingsnøkkel)
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

    fun vedtakMedInnvilgetSøknadsbehandling(): Pair<Vedtak.EndringIYtelse, Utbetaling> {
        val (søknadsbehandling, utbetaling) = nyOversendtUtbetalingMedKvittering()
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
            utbetalingslinjer = listOf(utbetalingslinje()),
        ).copy(id = utbetalingId)

        utbetalingRepo.opprettUtbetaling(utbetaling)

        return Pair(
            Vedtak.from(
                revurdering = revurdering.tilIverksatt(
                    attestant = attestant,
                ) { utbetaling.id.right() }.orNull()!!,
                utbetalingId = utbetaling.id,
                fixedClock
            ).also {
                vedtakRepo.lagre(it)
            },
            utbetaling,
        )
    }

    fun nyRevurdering(innvilget: Vedtak.EndringIYtelse, periode: Periode, epsFnr: Fnr? = null): OpprettetRevurdering {
        val revurderingId = UUID.randomUUID()
        val grunnlagsdata = if (epsFnr == null) Grunnlagsdata.EMPTY else Grunnlagsdata(
            bosituasjon = listOf(
                Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning(
                    id = UUID.randomUUID(),
                    fnr = epsFnr,
                    opprettet = fixedTidspunkt,
                    periode = stønadsperiode.periode,
                    begrunnelse = null,
                ),
            ),
        )

        return OpprettetRevurdering(
            id = revurderingId,
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
            behandlingsinformasjon = innvilget.behandlingsinformasjon,
            grunnlagsdata = grunnlagsdata,
            vilkårsvurderinger = Vilkårsvurderinger.IkkeVurdert,
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
            attesteringer = Attesteringshistorikk.empty()
        ).also {
            revurderingRepo.lagre(it)
            grunnlagRepo.lagreBosituasjongrunnlag(revurderingId, grunnlagsdata.bosituasjon)
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

    private fun innvilgetVilkårsvurderinger() = Vilkårsvurderinger(
        uføre = Vilkår.Uførhet.Vurdert.create(
            vurderingsperioder = nonEmptyListOf(
                Vurderingsperiode.Uføre.create(
                    id = UUID.randomUUID(),
                    opprettet = fixedTidspunkt,
                    resultat = Resultat.Innvilget,
                    grunnlag = Grunnlag.Uføregrunnlag(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        periode = stønadsperiode.periode,
                        uføregrad = Uføregrad.parse(100),
                        forventetInntekt = 0,
                    ),
                    periode = stønadsperiode.periode,
                    begrunnelse = null,
                ),
            ),
        ),
    )

    private fun innvilgetGrunnlagsdata(epsFnr: Fnr? = null) = Grunnlagsdata(
        bosituasjon = listOf(
            if (epsFnr != null) Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning(
                id = UUID.randomUUID(),
                fnr = epsFnr,
                opprettet = fixedTidspunkt,
                periode = stønadsperiode.periode,
                begrunnelse = null,
            ) else
                Grunnlag.Bosituasjon.Fullstendig.Enslig(
                    id = UUID.randomUUID(),
                    opprettet = fixedTidspunkt,
                    periode = stønadsperiode.periode,
                    begrunnelse = null,
                ),
        ),
        fradragsgrunnlag = emptyList(),
    )

    internal fun nyInnvilgetVilkårsvurdering(
        behandlingsinformasjon: Behandlingsinformasjon = behandlingsinformasjonMedAlleVilkårOppfylt,
        vilkårsvurderinger: Vilkårsvurderinger = innvilgetVilkårsvurderinger(),
        grunnlagsdata: Grunnlagsdata = innvilgetGrunnlagsdata(),
    ): Søknadsbehandling.Vilkårsvurdert.Innvilget {
        return nySøknadsbehandling(behandlingsinformasjon = behandlingsinformasjon).copy(
            vilkårsvurderinger = vilkårsvurderinger,
            grunnlagsdata = grunnlagsdata,
        ).tilVilkårsvurdert(
            behandlingsinformasjon,
        ).also {
            lagreVilkårOgGrunnlag(it.id, vilkårsvurderinger, grunnlagsdata)
            søknadsbehandlingRepo.lagre(it)
        } as Søknadsbehandling.Vilkårsvurdert.Innvilget
    }

    internal fun lagreVilkårOgGrunnlag(
        behandlingId: UUID,
        vilkårsvurderinger: Vilkårsvurderinger,
        grunnlagsdata: Grunnlagsdata,
    ) {
        bosituasjongrunnlagPostgresRepo.lagreBosituasjongrunnlag(behandlingId, grunnlagsdata.bosituasjon)
        fradragsgrunnlagPostgresRepo.lagreFradragsgrunnlag(behandlingId, grunnlagsdata.fradragsgrunnlag)
        uføreVilkårsvurderingRepo.lagre(behandlingId, vilkårsvurderinger.uføre)
    }

    internal fun nyAvslåttVilkårsvurdering(): Søknadsbehandling.Vilkårsvurdert.Avslag {
        return nySøknadsbehandling().tilVilkårsvurdert(
            behandlingsinformasjonMedAvslag,
        ).also {
            søknadsbehandlingRepo.lagre(it)
        } as Søknadsbehandling.Vilkårsvurdert.Avslag
    }

    private fun nyInnvilgetBeregning(
        behandlingsinformasjon: Behandlingsinformasjon = behandlingsinformasjonMedAlleVilkårOppfylt,
        vilkårsvurderinger: Vilkårsvurderinger = innvilgetVilkårsvurderinger(),
        grunnlagsdata: Grunnlagsdata = innvilgetGrunnlagsdata(),
    ): Søknadsbehandling.Beregnet.Innvilget {
        return nyInnvilgetVilkårsvurdering(behandlingsinformasjon, vilkårsvurderinger, grunnlagsdata).tilBeregnet(
            beregning(),
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
        behandlingsinformasjon: Behandlingsinformasjon = behandlingsinformasjonMedAlleVilkårOppfylt,
        vilkårsvurderinger: Vilkårsvurderinger = innvilgetVilkårsvurderinger(),
        grunnlagsdata: Grunnlagsdata = innvilgetGrunnlagsdata(),
    ): Søknadsbehandling.Simulert {
        return nyInnvilgetBeregning(behandlingsinformasjon, vilkårsvurderinger, grunnlagsdata).let {
            it.tilSimulert(simulering(it.fnr))
        }.also {
            søknadsbehandlingRepo.lagre(it)
        }
    }

    internal fun nyTilInnvilgetAttestering(
        behandlingsinformasjon: Behandlingsinformasjon = behandlingsinformasjonMedAlleVilkårOppfylt,
        vilkårsvurderinger: Vilkårsvurderinger = innvilgetVilkårsvurderinger(),
        grunnlagsdata: Grunnlagsdata = innvilgetGrunnlagsdata(),
        fritekstTilBrev: String = "",
    ): Søknadsbehandling.TilAttestering.Innvilget {
        return nySimulering(behandlingsinformasjon, vilkårsvurderinger, grunnlagsdata).tilAttestering(
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
        behandlingsinformasjon: Behandlingsinformasjon = behandlingsinformasjonMedAlleVilkårOppfylt,
        vilkårsvurderinger: Vilkårsvurderinger = innvilgetVilkårsvurderinger(),
        epsFnr: Fnr? = null,
        grunnlagsdata: Grunnlagsdata = innvilgetGrunnlagsdata(epsFnr),
        avstemmingsnøkkel: Avstemmingsnøkkel = no.nav.su.se.bakover.database.avstemmingsnøkkel,
        utbetalingslinjer: List<Utbetalingslinje> = listOf(utbetalingslinje()),
    ): Pair<Søknadsbehandling.Iverksatt.Innvilget, Utbetaling.OversendtUtbetaling.UtenKvittering> {
        val utbetalingId = UUID30.randomUUID()
        val innvilget =
            nyTilInnvilgetAttestering(behandlingsinformasjon, vilkårsvurderinger, grunnlagsdata).tilIverksatt(
                iverksattAttestering,
            )
        val utbetaling = oversendtUtbetalingUtenKvittering(
            søknadsbehandling = innvilget,
            avstemmingsnøkkel = avstemmingsnøkkel,
            utbetalingslinjer = utbetalingslinjer,
        ).copy(id = utbetalingId)
        utbetalingRepo.opprettUtbetaling(utbetaling)
        søknadsbehandlingRepo.lagre(innvilget)
        return innvilget to utbetaling
    }

    internal fun nyUtbetalingUtenKvittering(
        revurderingTilAttestering: RevurderingTilAttestering,
    ): Utbetaling.OversendtUtbetaling.UtenKvittering {
        val utbetaling = oversendtUtbetalingUtenKvittering(
            revurdering = revurderingTilAttestering,
            avstemmingsnøkkel = avstemmingsnøkkel,
            utbetalingslinjer = listOf(utbetalingslinje()),
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
        fun Sak.journalførtSøknadMedOppgave(): Søknad.Journalført.MedOppgave {
            kastDersomSøknadErUlikEn()
            return søknader.first() as Søknad.Journalført.MedOppgave
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
