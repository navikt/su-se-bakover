package no.nav.su.se.bakover.client.stubs.oppdrag

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.periode.Periode
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
import java.time.LocalDate

object SimuleringStub : SimuleringClient {
    override fun simulerUtbetaling(utbetaling: Utbetaling): Either<SimuleringFeilet, Simulering> {
        return when (utbetaling.type) {
            Utbetaling.UtbetalingsType.NY -> simulerNyUtbetaling(utbetaling, utbetaling.saksnummer).right()
            Utbetaling.UtbetalingsType.STANS -> simulerIngenUtbetaling(utbetaling).right()
            Utbetaling.UtbetalingsType.GJENOPPTA -> simulerNyUtbetaling(utbetaling, utbetaling.saksnummer).right()
            Utbetaling.UtbetalingsType.OPPHØR -> simulerIngenUtbetaling(utbetaling).right()
        }
    }

    private fun simulerNyUtbetaling(utbetaling: Utbetaling, saksnummer: Saksnummer): Simulering {
        val perioder = utbetaling.utbetalingslinjer.flatMap { utbetalingslinje ->
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
                                    createYtelse(it.fraOgMed, it.tilOgMed, utbetalingslinje.beløp),
                                ),
                            ),
                        ),
                    )
                } else {
                    null
                }
            }
        }

        return Simulering(
            gjelderId = utbetaling.fnr,
            gjelderNavn = "MYGG LUR",
            datoBeregnet = idag(),
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
                fraOgMed = sisteUtbetalingslinje.statusendring.fraOgMed,
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
            datoBeregnet = idag(),
            nettoBeløp = 0,
            periodeList = listOf(simuleringsPeriode),
        )
    }

    private fun createYtelse(fraOgMed: LocalDate, tilOgMed: LocalDate, beløp: Int) = SimulertDetaljer(
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
}
