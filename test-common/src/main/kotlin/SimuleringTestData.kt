package no.nav.su.se.bakover.test

import no.nav.su.se.bakover.client.stubs.oppdrag.SimuleringStub.simulerUtbetaling
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsstrategi
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import java.util.UUID

/**
 * Ved simulering av nye utbetalingslinjer (søknadsbehandling eller revurdering som fører til endring).
 * Lag egen funksjon for opphør ved behov.
 */
fun simuleringNy(
    beregning: Beregning = beregning(),
    tidligereUtbetalinger: List<Utbetaling> = emptyList(),
    fnr: Fnr = no.nav.su.se.bakover.test.fnr,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
): Simulering {
    return Utbetalingsstrategi.Ny(
        sakId = sakId,
        saksnummer = saksnummer,
        fnr = fnr,
        utbetalinger = tidligereUtbetalinger,
        behandler = saksbehandler,
        beregning = beregning,
        clock = fixedClock,
    ).generate().let {
        simulerUtbetaling(it)
    }.orNull()!!
}
