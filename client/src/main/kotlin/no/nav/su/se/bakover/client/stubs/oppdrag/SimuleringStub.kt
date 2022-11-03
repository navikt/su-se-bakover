package no.nav.su.se.bakover.client.stubs.oppdrag

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.periode.Måned
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingslinjePåTidslinje
import no.nav.su.se.bakover.domain.oppdrag.simulering.KlasseType
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulerUtbetalingRequest
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringClient
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertDetaljer
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertPeriode
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertUtbetaling
import no.nav.su.se.bakover.domain.oppdrag.simulering.toFeilkode
import no.nav.su.se.bakover.domain.oppdrag.simulering.toYtelsekode
import no.nav.su.se.bakover.domain.oppdrag.tidslinje
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingRepo
import no.nav.su.se.bakover.domain.sak.Sakstype
import java.time.Clock
import java.time.LocalDate

class SimuleringStub(
    val clock: Clock,
    val utbetalingerKjørtTilOgMed: LocalDate = LocalDate.now(clock),
    val utbetalingRepo: UtbetalingRepo,
) : SimuleringClient {

    override fun simulerUtbetaling(request: SimulerUtbetalingRequest): Either<SimuleringFeilet, Simulering> {
        return simulerUtbetalinger(request).right()
    }

    private fun List<SimulertPeriode>.calculateNetto() =
        this.sumOf { it.bruttoYtelse() } + this.sumOf { simulertPeriode ->
            simulertPeriode.utbetaling
                .flatMap { it.detaljer }
                .filter { !it.isYtelse() }
                .sumOf { it.belop }
        }

    private fun simulerUtbetalinger(request: SimulerUtbetalingRequest): Simulering {
        val utbetaling = request.utbetaling
        val simuleringsperiode = request.simuleringsperiode
        val tidslinjeEksisterendeUtbetalinger = utbetalingRepo.hentUtbetalinger(utbetaling.sakId)
            .tidslinje(
                periode = simuleringsperiode,
                clock = clock,
            )

        val tidslinjeNyUtbetaling = utbetaling.utbetalingslinjer
            .tidslinje(
                periode = simuleringsperiode,
                clock = clock,
            )

        return simuleringsperiode.måneder()
            .asSequence()
            .map { måned ->
                val utbetaltLinje = tidslinjeEksisterendeUtbetalinger.fold(
                    { null },
                    { it.gjeldendeForDato(måned.fraOgMed) },
                )
                val nyLinje = tidslinjeNyUtbetaling.fold(
                    { throw RuntimeException("Disse skal eksistere") },
                    { it.gjeldendeForDato(måned.fraOgMed) },
                )
                if (utbetaltLinje == null && nyLinje != null) {
                    // ingen tidligere utbetaling for måned
                    when (nyLinje) {
                        is UtbetalingslinjePåTidslinje.Ny -> {
                            måned to SimulertUtbetaling(
                                fagSystemId = utbetaling.saksnummer.toString(),
                                utbetalesTilId = utbetaling.fnr,
                                utbetalesTilNavn = "LYR MYGG",
                                forfall = LocalDate.now(clock),
                                feilkonto = false,
                                detaljer = listOf(
                                    createOrdinær(
                                        fraOgMed = måned.fraOgMed,
                                        tilOgMed = måned.tilOgMed,
                                        beløp = nyLinje.beløp,
                                        sakstype = utbetaling.sakstype,
                                    ),
                                ),
                            )
                        }

                        is UtbetalingslinjePåTidslinje.Opphør -> {
                            måned to null
                        }

                        is UtbetalingslinjePåTidslinje.Reaktivering -> {
                            måned to SimulertUtbetaling(
                                fagSystemId = utbetaling.saksnummer.toString(),
                                utbetalesTilId = utbetaling.fnr,
                                utbetalesTilNavn = "LYR MYGG",
                                forfall = LocalDate.now(clock),
                                feilkonto = false,
                                detaljer = listOf(
                                    createOrdinær(
                                        fraOgMed = måned.fraOgMed,
                                        tilOgMed = måned.tilOgMed,
                                        beløp = nyLinje.beløp,
                                        sakstype = utbetaling.sakstype,
                                    ),
                                ),
                            )
                        }

                        is UtbetalingslinjePåTidslinje.Stans -> {
                            måned to null
                        }
                    }
                } else if (utbetaltLinje != null && nyLinje != null) {
                    when (nyLinje) {
                        is UtbetalingslinjePåTidslinje.Ny -> {
                            val diff = nyLinje.beløp - utbetaltLinje.beløp
                            when {
                                /**
                                 * Endringer tilbake i tid, ny utbetaling er lavere enn allerede utbetalt beløp -> feilutbetaling
                                 */
                                diff < 0 && erIFortiden(måned) -> {
                                    måned to SimulertUtbetaling(
                                        fagSystemId = utbetaling.saksnummer.toString(),
                                        utbetalesTilId = utbetaling.fnr,
                                        utbetalesTilNavn = "LYR MYGG",
                                        forfall = LocalDate.now(clock),
                                        feilkonto = true,
                                        detaljer = listOf(
                                            createTidligereUtbetalt(
                                                fraOgMed = måned.fraOgMed,
                                                tilOgMed = måned.tilOgMed,
                                                beløp = utbetaltLinje.beløp,
                                                sakstype = utbetaling.sakstype,
                                            ),
                                            createOrdinær(
                                                fraOgMed = måned.fraOgMed,
                                                tilOgMed = måned.tilOgMed,
                                                beløp = nyLinje.beløp,
                                                sakstype = utbetaling.sakstype,
                                            ),
                                            createFeilutbetaling(
                                                fraOgMed = måned.fraOgMed,
                                                tilOgMed = måned.tilOgMed,
                                                beløp = diff,
                                                sakstype = utbetaling.sakstype,
                                            ),
                                        ),
                                    )
                                }
                                /**
                                 * Endringer tilbake i tid, ny utbetaling er større enn allerede utbetalt beløp -> etterbetaling
                                 */
                                diff > 0 && erIFortiden(måned) -> {
                                    måned to SimulertUtbetaling(
                                        fagSystemId = utbetaling.saksnummer.toString(),
                                        utbetalesTilId = utbetaling.fnr,
                                        utbetalesTilNavn = "LYR MYGG",
                                        forfall = LocalDate.now(clock),
                                        feilkonto = false,
                                        detaljer = listOf(
                                            createTidligereUtbetalt(
                                                fraOgMed = måned.fraOgMed,
                                                tilOgMed = måned.tilOgMed,
                                                beløp = utbetaltLinje.beløp,
                                                sakstype = utbetaling.sakstype,
                                            ),
                                            createOrdinær(
                                                fraOgMed = måned.fraOgMed,
                                                tilOgMed = måned.tilOgMed,
                                                beløp = nyLinje.beløp,
                                                sakstype = utbetaling.sakstype,
                                            ),
                                        ),
                                    )
                                }
                                /**
                                 * Endringer tilbake i tid, ny utbetaling er det samme som allerede utbetalt beløp -> uendret
                                 */
                                diff == 0 && erIFortiden(måned) -> {
                                    måned to SimulertUtbetaling(
                                        fagSystemId = utbetaling.saksnummer.toString(),
                                        utbetalesTilId = utbetaling.fnr,
                                        utbetalesTilNavn = "LYR MYGG",
                                        forfall = LocalDate.now(clock),
                                        feilkonto = false,
                                        detaljer = listOf(
                                            createOrdinær(
                                                fraOgMed = måned.fraOgMed,
                                                tilOgMed = måned.tilOgMed,
                                                beløp = nyLinje.beløp,
                                                sakstype = utbetaling.sakstype,
                                            ),
                                            createTidligereUtbetalt(
                                                fraOgMed = måned.fraOgMed,
                                                tilOgMed = måned.tilOgMed,
                                                beløp = utbetaltLinje.beløp,
                                                sakstype = utbetaling.sakstype,
                                            ),
                                        ),
                                    )
                                }
                                /**
                                 * Endringer fremover i tid, ingen utbetalinger er gjennomført -> ordinær
                                 */
                                else -> {
                                    måned to SimulertUtbetaling(
                                        fagSystemId = utbetaling.saksnummer.toString(),
                                        utbetalesTilId = utbetaling.fnr,
                                        utbetalesTilNavn = "LYR MYGG",
                                        forfall = LocalDate.now(clock),
                                        feilkonto = false,
                                        detaljer = listOf(
                                            createOrdinær(
                                                fraOgMed = måned.fraOgMed,
                                                tilOgMed = måned.tilOgMed,
                                                beløp = nyLinje.beløp,
                                                sakstype = utbetaling.sakstype,
                                            ),
                                        ),
                                    )
                                }
                            }
                        }

                        is UtbetalingslinjePåTidslinje.Opphør -> {
                            val diff = nyLinje.beløp - utbetaltLinje.beløp
                            val feilutbetaling = diff < 0 && erIFortiden(måned)

                            if (feilutbetaling) {
                                måned to SimulertUtbetaling(
                                    fagSystemId = utbetaling.saksnummer.toString(),
                                    utbetalesTilId = utbetaling.fnr,
                                    utbetalesTilNavn = "LYR MYGG",
                                    forfall = LocalDate.now(clock),
                                    feilkonto = feilutbetaling,
                                    detaljer = listOf(
                                        createTidligereUtbetalt(
                                            fraOgMed = måned.fraOgMed,
                                            tilOgMed = måned.tilOgMed,
                                            beløp = utbetaltLinje.beløp,
                                            sakstype = utbetaling.sakstype,
                                        ),
                                        createFeilutbetaling(
                                            fraOgMed = måned.fraOgMed,
                                            tilOgMed = måned.tilOgMed,
                                            beløp = diff,
                                            sakstype = utbetaling.sakstype,
                                        ),
                                    ),
                                )
                            } else {
                                måned to null
                            }
                        }

                        is UtbetalingslinjePåTidslinje.Reaktivering -> {
                            måned to SimulertUtbetaling(
                                fagSystemId = utbetaling.saksnummer.toString(),
                                utbetalesTilId = utbetaling.fnr,
                                utbetalesTilNavn = "LYR MYGG",
                                forfall = LocalDate.now(clock),
                                feilkonto = false,
                                detaljer = listOf(
                                    createOrdinær(
                                        fraOgMed = måned.fraOgMed,
                                        tilOgMed = måned.tilOgMed,
                                        beløp = nyLinje.beløp,
                                        sakstype = utbetaling.sakstype,
                                    ),
                                ),
                            )
                        }

                        is UtbetalingslinjePåTidslinje.Stans -> {
                            val diff = nyLinje.beløp - utbetaltLinje.beløp
                            val feilutbetaling = diff < 0 && erIFortiden(måned)

                            if (feilutbetaling) {
                                måned to SimulertUtbetaling(
                                    fagSystemId = utbetaling.saksnummer.toString(),
                                    utbetalesTilId = utbetaling.fnr,
                                    utbetalesTilNavn = "LYR MYGG",
                                    forfall = LocalDate.now(clock),
                                    feilkonto = feilutbetaling,
                                    detaljer = listOf(
                                        createTidligereUtbetalt(
                                            fraOgMed = måned.fraOgMed,
                                            tilOgMed = måned.tilOgMed,
                                            beløp = utbetaltLinje.beløp,
                                            sakstype = utbetaling.sakstype,
                                        ),
                                        createFeilutbetaling(
                                            fraOgMed = måned.fraOgMed,
                                            tilOgMed = måned.tilOgMed,
                                            beløp = diff,
                                            sakstype = utbetaling.sakstype,
                                        ),
                                    ),
                                )
                            } else {
                                måned to null
                            }
                        }
                    }
                } else {
                    måned to null
                }
            }
            .mapNotNull { (periode, simulertUtbetaling) ->
                if (simulertUtbetaling == null) {
                    null
                } else {
                    SimulertPeriode(
                        fraOgMed = periode.fraOgMed,
                        tilOgMed = periode.tilOgMed,
                        utbetaling = listOf(simulertUtbetaling),
                    )
                }
            }
            .toList()
            .let {
                Simulering(
                    gjelderId = utbetaling.fnr,
                    gjelderNavn = "MYGG LUR",
                    datoBeregnet = idag(clock),
                    nettoBeløp = it.calculateNetto(),
                    periodeList = it,
                )
            }
    }

    private fun erIFortiden(måned: Måned) = måned.tilOgMed < utbetalingerKjørtTilOgMed
}

private fun createOrdinær(fraOgMed: LocalDate, tilOgMed: LocalDate, beløp: Int, sakstype: Sakstype) = SimulertDetaljer(
    faktiskFraOgMed = fraOgMed,
    faktiskTilOgMed = tilOgMed,
    konto = "4952000",
    belop = beløp,
    tilbakeforing = false,
    sats = beløp,
    typeSats = "MND",
    antallSats = 1,
    uforegrad = 0,
    klassekode = sakstype.toYtelsekode(),
    klassekodeBeskrivelse = "Supplerende stønad $sakstype",
    klasseType = KlasseType.YTEL,
)

private fun createTidligereUtbetalt(fraOgMed: LocalDate, tilOgMed: LocalDate, beløp: Int, sakstype: Sakstype) =
    SimulertDetaljer(
        faktiskFraOgMed = fraOgMed,
        faktiskTilOgMed = tilOgMed,
        konto = "4952000",
        belop = -beløp,
        tilbakeforing = true,
        sats = 0,
        typeSats = "",
        antallSats = 0,
        uforegrad = 0,
        klassekode = sakstype.toYtelsekode(),
        klassekodeBeskrivelse = "Supplerende stønad $sakstype",
        klasseType = KlasseType.YTEL,
    )

private fun createFeilutbetaling(fraOgMed: LocalDate, tilOgMed: LocalDate, beløp: Int, sakstype: Sakstype) = SimulertDetaljer(
    faktiskFraOgMed = fraOgMed,
    faktiskTilOgMed = tilOgMed,
    konto = "4952000",
    belop = beløp,
    tilbakeforing = false,
    sats = 0,
    typeSats = "",
    antallSats = 0,
    uforegrad = 0,
    klassekode = sakstype.toFeilkode(),
    klassekodeBeskrivelse = "Feilutbetaling $sakstype",
    klasseType = KlasseType.FEIL,
)
