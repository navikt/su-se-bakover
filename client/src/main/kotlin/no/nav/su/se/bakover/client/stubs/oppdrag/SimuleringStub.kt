package no.nav.su.se.bakover.client.stubs.oppdrag

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.simulering.KlasseKode
import no.nav.su.se.bakover.domain.oppdrag.simulering.KlasseType
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringClient
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertDetaljer
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertPeriode
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertUtbetaling
import java.time.Clock
import java.time.LocalDate

class SimuleringStub(val clock: Clock) : SimuleringClient {

    override fun simulerUtbetaling(utbetaling: Utbetaling): Either<SimuleringFeilet, Simulering> {
        return when (utbetaling.type) {
            Utbetaling.UtbetalingsType.NY -> {
                simulerNyUtbetaling(utbetaling, utbetaling.saksnummer)
            }
            Utbetaling.UtbetalingsType.STANS -> {
                simulerIngenUtbetaling(utbetaling)
            }
            Utbetaling.UtbetalingsType.GJENOPPTA -> {
                simulerNyUtbetaling(utbetaling, utbetaling.saksnummer)
            }
            Utbetaling.UtbetalingsType.OPPHØR -> {
                simulerFeilutbetaling(utbetaling)
            }
        }.right()
    }

    private fun simulerNyUtbetaling(utbetaling: Utbetaling, saksnummer: Saksnummer): Simulering {
        val perioder = utbetaling.utbetalingslinjer.map { utbetalingslinje ->
            Periode.create(utbetalingslinje.fraOgMed, utbetalingslinje.tilOgMed).tilMånedsperioder().mapNotNull {
                if (utbetalingslinje.beløp > 0) {
                    SimulertPeriode(
                        fraOgMed = it.fraOgMed,
                        tilOgMed = it.tilOgMed,
                        utbetaling = listOf(
                            SimulertUtbetaling(
                                fagSystemId = saksnummer.toString(),
                                feilkonto = false,
                                forfall = it.tilOgMed,
                                utbetalesTilId = utbetaling.fnr,
                                utbetalesTilNavn = "MYGG LUR",
                                detaljer = listOf(
                                    createOrdinær(it.fraOgMed, it.tilOgMed, utbetalingslinje.beløp),
                                ),
                            ),
                        ),
                    )
                } else {
                    null
                }
            }
        }.flatten()

        return Simulering(
            gjelderId = utbetaling.fnr,
            gjelderNavn = "MYGG LUR",
            datoBeregnet = idag(clock = clock),
            nettoBeløp = perioder.calculateNetto(),
            periodeList = perioder,
        )
    }

    private fun List<SimulertPeriode>.calculateNetto() =
        this.sumOf { it.bruttoYtelse() } + this.sumOf { simulertPeriode ->
            simulertPeriode.utbetaling
                .flatMap { it.detaljer }
                .filter { !it.isYtelse() }
                .sumOf { it.belop }
        }

    private fun simulerIngenUtbetaling(utbetaling: Utbetaling): Simulering {
        val simuleringsPeriode = when (val sisteUtbetalingslinje = utbetaling.sisteUtbetalingslinje()) {
            is Utbetalingslinje.Endring -> SimulertPeriode(
                fraOgMed = sisteUtbetalingslinje.virkningstidspunkt,
                tilOgMed = utbetaling.senesteDato(),
                utbetaling = emptyList(),
            )
            else -> SimulertPeriode(
                fraOgMed = utbetaling.tidligsteDato(),
                tilOgMed = utbetaling.senesteDato(),
                utbetaling = emptyList(),
            )
        }

        return Simulering(
            gjelderId = utbetaling.fnr,
            gjelderNavn = "MYGG LUR",
            datoBeregnet = idag(clock),
            nettoBeløp = 0,
            periodeList = listOf(simuleringsPeriode),
        )
    }

    private fun simulerFeilutbetaling(utbetaling: Utbetaling): Simulering {
        return utbetaling.utbetalingslinjer.map {
            when (it) {
                is Utbetalingslinje.Endring -> {
                    Periode.create(it.virkningstidspunkt, it.tilOgMed)
                }
                is Utbetalingslinje.Ny -> {
                    Periode.create(it.fraOgMed, it.tilOgMed)
                }
            }
        }.let {
            Periode.create(it.minOf { it.fraOgMed }, it.maxOf { it.tilOgMed })
                .tilMånedsperioder()
                .mapNotNull {
                    if (it.tilOgMed < Tidspunkt.now(clock).toLocalDate(zoneIdOslo)) {
                        listOf(
                            createTidligereUtbetalt(it.fraOgMed, it.tilOgMed, utbetaling.sisteUtbetalingslinje().beløp),
                            createFeilutbetaling(it.fraOgMed, it.tilOgMed, utbetaling.sisteUtbetalingslinje().beløp),
                        )
                    } else {
                        null
                    }
                }
                .groupBy { Periode.create(it.first().faktiskFraOgMed, it.first().faktiskTilOgMed) }
                .map {
                    SimulertUtbetaling(
                        fagSystemId = utbetaling.saksnummer.toString(),
                        utbetalesTilId = utbetaling.fnr,
                        utbetalesTilNavn = "LYR MYGG",
                        forfall = LocalDate.now(clock),
                        feilkonto = true,
                        detaljer = it.value.flatMap { it },
                    )
                }
                .groupBy {
                    Periode.create(
                        it.detaljer.minOf { it.faktiskFraOgMed },
                        it.detaljer.maxOf { it.faktiskTilOgMed },
                    )
                }
                .map {
                    SimulertPeriode(
                        fraOgMed = it.key.fraOgMed,
                        tilOgMed = it.key.tilOgMed,
                        utbetaling = it.value,
                    )
                }
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
    }

    private fun createOrdinær(fraOgMed: LocalDate, tilOgMed: LocalDate, beløp: Int) = SimulertDetaljer(
        faktiskFraOgMed = fraOgMed,
        faktiskTilOgMed = tilOgMed,
        konto = "4952000",
        belop = beløp,
        tilbakeforing = false,
        sats = beløp,
        typeSats = "MND",
        antallSats = 1,
        uforegrad = 0,
        klassekode = KlasseKode.SUUFORE,
        klassekodeBeskrivelse = "Supplerende stønad Uføre",
        klasseType = KlasseType.YTEL,
    )

    private fun createTidligereUtbetalt(fraOgMed: LocalDate, tilOgMed: LocalDate, beløp: Int) = SimulertDetaljer(
        faktiskFraOgMed = fraOgMed,
        faktiskTilOgMed = tilOgMed,
        konto = "4952000",
        belop = -beløp,
        tilbakeforing = true,
        sats = 0,
        typeSats = "",
        antallSats = 0,
        uforegrad = 0,
        klassekode = KlasseKode.SUUFORE,
        klassekodeBeskrivelse = "suBeskrivelse",
        klasseType = KlasseType.YTEL,
    )

    private fun createFeilutbetaling(fraOgMed: LocalDate, tilOgMed: LocalDate, beløp: Int) = SimulertDetaljer(
        faktiskFraOgMed = fraOgMed,
        faktiskTilOgMed = tilOgMed,
        konto = "4952000",
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
}
