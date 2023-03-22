package no.nav.su.se.bakover.client.stubs.oppdrag

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.right
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.periode.Måned
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingslinjePåTidslinje
import no.nav.su.se.bakover.domain.oppdrag.simulering.KlasseKode
import no.nav.su.se.bakover.domain.oppdrag.simulering.KlasseType
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulerUtbetalingRequest
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringClient
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertDetaljer
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertPeriode
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertUtbetaling
import no.nav.su.se.bakover.domain.oppdrag.simulering.toYtelsekode
import no.nav.su.se.bakover.domain.oppdrag.tidslinje
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingRepo
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.tidslinje.TidslinjeForUtbetalinger
import java.time.Clock
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

class SimuleringStub(
    val clock: Clock,
    val utbetalingerKjørtTilOgMed: LocalDate = LocalDate.now(clock),
    val utbetalingRepo: UtbetalingRepo,
) : SimuleringClient {

    override fun simulerUtbetaling(request: SimulerUtbetalingRequest): Either<SimuleringFeilet, Simulering> {
        return simulerUtbetalinger(request).right()
    }
    private fun simulerUtbetalinger(request: SimulerUtbetalingRequest): Simulering {
        val utbetaling = request.utbetaling
        val simuleringsperiode = request.simuleringsperiode
        val oversendteUtbetalinger = utbetalingRepo.hentOversendteUtbetalinger(utbetaling.sakId)

        val tidslinjeEksisterendeUtbetalinger = oversendteUtbetalinger.tidslinje()
        val tidslinjeNyUtbetaling = utbetaling.utbetalingslinjer.tidslinje().getOrElse {
            throw RuntimeException("Kunne ikke lage tidslinje fra ny utbetaling: $utbetaling")
        }
        val tidslinjeEksisterendeOgNy: TidslinjeForUtbetalinger by lazy {
            (oversendteUtbetalinger + utbetaling).tidslinje().getOrElse {
                throw RuntimeException("Kunne ikke lage tidslinje fra eksisterende $oversendteUtbetalinger og ny utbetaling: $utbetaling")
            }
        }

        /**
         * Reaktiveringen vil bare bære med seg 1 beløp for utbetaling. Dersom vi reaktiverer over perioder med flere
         * endringer i ytelse må vi finne fram til det faktiske beløpet for hver enkelt periode. Lager en midlertidig
         * tidslinje som inkuderer utbetalingen som simuleres slik at vi kan delegere denne jobben til
         * [no.nav.su.se.bakover.domain.tidslinje.TidslinjeForUtbetalinger].
         */
        fun finnBeløpVedReaktivering(måned: Måned): Int {
            return tidslinjeEksisterendeOgNy.gjeldendeForDato(måned.fraOgMed)!!.beløp
        }

        return simuleringsperiode.måneder()
            .asSequence()
            .map { måned ->
                val utbetaltLinje = tidslinjeEksisterendeUtbetalinger.fold(
                    { null },
                    { it.gjeldendeForDato(måned.fraOgMed) },
                )
                val nyLinje = tidslinjeNyUtbetaling.gjeldendeForDato(måned.fraOgMed)
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
                                        beløp = finnBeløpVedReaktivering(måned),
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
                                            createOrdinær(
                                                fraOgMed = måned.fraOgMed,
                                                tilOgMed = måned.tilOgMed,
                                                beløp = abs(diff),
                                                sakstype = utbetaling.sakstype,
                                            ),
                                            createFeilutbetaling(
                                                fraOgMed = måned.fraOgMed,
                                                tilOgMed = måned.tilOgMed,
                                                beløp = diff,
                                                sakstype = utbetaling.sakstype,
                                            ),
                                            createMotpostFeilkonto(
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
                                        createOrdinær(
                                            fraOgMed = måned.fraOgMed,
                                            tilOgMed = måned.tilOgMed,
                                            beløp = abs(diff),
                                            sakstype = utbetaling.sakstype,
                                        ),
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
                                        createMotpostFeilkonto(
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
                                        beløp = finnBeløpVedReaktivering(måned),
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
                                        createOrdinær(
                                            fraOgMed = måned.fraOgMed,
                                            tilOgMed = måned.tilOgMed,
                                            beløp = abs(diff),
                                            sakstype = utbetaling.sakstype,
                                        ),
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
                                        createMotpostFeilkonto(
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
                        utbetaling = simulertUtbetaling,
                    )
                }
            }
            .toList()
            .let {
                Simulering(
                    gjelderId = utbetaling.fnr,
                    gjelderNavn = "MYGG LUR",
                    datoBeregnet = idag(clock),
                    nettoBeløp = 0,
                    periodeList = it.ifEmpty {
                        listOf(
                            SimulertPeriode(
                                fraOgMed = simuleringsperiode.fraOgMed,
                                tilOgMed = simuleringsperiode.tilOgMed,
                                utbetaling = null,
                            ),
                        )
                    },
                    rawXml = "SimuleringStub forholder seg ikke til XML, men oppretter domenetypene direkte",
                ).let { simulering ->
                    /**
                     * Setter bare netto til halvparten av brutto for at det skal oppføre seg ca som OS.
                     * Eventuell skatt som trekkes fra brutto filtreres ut i [no.nav.su.se.bakover.client.oppdrag.simulering.SimuleringResponseMapper]
                     */
                    simulering.copy(nettoBeløp = (simulering.hentTilUtbetaling().sum() * 0.5).roundToInt())
                }
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
    belop = abs(beløp),
    tilbakeforing = false,
    sats = 0,
    typeSats = "",
    antallSats = 0,
    uforegrad = 0,
    klassekode = KlasseKode.KL_KODE_FEIL_INNT,
    klassekodeBeskrivelse = "Feilutbetaling $sakstype",
    klasseType = KlasseType.FEIL,
)

private fun createMotpostFeilkonto(fraOgMed: LocalDate, tilOgMed: LocalDate, beløp: Int, sakstype: Sakstype) = SimulertDetaljer(
    faktiskFraOgMed = fraOgMed,
    faktiskTilOgMed = tilOgMed,
    konto = "4952000",
    belop = -abs(beløp),
    tilbakeforing = false,
    sats = 0,
    typeSats = "",
    antallSats = 0,
    uforegrad = 0,
    klassekode = KlasseKode.TBMOTOBS,
    klassekodeBeskrivelse = "Motpost feilkonto $sakstype",
    klasseType = KlasseType.MOTP,
)
