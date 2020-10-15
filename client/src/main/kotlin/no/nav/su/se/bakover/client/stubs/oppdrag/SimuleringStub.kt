package no.nav.su.se.bakover.client.stubs.oppdrag

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.between
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.domain.oppdrag.OversendelseTilOppdrag
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.simulering.KlasseType
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringClient
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertDetaljer
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertPeriode
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertUtbetaling
import java.time.LocalDate
import java.time.Month

object SimuleringStub : SimuleringClient {
    override fun simulerUtbetaling(tilSimulering: OversendelseTilOppdrag.NyUtbetaling): Either<SimuleringFeilet, Simulering> =
        when (tilSimulering.utbetaling.type) {
            Utbetaling.UtbetalingsType.NY -> simulerNyUtbetaling(tilSimulering.utbetaling, tilSimulering.oppdrag.id).right()
            Utbetaling.UtbetalingsType.STANS -> simulerStans(tilSimulering.utbetaling).right()
            Utbetaling.UtbetalingsType.GJENOPPTA -> simulerNyUtbetaling(tilSimulering.utbetaling, tilSimulering.oppdrag.id).right()
        }

    private fun simulerNyUtbetaling(utbetaling: Utbetaling, oppdragId: UUID30): Simulering {
        val months = utbetaling.tidligsteDato().monthValue..utbetaling.senesteDato().monthValue
        val perioder = months.map {
            val fraOgMed = LocalDate.of(utbetaling.tidligsteDato().year, Month.of((it)), 1)
            val tilOgMed = fraOgMed.plusMonths(1).minusDays(1)
            val beløp = utbetaling.utbetalingslinjer.findBeløpForDate(fraOgMed)
            SimulertPeriode(
                fraOgMed = fraOgMed,
                tilOgMed = tilOgMed,
                utbetaling = listOf(
                    SimulertUtbetaling(
                        fagSystemId = oppdragId.toString(),
                        feilkonto = false,
                        forfall = idag(),
                        utbetalesTilId = utbetaling.fnr,
                        utbetalesTilNavn = "MYGG LUR",
                        detaljer = listOf(
                            createYtelse(fraOgMed, tilOgMed, beløp),
                            createForskuddsskatt(fraOgMed, tilOgMed, beløp)
                        )
                    )
                )
            )
        }

        return Simulering(
            gjelderId = utbetaling.fnr,
            gjelderNavn = "MYGG LUR",
            datoBeregnet = idag(),
            nettoBeløp = perioder.calculateNetto(),
            periodeList = perioder
        )
    }

    private fun List<SimulertPeriode>.calculateNetto() = this.sumBy { it.bruttoYtelse() } + this.sumBy {
        it.utbetaling.flatMap { it.detaljer }.filter { !it.isYtelse() }.sumBy { it.belop }
    }

    private fun List<Utbetalingslinje>.findBeløpForDate(fraOgMed: LocalDate) =
        this.first() { fraOgMed.between(it.fraOgMed, it.tilOgMed) }.beløp

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
        belop = -(beløp * 0.25).toInt(),
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
