package no.nav.su.se.bakover.test.simulering

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.right
import no.nav.su.se.bakover.client.stubs.oppdrag.SimuleringStub
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.juni
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.common.toNonEmptyList
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingsinstruksjonForEtterbetalinger
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsstrategi
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.KlasseKode
import no.nav.su.se.bakover.domain.oppdrag.simulering.KlasseType
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulerUtbetalingForPeriode
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertDetaljer
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertPeriode
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertUtbetaling
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingRepo
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.Utbetalinger
import no.nav.su.se.bakover.domain.regulering.Regulering
import no.nav.su.se.bakover.domain.revurdering.GjenopptaYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.revurdering.StansAvYtelseRevurdering
import no.nav.su.se.bakover.domain.sak.Saksnummer
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.sak.SimulerUtbetalingFeilet
import no.nav.su.se.bakover.domain.sak.lagNyUtbetaling
import no.nav.su.se.bakover.domain.sak.lagUtbetalingForGjenopptak
import no.nav.su.se.bakover.domain.sak.lagUtbetalingForOpphør
import no.nav.su.se.bakover.domain.sak.lagUtbetalingForStans
import no.nav.su.se.bakover.domain.sak.simulerUtbetaling
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.test.beregning
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.nåtidForSimuleringStub
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.saksnummer
import no.nav.su.se.bakover.test.tikkendeFixedClock
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

private data class UtbetalingRepoMock(
    private val eksisterendeUtbetalinger: Utbetalinger,
) : UtbetalingRepo {
    override fun hentOversendtUtbetalingForUtbetalingId(utbetalingId: UUID30): Utbetaling.OversendtUtbetaling? {
        TODO("Not yet implemented")
    }

    override fun hentOversendtUtbetalingForAvstemmingsnøkkel(avstemmingsnøkkel: Avstemmingsnøkkel): Utbetaling.OversendtUtbetaling? {
        TODO("Not yet implemented")
    }

    override fun hentOversendteUtbetalinger(sakId: UUID): Utbetalinger {
        return eksisterendeUtbetalinger
    }

    override fun oppdaterMedKvittering(utbetaling: Utbetaling.OversendtUtbetaling.MedKvittering) {
        TODO("Not yet implemented")
    }

    override fun opprettUtbetaling(
        utbetaling: Utbetaling.OversendtUtbetaling,
        transactionContext: TransactionContext,
    ) {
        TODO("Not yet implemented")
    }

    override fun hentUkvitterteUtbetalinger(): Utbetalinger {
        TODO("Not yet implemented")
    }

    override fun defaultTransactionContext(): TransactionContext {
        TODO("Not yet implemented")
    }
}

fun simulerNyUtbetaling(
    sak: Sak,
    utbetaling: Utbetaling.UtbetalingForSimulering,
    beregningsperiode: Periode = Periode.create(utbetaling.tidligsteDato(), utbetaling.senesteDato()),
): Either<SimuleringFeilet, Simulering> {
    return simulerUtbetaling(
        sak = sak,
        utbetaling = utbetaling,
        simuleringsperiode = beregningsperiode,
    ).map {
        it.simulering
    }
}

fun simulerUtbetaling(
    sak: Sak,
    utbetaling: Utbetaling.UtbetalingForSimulering,
    simuleringsperiode: Periode = Periode.create(utbetaling.tidligsteDato(), utbetaling.senesteDato()),
    clock: Clock = nåtidForSimuleringStub,
    utbetalingerKjørtTilOgMed: LocalDate = LocalDate.now(clock),
): Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling> {
    return SimuleringStub(
        clock = clock,
        utbetalingerKjørtTilOgMed = utbetalingerKjørtTilOgMed,
        utbetalingRepo = UtbetalingRepoMock(sak.utbetalinger),
    ).simulerUtbetaling(
        SimulerUtbetalingForPeriode(
            utbetaling = utbetaling,
            simuleringsperiode = simuleringsperiode,
        ),
    ).getOrFail().let {
        utbetaling.toSimulertUtbetaling(it).right()
    }
}

/**
 * @param strict hvis satt til true vil kryssjekk for tidslinjer og simuleringer gjennomføres.
 */
fun simulerUtbetaling(
    sak: Sak,
    søknadsbehandling: Søknadsbehandling,
    simuleringsperiode: Periode = søknadsbehandling.periode,
    behandler: NavIdentBruker = saksbehandler,
    clock: Clock = tikkendeFixedClock(),
    strict: Boolean = true,
): Either<SimulerUtbetalingFeilet, Utbetaling.SimulertUtbetaling> {
    return simulerNyUtbetaling(
        sak = sak,
        beregning = søknadsbehandling.beregning
            ?: throw IllegalArgumentException("Kan ikke simulere, søknadsbehandling har ingen beregning"),
        kontrollerMotSimulering = søknadsbehandling.simulering,
        uføregrunnlag = søknadsbehandling.vilkårsvurderinger.uføreVilkår().getOrFail().grunnlag.toNonEmptyList(),
        simuleringsperiode = simuleringsperiode,
        behandler = behandler,
        clock = clock,
        strict = strict,
    )
}

/**
 * @param strict hvis satt til true vil kryssjekk for tidslinjer og simuleringer gjennomføres.
 */
fun simulerUtbetaling(
    sak: Sak,
    revurdering: Revurdering,
    simuleringsperiode: Periode = revurdering.periode,
    behandler: NavIdentBruker = saksbehandler,
    clock: Clock = tikkendeFixedClock(),
    utbetalingerKjørtTilOgMed: LocalDate = LocalDate.now(clock),
    strict: Boolean = true,
): Either<SimulerUtbetalingFeilet, Utbetaling.SimulertUtbetaling> {
    return simulerNyUtbetaling(
        sak = sak,
        beregning = revurdering.beregning
            ?: throw IllegalArgumentException("Kan ikke simulere, revurdering har ingen beregning"),
        kontrollerMotSimulering = revurdering.simulering,
        uføregrunnlag = revurdering.vilkårsvurderinger.uføreVilkår().getOrFail().grunnlag.toNonEmptyList(),
        simuleringsperiode = simuleringsperiode,
        behandler = behandler,
        clock = clock,
        utbetalingerKjørtTilOgMed = utbetalingerKjørtTilOgMed,
        strict = strict,
    )
}

/**
 * @param strict hvis satt til true vil kryssjekk for tidslinjer og simuleringer gjennomføres.
 */
fun simulerUtbetaling(
    sak: Sak,
    regulering: Regulering,
    simuleringsperiode: Periode = regulering.periode,
    behandler: NavIdentBruker = saksbehandler,
    clock: Clock = tikkendeFixedClock(),
    utbetalingerKjørtTilOgMed: LocalDate = LocalDate.now(clock),
    strict: Boolean = true,
): Either<SimulerUtbetalingFeilet, Utbetaling.SimulertUtbetaling> {
    return simulerNyUtbetaling(
        sak = sak,
        beregning = regulering.beregning
            ?: throw IllegalArgumentException("Kan ikke simulere, regulering har ingen beregning"),
        kontrollerMotSimulering = regulering.simulering,
        uføregrunnlag = regulering.vilkårsvurderinger.uføreVilkår().getOrFail().grunnlag.toNonEmptyList(),
        simuleringsperiode = simuleringsperiode,
        behandler = behandler,
        clock = clock,
        utbetalingerKjørtTilOgMed = utbetalingerKjørtTilOgMed,
        strict = strict,
    )
}

fun simulerGjenopptak(
    sak: Sak,
    gjenopptak: GjenopptaYtelseRevurdering?,
    behandler: NavIdentBruker = saksbehandler,
    clock: Clock = tikkendeFixedClock(),
    utbetalingerKjørtTilOgMed: LocalDate = LocalDate.now(clock),
    strict: Boolean = true,
): Either<SimulerUtbetalingFeilet, Utbetaling.SimulertUtbetaling> {
    return sak.lagUtbetalingForGjenopptak(
        saksbehandler = behandler,
        clock = clock,
    ).getOrFail().let { utbetaling ->
        val simuleringsperiode = Periode.create(utbetaling.tidligsteDato(), utbetaling.senesteDato())
        simuler(
            sak = sak,
            utbetaling = utbetaling,
            simuleringsperiode = simuleringsperiode,
            kontrollerMotSimulering = gjenopptak?.simulering,
            clock = clock,
            utbetalingerKjørtTilOgMed = utbetalingerKjørtTilOgMed,
            strict = strict,
        )
    }
}

fun simulerStans(
    sak: Sak,
    stans: StansAvYtelseRevurdering?,
    stansDato: LocalDate,
    behandler: NavIdentBruker = saksbehandler,
    clock: Clock = tikkendeFixedClock(),
    utbetalingerKjørtTilOgMed: LocalDate = LocalDate.now(clock),
    strict: Boolean = true,
): Either<SimulerUtbetalingFeilet, Utbetaling.SimulertUtbetaling> {
    return sak.lagUtbetalingForStans(
        stansdato = stansDato,
        behandler = behandler,
        clock = clock,
    ).getOrFail().let { utbetaling ->
        val simuleringsperiode = Periode.create(utbetaling.tidligsteDato(), utbetaling.senesteDato())
        simuler(
            sak = sak,
            utbetaling = utbetaling,
            simuleringsperiode = simuleringsperiode,
            kontrollerMotSimulering = stans?.simulering,
            clock = clock,
            utbetalingerKjørtTilOgMed = utbetalingerKjørtTilOgMed,
            strict = strict,
        )
    }
}

fun simulerNyUtbetaling(
    sak: Sak,
    beregning: Beregning,
    kontrollerMotSimulering: Simulering?,
    uføregrunnlag: NonEmptyList<Grunnlag.Uføregrunnlag>,
    simuleringsperiode: Periode = beregning.periode,
    behandler: NavIdentBruker = saksbehandler,
    clock: Clock = tikkendeFixedClock(),
    utbetalingerKjørtTilOgMed: LocalDate = LocalDate.now(clock),
    strict: Boolean = true,
): Either<SimulerUtbetalingFeilet, Utbetaling.SimulertUtbetaling> {
    return sak.lagNyUtbetaling(
        saksbehandler = behandler,
        beregning = beregning,
        clock = clock,
        utbetalingsinstruksjonForEtterbetaling = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
        uføregrunnlag = uføregrunnlag,
    ).let { utbetaling ->
        simuler(
            sak = sak,
            utbetaling = utbetaling,
            simuleringsperiode = simuleringsperiode,
            kontrollerMotSimulering = kontrollerMotSimulering,
            clock = clock,
            utbetalingerKjørtTilOgMed = utbetalingerKjørtTilOgMed,
            strict = strict,
        )
    }
}

/**
 * @param strict hvis satt til true vil kryssjekk for tidslinjer og simuleringer gjennomføres.
 */
fun simulerOpphør(
    sak: Sak,
    revurdering: Revurdering,
    simuleringsperiode: Periode = revurdering.periode,
    behandler: NavIdentBruker = saksbehandler,
    clock: Clock = tikkendeFixedClock(),
    utbetalingerKjørtTilOgMed: LocalDate = LocalDate.now(clock),
    strict: Boolean = true,
): Either<SimulerUtbetalingFeilet, Utbetaling.SimulertUtbetaling> {
    return sak.lagUtbetalingForOpphør(
        opphørsperiode = simuleringsperiode,
        behandler = behandler,
        clock = clock,
    ).let { utbetaling ->
        simuler(
            sak = sak,
            utbetaling = utbetaling,
            simuleringsperiode = simuleringsperiode,
            kontrollerMotSimulering = revurdering.simulering,
            clock = clock,
            utbetalingerKjørtTilOgMed = utbetalingerKjørtTilOgMed,
            strict = strict,
        )
    }
}

fun simuler(
    sak: Sak,
    utbetaling: Utbetaling.UtbetalingForSimulering,
    simuleringsperiode: Periode,
    kontrollerMotSimulering: Simulering?,
    clock: Clock,
    strict: Boolean,
    utbetalingerKjørtTilOgMed: LocalDate = LocalDate.now(clock),
): Either<SimulerUtbetalingFeilet, Utbetaling.SimulertUtbetaling> {
    return if (strict) {
        sak.simulerUtbetaling(
            utbetalingForSimulering = utbetaling,
            periode = simuleringsperiode,
            simuler = { utbetalingForSimulering: Utbetaling.UtbetalingForSimulering, periode: Periode ->
                simulerUtbetaling(
                    sak = sak,
                    utbetaling = utbetalingForSimulering,
                    simuleringsperiode = periode,
                    clock = clock,
                    utbetalingerKjørtTilOgMed = utbetalingerKjørtTilOgMed,
                )
            },
            kontrollerMotTidligereSimulering = kontrollerMotSimulering,
        )
    } else {
        simulerUtbetaling(
            sak = sak,
            utbetaling = utbetaling,
            simuleringsperiode = simuleringsperiode,
            clock = clock,
        ).mapLeft {
            SimulerUtbetalingFeilet.FeilVedSimulering(it)
        }
    }
}

/**
 * Ved simulering av nye utbetalingslinjer (søknadsbehandling eller revurdering som fører til endring).
 * Ved opphør bruk simuleringOpphørt()
 */
fun simuleringNy(
    beregning: Beregning = beregning(periode = år(2021)),
    eksisterendeUtbetalinger: Utbetalinger = Utbetalinger(),
    fnr: Fnr = no.nav.su.se.bakover.test.fnr,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    clock: Clock = fixedClock,
    // TODO default her bør fjernes og sendes inn fra behandlingen
    uføregrunnlag: List<Grunnlag.Uføregrunnlag> = listOf(
        Grunnlag.Uføregrunnlag(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(clock),
            periode = beregning.periode,
            uføregrad = Uføregrad.parse(50),
            forventetInntekt = 0,
        ),
    ),
): Simulering {
    return Utbetalingsstrategi.NyUføreUtbetaling(
        sakId = sakId,
        saksnummer = saksnummer,
        fnr = fnr,
        eksisterendeUtbetalinger = eksisterendeUtbetalinger,
        behandler = saksbehandler,
        beregning = beregning,
        clock = clock,
        uføregrunnlag = uføregrunnlag,
        kjøreplan = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
        sakstype = Sakstype.UFØRE, // TODO("simulering_utbetaling_alder utled fra sak/behandling")
    ).generate().let {
        SimuleringStub(
            clock = nåtidForSimuleringStub, // Overstyr klokke slik at vi kan simulere feilutbetalinger tilbake i tid,
            utbetalingRepo = UtbetalingRepoMock(eksisterendeUtbetalinger),
        ).simulerUtbetaling(
            SimulerUtbetalingForPeriode(
                utbetaling = it,
                simuleringsperiode = beregning.periode,
            ),
        )
    }.getOrFail()
}

fun simuleringOpphørt(
    opphørsperiode: Periode,
    eksisterendeUtbetalinger: Utbetalinger,
    fnr: Fnr = no.nav.su.se.bakover.test.fnr,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    clock: Clock = fixedClock,
): Simulering {
    return Utbetalingsstrategi.Opphør(
        sakId = sakId,
        saksnummer = saksnummer,
        fnr = fnr,
        eksisterendeUtbetalinger = eksisterendeUtbetalinger,
        behandler = saksbehandler,
        clock = clock,
        periode = opphørsperiode,
        sakstype = Sakstype.UFØRE, // TODO("simulering_utbetaling_alder utled fra sak/behandling")
    ).generate().let {
        val opphør = it.utbetalingslinjer.filterIsInstance<Utbetalingslinje.Endring.Opphør>().single()

        SimuleringStub(
            clock = nåtidForSimuleringStub, // Overstyr klokke slik at vi kan simulere feilutbetalinger tilbake i tid,
            utbetalingRepo = UtbetalingRepoMock(eksisterendeUtbetalinger),
        ).simulerUtbetaling(
            request = SimulerUtbetalingForPeriode(
                utbetaling = it,
                simuleringsperiode = opphør.periode,
            ),
        )
    }.getOrFail()
}

fun Simulering.settFiktivNetto(): Simulering {
    return copy(nettoBeløp = (hentTilUtbetaling().sum() * 0.5).roundToInt())
}

fun simulering(
    fnr: Fnr = no.nav.su.se.bakover.test.fnr,
    perioder: List<Periode> = år(2021).måneder(),
    simulertePerioder: List<SimulertPeriode> = perioder.map { simulertPeriode(it) },
): Simulering {
    return Simulering(
        gjelderId = fnr,
        gjelderNavn = "navn",
        datoBeregnet = fixedLocalDate,
        nettoBeløp = 0,
        periodeList = simulertePerioder,
        rawResponse = "SimuleringTestData baserer seg ikke på rå XML.",
    ).settFiktivNetto()
}

fun simuleringFeilutbetaling(
    vararg perioder: Periode = listOf(juni(2021)).toTypedArray(),
    simulertePerioder: List<SimulertPeriode> = perioder.map { it.måneder() }.flatten()
        .map { simulertPeriodeFeilutbetaling(it) },
): Simulering {
    return Simulering(
        gjelderId = fnr,
        gjelderNavn = "navn",
        datoBeregnet = fixedLocalDate,
        nettoBeløp = 0,
        periodeList = simulertePerioder,
        rawResponse = "SimuleringTestData baserer seg ikke på rå XML.",
    ).settFiktivNetto()
}

fun simulertPeriode(
    periode: Periode,
    simulerteUtbetalinger: SimulertUtbetaling? = simulertUtbetaling(periode),
): SimulertPeriode = SimulertPeriode(
    fraOgMed = periode.fraOgMed,
    tilOgMed = periode.tilOgMed,
    utbetaling = simulerteUtbetalinger,
)

fun simulertPeriodeFeilutbetaling(
    periode: Periode,
    simulerteUtbetalinger: SimulertUtbetaling? = simulertUtbetaling(
        periode = periode,
        simulertDetaljer = listOf(
            simulertDetaljFeilutbetaling(periode, 15000),
            simulertDetaljTilbakeføring(periode, 15000),
            simulertDetaljOrdinær(periode, 7000),
        ),
    ),
): SimulertPeriode = SimulertPeriode(
    fraOgMed = periode.fraOgMed,
    tilOgMed = periode.tilOgMed,
    utbetaling = simulerteUtbetalinger,
)

fun simulertUtbetaling(
    periode: Periode,
    simulertDetaljer: List<SimulertDetaljer> = listOf(simulertDetaljOrdinær(periode, 15000)),
): SimulertUtbetaling = SimulertUtbetaling(
    fagSystemId = "",
    utbetalesTilId = fnr,
    utbetalesTilNavn = "",
    forfall = periode.fraOgMed.plusDays(5),
    feilkonto = false,
    detaljer = simulertDetaljer,
)

fun simulertDetaljOrdinær(
    periode: Periode,
    beløp: Int,
): SimulertDetaljer = SimulertDetaljer(
    faktiskFraOgMed = periode.fraOgMed,
    faktiskTilOgMed = periode.tilOgMed,
    konto = "",
    belop = beløp,
    tilbakeforing = false,
    sats = beløp,
    typeSats = "MND",
    antallSats = 1,
    uforegrad = 0,
    klassekode = KlasseKode.SUUFORE,
    klassekodeBeskrivelse = "",
    klasseType = KlasseType.YTEL,
)

fun simulertDetaljFeilutbetaling(
    periode: Periode,
    beløp: Int,
): SimulertDetaljer = SimulertDetaljer(
    faktiskFraOgMed = periode.fraOgMed,
    faktiskTilOgMed = periode.tilOgMed,
    konto = "",
    belop = beløp,
    tilbakeforing = false,
    sats = 0,
    typeSats = "",
    antallSats = 0,
    uforegrad = 0,
    klassekode = KlasseKode.KL_KODE_FEIL_INNT,
    klassekodeBeskrivelse = "Feilutbetaling Inntektsytelser",
    klasseType = KlasseType.FEIL,
)

fun simulertDetaljTidligereUtbetalt(
    periode: Periode,
    beløp: Int,
): SimulertDetaljer = SimulertDetaljer(
    faktiskFraOgMed = periode.fraOgMed,
    faktiskTilOgMed = periode.tilOgMed,
    konto = "",
    belop = beløp,
    tilbakeforing = false,
    sats = 0,
    typeSats = "",
    antallSats = 0,
    uforegrad = 0,
    klassekode = KlasseKode.SUUFORE,
    klassekodeBeskrivelse = "",
    klasseType = KlasseType.YTEL,
)

fun simulertDetaljTilbakeføring(
    periode: Periode,
    beløp: Int,
): SimulertDetaljer = SimulertDetaljer(
    faktiskFraOgMed = periode.fraOgMed,
    faktiskTilOgMed = periode.tilOgMed,
    konto = "",
    belop = -beløp,
    tilbakeforing = true,
    sats = 0,
    typeSats = "",
    antallSats = 0,
    uforegrad = 0,
    klassekode = KlasseKode.SUUFORE,
    klassekodeBeskrivelse = "",
    klasseType = KlasseType.YTEL,
)

data class SimuleringResponseData(
    var gjelderId: String = fnr.toString(),
    var gjelderNavn: String = "Test Testesen",
    var datoBeregnet: String = "2021-01-02",
    var kodeFaggruppe: String = "INNT",
    var belop: String = "19611.00",
    var perioder: List<Periode> = emptyList(),
) {
    fun periode(init: Periode.() -> Unit) {
        perioder += Periode().apply(init)
    }

    data class Periode(
        var periodeFom: String = "2021-01-01",
        var periodeTom: String = "2021-01-31",
        var stoppnivåer: List<Stoppnivå> = emptyList(),
    ) {
        init {
            require(
                no.nav.su.se.bakover.common.periode.Periode.tryCreate(
                    LocalDate.parse(periodeFom),
                    LocalDate.parse(periodeTom),
                ).isRight(),
            )
        }

        fun stoppnivå(init: Stoppnivå.() -> Unit) {
            stoppnivåer += Stoppnivå().apply(init)
        }

        data class Stoppnivå(
            var kodeFagomraade: String = "SUUFORE",
            var fagsystemId: String = saksnummer.toString(),
            var forfall: String = "2021-01-15",
            var feilkonto: String = "false",
            var utbetalesTilNavn: String = "Test Testesen",
            var utbetalesTilId: String = fnr.toString(),
            var detaljer: List<Detalj> = emptyList(),
        ) {
            /**
             * @param belop må være 0/positivt
             */
            fun Periode.ordinær(
                belop: Int,
                klassekode: String = "SUUFORE",
                typeKlasse: String = "YTEL",
            ) {
                require(belop >= 0)

                detaljer += Detalj(
                    faktiskFom = periodeFom,
                    faktiskTom = periodeTom,
                    kontoStreng = "4952000",
                    behandlingskode = "2",
                    belop = "$belop.00",
                    korrigering = "",
                    tilbakeforing = "false",
                    linjeId = "4",
                    sats = "$belop.00",
                    typeSats = "MND",
                    antallSats = "1.00",
                    uforeGrad = "100",
                    klassekode = klassekode,
                    klasseKodeBeskrivelse = "Supplerende stønad Uføre",
                    typeKlasse = typeKlasse,
                    typeKlasseBeskrivelse = "Klassetype for ytelseskonti",
                )
            }

            /**
             * Vil være samme beløp som KL_KODE_FEIL_INNT og motsatt av TBMOTOBS.
             * Dukker kun og alltid opp ved feilutbetaling.
             *
             * @param belop må være positivt
             */
            fun Periode.debetFeilutbetaling(belop: Int) {
                require(belop > 0)

                detaljer += Detalj(
                    faktiskFom = periodeFom,
                    faktiskTom = periodeTom,
                    kontoStreng = "4952000",
                    behandlingskode = "2",
                    belop = "$belop.00",
                    korrigering = "",
                    tilbakeforing = "false",
                    linjeId = "0",
                    sats = "0.00",
                    typeSats = "",
                    antallSats = "0.00",
                    uforeGrad = "0",
                    klassekode = "SUUFORE",
                    klasseKodeBeskrivelse = "Supplerende stønad Uføre",
                    typeKlasse = "YTEL",
                    typeKlasseBeskrivelse = "Klassetype for ytelseskonti",
                )
            }

            /**
             * Beløp vil være motsatt av KL_KODE_FEIL_INNT.
             * Dukker kun og alltid opp ved feilutbetaling.
             *
             * @param belop må være negativt
             */
            fun Periode.motposteringskonto(belop: Int) {
                require(belop < 0) { "Skattedetaljer belop må være negativt og motsatt av feilutbetaling" }

                detaljer += Detalj(
                    faktiskFom = periodeFom,
                    faktiskTom = periodeTom,
                    kontoStreng = "0902900",
                    behandlingskode = "0",
                    belop = "$belop.00",
                    korrigering = "",
                    tilbakeforing = "false",
                    linjeId = "0",
                    sats = "0.00",
                    typeSats = "",
                    antallSats = "0.00",
                    uforeGrad = "0",
                    klassekode = "TBMOTOBS",
                    klasseKodeBeskrivelse = "Feilutbetaling motkonto til OBS konto",
                    typeKlasse = "MOTP",
                    typeKlasseBeskrivelse = "Klassetype for motposteringskonto",
                )
            }

            /**
             * Også kalt tilbakeføring.
             * Denne dukker opp dersom det har blitt utbetalt et beløp før.
             * Dette vil da blir trukket fra, før det nye beløpet legges til (så lenge ikke det nye beløpet er 0).
             *
             * @param belop må være negativt
             */
            fun Periode.tidligereUtbetalt(belop: Int) {
                require(belop < 0) { "tidligere utbetalt belop må være negativt" }

                detaljer += Detalj(
                    faktiskFom = periodeFom,
                    faktiskTom = periodeTom,
                    kontoStreng = "4952000",
                    behandlingskode = "2",
                    belop = "$belop.00",
                    korrigering = "",
                    tilbakeforing = "true",
                    linjeId = "1",
                    sats = "${abs(belop)}.00",
                    typeSats = "MND",
                    antallSats = "0.00",
                    uforeGrad = "100",
                    klassekode = "SUUFORE",
                    klasseKodeBeskrivelse = "Supplerende stønad Uføre",
                    typeKlasse = "YTEL",
                    typeKlasseBeskrivelse = "Klassetype for ytelseskonti",
                )
            }

            /**
             * Dette skjer kun ved feilutbetaling, ved nedjustering av tidligere utbetalt beløp. Inkl. nedjustert til 0 (opphør)
             *
             * @param belop må være positivt
             */
            fun Periode.feilutbetaling(belop: Int) {
                require(belop > 0)

                detaljer += Detalj(
                    faktiskFom = periodeFom,
                    faktiskTom = periodeTom,
                    kontoStreng = "0630986",
                    behandlingskode = "0",
                    belop = "$belop.00",
                    korrigering = "J",
                    tilbakeforing = "false",
                    linjeId = "0",
                    sats = "0.00",
                    typeSats = "",
                    antallSats = "0.00",
                    uforeGrad = "0",
                    klassekode = "KL_KODE_FEIL_INNT",
                    klasseKodeBeskrivelse = "Feilutbetaling Inntektsytelser",
                    typeKlasse = "FEIL",
                    typeKlasseBeskrivelse = "Klassetype for feilkontoer",
                )
            }

            /**
             * Vi filtrerer vekk SKAT i alle tilfeller.
             * Tilfeller man får detaljen SKAT i simuleringen (de tilfellene hvor bruker får oppjustert stønad):
             * - Ny måned fram/tilbake i tid (en "oppjustering" fra 0).
             * - Overskriving med oppjustering fram/tilbake i tid.
             * - Overskriving med nedjustering fram i tid.
             */
            fun Periode.skattedetalj(belop: Int) {
                require(belop < 0) { "Skattedetaljer belop må være negativt" }

                detaljer += Detalj(
                    faktiskFom = periodeFom,
                    faktiskTom = periodeTom,
                    kontoStreng = "0510000",
                    behandlingskode = "0",
                    belop = "$belop.00",
                    korrigering = "",
                    tilbakeforing = "false",
                    linjeId = "0",
                    sats = "0.00",
                    typeSats = "MND",
                    antallSats = LocalDate.parse(periodeTom).dayOfMonth.toString(),
                    uforeGrad = "0",
                    klassekode = "FSKTSKAT",
                    klasseKodeBeskrivelse = "Forskuddskatt",
                    typeKlasse = "SKAT",
                    typeKlasseBeskrivelse = "Klassetype for skatt",
                )
            }

            data class Detalj(
                var faktiskFom: String,
                var faktiskTom: String,
                var kontoStreng: String,
                var behandlingskode: String,
                var belop: String,
                /** Denne er kun J ved KL_KODE_FEIL_INNT */
                var korrigering: String,
                var tilbakeforing: String,
                var linjeId: String,
                var sats: String,
                var typeSats: String,
                var antallSats: String,
                var uforeGrad: String,
                var klassekode: String,
                var klasseKodeBeskrivelse: String,
                var typeKlasse: String,
                var typeKlasseBeskrivelse: String,
            ) {
                init {
                    // Validerer måneden
                    no.nav.su.se.bakover.common.periode.Måned.fra(
                        LocalDate.parse(faktiskFom),
                        LocalDate.parse(faktiskTom),
                    )
                }
            }
        }
    }

    companion object {
        fun simuleringXml(
            init: SimuleringResponseData.() -> Unit,
        ): String {
            return SimuleringResponseData().apply(init).let {
                // language=XML
                """
                  <simulerBeregningResponse xmlns="http://nav.no/system/os/tjenester/simulerFpService/simulerFpServiceGrensesnitt">
                    <response xmlns="">
                      <simulering>
                        <gjelderId>${it.gjelderId}</gjelderId>
                        <gjelderNavn>${it.gjelderNavn}</gjelderNavn>
                        <datoBeregnet>${it.datoBeregnet}</datoBeregnet>
                        <kodeFaggruppe>${it.kodeFaggruppe}</kodeFaggruppe>
                        <belop>${it.belop}</belop>
                        ${
                it.perioder.joinToString("") {
                    // language=XML
                    """
                        <beregningsPeriode xmlns="http://nav.no/system/os/entiteter/beregningSkjema">
                          <periodeFom xmlns="">${it.periodeFom}</periodeFom>
                          <periodeTom xmlns="">${it.periodeTom}</periodeTom>
                          ${
                    it.stoppnivåer.joinToString("\n") {
                        // language=XML
                        """
                          <beregningStoppnivaa>
                            <kodeFagomraade xmlns="">${it.kodeFagomraade}</kodeFagomraade>
                            <stoppNivaaId xmlns="">1</stoppNivaaId>
                            <behandlendeEnhet xmlns="">8020</behandlendeEnhet>
                            <oppdragsId xmlns="">53387554</oppdragsId>
                            <fagsystemId xmlns="">${it.fagsystemId}</fagsystemId>
                            <kid xmlns=""/>
                            <utbetalesTilId xmlns="">${it.utbetalesTilId}</utbetalesTilId>
                            <utbetalesTilNavn xmlns="">${it.utbetalesTilNavn}</utbetalesTilNavn>
                            <bilagsType xmlns="">U</bilagsType>
                            <forfall xmlns="">${it.forfall}</forfall>
                            <feilkonto xmlns="">${it.feilkonto}</feilkonto>
                            ${
                        it.detaljer.joinToString("\n") {
                            """
                            <beregningStoppnivaaDetaljer>
                              <faktiskFom xmlns="">${it.faktiskFom}</faktiskFom>
                              <faktiskTom xmlns="">${it.faktiskTom}</faktiskTom>
                              <kontoStreng xmlns="">${it.kontoStreng}</kontoStreng>
                              <behandlingskode xmlns="">${it.behandlingskode}</behandlingskode>
                              <belop xmlns="">${it.belop}</belop>
                              <trekkVedtakId xmlns="">0</trekkVedtakId>
                              <stonadId xmlns=""></stonadId>
                              <korrigering xmlns="">${it.korrigering}</korrigering>
                              <tilbakeforing xmlns="">${it.tilbakeforing}</tilbakeforing>
                              <linjeId xmlns="">${it.linjeId}</linjeId>
                              <sats xmlns="">${it.sats}</sats>
                              <typeSats xmlns="">${it.typeSats}</typeSats>
                              <antallSats xmlns="">${it.antallSats}</antallSats>
                              <saksbehId xmlns="">ignored</saksbehId>
                              <uforeGrad xmlns="">${it.uforeGrad}</uforeGrad>
                              <kravhaverId xmlns=""></kravhaverId>
                              <delytelseId xmlns=""></delytelseId>
                              <bostedsenhet xmlns="">8020</bostedsenhet>
                              <skykldnerId xmlns=""></skykldnerId>
                              <klassekode xmlns="">${it.klassekode}</klassekode>
                              <klasseKodeBeskrivelse xmlns="">${it.klasseKodeBeskrivelse}</klasseKodeBeskrivelse>
                              <typeKlasse xmlns="">${it.typeKlasse}</typeKlasse>
                              <typeKlasseBeskrivelse xmlns="">${it.typeKlasseBeskrivelse}</typeKlasseBeskrivelse>
                              <refunderesOrgNr xmlns=""></refunderesOrgNr>
                            </beregningStoppnivaaDetaljer>
                            """.trimIndent()
                        }
                        }
                          </beregningStoppnivaa>
                        """.trimIndent()
                    }
                    }
                        </beregningsPeriode>
                    """.trimIndent()
                }
                }
                      </simulering>
                      <infomelding>
                         <beskrMelding>Simulering er utført uten skattevedtak. Nominell sats benyttet.</beskrMelding>
                      </infomelding>
                    </response>
                  </simulerBeregningResponse>
                """.trimIndent()
            }
        }
    }
}
