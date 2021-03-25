package no.nav.su.se.bakover.client.stubs.oppdrag

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.simulering.KlasseType
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringClient
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertDetaljer
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertPeriode
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertUtbetaling
import java.time.LocalDate
import kotlin.math.roundToInt

object SimuleringStub : SimuleringClient {
    override fun simulerUtbetaling(utbetaling: Utbetaling): Either<SimuleringFeilet, Simulering> {
        return when (utbetaling.type) {
            Utbetaling.UtbetalingsType.NY -> simulerNyUtbetaling(utbetaling, utbetaling.saksnummer).right()
            Utbetaling.UtbetalingsType.STANS -> simulerStans(utbetaling).right()
            Utbetaling.UtbetalingsType.GJENOPPTA -> simulerNyUtbetaling(utbetaling, utbetaling.saksnummer).right()
        }
    }

    private fun simulerNyUtbetaling(utbetaling: Utbetaling, saksnummer: Saksnummer): Simulering {
        val perioder = utbetaling.utbetalingslinjer.flatMap { utbetalingslinje ->
            Periode.create(utbetalingslinje.fraOgMed, utbetalingslinje.tilOgMed).tilMånedsperioder().mapNotNull {
                if (utbetalingslinje.beløp > 0) {
                    SimulertPeriode(
                        fraOgMed = it.getFraOgMed(),
                        tilOgMed = it.getTilOgMed(),
                        utbetaling = listOf(
                            SimulertUtbetaling(
                                fagSystemId = saksnummer.toString(),
                                feilkonto = false,
                                forfall = it.getTilOgMed(),
                                utbetalesTilId = utbetaling.fnr,
                                utbetalesTilNavn = "MYGG LUR",
                                detaljer = listOf(
                                    createYtelse(it.getFraOgMed(), it.getTilOgMed(), utbetalingslinje.beløp),
                                    createForskuddsskatt(it.getFraOgMed(), it.getTilOgMed(), utbetalingslinje.beløp)
                                )
                            )
                        )
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
            periodeList = perioder
        )
    }

    private fun List<SimulertPeriode>.calculateNetto() =
        this.sumBy { it.bruttoYtelse() } + this.sumBy { simulertPeriode ->
            simulertPeriode.utbetaling
                .flatMap { it.detaljer }
                .filter { !it.isYtelse() }
                .sumBy { it.belop }
        }

    private fun simulerStans(utbetaling: Utbetaling): Simulering {
        return Simulering(
            gjelderId = utbetaling.fnr,
            gjelderNavn = "MYGG LUR",
            datoBeregnet = idag(),
            nettoBeløp = 0,
            periodeList = listOf(
                SimulertPeriode(
                    fraOgMed = utbetaling.tidligsteDato(),
                    tilOgMed = utbetaling.senesteDato(),
                    utbetaling = emptyList()
                )
            )
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
        klassekode = "SUUFORE",
        klassekodeBeskrivelse = "Supplerende stønad Uføre",
        klasseType = KlasseType.YTEL
    )

    private fun createForskuddsskatt(fraOgMed: LocalDate, tilOgMed: LocalDate, beløp: Int) = SimulertDetaljer(
        faktiskFraOgMed = fraOgMed,
        faktiskTilOgMed = tilOgMed,
        konto = "0510000",
        belop = -(beløp * 0.25).roundToInt(),
        tilbakeforing = false,
        sats = 1,
        typeSats = "MND",
        antallSats = 31,
        uforegrad = 0,
        klassekode = "FSKTSKAT",
        klassekodeBeskrivelse = "Forskuddskatt",
        klasseType = KlasseType.SKAT
    )
}
