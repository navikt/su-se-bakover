package no.nav.su.se.bakover.domain.oppdrag.simulering

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.types.shouldBeInstanceOf
import no.nav.su.se.bakover.common.Rekkefølge
import no.nav.su.se.bakover.common.domain.tid.april
import no.nav.su.se.bakover.common.domain.tid.desember
import no.nav.su.se.bakover.common.domain.tid.februar
import no.nav.su.se.bakover.common.domain.tid.juli
import no.nav.su.se.bakover.common.domain.tid.juni
import no.nav.su.se.bakover.common.domain.tid.mai
import no.nav.su.se.bakover.common.domain.tid.september
import no.nav.su.se.bakover.common.domain.tid.zoneIdOslo
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.april
import no.nav.su.se.bakover.common.tid.periode.desember
import no.nav.su.se.bakover.common.tid.periode.juni
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.common.tid.periode.november
import no.nav.su.se.bakover.common.tid.periode.oktober
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
import java.time.LocalDate
import java.time.Month
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class KryssjekkSimuleringMotUtbetalingTest {

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
        assertEquals(feilklasse.size, 1)
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
        assertEquals(feilklasse.size, 1)
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
        assertEquals(feilklasse.size, 2)
        assertTrue { feilklasse.any { it is ForskjellerMellomUtbetalingslinjeOgSimuleringsperiode.UliktBeløp } }
        assertTrue { feilklasse.any { it is ForskjellerMellomUtbetalingslinjeOgSimuleringsperiode.UlikPeriode } }
    }

    @Test
    fun `Skal ikke sammenligne 0 utbetalinger(linje) mot simuleringer hvis opphør og opphøret er frem i tid`() {
        val clock = TikkendeKlokke()

        val simuleringsÅr = 2025
        val naa = LocalDate.of(simuleringsÅr, Month.SEPTEMBER, 1)
        val juni = SimulertMåned(
            måned = no.nav.su.se.bakover.common.tid.periode.juni(simuleringsÅr),
            utbetaling = SimulertUtbetaling(
                fagSystemId = fagsystemId,
                utbetalesTilId = fnr,
                utbetalesTilNavn = navn,
                forfall = 2.februar(2026),
                feilkonto = false,
                detaljer = listOf(
                    SimulertDetaljer(
                        faktiskFraOgMed = 1.juni(simuleringsÅr),
                        faktiskTilOgMed = 30.juni(simuleringsÅr),
                        konto = konto,
                        belop = 0,
                        tilbakeforing = false,
                        sats = 0,
                        typeSats = typeSats,
                        antallSats = 0,
                        uforegrad = 0,
                        klassekode = KlasseKode.SUALDER,
                        klassekodeBeskrivelse = suBeskrivelse,
                        klasseType = KlasseType.YTEL,
                    ),
                ),
            ),
        )

        val julisim = SimulertMåned(
            måned = no.nav.su.se.bakover.common.tid.periode.juli(simuleringsÅr),
            utbetaling = SimulertUtbetaling(
                fagSystemId = fagsystemId,
                utbetalesTilId = fnr,
                utbetalesTilNavn = navn,
                forfall = 2.februar(2026),
                feilkonto = false,
                detaljer = listOf(
                    SimulertDetaljer(
                        faktiskFraOgMed = 1.juli(simuleringsÅr),
                        faktiskTilOgMed = 30.juli(simuleringsÅr),
                        konto = konto,
                        belop = 0,
                        tilbakeforing = false,
                        sats = 0,
                        typeSats = typeSats,
                        antallSats = 0,
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
            1.september(simuleringsÅr),
            nettoBeløp = 0,
            måneder = listOf(juni, julisim),
            rawResponse = "SimuleringTest baserer ikke denne på rå XML.",
        )
        val rekkefølge = Rekkefølge.generator()
        val første = utbetalingslinjeNy(
            opprettet = 1.september(simuleringsÅr).atStartOfDay(zoneIdOslo).toTidspunkt(),
            periode = no.nav.su.se.bakover.common.tid.periode.juni(simuleringsÅr)..oktober(simuleringsÅr),
            beløp = 0, // ?
            rekkefølge = rekkefølge.neste(),
        )
        val test = TidslinjeForUtbetalinger.fra(
            utbetalinger(
                clock,
                første,
            ),
        )

        val svar = sjekkUtbetalingMotSimulering(simulering, test!!, erOpphør = true, naa = naa)
        svar.shouldBeRight()
    }

    @Test
    fun `Skal ikke feile når opphør frem i tid mangler simulering for månedene etter linjen`() {
        val clock = TikkendeKlokke()
        val simuleringsÅr = 2025
        val naa = LocalDate.of(simuleringsÅr, Month.SEPTEMBER, 1)

        // Simuleringsdata: kun juni og juli (første måneder i linjen), resten mangler
        val juni = SimulertMåned(
            måned = no.nav.su.se.bakover.common.tid.periode.juni(simuleringsÅr),
            utbetaling = SimulertUtbetaling(
                fagSystemId = fagsystemId,
                utbetalesTilId = fnr,
                utbetalesTilNavn = navn,
                forfall = 2.februar(2026),
                feilkonto = false,
                detaljer = listOf(
                    SimulertDetaljer(
                        faktiskFraOgMed = 1.juni(simuleringsÅr),
                        faktiskTilOgMed = 30.juni(simuleringsÅr),
                        konto = konto,
                        belop = 0,
                        tilbakeforing = false,
                        sats = 0,
                        typeSats = typeSats,
                        antallSats = 0,
                        uforegrad = 0,
                        klassekode = KlasseKode.SUALDER,
                        klassekodeBeskrivelse = suBeskrivelse,
                        klasseType = KlasseType.YTEL,
                    ),
                ),
            ),
        )

        val julisim = SimulertMåned(
            måned = no.nav.su.se.bakover.common.tid.periode.juli(simuleringsÅr),
            utbetaling = SimulertUtbetaling(
                fagSystemId = fagsystemId,
                utbetalesTilId = fnr,
                utbetalesTilNavn = navn,
                forfall = 2.februar(2026),
                feilkonto = false,
                detaljer = listOf(
                    SimulertDetaljer(
                        faktiskFraOgMed = 1.juli(simuleringsÅr),
                        faktiskTilOgMed = 31.juli(simuleringsÅr),
                        konto = konto,
                        belop = 0,
                        tilbakeforing = false,
                        sats = 0,
                        typeSats = typeSats,
                        antallSats = 0,
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
            datoBeregnet = 1.september(simuleringsÅr),
            nettoBeløp = 0,
            måneder = listOf(juni, julisim),
            rawResponse = "SimuleringTest baserer ikke denne på rå XML.",
        )

        val rekkefølge = Rekkefølge.generator()
        // Opphør-linje som strekker seg fra juni til oktober, beløp > 0 (f.eks. opphør i fremtidige måneder)
        val opphørsLinje = utbetalingslinjeNy(
            opprettet = 1.september(simuleringsÅr).atStartOfDay(zoneIdOslo).toTidspunkt(),
            periode = no.nav.su.se.bakover.common.tid.periode.juni(simuleringsÅr)..oktober(simuleringsÅr),
            beløp = 25262, // Linjen har fortsatt beløp, selv om simulering mangler fremtidige måneder
            rekkefølge = rekkefølge.neste(),
        )

        val utbetalingerTidslinje = TidslinjeForUtbetalinger.fra(
            utbetalinger(clock, opphørsLinje),
        )!!

        val svar = sjekkUtbetalingMotSimulering(
            simulering = simulering,
            utbetalingslinjePåTidslinjer = utbetalingerTidslinje,
            erOpphør = true,
            naa = naa,
        )

        // Skal være right, dvs. ingen feil, fordi fremtidige måneder uten simulering er OK ved opphør
        svar.shouldBeRight()
    }

    @Test
    fun `Ulik periode men begge beløp er 0 skal ikke gi feil - sosialstønad og 2 prosent-regel-scenario sak 2442`() {
        /*
         * Reprodusere kryssjekkfeilen som ble logget for sak 2442:
         *
         * Stønadsperiode 2026-04-01 → 2027-03-31. Saksbehandler la inn:
         *   - Sosialstønad 8284 kr i apr+mai (bortfaller fra juni)
         *   - Uføretrygd 25 838 kr hele perioden
         *
         * Beregningen for apr+mai gir sumYtelse = 0 fordi sosialstønad + uføretrygd > satsbeløpet
         * (REGEL-SOSIALSTØNAD-UNDER-2-PROSENT = true). Vi sender derfor utbetalingslinje
         * 2026-04-01 → 2026-05-31 med beløp 0 til Oppdrag.
         *
         * Oppdrag returnerer simulering kun for mai 2026 (beløp 0). April er ikke med. Vi vet ikke nøyaktig
         * hvorfor — sannsynligvis ligger april bak en forfallsgrense (forfall ~20. april var passert da
         * vi simulerte 15. mai), men det er Oppdrag sin interne logikk. Det vi vet er at periodene blir
         * ulike, men begge beløp er 0.
         *
         * Tidslinje:
         *   forrige iverksatt:  …jan-2026 ──── mar-2026│   (slutter 2026-03-31, månedsbeløp 8495)
         *   ny utbetalingslinje:                       │apr-2026 ─── mai-2026│ (beløp 0)
         *   oppdrag-simulering:                                    │mai-2026│ (beløp 0)
         *
         * Periodene matcher ikke (apr-mai vs. mai), men begge beløp er 0 → ingen reell forskjell.
         * Kryssjekken skal logge info og returnere Right.
         */
        val clock = TikkendeKlokke()

        val maiSimulering = SimulertMåned(
            måned = mai(2026),
            utbetaling = SimulertUtbetaling(
                fagSystemId = fagsystemId,
                utbetalesTilId = fnr,
                utbetalesTilNavn = navn,
                forfall = 2.februar(2026),
                feilkonto = false,
                detaljer = listOf(
                    SimulertDetaljer(
                        faktiskFraOgMed = 1.mai(2026),
                        faktiskTilOgMed = 31.mai(2026),
                        konto = konto,
                        belop = 0,
                        tilbakeforing = false,
                        sats = 0,
                        typeSats = typeSats,
                        antallSats = 0,
                        uforegrad = 0,
                        klassekode = KlasseKode.SUUFORE,
                        klassekodeBeskrivelse = suBeskrivelse,
                        klasseType = KlasseType.YTEL,
                    ),
                ),
            ),
        )

        val simulering = Simulering(
            gjelderId = Fnr.generer(),
            gjelderNavn = "tester",
            datoBeregnet = 15.mai(2026),
            nettoBeløp = 0,
            måneder = listOf(maiSimulering),
            rawResponse = "SimuleringTest baserer ikke denne på rå XML.",
        )

        val rekkefølge = Rekkefølge.generator()
        val nullLinje = utbetalingslinjeNy(
            opprettet = 15.mai(2026).atStartOfDay(zoneIdOslo).toTidspunkt(),
            periode = april(2026)..mai(2026),
            beløp = 0,
            rekkefølge = rekkefølge.neste(),
        )

        val tidslinje = TidslinjeForUtbetalinger.fra(utbetalinger(clock, nullLinje))!!

        val svar = sjekkUtbetalingMotSimulering(
            simulering = simulering,
            utbetalingslinjePåTidslinjer = tidslinje,
            erOpphør = false,
            naa = LocalDate.of(2026, Month.MAY, 15),
        )

        svar.shouldBeRight()
    }

    @Test
    fun `Skal ikke feile når det er opphør frem i tid og simulering mangler for månedene etter linjen`() {
        val clock = TikkendeKlokke()
        val simuleringsÅr = 2025
        val naa = LocalDate.of(simuleringsÅr, Month.MAY, 1)

        // Simuleringsdata: kun juni og juli (første måneder i linjen), resten mangler
        val juni = SimulertMåned(
            måned = no.nav.su.se.bakover.common.tid.periode.juni(simuleringsÅr),
            utbetaling = SimulertUtbetaling(
                fagSystemId = fagsystemId,
                utbetalesTilId = fnr,
                utbetalesTilNavn = navn,
                forfall = 2.februar(2026),
                feilkonto = false,
                detaljer = listOf(
                    SimulertDetaljer(
                        faktiskFraOgMed = 1.juni(simuleringsÅr),
                        faktiskTilOgMed = 30.juni(simuleringsÅr),
                        konto = konto,
                        belop = 0,
                        tilbakeforing = false,
                        sats = 0,
                        typeSats = typeSats,
                        antallSats = 0,
                        uforegrad = 0,
                        klassekode = KlasseKode.SUALDER,
                        klassekodeBeskrivelse = suBeskrivelse,
                        klasseType = KlasseType.YTEL,
                    ),
                ),
            ),
        )

        val julisim = SimulertMåned(
            måned = no.nav.su.se.bakover.common.tid.periode.juli(simuleringsÅr),
            utbetaling = SimulertUtbetaling(
                fagSystemId = fagsystemId,
                utbetalesTilId = fnr,
                utbetalesTilNavn = navn,
                forfall = 2.februar(2026),
                feilkonto = false,
                detaljer = listOf(
                    SimulertDetaljer(
                        faktiskFraOgMed = 1.juli(simuleringsÅr),
                        faktiskTilOgMed = 31.juli(simuleringsÅr),
                        konto = konto,
                        belop = 0,
                        tilbakeforing = false,
                        sats = 0,
                        typeSats = typeSats,
                        antallSats = 0,
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
            datoBeregnet = 1.september(simuleringsÅr),
            nettoBeløp = 0,
            måneder = listOf(juni, julisim),
            rawResponse = "SimuleringTest baserer ikke denne på rå XML.",
        )

        val rekkefølge = Rekkefølge.generator()
        // Opphør-linje som strekker seg fra juni til oktober, beløp > 0 (f.eks. opphør i fremtidige måneder)
        val opphørsLinje = utbetalingslinjeNy(
            opprettet = 1.september(simuleringsÅr).atStartOfDay(zoneIdOslo).toTidspunkt(),
            periode = juni(simuleringsÅr),
            beløp = 0, // Linjen har fortsatt beløp, selv om simulering mangler fremtidige måneder
            rekkefølge = rekkefølge.neste(),
        )

        val utbetalingerTidslinje = TidslinjeForUtbetalinger.fra(
            utbetalinger(clock, opphørsLinje),
        )!!

        val svar = sjekkUtbetalingMotSimulering(
            simulering = simulering,
            utbetalingslinjePåTidslinjer = utbetalingerTidslinje,
            erOpphør = false,
            naa = naa,
        )

        svar.shouldBeRight()
    }
}
