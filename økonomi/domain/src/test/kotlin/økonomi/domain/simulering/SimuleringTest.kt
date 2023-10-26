package økonomi.domain.simulering

import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Beløp
import no.nav.su.se.bakover.common.MånedBeløp
import no.nav.su.se.bakover.common.Månedsbeløp
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.extensions.april
import no.nav.su.se.bakover.common.extensions.februar
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.extensions.juni
import no.nav.su.se.bakover.common.extensions.mars
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.periode.april
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.mars
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.simulering.simuleringNy
import no.nav.su.se.bakover.test.simulering.simulertDetaljDebetFeilutbetaling
import no.nav.su.se.bakover.test.simulering.simulertDetaljFeilutbetaling
import no.nav.su.se.bakover.test.simulering.simulertDetaljMotpostering
import no.nav.su.se.bakover.test.simulering.simulertDetaljTilbakeføring
import org.junit.jupiter.api.Test
import økonomi.domain.KlasseType

internal class SimuleringTest {

    private val fagsystemId = "2100"
    private val fnr = Fnr("12345678910")
    private val navn = "SNERK RAKRYGGET"
    private val konto = "123.123.123"
    private val typeSats = "MND"
    private val suBeskrivelse = "Supplerende stønad"

    @Test
    fun `tolker etterbetaling av ordinære utbetalinger`() {
        Sakstype.entries
            .map { it.toYtelsekode() to it.toFeilkode() }
            .forEach { (ytelse, _) ->
                Simulering(
                    gjelderId = fnr,
                    gjelderNavn = navn,
                    datoBeregnet = 14.april(2021),
                    nettoBeløp = 10390,
                    måneder = listOf(
                        SimulertMåned(
                            måned = januar(2021),
                            utbetaling = SimulertUtbetaling(
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
                    rawResponse = "SimuleringTest baserer ikke denne på rå XML.",
                ).let {
                    it.hentTotalUtbetaling() shouldBe Månedsbeløp(
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
        Sakstype.entries
            .map { it.toYtelsekode() to it.toFeilkode() }
            .forEach { (ytelse, _) ->
                Simulering(
                    gjelderId = fnr,
                    gjelderNavn = navn,
                    datoBeregnet = 14.april(2021),
                    nettoBeløp = 10390,
                    måneder = listOf(
                        SimulertMåned(
                            måned = april(2021),
                            utbetaling = SimulertUtbetaling(
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
                    rawResponse = "SimuleringTest baserer ikke denne på rå XML.",
                ).let {
                    it.hentTotalUtbetaling() shouldBe Månedsbeløp(
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
        Sakstype.entries
            .map { it.toYtelsekode() to it.toFeilkode() }
            .forEach { (ytelse, feilkode) ->
                Simulering(
                    gjelderId = fnr,
                    gjelderNavn = navn,
                    datoBeregnet = 14.april(2021),
                    nettoBeløp = 5000,
                    måneder = listOf(
                        SimulertMåned(
                            måned = februar(2021),
                            utbetaling = SimulertUtbetaling(
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
                        SimulertMåned(
                            måned = mars(2021),
                            utbetaling = SimulertUtbetaling(
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
                    rawResponse = "SimuleringTest baserer ikke denne på rå XML.",
                ).let {
                    it.hentTotalUtbetaling() shouldBe Månedsbeløp(
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
        /**
         * Eksempelvis:
         *   Opphør av allerede utbetalt måned (kan sende linje med OPPH eller NY med 0 som beløp).
         *   Dette vil føre til en feilutbetaling, men i disse tilfellene får vi ikke debet-postering for YTEL med sats==beløp.
         *   Disse posteringene som inngår i total utbetaling.
         *   Dette tilsier et beløpet brukeren EGENTLIG skulle hatt (hvis vi ser bort fra hva som allerede er utbetalt).
         *   Tilsvarende skjer dersom man opphører en ytelse som ikke er utbetalt enda, da vil man få tom respons fra oppdrag dersom ingen måneder skal utbetales.
         *   Dersom deler av simuleringen har posteringer, vil månedene som ikke har posteringer bli utelatt.
         *   Vi har en mekanisme der vi inkluderer måneden, men setter `utbetaling` til null.
         */

        Simulering(
            gjelderId = fnr,
            gjelderNavn = navn,
            datoBeregnet = 2.juni(2021),
            nettoBeløp = 51924,
            måneder = listOf(
                SimulertMåned(
                    måned = januar(2021),
                    utbetaling = SimulertUtbetaling(
                        fagSystemId = fagsystemId,
                        utbetalesTilId = fnr,
                        utbetalesTilNavn = navn,
                        forfall = 2.juni(2021),
                        feilkonto = false,
                        detaljer = listOf(
                            // Vi får ikke en ordinær ved OPPH eller beløp=0
                            simulertDetaljDebetFeilutbetaling(
                                måned = januar(2021),
                                beløp = 8949,
                            ),
                            simulertDetaljFeilutbetaling(
                                måned = januar(2021),
                                beløp = 8949,
                            ),
                            simulertDetaljMotpostering(
                                måned = januar(2021),
                                beløp = 8949,
                            ),
                            simulertDetaljTilbakeføring(
                                måned = januar(2021),
                                beløp = 8949,
                            ),
                        ),
                    ),
                ),
            ),
            rawResponse = "SimuleringTest baserer ikke denne på rå XML.",
        ).let {
            it.hentTotalUtbetaling() shouldBe Månedsbeløp(listOf(MånedBeløp(januar(2021), Beløp(0))))
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

    @Test
    fun `tolker simulerte etterbetalinger`() {
        Sakstype.entries
            .map { it.toYtelsekode() to it.toFeilkode() }
            .forEach { (ytelse, _) ->
                Simulering(
                    gjelderId = fnr,
                    gjelderNavn = navn,
                    datoBeregnet = 14.april(2021),
                    nettoBeløp = 19611,
                    måneder = listOf(
                        SimulertMåned(
                            måned = februar(2021),
                            utbetaling = SimulertUtbetaling(
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
                        SimulertMåned(
                            måned = mars(2021),
                            utbetaling = SimulertUtbetaling(
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
                    rawResponse = "SimuleringTest baserer ikke denne på rå XML.",
                ).let {
                    it.hentTotalUtbetaling() shouldBe Månedsbeløp(
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
        Sakstype.entries
            .map { it.toYtelsekode() to it.toFeilkode() }
            .forEach { (ytelse, _) ->
                Simulering(
                    gjelderId = fnr,
                    gjelderNavn = navn,
                    datoBeregnet = 14.april(2021),
                    nettoBeløp = 19611,
                    måneder = listOf(
                        SimulertMåned(
                            måned = februar(2021),
                            utbetaling = SimulertUtbetaling(
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
                        SimulertMåned(
                            måned = mars(2021),
                            utbetaling = SimulertUtbetaling(
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
                    rawResponse = "SimuleringTest baserer ikke denne på rå XML.",
                ).let {
                    it.hentTotalUtbetaling() shouldBe Månedsbeløp(
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
        Sakstype.entries
            .map { it.toYtelsekode() to it.toFeilkode() }
            .forEach { (_, _) ->
                Simulering(
                    gjelderId = fnr,
                    // Usually returned by response, which in this case is empty.
                    gjelderNavn = fnr.toString(),
                    datoBeregnet = fixedLocalDate,
                    nettoBeløp = 0,
                    måneder = SimulertMåned.create(år(2021)),
                    rawResponse = "SimuleringTest baserer ikke denne på rå XML.",
                ).let {
                    it.kontooppstilling() shouldBe mapOf(
                        år(2021) to Kontooppstilling(
                            debetYtelse = Kontobeløp.Debet(0),
                            kreditYtelse = Kontobeløp.Kredit(0),
                            debetFeilkonto = Kontobeløp.Debet(0),
                            kreditFeilkonto = Kontobeløp.Kredit(0),
                            debetMotpostFeilkonto = Kontobeløp.Debet(0),
                            kreditMotpostFeilkonto = Kontobeløp.Kredit(0),
                        ),
                    )
                    it.erAllePerioderUtenUtbetaling() shouldBe true
                    it.hentTotalUtbetaling() shouldBe Månedsbeløp(emptyList())
                    it.hentFeilutbetalteBeløp() shouldBe Månedsbeløp(emptyList())
                    it.hentUtbetalteBeløp() shouldBe Månedsbeløp(emptyList())
                }
            }
    }

    @Test
    fun `Skal ikke kunne serialisere Simulering`() {
        shouldThrowWithMessage<IllegalStateException>("Don't serialize/deserialize domain types: Simulering") {
            serialize(simuleringNy())
        }
    }

    @Test
    fun `Skal ikke kunne deserialisere Simulering`() {
        shouldThrowWithMessage<IllegalStateException>("Don't serialize/deserialize domain types: Simulering") {
            deserialize<Simulering>(
                """
                {
                    "gjelderId": "12345678901",
                    "gjelderNavn": "John Doe",
                    "datoBeregnet": "2023-06-01",
                    "nettoBeløp": 5000,
                    "måneder": [{
                      "måned": "2023-06",
                      "utbealing": null
                    }],
                    "rawResponse": ""
                }

                """.trimIndent(),
            )
        }
    }
}
