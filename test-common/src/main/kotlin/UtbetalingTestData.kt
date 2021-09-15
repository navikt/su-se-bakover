package no.nav.su.se.bakover.test

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import java.util.UUID

val avstemmingsnøkkel = Avstemmingsnøkkel()

fun utbetalingslinje(
    periode: Periode = periode2021,
) = Utbetalingslinje.Ny(
    fraOgMed = periode.fraOgMed,
    tilOgMed = periode.tilOgMed,
    forrigeUtbetalingslinjeId = null,
    beløp = 15000,
)

@Suppress("unused")
fun oversendtUtbetalingUtenKvittering(
    periode: Periode = periode2021,
    søknadsbehandling: Søknadsbehandling.Iverksatt.Innvilget,
    avstemmingsnøkkel: Avstemmingsnøkkel = no.nav.su.se.bakover.test.avstemmingsnøkkel,
    utbetalingslinjer: NonEmptyList<Utbetalingslinje> = nonEmptyListOf(utbetalingslinje(periode = periode)),
    eksisterendeUtbetalinger: List<Utbetaling> = emptyList(),
) = oversendtUtbetalingUtenKvittering(
    fnr = søknadsbehandling.fnr,
    sakId = søknadsbehandling.sakId,
    saksnummer = søknadsbehandling.saksnummer,
    utbetalingslinjer = utbetalingslinjer,
    avstemmingsnøkkel = avstemmingsnøkkel,
    eksisterendeUtbetalinger = eksisterendeUtbetalinger,
)

@Suppress("unused")
fun oversendtUtbetalingUtenKvittering(
    periode: Periode = periode2021,
    revurdering: RevurderingTilAttestering,
    avstemmingsnøkkel: Avstemmingsnøkkel = no.nav.su.se.bakover.test.avstemmingsnøkkel,
    utbetalingslinjer: NonEmptyList<Utbetalingslinje> = nonEmptyListOf(utbetalingslinje(periode = periode)),
    eksisterendeUtbetalinger: List<Utbetaling> = emptyList(),
) = oversendtUtbetalingUtenKvittering(
    fnr = revurdering.fnr,
    sakId = revurdering.sakId,
    saksnummer = revurdering.saksnummer,
    utbetalingslinjer = utbetalingslinjer,
    avstemmingsnøkkel = avstemmingsnøkkel,
    eksisterendeUtbetalinger = eksisterendeUtbetalinger,
)

fun oversendtUtbetalingUtenKvittering(
    id: UUID30 = UUID30.randomUUID(),
    periode: Periode = periode2021,
    fnr: Fnr = no.nav.su.se.bakover.test.fnr,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    utbetalingslinjer: NonEmptyList<Utbetalingslinje> = nonEmptyListOf(utbetalingslinje(periode = periode)),
    avstemmingsnøkkel: Avstemmingsnøkkel = no.nav.su.se.bakover.test.avstemmingsnøkkel,
    type: Utbetaling.UtbetalingsType = Utbetaling.UtbetalingsType.NY,
    eksisterendeUtbetalinger: List<Utbetaling> = emptyList(),
) = Utbetaling.OversendtUtbetaling.UtenKvittering(
    id = id,
    opprettet = fixedTidspunkt,
    sakId = sakId,
    saksnummer = saksnummer,
    fnr = fnr,
    utbetalingslinjer = utbetalingslinjer,
    type = type,
    behandler = attestant,
    avstemmingsnøkkel = avstemmingsnøkkel,
    simulering = simuleringNy(fnr = fnr, eksisterendeUtbetalinger = eksisterendeUtbetalinger),
    utbetalingsrequest = Utbetalingsrequest("<xml></xml>"),
)

fun simulertUtbetaling(
    id: UUID30 = UUID30.randomUUID(),
    periode: Periode = periode2021,
    fnr: Fnr = no.nav.su.se.bakover.test.fnr,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    utbetalingslinjer: NonEmptyList<Utbetalingslinje> = nonEmptyListOf(utbetalingslinje(periode = periode)),
    avstemmingsnøkkel: Avstemmingsnøkkel = no.nav.su.se.bakover.test.avstemmingsnøkkel,
    type: Utbetaling.UtbetalingsType = Utbetaling.UtbetalingsType.NY,
    eksisterendeUtbetalinger: List<Utbetaling> = emptyList(),
) = Utbetaling.SimulertUtbetaling(
    id = id,
    opprettet = fixedTidspunkt,
    sakId = sakId,
    saksnummer = saksnummer,
    fnr = fnr,
    utbetalingslinjer = utbetalingslinjer,
    type = type,
    behandler = attestant,
    avstemmingsnøkkel = avstemmingsnøkkel,
    simulering = simuleringNy(fnr = fnr, eksisterendeUtbetalinger = eksisterendeUtbetalinger),
)

/**
 * Defaultverdier:
 * - id: arbitrær
 * - utbetalingsstatus: OK
 * - type: NY
 */
fun oversendtUtbetalingMedKvittering(
    id: UUID30 = UUID30.randomUUID(),
    utbetalingsstatus: Kvittering.Utbetalingsstatus = Kvittering.Utbetalingsstatus.OK,
    type: Utbetaling.UtbetalingsType = Utbetaling.UtbetalingsType.NY,
    fnr: Fnr = no.nav.su.se.bakover.test.fnr,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    eksisterendeUtbetalinger: List<Utbetaling> = emptyList(),
): Utbetaling.OversendtUtbetaling.MedKvittering {
    return oversendtUtbetalingUtenKvittering(
        id = id,
        type = type,
        sakId = sakId,
        saksnummer = saksnummer,
        fnr = fnr,
        eksisterendeUtbetalinger = eksisterendeUtbetalinger,
    )
        .toKvittertUtbetaling(kvittering(utbetalingsstatus = utbetalingsstatus))
}

/**
 * Defaultverdier:
 * - utbetalingsstatus: OK
 */
fun kvittering(
    utbetalingsstatus: Kvittering.Utbetalingsstatus = Kvittering.Utbetalingsstatus.OK,
) = Kvittering(
    utbetalingsstatus = utbetalingsstatus,
    originalKvittering = "<xml></xml>",
    mottattTidspunkt = fixedTidspunkt,
)
