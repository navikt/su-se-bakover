package no.nav.su.se.bakover.domain.oppdrag.simulering

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.matchers.types.shouldBeInstanceOf
import no.nav.su.se.bakover.common.Rekkefølge
import no.nav.su.se.bakover.common.domain.tid.desember
import no.nav.su.se.bakover.common.domain.tid.februar
import no.nav.su.se.bakover.common.domain.tid.mai
import no.nav.su.se.bakover.common.domain.tid.zoneIdOslo
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.desember
import no.nav.su.se.bakover.common.tid.periode.november
import no.nav.su.se.bakover.common.tid.toTidspunkt
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.utbetaling.utbetalinger
import no.nav.su.se.bakover.test.utbetaling.utbetalingslinjeNy
import org.junit.jupiter.api.Test
import økonomi.domain.KlasseKode
import økonomi.domain.KlasseType
import økonomi.domain.simulering.ForskjellerMellomUtbetalingslinjeOgSimuleringsperiode
import økonomi.domain.simulering.Simulering
import økonomi.domain.simulering.SimulertDetaljer
import økonomi.domain.simulering.SimulertMåned
import økonomi.domain.simulering.SimulertUtbetaling
import økonomi.domain.utbetaling.TidslinjeForUtbetalinger
import kotlin.test.assertTrue

internal class KryssjekkSimuleringMotUtbetaling {

    private val fagsystemId = "2100"
    private val fnr = Fnr("12345678910")
    private val navn = "SNERK RAKRYGGET"
    private val konto = "123.123.123"
    private val typeSats = "MND"
    private val suBeskrivelse = "Supplerende stønad"

    @Test
    fun `Like perioder for simulering og beregning men ulike beløp skal gi kun ulikt beløp feil`() {
        val clock = TikkendeKlokke()
        val simMaaned = SimulertMåned(
            måned = desember(2025),
            utbetaling = SimulertUtbetaling(
                fagSystemId = fagsystemId,
                utbetalesTilId = fnr,
                utbetalesTilNavn = navn,
                forfall = 2.februar(2026),
                feilkonto = false,
                detaljer = listOf(
                    SimulertDetaljer(
                        faktiskFraOgMed = 1.desember(2025),
                        faktiskTilOgMed = 30.desember(2025),
                        konto = konto,
                        belop = 20779,
                        tilbakeforing = false,
                        sats = 20779,
                        typeSats = typeSats,
                        antallSats = 1,
                        uforegrad = 0,
                        klassekode = KlasseKode.SUALDER,
                        klassekodeBeskrivelse = suBeskrivelse,
                        klasseType = KlasseType.YTEL,
                    ),
                ),
            ),
        )
        val simulering = Simulering(
            gjelderId = Fnr.generer(),
            gjelderNavn = "tester",
            1.mai(2025),
            nettoBeløp = 12345,
            måneder = listOf(simMaaned),
            rawResponse = "SimuleringTest baserer ikke denne på rå XML.",
        )
        val rekkefølge = Rekkefølge.generator()
        val første = utbetalingslinjeNy(
            opprettet = 1.mai(2025).atStartOfDay(zoneIdOslo).toTidspunkt(),
            periode = desember(2025)..desember(2025),
            beløp = 1000,
            rekkefølge = rekkefølge.neste(),
        )

        val test = TidslinjeForUtbetalinger.fra(
            utbetalinger(
                clock,
                første,
            ),
        )

        val svar = sjekkUtbetalingMotSimulering(simulering, test!!)
        val feilklasse = svar.shouldBeLeft()
        assertTrue(feilklasse.size == 1)
        feilklasse.first().shouldBeInstanceOf<ForskjellerMellomUtbetalingslinjeOgSimuleringsperiode.UliktBeløp>()
    }

    @Test
    fun `Ulike perioder for simulering og beregning og like like beløp skal gi ulike perioder feil`() {
        /*
        beløpene her gir egentlig ikke mening for simuleringen men vi setter den til det samme som 2 månder * beløp 1000 for at det kun skal validere på den ene
         */
        val clock = TikkendeKlokke()
        val beløp = 1000
        val simMaaned = SimulertMåned(
            måned = desember(2025),
            utbetaling = SimulertUtbetaling(
                fagSystemId = fagsystemId,
                utbetalesTilId = fnr,
                utbetalesTilNavn = navn,
                forfall = 2.februar(2026),
                feilkonto = false,
                detaljer = listOf(
                    SimulertDetaljer(
                        faktiskFraOgMed = 1.desember(2025),
                        faktiskTilOgMed = 30.desember(2025),
                        konto = konto,
                        belop = beløp * 2,
                        tilbakeforing = false,
                        sats = beløp * 2,
                        typeSats = typeSats,
                        antallSats = 1,
                        uforegrad = 0,
                        klassekode = KlasseKode.SUALDER,
                        klassekodeBeskrivelse = suBeskrivelse,
                        klasseType = KlasseType.YTEL,
                    ),
                ),
            ),
        )
        val simulering = Simulering(
            gjelderId = Fnr.generer(),
            gjelderNavn = "tester",
            1.mai(2025),
            nettoBeløp = 12345,
            måneder = listOf(simMaaned),
            rawResponse = "SimuleringTest baserer ikke denne på rå XML.",
        )
        val rekkefølge = Rekkefølge.generator()
        val første = utbetalingslinjeNy(
            opprettet = 1.mai(2025).atStartOfDay(zoneIdOslo).toTidspunkt(),
            periode = november(2025)..desember(2025),
            beløp = beløp,
            rekkefølge = rekkefølge.neste(),
        )
        val test = TidslinjeForUtbetalinger.fra(
            utbetalinger(
                clock,
                første,
            ),
        )

        val svar = sjekkUtbetalingMotSimulering(simulering, test!!)
        val feilklasse = svar.shouldBeLeft()
        assertTrue(feilklasse.size == 1)
        feilklasse.first().shouldBeInstanceOf<ForskjellerMellomUtbetalingslinjeOgSimuleringsperiode.UlikPeriode>()
    }

    @Test
    fun `Ulike perioder for simulering og beregning og ulike beløp skal gi ulike perioder og ulike beløp feil`() {
        val clock = TikkendeKlokke()
        val simMaaned = SimulertMåned(
            måned = desember(2025),
            utbetaling = SimulertUtbetaling(
                fagSystemId = fagsystemId,
                utbetalesTilId = fnr,
                utbetalesTilNavn = navn,
                forfall = 2.februar(2026),
                feilkonto = false,
                detaljer = listOf(
                    SimulertDetaljer(
                        faktiskFraOgMed = 1.desember(2025),
                        faktiskTilOgMed = 30.desember(2025),
                        konto = konto,
                        belop = 20779,
                        tilbakeforing = false,
                        sats = 20779,
                        typeSats = typeSats,
                        antallSats = 1,
                        uforegrad = 0,
                        klassekode = KlasseKode.SUALDER,
                        klassekodeBeskrivelse = suBeskrivelse,
                        klasseType = KlasseType.YTEL,
                    ),
                ),
            ),
        )
        val simulering = Simulering(
            gjelderId = Fnr.generer(),
            gjelderNavn = "tester",
            1.mai(2025),
            nettoBeløp = 12345,
            måneder = listOf(simMaaned),
            rawResponse = "SimuleringTest baserer ikke denne på rå XML.",
        )
        val rekkefølge = Rekkefølge.generator()
        val første = utbetalingslinjeNy(
            opprettet = 1.mai(2025).atStartOfDay(zoneIdOslo).toTidspunkt(),
            periode = november(2025)..desember(2025),
            beløp = 1000,
            rekkefølge = rekkefølge.neste(),
        )
        val test = TidslinjeForUtbetalinger.fra(
            utbetalinger(
                clock,
                første,
            ),
        )

        val svar = sjekkUtbetalingMotSimulering(simulering, test!!)
        val feilklasse = svar.shouldBeLeft()
        assertTrue(feilklasse.size == 2)
        assertTrue { feilklasse.any { it is ForskjellerMellomUtbetalingslinjeOgSimuleringsperiode.UliktBeløp } }
        assertTrue { feilklasse.any { it is ForskjellerMellomUtbetalingslinjeOgSimuleringsperiode.UlikPeriode } }
    }
}
