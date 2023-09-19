package no.nav.su.se.bakover.client.stubs.oppdrag

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.right
import no.nav.su.se.bakover.common.extensions.idag
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulerUtbetalingRequest
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringClient
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertDetaljer
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertMåned
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertUtbetaling
import no.nav.su.se.bakover.domain.oppdrag.simulering.toYtelsekode
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.TidslinjeForUtbetalinger
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingRepo
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingslinjePåTidslinje
import no.nav.su.se.bakover.domain.sak.Sakstype
import økonomi.domain.KlasseKode
import økonomi.domain.KlasseType
import java.time.Clock
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

class SimuleringStub(
    val clock: Clock,
    val utbetalingerKjørtTilOgMed: (clock: Clock) -> LocalDate = { LocalDate.now(it) },
    val utbetalingRepo: UtbetalingRepo,
) : SimuleringClient {

    override fun simulerUtbetaling(request: SimulerUtbetalingRequest): Either<SimuleringFeilet, Simulering> {
        return simulerUtbetalinger(request).right()
    }

    private fun simulerUtbetalinger(request: SimulerUtbetalingRequest): Simulering {
        val utbetaling = request.utbetaling
        val simuleringsperiode = request.simuleringsperiode
        val eksisterendeUtbetalinger =
            utbetalingRepo.hentOversendteUtbetalinger(
                sakId = utbetaling.sakId,
                // Den spammer veldig ved kjøring lokalt/test. Siden vi ikke får tak i den databasesesjonen, disabler vi den for denne stubben.
                disableSessionCounter = true,
            )

        val tidslinjeEksisterendeUtbetalinger = eksisterendeUtbetalinger.tidslinje()
        val tidslinjeEksisterendeOgNy: TidslinjeForUtbetalinger by lazy {
            (eksisterendeUtbetalinger + utbetaling).tidslinje().getOrElse {
                throw RuntimeException("Kunne ikke lage tidslinje fra eksisterende $eksisterendeUtbetalinger og ny utbetaling: $utbetaling")
            }.krympTilPeriode(simuleringsperiode)!!
        }

        /**
         * Reaktiveringen vil bare bære med seg 1 beløp for utbetaling. Dersom vi reaktiverer over perioder med flere
         * endringer i ytelse må vi finne fram til det faktiske beløpet for hver enkelt periode. Lager en midlertidig
         * tidslinje som inkuderer utbetalingen som simuleres slik at vi kan delegere denne jobben til
         * [no.nav.su.se.bakover.domain.oppdrag.utbetaling.TidslinjeForUtbetalinger].
         */
        fun finnBeløpVedReaktivering(måned: Måned): Int {
            return tidslinjeEksisterendeOgNy.gjeldendeForDato(måned.fraOgMed)!!.beløp
        }

        fun linjeForMåned(måned: Måned): Utbetalingslinje {
            return utbetaling.utbetalingslinjer.last { it.periode inneholder måned }
        }

        return simuleringsperiode.måneder()
            .asSequence()
            .map { måned ->
                // Vil være null dersom alder
                val uføregrad: Int = linjeForMåned(måned).uføregrad?.value ?: 0
                val utbetaltLinje = tidslinjeEksisterendeUtbetalinger.fold(
                    { null },
                    { it.gjeldendeForDato(måned.fraOgMed) },
                )
                val nyLinje = tidslinjeEksisterendeOgNy.gjeldendeForDato(måned.fraOgMed)
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
                                        uføregrad = uføregrad,
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
                                        uføregrad = uføregrad,
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
                                            createTidligereUtbetaltKredit(
                                                fraOgMed = måned.fraOgMed,
                                                tilOgMed = måned.tilOgMed,
                                                beløp = utbetaltLinje.beløp,
                                                sakstype = utbetaling.sakstype,
                                                uføregrad = uføregrad,
                                            ),
                                            createOrdinær(
                                                fraOgMed = måned.fraOgMed,
                                                tilOgMed = måned.tilOgMed,
                                                beløp = nyLinje.beløp,
                                                sakstype = utbetaling.sakstype,
                                                uføregrad = uføregrad,
                                            ),
                                            createDebetFeilutbetaling(
                                                fraOgMed = måned.fraOgMed,
                                                tilOgMed = måned.tilOgMed,
                                                beløp = abs(diff),
                                                sakstype = utbetaling.sakstype,
                                            ),
                                            createFeilutbetaling(
                                                fraOgMed = måned.fraOgMed,
                                                tilOgMed = måned.tilOgMed,
                                                beløp = abs(diff),
                                            ),
                                            createMotpostFeilkonto(
                                                fraOgMed = måned.fraOgMed,
                                                tilOgMed = måned.tilOgMed,
                                                beløp = abs(diff),
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
                                            createTidligereUtbetaltKredit(
                                                fraOgMed = måned.fraOgMed,
                                                tilOgMed = måned.tilOgMed,
                                                beløp = utbetaltLinje.beløp,
                                                sakstype = utbetaling.sakstype,
                                                uføregrad = uføregrad,
                                            ),
                                            createOrdinær(
                                                fraOgMed = måned.fraOgMed,
                                                tilOgMed = måned.tilOgMed,
                                                beløp = nyLinje.beløp,
                                                sakstype = utbetaling.sakstype,
                                                uføregrad = uføregrad,
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
                                                uføregrad = uføregrad,
                                            ),
                                            createTidligereUtbetaltKredit(
                                                fraOgMed = måned.fraOgMed,
                                                tilOgMed = måned.tilOgMed,
                                                beløp = utbetaltLinje.beløp,
                                                sakstype = utbetaling.sakstype,
                                                uføregrad = uføregrad,
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
                                                uføregrad = uføregrad,
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
                                    feilkonto = true,
                                    detaljer = listOf(
                                        createDebetFeilutbetaling(
                                            fraOgMed = måned.fraOgMed,
                                            tilOgMed = måned.tilOgMed,
                                            beløp = utbetaltLinje.beløp,
                                            sakstype = utbetaling.sakstype,
                                        ),
                                        createTidligereUtbetaltKredit(
                                            fraOgMed = måned.fraOgMed,
                                            tilOgMed = måned.tilOgMed,
                                            beløp = utbetaltLinje.beløp,
                                            sakstype = utbetaling.sakstype,
                                            uføregrad = uføregrad,
                                        ),
                                        createFeilutbetaling(
                                            fraOgMed = måned.fraOgMed,
                                            tilOgMed = måned.tilOgMed,
                                            beløp = abs(diff),
                                        ),
                                        createMotpostFeilkonto(
                                            fraOgMed = måned.fraOgMed,
                                            tilOgMed = måned.tilOgMed,
                                            beløp = abs(diff),
                                        ),
                                    ),
                                )
                            } else {
                                måned to null
                            }
                        }

                        is UtbetalingslinjePåTidslinje.Reaktivering -> {
                            val diff = nyLinje.beløp - utbetaltLinje.beløp
                            val feilutbetaling = diff < 0 && erIFortiden(måned)
                            if (feilutbetaling) {
                                throw IllegalStateException("Feilutbetaling ved reaktivering er ikke støttet og skal vel strengt tatt ikke være mulig.")
                            }
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
                                        uføregrad = uføregrad,
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
                                    feilkonto = true,
                                    detaljer = listOf(
                                        createTidligereUtbetaltKredit(
                                            fraOgMed = måned.fraOgMed,
                                            tilOgMed = måned.tilOgMed,
                                            beløp = abs(diff),
                                            sakstype = utbetaling.sakstype,
                                            uføregrad = uføregrad,
                                        ),
                                        createDebetFeilutbetaling(
                                            fraOgMed = måned.fraOgMed,
                                            tilOgMed = måned.tilOgMed,
                                            beløp = utbetaltLinje.beløp,
                                            sakstype = utbetaling.sakstype,
                                        ),
                                        createFeilutbetaling(
                                            fraOgMed = måned.fraOgMed,
                                            tilOgMed = måned.tilOgMed,
                                            beløp = abs(diff),
                                        ),
                                        createMotpostFeilkonto(
                                            fraOgMed = måned.fraOgMed,
                                            tilOgMed = måned.tilOgMed,
                                            beløp = abs(diff),
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
            .mapNotNull { (måned, simulertUtbetaling) ->
                if (simulertUtbetaling == null) {
                    // TODO jah: Dette kan føre til at vi får hull, men samtidig brekker det andre ting hvis vi ikke gjør det.
                    null
                } else {
                    SimulertMåned(
                        måned = måned,
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
                    måneder = it.ifEmpty {
                        SimulertMåned.create(simuleringsperiode)
                    },
                    rawResponse = "SimuleringStub forholder seg ikke til XML, men oppretter domenetypene direkte",
                ).let { simulering ->
                    /**
                     * Setter bare netto til halvparten av brutto for at det skal oppføre seg ca som OS.
                     * Eventuell skatt som trekkes fra brutto filtreres ut i [no.nav.su.se.bakover.client.oppdrag.simulering.SimuleringResponseMapper]
                     */
                    simulering.copy(nettoBeløp = (simulering.hentTilUtbetaling().sum() * 0.5).roundToInt())
                }
            }
    }

    private fun erIFortiden(måned: Måned) = måned.tilOgMed < utbetalingerKjørtTilOgMed(clock)
}

/**
 * Dette skal tilsvare beløpet vi sender over til oppdrag. Dersom det er et opphør (OPPH) eller man sender beløpet 0 til oppdrag, vil dette innslaget mangle i simuleringen.
 *
 * Se også: SimuleringTestData - Stoppnivå.Periode.ordinær(...)
 */
private fun createOrdinær(
    fraOgMed: LocalDate,
    tilOgMed: LocalDate,
    beløp: Int,
    sakstype: Sakstype,
    uføregrad: Int,
): SimulertDetaljer {
    // F.eks. ved ny stønadsperiode med avkorting vil vi sende måneder med 0. I teorien kunne vi skippet sende disse.
    require(beløp >= 0) { "For SimuleringStub, opererer vi med større eller lik 0; det vi har sendt til oppdrag" }
    return SimulertDetaljer(
        faktiskFraOgMed = fraOgMed,
        faktiskTilOgMed = tilOgMed,
        konto = "4952000",
        belop = beløp,
        tilbakeforing = false,
        sats = beløp,
        typeSats = "MND",
        antallSats = 1,
        uforegrad = uføregrad,
        klassekode = sakstype.toYtelsekode(),
        klassekodeBeskrivelse = when (sakstype) {
            Sakstype.ALDER -> "Supplerende stønad Alder"
            Sakstype.UFØRE -> "Supplerende stønad Uføre"
        },
        klasseType = KlasseType.YTEL,
    )
}

/**
 * Også kalt tilbakeføring.
 * Denne dukker opp dersom det har blitt utbetalt et beløp før.
 * Dette vil da blir trukket fra, før det nye beløpet legges til (så lenge ikke det nye beløpet er 0).
 *
 * Obs: Det finnes tilfeller der en feilutbetaling vil bli returnert fra UR til OS (da vil vi få en debet tilbakeføring som utligner denne).
 *
 * Se også: SimuleringTestData - Stoppnivå.Periode.tidligereUtbetalt(...)
 */
private fun createTidligereUtbetaltKredit(
    fraOgMed: LocalDate,
    tilOgMed: LocalDate,
    beløp: Int,
    sakstype: Sakstype,
    uføregrad: Int,
): SimulertDetaljer {
    require(beløp > 0) { "For SimuleringStub, opererer vi med større enn 0; det vi har sendt til oppdrag" }
    return SimulertDetaljer(
        faktiskFraOgMed = fraOgMed,
        faktiskTilOgMed = tilOgMed,
        konto = "4952000",
        belop = -beløp,
        tilbakeforing = true,
        sats = beløp,
        typeSats = "MND",
        antallSats = 0,
        uforegrad = uføregrad,
        klassekode = sakstype.toYtelsekode(),
        klassekodeBeskrivelse = when (sakstype) {
            Sakstype.ALDER -> "Supplerende stønad Alder"
            Sakstype.UFØRE -> "Supplerende stønad Uføre"
        },
        klasseType = KlasseType.YTEL,
    )
}

/**
 * Vil være samme beløp som KL_KODE_FEIL_INNT og motsatt av TBMOTOBS.
 * Dukker kun og alltid opp ved feilutbetaling.
 *
 * Se også: SimuleringTestData - Stoppnivå.Periode.debetFeilutbetaling(...)
 */
private fun createDebetFeilutbetaling(
    fraOgMed: LocalDate,
    tilOgMed: LocalDate,
    beløp: Int,
    sakstype: Sakstype,
): SimulertDetaljer {
    require(beløp > 0) { "For SimuleringStub, opererer vi med større enn 0; det vi har sendt til oppdrag" }
    return SimulertDetaljer(
        faktiskFraOgMed = fraOgMed,
        faktiskTilOgMed = tilOgMed,
        konto = "4952000",
        belop = beløp,
        tilbakeforing = false,
        sats = 0,
        typeSats = "",
        antallSats = 0,
        // Denne er alltid 0 for denne posteringstypen.
        uforegrad = 0,
        klassekode = sakstype.toYtelsekode(),
        klassekodeBeskrivelse = when (sakstype) {
            Sakstype.ALDER -> "Supplerende stønad Alder"
            Sakstype.UFØRE -> "Supplerende stønad Uføre"
        },
        klasseType = KlasseType.YTEL,
    )
}

/**
 * Dette skjer kun ved feilutbetaling, ved nedjustering av tidligere utbetalt beløp. Inkl. nedjustert til 0 (opphør)
 *
 * Se også: SimuleringTestData - Stoppnivå.Periode.feilutbetaling(...)
 */
private fun createFeilutbetaling(
    fraOgMed: LocalDate,
    tilOgMed: LocalDate,
    beløp: Int,
): SimulertDetaljer {
    require(beløp > 0) { "For SimuleringStub, opererer vi med større enn 0; det vi har sendt til oppdrag, men var: $beløp" }
    return SimulertDetaljer(
        faktiskFraOgMed = fraOgMed,
        faktiskTilOgMed = tilOgMed,
        konto = "0630986",
        belop = beløp,
        tilbakeforing = false,
        sats = 0,
        typeSats = "",
        antallSats = 0,
        // Denne er alltid 0 for denne posteringstypen.
        uforegrad = 0,
        klassekode = KlasseKode.KL_KODE_FEIL_INNT,
        klassekodeBeskrivelse = "Feilutbetaling Inntektsytelser",
        klasseType = KlasseType.FEIL,
    )
}

/**
 * Beløp vil være motsatt av KL_KODE_FEIL_INNT.
 * Dukker kun og alltid opp ved feilutbetaling.
 *
 * Se også: SimuleringTestData - Stoppnivå.Periode.motposteringskonto(...)
 */
private fun createMotpostFeilkonto(
    fraOgMed: LocalDate,
    tilOgMed: LocalDate,
    beløp: Int,
): SimulertDetaljer {
    require(beløp > 0) { "For SimuleringStub, opererer vi med større enn 0; det vi har sendt til oppdrag" }
    return SimulertDetaljer(
        faktiskFraOgMed = fraOgMed,
        faktiskTilOgMed = tilOgMed,
        konto = "0902900",
        belop = -beløp,
        tilbakeforing = false,
        sats = 0,
        typeSats = "",
        antallSats = 0,
        // Denne er alltid 0 for denne posteringstypen.
        uforegrad = 0,
        klassekode = KlasseKode.TBMOTOBS,
        klassekodeBeskrivelse = "Feilutbetaling motkonto til OBS konto",
        klasseType = KlasseType.MOTP,
    )
}
