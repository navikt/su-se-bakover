package no.nav.su.se.bakover.database

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.beregning.PersistertBeregning
import no.nav.su.se.bakover.database.beregning.PersistertMånedsberegning
import no.nav.su.se.bakover.database.beregning.TestBeregning
import no.nav.su.se.bakover.database.beregning.toSnapshot
import no.nav.su.se.bakover.database.grunnlag.FradragsgrunnlagPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.GrunnlagPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.UføregrunnlagPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.VilkårsvurderingPostgresRepo
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
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.behandling.withVilkårAvslått
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.hendelseslogg.Hendelseslogg
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.søknadsbehandling.NySøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
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
    Attestering.Underkjent(attestant, Attestering.Underkjent.Grunn.ANDRE_FORHOLD, "kommentar")
internal val iverksattAttestering = Attestering.Iverksatt(attestant)
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
    dataSource: DataSource = EmbeddedDatabase.instance(),
    private val clock: Clock = fixedClock,
) {
    private val utbetalingRepo = UtbetalingPostgresRepo(dataSource)
    private val hendelsesloggRepo = HendelsesloggPostgresRepo(dataSource)
    private val søknadRepo = SøknadPostgresRepo(dataSource)
    val uføregrunnlagPostgresRepo = UføregrunnlagPostgresRepo(dataSource)
    val inntektgrunnlagPostgresRepo = FradragsgrunnlagPostgresRepo(dataSource)
    val grunnlagRepo = GrunnlagPostgresRepo(uføregrunnlagPostgresRepo, inntektgrunnlagPostgresRepo)
    val vilkårsvurderingRepo = VilkårsvurderingPostgresRepo(dataSource, uføregrunnlagPostgresRepo)
    private val søknadsbehandlingRepo = SøknadsbehandlingPostgresRepo(dataSource, grunnlagRepo, vilkårsvurderingRepo)
    val revurderingRepo = RevurderingPostgresRepo(dataSource, søknadsbehandlingRepo, grunnlagRepo, vilkårsvurderingRepo)
    val vedtakRepo = VedtakPosgresRepo(dataSource, søknadsbehandlingRepo, revurderingRepo)
    private val sakRepo = SakPostgresRepo(dataSource, søknadsbehandlingRepo, revurderingRepo, vedtakRepo)

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

    fun vedtakForSøknadsbehandlingOgUtbetalingId(søknadsbehandling: Søknadsbehandling.Iverksatt.Innvilget, utbetalingId: UUID30) =
        Vedtak.fromSøknadsbehandling(søknadsbehandling, utbetalingId).also {
            vedtakRepo.lagre(it)
        }

    fun vedtakMedInnvilgetSøknadsbehandling(): Pair<Vedtak.EndringIYtelse, Utbetaling> {
        val (søknadsbehandling, utbetaling) = nyOversendtUtbetalingMedKvittering()
        return Pair(
            Vedtak.fromSøknadsbehandling(søknadsbehandling, utbetaling.id).also {
                vedtakRepo.lagre(it)
            },
            utbetaling,
        )
    }

    fun nyRevurdering(innvilget: Vedtak.EndringIYtelse, periode: Periode) =
        OpprettetRevurdering(
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
            behandlingsinformasjon = innvilget.behandlingsinformasjon,
            grunnlagsdata = Grunnlagsdata.EMPTY,
            vilkårsvurderinger = Vilkårsvurderinger.EMPTY,
        ).also {
            revurderingRepo.lagre(it)
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

    internal fun nyInnvilgetVilkårsvurdering(
        behandlingsinformasjon: Behandlingsinformasjon = behandlingsinformasjonMedAlleVilkårOppfylt,
    ): Søknadsbehandling.Vilkårsvurdert.Innvilget {
        return nySøknadsbehandling(behandlingsinformasjon = behandlingsinformasjon).tilVilkårsvurdert(
            behandlingsinformasjon,
        ).also {
            søknadsbehandlingRepo.lagre(it)
        } as Søknadsbehandling.Vilkårsvurdert.Innvilget
    }

    internal fun nyAvslåttVilkårsvurdering(): Søknadsbehandling.Vilkårsvurdert.Avslag {
        return nySøknadsbehandling().tilVilkårsvurdert(
            behandlingsinformasjonMedAvslag,
        ).also {
            søknadsbehandlingRepo.lagre(it)
        } as Søknadsbehandling.Vilkårsvurdert.Avslag
    }

    internal fun nyInnvilgetBeregning(
        behandlingsinformasjon: Behandlingsinformasjon = behandlingsinformasjonMedAlleVilkårOppfylt,
    ): Søknadsbehandling.Beregnet.Innvilget {
        return nyInnvilgetVilkårsvurdering(behandlingsinformasjon).tilBeregnet(
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

    internal fun nySimulering(
        behandlingsinformasjon: Behandlingsinformasjon = behandlingsinformasjonMedAlleVilkårOppfylt,
    ): Søknadsbehandling.Simulert {
        return nyInnvilgetBeregning(behandlingsinformasjon).let {
            it.tilSimulert(simulering(it.fnr))
        }.also {
            søknadsbehandlingRepo.lagre(it)
        }
    }

    internal fun nyTilInnvilgetAttestering(
        behandlingsinformasjon: Behandlingsinformasjon = behandlingsinformasjonMedAlleVilkårOppfylt,
        fritekstTilBrev: String = "",
    ): Søknadsbehandling.TilAttestering.Innvilget {
        return nySimulering(behandlingsinformasjon).tilAttestering(
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
        avstemmingsnøkkel: Avstemmingsnøkkel = no.nav.su.se.bakover.database.avstemmingsnøkkel,
        utbetalingslinjer: List<Utbetalingslinje> = listOf(utbetalingslinje()),
    ): Pair<Søknadsbehandling.Iverksatt.Innvilget, Utbetaling.OversendtUtbetaling.UtenKvittering> {
        val utbetalingId = UUID30.randomUUID()
        val innvilget = nyTilInnvilgetAttestering(behandlingsinformasjon).tilIverksatt(
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
