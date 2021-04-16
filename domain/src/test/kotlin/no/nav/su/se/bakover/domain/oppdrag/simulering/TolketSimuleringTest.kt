package no.nav.su.se.bakover.domain.oppdrag.simulering

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.domain.Fnr
import org.junit.jupiter.api.Test

internal class TolketSimuleringTest {

    private val fagsystemId = "2100"
    private val fnr = Fnr("12345678910")
    private val navn = "SNERK RAKRYGGET"
    private val konto = "123.123.123"
    private val typeSats = "MND"
    private val suBeskrivelse = "Supplerende stønad Uføre"

    @Test
    fun `tolker fremtidige simulerte utbetalinger`() {
        TolketSimulering(
            Simulering(
                gjelderId = fnr,
                gjelderNavn = navn,
                datoBeregnet = 14.april(2021),
                nettoBeløp = 10390,
                periodeList = listOf(
                    SimulertPeriode(
                        fraOgMed = 1.april(2021),
                        tilOgMed = 30.april(2021),
                        utbetaling = listOf(
                            SimulertUtbetaling(
                                fagSystemId = fagsystemId,
                                utbetalesTilId = fnr,
                                utbetalesTilNavn = navn,
                                forfall = 19.april(2021),
                                feilkonto = false,
                                detaljer = listOf(
                                    SimulertDetaljer(
                                        faktiskFraOgMed = 1.april(2021),
                                        faktiskTilOgMed = 30.april(2021),
                                        konto = konto,
                                        belop = 20779,
                                        tilbakeforing = false,
                                        sats = 20779,
                                        typeSats = typeSats,
                                        antallSats = 1,
                                        uforegrad = 0,
                                        klassekode = KlasseKode.SUUFORE,
                                        klassekodeBeskrivelse = suBeskrivelse,
                                        klasseType = KlasseType.YTEL,
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ).simulertePerioder shouldBe listOf(
            TolketPeriode(
                fraOgMed = 1.april(2021),
                tilOgMed = 30.april(2021),
                utbetalinger = listOf(
                    TolketUtbetaling.Ordinær(
                        tolketDetalj = listOf(
                            TolketDetalj.Ordinær(
                                beløp = 20779,
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `tolker simulerte feilutbetalinger`() {
        TolketSimulering(
            Simulering(
                gjelderId = fnr,
                gjelderNavn = navn,
                datoBeregnet = 14.april(2021),
                nettoBeløp = 5000,
                periodeList = listOf(
                    SimulertPeriode(
                        fraOgMed = 1.februar(2021),
                        tilOgMed = 28.februar(2021),
                        utbetaling = listOf(
                            SimulertUtbetaling(
                                fagSystemId = fagsystemId,
                                utbetalesTilId = fnr,
                                utbetalesTilNavn = navn,
                                forfall = 14.april(2021),
                                feilkonto = false,
                                detaljer = listOf(
                                    SimulertDetaljer(
                                        faktiskFraOgMed = 1.februar(2021),
                                        faktiskTilOgMed = 28.februar(2021),
                                        konto = konto,
                                        belop = 10779,
                                        tilbakeforing = false,
                                        sats = 0,
                                        typeSats = "",
                                        antallSats = 0,
                                        uforegrad = 0,
                                        klassekode = KlasseKode.SUUFORE,
                                        klassekodeBeskrivelse = suBeskrivelse,
                                        klasseType = KlasseType.YTEL,
                                    ),
                                    SimulertDetaljer(
                                        faktiskFraOgMed = 1.februar(2021),
                                        faktiskTilOgMed = 28.februar(2021),
                                        konto = konto,
                                        belop = 10000,
                                        tilbakeforing = false,
                                        sats = 10000,
                                        typeSats = typeSats,
                                        antallSats = 1,
                                        uforegrad = 0,
                                        klassekode = KlasseKode.SUUFORE,
                                        klassekodeBeskrivelse = suBeskrivelse,
                                        klasseType = KlasseType.YTEL,
                                    ),
                                    SimulertDetaljer(
                                        faktiskFraOgMed = 1.februar(2021),
                                        faktiskTilOgMed = 28.februar(2021),
                                        konto = konto,
                                        belop = 10779,
                                        tilbakeforing = false,
                                        sats = 0,
                                        typeSats = "",
                                        antallSats = 0,
                                        uforegrad = 0,
                                        klassekode = KlasseKode.KL_KODE_FEIL_INNT,
                                        klassekodeBeskrivelse = "Feilutbetaling Inntektsytelser",
                                        klasseType = KlasseType.FEIL,
                                    ),
                                    SimulertDetaljer(
                                        faktiskFraOgMed = 1.februar(2021),
                                        faktiskTilOgMed = 28.februar(2021),
                                        konto = konto,
                                        belop = -20779,
                                        tilbakeforing = true,
                                        sats = 0,
                                        typeSats = "",
                                        antallSats = 0,
                                        uforegrad = 0,
                                        klassekode = KlasseKode.SUUFORE,
                                        klassekodeBeskrivelse = suBeskrivelse,
                                        klasseType = KlasseType.YTEL,
                                    ),
                                ),
                            ),
                        ),
                    ),
                    SimulertPeriode(
                        fraOgMed = 1.mars(2021),
                        tilOgMed = 31.mars(2021),
                        utbetaling = listOf(
                            SimulertUtbetaling(
                                fagSystemId = fagsystemId,
                                utbetalesTilId = fnr,
                                utbetalesTilNavn = navn,
                                forfall = 10.mars(2021),
                                feilkonto = false,
                                detaljer = listOf(
                                    SimulertDetaljer(
                                        faktiskFraOgMed = 1.mars(2021),
                                        faktiskTilOgMed = 31.mars(2021),
                                        konto = konto,
                                        belop = 10000,
                                        tilbakeforing = false,
                                        sats = 10000,
                                        typeSats = typeSats,
                                        antallSats = 1,
                                        uforegrad = 0,
                                        klassekode = KlasseKode.SUUFORE,
                                        klassekodeBeskrivelse = suBeskrivelse,
                                        klasseType = KlasseType.YTEL,
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ).simulertePerioder shouldBe listOf(
            TolketPeriode(
                fraOgMed = 1.februar(2021),
                tilOgMed = 28.februar(2021),
                utbetalinger = listOf(
                    TolketUtbetaling.Feilutbetaling(
                        tolketDetalj = listOf(
                            TolketDetalj.Ordinær(
                                beløp = 10000,
                            ),
                            TolketDetalj.Feilutbetaling(
                                beløp = 10779,
                            ),
                            TolketDetalj.TidligereUtbetalt(
                                beløp = -20779,
                            ),
                        ),
                    ),
                ),
            ),
            TolketPeriode(
                fraOgMed = 1.mars(2021),
                tilOgMed = 31.mars(2021),
                utbetalinger = listOf(
                    TolketUtbetaling.Ordinær(
                        tolketDetalj = listOf(
                            TolketDetalj.Ordinær(
                                beløp = 10000,
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `tolker simulerte etterbetalinger`() {
        TolketSimulering(
            Simulering(
                gjelderId = fnr,
                gjelderNavn = navn,
                datoBeregnet = 14.april(2021),
                nettoBeløp = 19611,
                periodeList = listOf(
                    SimulertPeriode(
                        fraOgMed = 1.februar(2021),
                        tilOgMed = 28.februar(2021),
                        utbetaling = listOf(
                            SimulertUtbetaling(
                                fagSystemId = fagsystemId,
                                utbetalesTilId = fnr,
                                utbetalesTilNavn = navn,
                                forfall = 14.april(2021),
                                feilkonto = false,
                                detaljer = listOf(
                                    SimulertDetaljer(
                                        faktiskFraOgMed = 1.februar(2021),
                                        faktiskTilOgMed = 28.februar(2021),
                                        konto = konto,
                                        belop = 30000,
                                        tilbakeforing = false,
                                        sats = 30000,
                                        typeSats = typeSats,
                                        antallSats = 1,
                                        uforegrad = 0,
                                        klassekode = KlasseKode.SUUFORE,
                                        klassekodeBeskrivelse = suBeskrivelse,
                                        klasseType = KlasseType.YTEL,
                                    ),
                                    SimulertDetaljer(
                                        faktiskFraOgMed = 1.februar(2021),
                                        faktiskTilOgMed = 28.februar(2021),
                                        konto = konto,
                                        belop = -20779,
                                        tilbakeforing = true,
                                        sats = 0,
                                        typeSats = "",
                                        antallSats = 0,
                                        uforegrad = 0,
                                        klassekode = KlasseKode.SUUFORE,
                                        klassekodeBeskrivelse = suBeskrivelse,
                                        klasseType = KlasseType.YTEL,
                                    ),
                                ),
                            ),
                        ),
                    ),
                    SimulertPeriode(
                        fraOgMed = 1.mars(2021),
                        tilOgMed = 31.mars(2021),
                        utbetaling = listOf(
                            SimulertUtbetaling(
                                fagSystemId = fagsystemId,
                                utbetalesTilId = fnr,
                                utbetalesTilNavn = navn,
                                forfall = 10.mars(2021),
                                feilkonto = false,
                                detaljer = listOf(
                                    SimulertDetaljer(
                                        faktiskFraOgMed = 1.mars(2021),
                                        faktiskTilOgMed = 31.mars(2021),
                                        konto = konto,
                                        belop = 30000,
                                        tilbakeforing = false,
                                        sats = 30000,
                                        typeSats = typeSats,
                                        antallSats = 1,
                                        uforegrad = 0,
                                        klassekode = KlasseKode.SUUFORE,
                                        klassekodeBeskrivelse = suBeskrivelse,
                                        klasseType = KlasseType.YTEL,
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ).simulertePerioder shouldBe listOf(
            TolketPeriode(
                fraOgMed = 1.februar(2021),
                tilOgMed = 28.februar(2021),
                utbetalinger = listOf(
                    TolketUtbetaling.Etterbetaling(
                        tolketDetalj = listOf(
                            TolketDetalj.Ordinær(
                                beløp = 30000,
                            ),
                            TolketDetalj.TidligereUtbetalt(
                                beløp = -20779,
                            ),
                            TolketDetalj.Etterbetaling(
                                beløp = 9221,
                            ),
                        ),
                    ),
                ),
            ),
            TolketPeriode(
                fraOgMed = 1.mars(2021),
                tilOgMed = 31.mars(2021),
                utbetalinger = listOf(
                    TolketUtbetaling.Ordinær(
                        tolketDetalj = listOf(
                            TolketDetalj.Ordinær(
                                beløp = 30000,
                            ),
                        ),
                    ),
                ),
            ),
        )
    }
}
