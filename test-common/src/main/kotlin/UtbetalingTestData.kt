package no.nav.su.se.bakover.test

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.startOfMonth
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsstrategi
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

val avstemmingsnøkkel = Avstemmingsnøkkel()
val utbetalingsRequest = Utbetalingsrequest("<xml></<xml>")

fun utbetalingslinje(
    clock: Clock = fixedClock,
    periode: Periode = periode2021,
    beløp: Int = 15000,
) = Utbetalingslinje.Ny(
    opprettet = Tidspunkt.now(clock),
    fraOgMed = periode.fraOgMed,
    tilOgMed = periode.tilOgMed,
    forrigeUtbetalingslinjeId = null,
    beløp = beløp,
    uføregrad = Uføregrad.parse(50),
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
    clock: Clock = fixedClock,
    id: UUID30 = UUID30.randomUUID(),
    periode: Periode = periode2021,
    fnr: Fnr = no.nav.su.se.bakover.test.fnr,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    utbetalingslinjer: NonEmptyList<Utbetalingslinje> = nonEmptyListOf(
        utbetalingslinje(
            periode = periode,
            clock = clock,
        ),
    ),
    avstemmingsnøkkel: Avstemmingsnøkkel = no.nav.su.se.bakover.test.avstemmingsnøkkel,
    type: Utbetaling.UtbetalingsType = Utbetaling.UtbetalingsType.NY,
    eksisterendeUtbetalinger: List<Utbetaling> = emptyList(),
) = Utbetaling.OversendtUtbetaling.UtenKvittering(
    id = id,
    opprettet = Tidspunkt.now(clock),
    sakId = sakId,
    saksnummer = saksnummer,
    fnr = fnr,
    utbetalingslinjer = utbetalingslinjer,
    type = type,
    behandler = attestant,
    avstemmingsnøkkel = avstemmingsnøkkel,
    simulering = simuleringNy(fnr = fnr, eksisterendeUtbetalinger = eksisterendeUtbetalinger, clock = clock),
    utbetalingsrequest = utbetalingsRequest,
)

fun oversendtUtbetalingUtenKvitteringFraBeregning(
    clock: Clock = fixedClock,
    id: UUID30 = UUID30.randomUUID(),
    fnr: Fnr = no.nav.su.se.bakover.test.fnr,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    avstemmingsnøkkel: Avstemmingsnøkkel = no.nav.su.se.bakover.test.avstemmingsnøkkel,
    type: Utbetaling.UtbetalingsType = Utbetaling.UtbetalingsType.NY,
    eksisterendeUtbetalinger: List<Utbetaling> = emptyList(),
    beregning: Beregning,
    uføregrunnlag: List<Grunnlag.Uføregrunnlag>,
): Utbetaling.OversendtUtbetaling.UtenKvittering {
    val utbetalingslinjer = Utbetalingsstrategi.Ny(
        sakId = sakId,
        saksnummer = saksnummer,
        fnr = fnr,
        utbetalinger = eksisterendeUtbetalinger,
        behandler = attestant,
        beregning = beregning,
        clock = clock,
        uføregrunnlag = uføregrunnlag,
    ).generate().utbetalingslinjer
    return Utbetaling.OversendtUtbetaling.UtenKvittering(
        id = id,
        opprettet = Tidspunkt.now(clock),
        sakId = sakId,
        saksnummer = saksnummer,
        fnr = fnr,
        utbetalingslinjer = utbetalingslinjer,
        type = type,
        behandler = attestant,
        avstemmingsnøkkel = avstemmingsnøkkel,
        simulering = simuleringNy(
            fnr = fnr,
            eksisterendeUtbetalinger = eksisterendeUtbetalinger,
            clock = clock,
        ),
        utbetalingsrequest = utbetalingsRequest,
    )
}

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
    clock: Clock = fixedClock,
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
        clock = clock,
    ).toKvittertUtbetaling(kvittering(utbetalingsstatus = utbetalingsstatus))
}

fun oversendtUtbetalingMedKvitteringFraBeregning(
    clock: Clock = fixedClock,
    id: UUID30 = UUID30.randomUUID(),
    utbetalingsstatus: Kvittering.Utbetalingsstatus = Kvittering.Utbetalingsstatus.OK,
    type: Utbetaling.UtbetalingsType = Utbetaling.UtbetalingsType.NY,
    fnr: Fnr = no.nav.su.se.bakover.test.fnr,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    eksisterendeUtbetalinger: List<Utbetaling> = emptyList(),
    beregning: Beregning,
    uføregrunnlag: List<Grunnlag.Uføregrunnlag>,
): Utbetaling.OversendtUtbetaling.MedKvittering {
    return oversendtUtbetalingUtenKvitteringFraBeregning(
        id = id,
        fnr = fnr,
        sakId = sakId,
        saksnummer = saksnummer,
        type = type,
        eksisterendeUtbetalinger = eksisterendeUtbetalinger,
        beregning = beregning,
        uføregrunnlag = uføregrunnlag,
        clock = clock,
    ).toKvittertUtbetaling(kvittering(utbetalingsstatus = utbetalingsstatus))
}

fun stansUtbetalingForSimulering(
    stansDato: LocalDate,
    fnr: Fnr = no.nav.su.se.bakover.test.fnr,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    eksisterendeUtbetalinger: List<Utbetaling> = listOf(oversendtUtbetalingMedKvittering()),
    clock: Clock = fixedClock,
): Utbetaling.UtbetalingForSimulering {
    return Utbetalingsstrategi.Stans(
        sakId = sakId,
        saksnummer = saksnummer,
        fnr = fnr,
        utbetalinger = eksisterendeUtbetalinger,
        behandler = saksbehandler,
        clock = clock,
        stansDato = stansDato,
    ).generer().getOrFail()
}

fun simulertStansUtbetaling(
    stansDato: LocalDate,
    fnr: Fnr = no.nav.su.se.bakover.test.fnr,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    eksisterendeUtbetalinger: List<Utbetaling> = listOf(oversendtUtbetalingMedKvittering()),
    clock: Clock = fixedClock,
): Utbetaling.SimulertUtbetaling {
    return stansUtbetalingForSimulering(
        stansDato = stansDato,
        fnr = fnr,
        sakId = sakId,
        saksnummer = saksnummer,
        eksisterendeUtbetalinger = eksisterendeUtbetalinger,
        clock = clock,
    ).toSimulertUtbetaling(
        simuleringStans(
            stansDato = stansDato,
            eksisterendeUtbetalinger = eksisterendeUtbetalinger,
            fnr = fnr,
            sakId = sakId,
            saksnummer = saksnummer,
            clock = clock,
        ),
    )
}

fun oversendtStansUtbetalingUtenKvittering(
    stansDato: LocalDate,
    fnr: Fnr = no.nav.su.se.bakover.test.fnr,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    eksisterendeUtbetalinger: List<Utbetaling> = listOf(oversendtUtbetalingMedKvittering()),
    clock: Clock = fixedClock,
): Utbetaling.OversendtUtbetaling.UtenKvittering {
    return simulertStansUtbetaling(
        stansDato = stansDato,
        fnr = fnr,
        sakId = sakId,
        saksnummer = saksnummer,
        eksisterendeUtbetalinger = eksisterendeUtbetalinger,
        clock = clock,
    ).toOversendtUtbetaling(utbetalingsRequest)
}

fun gjenopptakUtbetalingForSimulering(
    fnr: Fnr = no.nav.su.se.bakover.test.fnr,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    eksisterendeUtbetalinger: List<Utbetaling> = listOf(
        oversendtStansUtbetalingUtenKvittering(
            stansDato = LocalDate.now(fixedClock).plusMonths(1).startOfMonth(),
        ),
    ),
    clock: Clock = fixedClock,
): Utbetaling.UtbetalingForSimulering {
    return Utbetalingsstrategi.Gjenoppta(
        sakId = sakId,
        saksnummer = saksnummer,
        fnr = fnr,
        utbetalinger = eksisterendeUtbetalinger,
        behandler = saksbehandler,
        clock = clock,
    ).generer().getOrFail()
}

fun simulertGjenopptakUtbetaling(
    fnr: Fnr = no.nav.su.se.bakover.test.fnr,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    eksisterendeUtbetalinger: List<Utbetaling> = listOf(
        oversendtStansUtbetalingUtenKvittering(
            stansDato = LocalDate.now(fixedClock).plusMonths(1).startOfMonth(),
        ),
    ),
    clock: Clock = fixedClock,
): Utbetaling.SimulertUtbetaling {
    return gjenopptakUtbetalingForSimulering(
        fnr = fnr,
        sakId = sakId,
        saksnummer = saksnummer,
        eksisterendeUtbetalinger = eksisterendeUtbetalinger,
        clock = clock,
    ).toSimulertUtbetaling(
        simuleringGjenopptak(
            eksisterendeUtbetalinger = eksisterendeUtbetalinger,
            fnr = fnr,
            sakId = sakId,
            saksnummer = saksnummer,
            clock = clock,
        ),
    )
}

fun oversendtGjenopptakUtbetalingUtenKvittering(
    fnr: Fnr = no.nav.su.se.bakover.test.fnr,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    eksisterendeUtbetalinger: List<Utbetaling> = listOf(
        oversendtStansUtbetalingUtenKvittering(
            stansDato = LocalDate.now(fixedClock).plusMonths(1).startOfMonth(),
        ),
    ),
    clock: Clock = fixedClock,
): Utbetaling.OversendtUtbetaling.UtenKvittering {
    return simulertGjenopptakUtbetaling(
        fnr = fnr,
        sakId = sakId,
        saksnummer = saksnummer,
        eksisterendeUtbetalinger = eksisterendeUtbetalinger,
        clock = clock,
    ).toOversendtUtbetaling(utbetalingsRequest)
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
