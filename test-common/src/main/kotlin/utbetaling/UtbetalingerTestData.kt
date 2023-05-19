package no.nav.su.se.bakover.test.utbetaling

import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.Rekkefølge
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.Utbetalinger
import no.nav.su.se.bakover.domain.sak.Saksnummer
import no.nav.su.se.bakover.test.TikkendeKlokke
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

/**
 * Generell testdata for utbetalinger.
 * Dersom du ønsker en ny, opphørt, stans eller reaktivering; bruk de respektive funksjonene.
 *
 * Alle utbetalinger må være oversendt med kvittering.
 */
fun utbetalinger(
    vararg utbetalinger: Utbetaling,
): Utbetalinger {
    return Utbetalinger(
        utbetalinger = utbetalinger.toList(),
    ).also { it.kastHvisIkkeAlleErKvitterteUtenFeil() }
}

/**
 * Generell testdata for utbetalinger.
 * Dersom du ønsker en ny, opphørt, stans eller reaktivering; bruk de respektive funksjonene.
 */
fun utbetalinger(
    clock: Clock = TikkendeKlokke(),
    utbetalingslinje: Utbetalingslinje,
    vararg utbetalingslinjer: Utbetalingslinje,
): Utbetalinger {
    return Utbetalinger(
        oversendtUtbetalingMedKvittering(clock = clock, utbetalingslinjer = nonEmptyListOf(utbetalingslinje, *utbetalingslinjer)),
    ).also { it.kastHvisIkkeAlleErKvitterteUtenFeil() }
}

/**
 * Alle er oversendt med kvittering.
 * I inneholder én utbetaling med én ny utbetalingslinje.
 */
fun utbetalingerNy(
    id: UUID30 = UUID30.randomUUID(),
    clock: Clock = TikkendeKlokke(),
    fnr: Fnr = no.nav.su.se.bakover.test.fnr,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    periode: Periode = år(2021),
    beløp: Int = 5000,
): Utbetalinger {
    return Utbetalinger(
        utbetalinger = nonEmptyListOf(
            oversendtUtbetalingMedKvittering(
                fnr = fnr,
                sakId = sakId,
                saksnummer = saksnummer,
                clock = clock,
                periode = periode,
                utbetalingslinjer = nonEmptyListOf(
                    utbetalingslinjeNy(
                        id = id,
                        clock = clock,
                        periode = periode,
                        beløp = beløp,
                    ),
                ),
            ),
        ),
    ).also { it.kastHvisIkkeAlleErKvitterteUtenFeil() }
}

/**
 * Alle er oversendt med kvittering.
 * Inneholder én utbetaling med to utbetalinger; én ny og ett opphør.
 */
fun utbetalingerOpphør(
    id: UUID30 = UUID30.randomUUID(),
    clock: Clock = TikkendeKlokke(),
    fnr: Fnr = no.nav.su.se.bakover.test.fnr,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    nyPeriode: Periode = år(2021),
    opphørsperiode: Periode = nyPeriode,
    beløp: Int = 5000,
): Utbetalinger {
    val første = utbetalingslinjeNy(
        id = id,
        clock = clock,
        periode = nyPeriode,
        beløp = beløp,
    )
    return Utbetalinger(
        utbetalinger = nonEmptyListOf(
            oversendtUtbetalingMedKvittering(
                fnr = fnr,
                sakId = sakId,
                saksnummer = saksnummer,
                clock = clock,
                utbetalingslinjer = nonEmptyListOf(
                    første,
                    Utbetalingslinje.Endring.Opphør(
                        utbetalingslinjeSomSkalEndres = første,
                        virkningsperiode = opphørsperiode,
                        opprettet = Tidspunkt.now(clock),
                        rekkefølge = Rekkefølge.ANDRE,
                    ),
                ),
            ),
        ),
    ).also { it.kastHvisIkkeAlleErKvitterteUtenFeil() }
}

/**
 * Alle er oversendt med kvittering.
 * Inneholder én utbetaling med to utbetalinger; én ny og én stans.
 */
fun utbetalingerStans(
    id: UUID30 = UUID30.randomUUID(),
    clock: Clock = TikkendeKlokke(),
    fnr: Fnr = no.nav.su.se.bakover.test.fnr,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    nyPeriode: Periode = år(2021),
    stansFraOgMed: LocalDate = nyPeriode.fraOgMed,
    beløp: Int = 5000,
): Utbetalinger {
    val første = utbetalingslinjeNy(
        id = id,
        clock = clock,
        periode = nyPeriode,
        beløp = beløp,
    )
    return Utbetalinger(
        utbetalinger = nonEmptyListOf(
            oversendtUtbetalingMedKvittering(
                fnr = fnr,
                sakId = sakId,
                saksnummer = saksnummer,
                clock = clock,
                utbetalingslinjer = nonEmptyListOf(
                    første,
                    Utbetalingslinje.Endring.Stans(
                        utbetalingslinjeSomSkalEndres = første,
                        virkningstidspunkt = stansFraOgMed,
                        opprettet = Tidspunkt.now(clock),
                        rekkefølge = Rekkefølge.ANDRE,
                    ),
                ),
            ),
        ),
    ).also { it.kastHvisIkkeAlleErKvitterteUtenFeil() }
}

/**
 * Alle er oversendt med kvittering.
 * Inneholder én utbetaling med tre utbetalinger; én ny, én stans og én reaktivering.
 */
fun utbetalingerReaktivering(
    id: UUID30 = UUID30.randomUUID(),
    clock: Clock = TikkendeKlokke(),
    fnr: Fnr = no.nav.su.se.bakover.test.fnr,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    nyPeriode: Periode = år(2021),
    stansFraOgMed: LocalDate = nyPeriode.fraOgMed,
    reaktiveringFraOgMed: LocalDate = stansFraOgMed,
    beløp: Int = 5000,
): Utbetalinger {
    val første = utbetalingslinjeNy(
        id = id,
        clock = clock,
        periode = nyPeriode,
        beløp = beløp,
    )
    val stans = Utbetalingslinje.Endring.Stans(
        utbetalingslinjeSomSkalEndres = første,
        virkningstidspunkt = stansFraOgMed,
        opprettet = Tidspunkt.now(clock),
        rekkefølge = Rekkefølge.ANDRE,
    )
    return Utbetalinger(
        utbetalinger = nonEmptyListOf(
            oversendtUtbetalingMedKvittering(
                fnr = fnr,
                sakId = sakId,
                saksnummer = saksnummer,
                clock = clock,
                utbetalingslinjer = nonEmptyListOf(
                    første,
                    stans,
                    Utbetalingslinje.Endring.Reaktivering(
                        utbetalingslinjeSomSkalEndres = stans,
                        virkningstidspunkt = reaktiveringFraOgMed,
                        opprettet = Tidspunkt.now(clock),
                        rekkefølge = Rekkefølge.TREDJE,
                    ),
                ),
            ),
        ),
    ).also { it.kastHvisIkkeAlleErKvitterteUtenFeil() }
}
