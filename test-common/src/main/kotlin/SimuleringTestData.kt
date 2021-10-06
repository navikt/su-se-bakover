package no.nav.su.se.bakover.test

import no.nav.su.se.bakover.client.stubs.oppdrag.SimuleringStub.simulerUtbetaling
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsstrategi
import no.nav.su.se.bakover.domain.oppdrag.simulering.KlasseKode
import no.nav.su.se.bakover.domain.oppdrag.simulering.KlasseType
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertDetaljer
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertPeriode
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertUtbetaling
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

/**
 * Ved simulering av nye utbetalingslinjer (søknadsbehandling eller revurdering som fører til endring).
 * Ved opphør bruk simuleringOpphørt()
 */
fun simuleringNy(
    clock: Clock = fixedClock,
    beregning: Beregning = beregning(),
    eksisterendeUtbetalinger: List<Utbetaling> = emptyList(),
    fnr: Fnr = no.nav.su.se.bakover.test.fnr,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
): Simulering {
    return Utbetalingsstrategi.Ny(
        sakId = sakId,
        saksnummer = saksnummer,
        fnr = fnr,
        utbetalinger = eksisterendeUtbetalinger,
        behandler = saksbehandler,
        beregning = beregning,
        clock = clock,
        uføregrunnlag = listOf(
            Grunnlag.Uføregrunnlag(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(clock),
                periode = beregning.periode,
                uføregrad = Uføregrad.parse(50),
                forventetInntekt = 0,
            ),
        ),
    ).generate().let {
        simulerUtbetaling(it)
    }.orNull()!!
}

fun simuleringStans(
    stansDato: LocalDate,
    eksisterendeUtbetalinger: List<Utbetaling> = emptyList(),
    fnr: Fnr = no.nav.su.se.bakover.test.fnr,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    clock: Clock = fixedClock,
): Simulering {
    return stansUtbetalingForSimulering(
        stansDato = stansDato,
        fnr = fnr,
        sakId = sakId,
        saksnummer = saksnummer,
        eksisterendeUtbetalinger = eksisterendeUtbetalinger,
        clock = clock,
    ).let {
        simulerUtbetaling(it)
    }.getOrFail()
}

fun simuleringGjenopptak(
    eksisterendeUtbetalinger: List<Utbetaling> = emptyList(),
    fnr: Fnr = no.nav.su.se.bakover.test.fnr,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    clock: Clock = fixedClock,
): Simulering {
    return Utbetalingsstrategi.Gjenoppta(
        sakId = sakId,
        saksnummer = saksnummer,
        fnr = fnr,
        utbetalinger = eksisterendeUtbetalinger,
        behandler = saksbehandler,
        clock = clock,
    ).generer().let {
        simulerUtbetaling(it.getOrFail())
    }.getOrFail()
}

fun simuleringOpphørt(
    opphørsdato: LocalDate,
    eksisterendeUtbetalinger: List<Utbetaling> = emptyList(),
    fnr: Fnr = no.nav.su.se.bakover.test.fnr,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
): Simulering {
    return Utbetalingsstrategi.Opphør(
        sakId = sakId,
        saksnummer = saksnummer,
        fnr = fnr,
        utbetalinger = eksisterendeUtbetalinger,
        behandler = saksbehandler,
        clock = fixedClock,
        opphørsDato = opphørsdato,
    ).generate().let {
        simulerUtbetaling(it)
    }.orNull()!!
}

fun simulering(
    vararg perioder: Periode,
    simulertePerioder: List<SimulertPeriode> = perioder.map { simulertPeriode(it) },
): Simulering = Simulering(
    gjelderId = fnr,
    gjelderNavn = "navn",
    datoBeregnet = LocalDate.now(),
    nettoBeløp = simulertePerioder.sumOf { it.bruttoYtelse() },
    periodeList = simulertePerioder,
)

fun simuleringFeilutbetaling(
    vararg perioder: Periode,
    simulertePerioder: List<SimulertPeriode> = perioder.map { simulertPeriodeFeilutbetaling(it) },
): Simulering {
    return Simulering(
        gjelderId = fnr,
        gjelderNavn = "navn",
        datoBeregnet = LocalDate.now(),
        nettoBeløp = simulertePerioder.sumOf { it.bruttoYtelse() },
        periodeList = simulertePerioder,
    )
}

fun simulertPeriode(
    periode: Periode,
    simulerteUtbetalinger: List<SimulertUtbetaling> = listOf(simulertUtbetaling(periode)),
): SimulertPeriode = SimulertPeriode(
    fraOgMed = periode.fraOgMed,
    tilOgMed = periode.tilOgMed,
    utbetaling = simulerteUtbetalinger,
)

fun simulertPeriodeFeilutbetaling(
    periode: Periode,
    simulerteUtbetalinger: List<SimulertUtbetaling> = listOf(
        simulertUtbetaling(
            periode = periode,
            simulertDetaljer = listOf(
                simulertDetaljFeilutbetaling(periode, 15000),
                simulertDetaljTilbakeføring(periode, 15000),
                simulertDetaljOrdinær(periode, 7000),
            ),
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
