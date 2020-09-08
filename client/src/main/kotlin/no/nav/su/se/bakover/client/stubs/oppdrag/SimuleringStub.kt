package no.nav.su.se.bakover.client.stubs.oppdrag

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.simulering.KlasseType
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringClient
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertDetaljer
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertPeriode
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertUtbetaling
import java.time.LocalDate
import java.time.Month
import java.time.Period

object SimuleringStub : SimuleringClient {
    override fun simulerUtbetaling(
        oppdrag: Oppdrag,
        utbetaling: Utbetaling,
        simuleringGjelder: Fnr
    ): Either<SimuleringFeilet, Simulering> {
        val months = 0L until Period.between(utbetaling.utbetalingslinjer.map { it.fom }.minOrNull()!!, utbetaling.utbetalingslinjer.map { it.tom }.maxOrNull()!!.plusDays(1)).toTotalMonths()
        val perioder = months.map {
            val fom = LocalDate.of(2020, Month.of((it + 1L).toInt()), 1)
            val tom = fom.plusMonths(1).minusDays(1)
            SimulertPeriode(
                fom = fom,
                tom = tom,
                utbetaling = listOf(
                    SimulertUtbetaling(
                        fagSystemId = UUID30.randomUUID().toString(),
                        feilkonto = false,
                        forfall = idag(),
                        utbetalesTilId = simuleringGjelder,
                        utbetalesTilNavn = "MYGG LUR",
                        detaljer = listOf(
                            createYtelse(fom, tom),
                            createForskuddsskatt(fom, tom)
                        )
                    )
                )
            )
        }

        return Simulering(
            gjelderId = simuleringGjelder,
            gjelderNavn = "MYGG LUR",
            datoBeregnet = idag(),
            nettoBeløp = perioder.sumBy { it.bruttoYtelse() / 2 },
            periodeList = perioder
        ).right()
    }

    private fun createYtelse(fom: LocalDate, tom: LocalDate) = SimulertDetaljer(
        faktiskFom = fom,
        faktiskTom = tom,
        konto = "4952000",
        belop = 20637,
        tilbakeforing = false,
        sats = 20637,
        typeSats = "MND",
        antallSats = 1,
        uforegrad = 0,
        klassekode = "SUUFORE",
        klassekodeBeskrivelse = "Supplerende stønad Uføre",
        klasseType = KlasseType.YTEL
    )

    private fun createForskuddsskatt(fom: LocalDate, tom: LocalDate) = SimulertDetaljer(
        faktiskFom = fom,
        faktiskTom = tom,
        konto = "0510000",
        belop = -10318,
        tilbakeforing = false,
        sats = 0,
        typeSats = "MND",
        antallSats = 31,
        uforegrad = 0,
        klassekode = "FSKTSKAT",
        klassekodeBeskrivelse = "Forskuddskatt",
        klasseType = KlasseType.SKAT
    )
}
