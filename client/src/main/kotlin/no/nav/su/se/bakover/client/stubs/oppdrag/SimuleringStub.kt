package no.nav.su.se.bakover.client.stubs.oppdrag

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.domain.oppdrag.NyUtbetaling
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
        nyUtbetaling: NyUtbetaling
    ): Either<SimuleringFeilet, Simulering> {
        val (_, utbetaling) = nyUtbetaling
        val months = 0L until Period.between(
            utbetaling.utbetalingslinjer.map { it.fraOgMed }.minOrNull()!!,
            utbetaling.utbetalingslinjer.map { it.tilOgMed }.maxOrNull()!!.plusDays(1)
        ).toTotalMonths()
        val perioder = months.map {
            val fraOgMed = LocalDate.of(2020, Month.of((it + 1L).toInt()), 1)
            val tilOgMed = fraOgMed.plusMonths(1).minusDays(1)
            SimulertPeriode(
                fraOgMed = fraOgMed,
                tilOgMed = tilOgMed,
                utbetaling = listOf(
                    SimulertUtbetaling(
                        fagSystemId = UUID30.randomUUID().toString(),
                        feilkonto = false,
                        forfall = idag(),
                        utbetalesTilId = utbetaling.fnr,
                        utbetalesTilNavn = "MYGG LUR",
                        detaljer = listOf(
                            createYtelse(fraOgMed, tilOgMed),
                            createForskuddsskatt(fraOgMed, tilOgMed)
                        )
                    )
                )
            )
        }

        return Simulering(
            gjelderId = utbetaling.fnr,
            gjelderNavn = "MYGG LUR",
            datoBeregnet = idag(),
            nettoBeløp = perioder.sumBy { it.bruttoYtelse() / 2 },
            periodeList = perioder
        ).right()
    }

    private fun createYtelse(fraOgMed: LocalDate, tilOgMed: LocalDate) = SimulertDetaljer(
        faktiskFraOgMed = fraOgMed,
        faktiskTilOgMed = tilOgMed,
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

    private fun createForskuddsskatt(fraOgMed: LocalDate, tilOgMed: LocalDate) = SimulertDetaljer(
        faktiskFraOgMed = fraOgMed,
        faktiskTilOgMed = tilOgMed,
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
