package no.nav.su.se.bakover.test

import no.nav.su.se.bakover.client.stubs.oppdrag.SimuleringStub.simulerUtbetaling
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsstrategi
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import java.time.LocalDate
import java.util.UUID

/**
 * Ved simulering av nye utbetalingslinjer (søknadsbehandling eller revurdering som fører til endring).
 * Ved opphør bruk simuleringOpphørt()
 */
fun simuleringNy(
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
        clock = fixedClock,
    ).generate().let {
        simulerUtbetaling(it)
    }.orNull()!!
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
