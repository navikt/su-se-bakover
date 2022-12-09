package no.nav.su.se.bakover.domain.oppdrag.simulering

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.application.Beløp
import no.nav.su.se.bakover.common.application.MånedBeløp
import no.nav.su.se.bakover.common.application.Månedsbeløp
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.periode.april
import no.nav.su.se.bakover.common.periode.februar
import no.nav.su.se.bakover.common.periode.januar
import no.nav.su.se.bakover.common.periode.mars
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.test.fixedLocalDate
import org.junit.jupiter.api.Test

internal class SimuleringTest {

    private val fagsystemId = "2100"
    private val fnr = Fnr("12345678910")
    private val navn = "SNERK RAKRYGGET"
    private val konto = "123.123.123"
    private val typeSats = "MND"
    private val suBeskrivelse = "Supplerende stønad"

    @Test
    fun `tolker etterbetaling av ordinære utbetalinger`() {
        Sakstype.values()
            .map { it.toYtelsekode() to it.toFeilkode() }
            .forEach { (ytelse, _) ->
                Simulering(
                    gjelderId = fnr,
                    gjelderNavn = navn,
                    datoBeregnet = 14.april(2021),
                    nettoBeløp = 10390,
                    periodeList = listOf(
                        SimulertPeriode(
                            fraOgMed = 1.januar(2021),
                            tilOgMed = 31.januar(2021),
                            utbetaling = listOf(
                                SimulertUtbetaling(
                                    fagSystemId = fagsystemId,
                                    utbetalesTilId = fnr,
                                    utbetalesTilNavn = navn,
                                    forfall = 14.april(2021),
                                    feilkonto = false,
                                    detaljer = listOf(
                                        SimulertDetaljer(
                                            faktiskFraOgMed = 1.januar(2021),
                                            faktiskTilOgMed = 31.januar(2021),
                                            konto = konto,
                                            belop = 20779,
                                            tilbakeforing = false,
                                            sats = 20779,
                                            typeSats = typeSats,
                                            antallSats = 1,
                                            uforegrad = 0,
                                            klassekode = ytelse,
                                            klassekodeBeskrivelse = suBeskrivelse,
                                            klasseType = KlasseType.YTEL,
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ).let {
                    it.hentUtbetalingSomSimuleres() shouldBe Månedsbeløp(
                        listOf(
                            MånedBeløp(januar(2021), Beløp(20779)),
                        ),
                    )
                    it.hentFeilutbetalteBeløp() shouldBe Månedsbeløp(emptyList())
                    it.hentUtbetalteBeløp() shouldBe Månedsbeløp(emptyList())
                    it.hentTilUtbetaling() shouldBe Månedsbeløp(
                        listOf(
                            MånedBeløp(januar(2021), Beløp(20779)),
                        ),
                    )
                }
            }
    }

    @Test
    fun `tolker fremtidige simulerte utbetalinger`() {
        Sakstype.values()
            .map { it.toYtelsekode() to it.toFeilkode() }
            .forEach { (ytelse, _) ->
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
                                            klassekode = ytelse,
                                            klassekodeBeskrivelse = suBeskrivelse,
                                            klasseType = KlasseType.YTEL,
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ).let {
                    it.hentUtbetalingSomSimuleres() shouldBe Månedsbeløp(
                        listOf(
                            MånedBeløp(april(2021), Beløp(20779)),
                        ),
                    )
                    it.hentFeilutbetalteBeløp() shouldBe Månedsbeløp(emptyList())
                    it.hentUtbetalteBeløp() shouldBe Månedsbeløp(emptyList())
                }
            }
    }

    @Test
    fun `tolker simulerte feilutbetalinger med restbeløp til utbetaling`() {
        Sakstype.values()
            .map { it.toYtelsekode() to it.toFeilkode() }
            .forEach { (ytelse, feilkode) ->
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
                                            klassekode = ytelse,
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
                                            klassekode = ytelse,
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
                                            klassekode = feilkode,
                                            klassekodeBeskrivelse = "Feilutbetaling $ytelse",
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
                                            klassekode = ytelse,
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
                                            klassekode = ytelse,
                                            klassekodeBeskrivelse = suBeskrivelse,
                                            klasseType = KlasseType.YTEL,
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ).let {
                    it.hentUtbetalingSomSimuleres() shouldBe Månedsbeløp(
                        listOf(
                            MånedBeløp(februar(2021), Beløp(10000)),
                            MånedBeløp(mars(2021), Beløp(10000)),
                        ),
                    )
                    it.hentFeilutbetalteBeløp() shouldBe Månedsbeløp(
                        listOf(
                            MånedBeløp(februar(2021), Beløp(10779)),
                        ),
                    )
                    it.hentUtbetalteBeløp() shouldBe Månedsbeløp(
                        listOf(
                            MånedBeløp(februar(2021), Beløp(20779)),
                        ),
                    )
                    it.hentTilUtbetaling() shouldBe Månedsbeløp(
                        listOf(
                            MånedBeløp(mars(2021), Beløp(10000)),
                        ),
                    )
                }
            }
    }

    @Test
    fun `tolker simulerte feilutbetalinger uten restbeløp til utbetaling (full tilbakekreving for måned)`() {
        Sakstype.values()
            .map { it.toYtelsekode() to it.toFeilkode() }
            .forEach { (ytelse, feilkode) ->
                Simulering(
                    gjelderId = fnr,
                    gjelderNavn = navn,
                    datoBeregnet = 2.juni(2021),
                    nettoBeløp = 51924,
                    periodeList = listOf(
                        SimulertPeriode(
                            fraOgMed = 1.januar(2021),
                            tilOgMed = 31.januar(2021),
                            utbetaling = listOf(
                                SimulertUtbetaling(
                                    fagSystemId = fagsystemId,
                                    utbetalesTilId = fnr,
                                    utbetalesTilNavn = navn,
                                    forfall = 2.juni(2021),
                                    feilkonto = false,
                                    detaljer = listOf(
                                        SimulertDetaljer(
                                            faktiskFraOgMed = 1.januar(2021),
                                            faktiskTilOgMed = 31.januar(2021),
                                            konto = konto,
                                            belop = 8946,
                                            tilbakeforing = false,
                                            sats = 0,
                                            typeSats = "",
                                            antallSats = 0,
                                            uforegrad = 0,
                                            klassekode = ytelse,
                                            klassekodeBeskrivelse = suBeskrivelse,
                                            klasseType = KlasseType.YTEL,
                                        ),
                                        SimulertDetaljer(
                                            faktiskFraOgMed = 1.januar(2021),
                                            faktiskTilOgMed = 31.januar(2021),
                                            konto = konto,
                                            belop = 8949,
                                            tilbakeforing = false,
                                            sats = 0,
                                            typeSats = "",
                                            antallSats = 0,
                                            uforegrad = 0,
                                            klassekode = feilkode,
                                            klassekodeBeskrivelse = "Feilutbetaling $ytelse",
                                            klasseType = KlasseType.FEIL,
                                        ),
                                        SimulertDetaljer(
                                            faktiskFraOgMed = 1.januar(2021),
                                            faktiskTilOgMed = 31.januar(2021),
                                            konto = konto,
                                            belop = -8949,
                                            tilbakeforing = true,
                                            sats = 0,
                                            typeSats = "",
                                            antallSats = 0,
                                            uforegrad = 0,
                                            klassekode = ytelse,
                                            klassekodeBeskrivelse = suBeskrivelse,
                                            klasseType = KlasseType.YTEL,
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ).let {
                    it.hentUtbetalingSomSimuleres() shouldBe Månedsbeløp(emptyList())
                    it.hentFeilutbetalteBeløp() shouldBe Månedsbeløp(
                        listOf(
                            MånedBeløp(januar(2021), Beløp(8949)),
                        ),
                    )
                    it.hentUtbetalteBeløp() shouldBe Månedsbeløp(
                        listOf(
                            MånedBeløp(januar(2021), Beløp(8949)),
                        ),
                    )
                    it.hentTilUtbetaling() shouldBe Månedsbeløp(emptyList())
                }
            }
    }

    @Test
    fun `tolker simulerte etterbetalinger`() {
        Sakstype.values()
            .map { it.toYtelsekode() to it.toFeilkode() }
            .forEach { (ytelse, _) ->
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
                                            klassekode = ytelse,
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
                                            klassekode = ytelse,
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
                                            klassekode = ytelse,
                                            klassekodeBeskrivelse = suBeskrivelse,
                                            klasseType = KlasseType.YTEL,
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ).let {
                    it.hentUtbetalingSomSimuleres() shouldBe Månedsbeløp(
                        listOf(
                            MånedBeløp(februar(2021), Beløp(30000)),
                            MånedBeløp(mars(2021), Beløp(30000)),
                        ),
                    )
                    it.hentFeilutbetalteBeløp() shouldBe Månedsbeløp(emptyList())
                    it.hentUtbetalteBeløp() shouldBe Månedsbeløp(
                        listOf(
                            MånedBeløp(februar(2021), Beløp(20779)),
                        ),
                    )
                    it.hentTilUtbetaling() shouldBe Månedsbeløp(
                        listOf(
                            MånedBeløp(februar(2021), Beløp(9221)),
                            MånedBeløp(mars(2021), Beløp(30000)),
                        ),
                    )
                }
            }
    }

    @Test
    fun `tolker simulerte utbetalinger uten endringer`() {
        Sakstype.values()
            .map { it.toYtelsekode() to it.toFeilkode() }
            .forEach { (ytelse, _) ->
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
                                            belop = 20779,
                                            tilbakeforing = false,
                                            sats = 20779,
                                            typeSats = typeSats,
                                            antallSats = 1,
                                            uforegrad = 0,
                                            klassekode = ytelse,
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
                                            klassekode = ytelse,
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
                                            belop = 20779,
                                            tilbakeforing = false,
                                            sats = 20779,
                                            typeSats = typeSats,
                                            antallSats = 1,
                                            uforegrad = 0,
                                            klassekode = ytelse,
                                            klassekodeBeskrivelse = suBeskrivelse,
                                            klasseType = KlasseType.YTEL,
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ).let {
                    it.hentUtbetalingSomSimuleres() shouldBe Månedsbeløp(
                        listOf(
                            MånedBeløp(februar(2021), Beløp(20779)),
                            MånedBeløp(mars(2021), Beløp(20779)),
                        ),
                    )
                    it.hentFeilutbetalteBeløp() shouldBe Månedsbeløp(emptyList())
                    it.hentUtbetalteBeløp() shouldBe Månedsbeløp(
                        listOf(
                            MånedBeløp(februar(2021), Beløp(20779)),
                        ),
                    )
                    it.hentTilUtbetaling() shouldBe Månedsbeløp(
                        listOf(
                            MånedBeløp(mars(2021), Beløp(20779)),
                        ),
                    )
                }
            }
    }

    @Test
    fun `tolker simulering med tom utbetalingsliste - representerer ingen utbetaling`() {
        Sakstype.values()
            .map { it.toYtelsekode() to it.toFeilkode() }
            .forEach { (_, _) ->
                Simulering(
                    gjelderId = fnr,
                    gjelderNavn = fnr.toString(), // Usually returned by response, which in this case is empty.
                    datoBeregnet = fixedLocalDate,
                    nettoBeløp = 0,
                    periodeList = listOf(
                        SimulertPeriode(
                            fraOgMed = 1.januar(2021),
                            tilOgMed = 31.desember(2021),
                            utbetaling = emptyList(),
                        ),
                    ),
                ).let {
                    it.kontooppstilling() shouldBe mapOf(
                        år(2021) to Kontooppstilling(
                            simulertUtbetaling = Kontobeløp.Debet(0),
                            debetYtelse = Kontobeløp.Debet(0),
                            kreditYtelse = Kontobeløp.Kredit(0),
                            debetFeilkonto = Kontobeløp.Debet(0),
                            kreditFeilkonto = Kontobeløp.Kredit(0),
                            debetMotpostFeilkonto = Kontobeløp.Debet(0),
                            kreditMotpostFeilkonto = Kontobeløp.Kredit(0),
                        ),
                    )
                    it.erAlleMånederUtenUtbetaling() shouldBe true
                    it.hentUtbetalingSomSimuleres() shouldBe Månedsbeløp(emptyList())
                    it.hentFeilutbetalteBeløp() shouldBe Månedsbeløp(emptyList())
                    it.hentUtbetalteBeløp() shouldBe Månedsbeløp(emptyList())
                }
            }
    }
}
