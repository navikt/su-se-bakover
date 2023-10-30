package no.nav.su.se.bakover.client.oppdrag.simulering

import arrow.core.left
import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import no.nav.su.se.bakover.common.Beløp
import no.nav.su.se.bakover.common.MånedBeløp
import no.nav.su.se.bakover.common.Månedsbeløp
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.extensions.april
import no.nav.su.se.bakover.common.extensions.februar
import no.nav.su.se.bakover.common.extensions.mars
import no.nav.su.se.bakover.common.infrastructure.xml.xmlMapper
import no.nav.su.se.bakover.common.tid.periode.april
import no.nav.su.se.bakover.common.tid.periode.august
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.common.tid.periode.mars
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.simulering.SimuleringResponseData.Companion.simuleringXml
import no.nav.su.se.bakover.test.simulering.utbetalingForSimulering
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.SimulerBeregningResponse
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.slf4j.Logger
import økonomi.domain.KlasseKode
import økonomi.domain.KlasseType
import økonomi.domain.simulering.Kontobeløp
import økonomi.domain.simulering.Kontooppstilling
import økonomi.domain.simulering.PeriodeOppsummering
import økonomi.domain.simulering.Simulering
import økonomi.domain.simulering.SimuleringsOppsummering
import økonomi.domain.simulering.SimulertDetaljer
import økonomi.domain.simulering.SimulertMåned
import økonomi.domain.simulering.SimulertUtbetaling
import java.math.BigDecimal
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningResponse as GrensesnittResponse

internal class SimuleringResponseMapperTest {

    private val saksnummer = no.nav.su.se.bakover.test.saksnummer
    private val fagsystemId = saksnummer.toString()
    private val fnr = no.nav.su.se.bakover.test.fnr
    private val navn = "Test Testesen"
    private val kontoYtel = "4952000"
    private val kontoFeil = "0630986"
    private val kontoMotp = "0902900"
    private val typeSats = "MND"
    private val suBeskrivelse = "Supplerende stønad Uføre"

    @Test
    fun `mapper fremtidige simulerte utbetalinger`() {
        val rawResponse = simuleringXml {
            datoBeregnet = "2021-04-14"
            belop = "10390.00"
            periode {
                periodeFom = "2021-04-01"
                periodeTom = "2021-04-30"
                stoppnivå {
                    forfall = "2021-04-19"
                    ordinær(20779)
                }
            }
        }
        val responseMedFremtidigUtbetaling = xmlMapper.readValue(
            rawResponse,
            GrensesnittResponse::class.java,
        ).response

        val request = utbetalingForSimulering()
        val actualSimulering = responseMedFremtidigUtbetaling.toSimulering(
            request = request,
            clock = fixedClock,
            soapRequest = SimuleringRequestBuilder(request).build(),
        ).getOrFail()
        val expectedSimulering = Simulering(
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
                                konto = kontoYtel,
                                belop = 20779,
                                tilbakeforing = false,
                                sats = 20779,
                                typeSats = typeSats,
                                antallSats = 1,
                                uforegrad = 100,
                                klassekode = KlasseKode.SUUFORE,
                                klassekodeBeskrivelse = suBeskrivelse,
                                klasseType = KlasseType.YTEL,
                            ),
                        ),
                    ),
                ),
            ),
            rawResponse = rawResponse,
        )
        actualSimulering.shouldBeEqualToIgnoringFields(expectedSimulering, Simulering::rawResponse)
        actualSimulering.also {
            it.erAlleMånederUtenUtbetaling() shouldBe false
            it.hentTilUtbetaling() shouldBe Månedsbeløp(
                listOf(
                    MånedBeløp(april(2021), Beløp(20779)),
                ),
            )
            it.hentTotalUtbetaling() shouldBe Månedsbeløp(
                listOf(
                    MånedBeløp(april(2021), Beløp(20779)),
                ),
            )
            it.hentUtbetalteBeløp() shouldBe Månedsbeløp(emptyList())
            it.hentFeilutbetalteBeløp() shouldBe Månedsbeløp(emptyList())
            it.kontooppstilling() shouldBe mapOf(
                april(2021) to Kontooppstilling(
                    debetYtelse = Kontobeløp.Debet(20779),
                    kreditYtelse = Kontobeløp.Kredit(0),
                    debetFeilkonto = Kontobeløp.Debet(0),
                    kreditFeilkonto = Kontobeløp.Kredit(0),
                    debetMotpostFeilkonto = Kontobeløp.Debet(0),
                    kreditMotpostFeilkonto = Kontobeløp.Kredit(0),
                ).also {
                    it.sumUtbetaling shouldBe Kontobeløp.Summert(20779)
                    it.sumFeilkonto shouldBe Kontobeløp.Summert(0)
                    it.sumMotpostFeilkonto shouldBe Kontobeløp.Summert(0)
                },
            )
            it.hentTotalUtbetaling() shouldBe Månedsbeløp(
                listOf(
                    MånedBeløp(april(2021), Beløp(20779)),
                ),
            )
        }
    }

    @Test
    fun `mapper fremtidige simulerte utbetalinger - alder`() {
        val simuleringXml = simuleringXml {
            datoBeregnet = "2021-04-14"
            belop = "10390.00"
            periode {
                periodeFom = "2021-04-01"
                periodeTom = "2021-04-30"
                stoppnivå {
                    kodeFagomraade = "SUALDER"
                    forfall = "2021-04-19"
                    ordinær(20779, "SUALDER")
                }
            }
        }
        val responseMedFremtidigUtbetaling = xmlMapper.readValue(
            simuleringXml,
            GrensesnittResponse::class.java,
        ).response
        val request = utbetalingForSimulering()
        val actualSimulering = responseMedFremtidigUtbetaling.toSimulering(
            request = request,
            clock = fixedClock,
            soapRequest = SimuleringRequestBuilder(request).build(),
        ).getOrFail()
        val expectedSimulering = Simulering(
            gjelderId = fnr,
            gjelderNavn = navn,
            datoBeregnet = 14.april(2021),
            nettoBeløp = 10390,
            måneder = listOf(
                SimulertMåned(
                    måned = april(2021),
                    utbetaling =
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
                                konto = kontoYtel,
                                belop = 20779,
                                tilbakeforing = false,
                                sats = 20779,
                                typeSats = typeSats,
                                antallSats = 1,
                                uforegrad = 100,
                                klassekode = KlasseKode.SUALDER,
                                klassekodeBeskrivelse = suBeskrivelse,
                                klasseType = KlasseType.YTEL,
                            ),
                        ),
                    ),
                ),
            ),
            rawResponse = simuleringXml,
        )
        actualSimulering.shouldBeEqualToIgnoringFields(expectedSimulering, Simulering::rawResponse)
        actualSimulering.also {
            it.kontooppstilling() shouldBe mapOf(
                april(2021) to Kontooppstilling(
                    debetYtelse = Kontobeløp.Debet(20779),
                    kreditYtelse = Kontobeløp.Kredit(0),
                    debetFeilkonto = Kontobeløp.Debet(0),
                    kreditFeilkonto = Kontobeløp.Kredit(0),
                    debetMotpostFeilkonto = Kontobeløp.Debet(0),
                    kreditMotpostFeilkonto = Kontobeløp.Kredit(0),
                ).also {
                    it.sumUtbetaling shouldBe Kontobeløp.Summert(20779)
                    it.sumFeilkonto shouldBe Kontobeløp.Summert(0)
                    it.sumMotpostFeilkonto shouldBe Kontobeløp.Summert(0)
                },
            )
        }
    }

    @Test
    fun `mapper simulerte feilutbetalinger`() {
        val simuleringXml = simuleringXml {
            datoBeregnet = "2021-04-14"
            belop = "5000.00"
            periode {
                periodeFom = "2021-02-01"
                periodeTom = "2021-02-28"
                stoppnivå {
                    forfall = "2021-04-14"
                    feilkonto = "true"
                    debetFeilutbetaling(10779)
                    ordinær(10000)
                    feilutbetaling(10779)
                    motposteringskonto(-10779)
                    kreditTidligereUtbetalt(-20779)
                }
            }
            periode {
                periodeFom = "2021-03-01"
                periodeTom = "2021-03-31"
                stoppnivå {
                    forfall = "2021-03-10"
                    ordinær(10000)
                    skattedetalj(-5000)
                }
            }
        }
        val responseMedFeilutbetaling = xmlMapper.readValue(
            simuleringXml,
            GrensesnittResponse::class.java,
        ).response

        val request = utbetalingForSimulering()
        val expectedSimulering = Simulering(
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
                        feilkonto = true,
                        detaljer = listOf(
                            SimulertDetaljer(
                                faktiskFraOgMed = 1.februar(2021),
                                faktiskTilOgMed = 28.februar(2021),
                                konto = kontoYtel,
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
                                konto = kontoYtel,
                                belop = 10000,
                                tilbakeforing = false,
                                sats = 10000,
                                typeSats = typeSats,
                                antallSats = 1,
                                uforegrad = 100,
                                klassekode = KlasseKode.SUUFORE,
                                klassekodeBeskrivelse = suBeskrivelse,
                                klasseType = KlasseType.YTEL,
                            ),
                            SimulertDetaljer(
                                faktiskFraOgMed = 1.februar(2021),
                                faktiskTilOgMed = 28.februar(2021),
                                konto = kontoFeil,
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
                                konto = kontoMotp,
                                belop = -10779,
                                tilbakeforing = false,
                                sats = 0,
                                typeSats = "",
                                antallSats = 0,
                                uforegrad = 0,
                                klassekode = KlasseKode.TBMOTOBS,
                                klassekodeBeskrivelse = "Feilutbetaling motkonto til OBS konto",
                                klasseType = KlasseType.MOTP,
                            ),
                            SimulertDetaljer(
                                faktiskFraOgMed = 1.februar(2021),
                                faktiskTilOgMed = 28.februar(2021),
                                konto = kontoYtel,
                                belop = -20779,
                                tilbakeforing = true,
                                sats = 20779,
                                typeSats = "MND",
                                antallSats = 0,
                                uforegrad = 100,
                                klassekode = KlasseKode.SUUFORE,
                                klassekodeBeskrivelse = suBeskrivelse,
                                klasseType = KlasseType.YTEL,
                            ),
                        ),
                    ),
                ),
                SimulertMåned(
                    måned = mars(2021),
                    utbetaling =
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
                                konto = kontoYtel,
                                belop = 10000,
                                tilbakeforing = false,
                                sats = 10000,
                                typeSats = typeSats,
                                antallSats = 1,
                                uforegrad = 100,
                                klassekode = KlasseKode.SUUFORE,
                                klassekodeBeskrivelse = suBeskrivelse,
                                klasseType = KlasseType.YTEL,
                            ),
                        ),
                    ),
                ),
            ),
            rawResponse = simuleringXml,
        )
        val actualSimulering = responseMedFeilutbetaling.toSimulering(
            request = request,
            clock = fixedClock,
            soapRequest = SimuleringRequestBuilder(request).build(),
        ).getOrFail()
        actualSimulering.shouldBeEqualToIgnoringFields(expectedSimulering, Simulering::rawResponse)

        actualSimulering.also {
            it.erAlleMånederUtenUtbetaling() shouldBe false
            it.hentTilUtbetaling() shouldBe Månedsbeløp(
                listOf(
                    MånedBeløp(mars(2021), Beløp(10000)),
                ),
            )
            it.hentTotalUtbetaling() shouldBe Månedsbeløp(
                listOf(
                    MånedBeløp(februar(2021), Beløp(10000)),
                    MånedBeløp(mars(2021), Beløp(10000)),
                ),
            )
            it.hentUtbetalteBeløp() shouldBe Månedsbeløp(
                listOf(
                    MånedBeløp(februar(2021), Beløp(20779)),
                ),
            )
            it.hentFeilutbetalteBeløp() shouldBe Månedsbeløp(
                listOf(
                    MånedBeløp(februar(2021), Beløp(10779)),
                ),
            )
            it.kontooppstilling() shouldBe mapOf(
                februar(2021).tilPeriode() to Kontooppstilling(
                    debetYtelse = Kontobeløp.Debet(20779),
                    kreditYtelse = Kontobeløp.Kredit(20779),
                    debetFeilkonto = Kontobeløp.Debet(10779),
                    kreditFeilkonto = Kontobeløp.Kredit(0),
                    debetMotpostFeilkonto = Kontobeløp.Debet(0),
                    kreditMotpostFeilkonto = Kontobeløp.Kredit(10779),
                ).also {
                    it.sumUtbetaling shouldBe Kontobeløp.Summert(0)
                    it.sumFeilkonto shouldBe Kontobeløp.Summert(10779)
                    it.sumMotpostFeilkonto shouldBe Kontobeløp.Summert(-10779)
                },
                mars(2021).tilPeriode() to Kontooppstilling(
                    debetYtelse = Kontobeløp.Debet(10000),
                    kreditYtelse = Kontobeløp.Kredit(0),
                    debetFeilkonto = Kontobeløp.Debet(0),
                    kreditFeilkonto = Kontobeløp.Kredit(0),
                    debetMotpostFeilkonto = Kontobeløp.Debet(0),
                    kreditMotpostFeilkonto = Kontobeløp.Kredit(0),
                ).also {
                    it.sumUtbetaling shouldBe Kontobeløp.Summert(10000)
                    it.sumFeilkonto shouldBe Kontobeløp.Summert(0)
                    it.sumMotpostFeilkonto shouldBe Kontobeløp.Summert(0)
                },
            )
            it.oppsummering() shouldBe SimuleringsOppsummering(
                totalOppsummering = PeriodeOppsummering(
                    periode = februar(2021)..mars(2021),
                    sumTilUtbetaling = 10000,
                    sumEtterbetaling = 0,
                    sumFramtidigUtbetaling = 10000,
                    sumTotalUtbetaling = 20000,
                    sumTidligereUtbetalt = 20779,
                    sumFeilutbetaling = 10779,
                    sumReduksjonFeilkonto = 0,
                ),
                periodeOppsummering = listOf(
                    PeriodeOppsummering(
                        periode = februar(2021),
                        sumTilUtbetaling = 0,
                        sumEtterbetaling = 0,
                        sumFramtidigUtbetaling = 0,
                        sumTotalUtbetaling = 10000,
                        sumTidligereUtbetalt = 20779,
                        sumFeilutbetaling = 10779,
                        sumReduksjonFeilkonto = 0,
                    ),
                    PeriodeOppsummering(
                        periode = mars(2021),
                        sumTilUtbetaling = 10000,
                        sumEtterbetaling = 0,
                        sumFramtidigUtbetaling = 10000,
                        sumTotalUtbetaling = 10000,
                        sumTidligereUtbetalt = 0,
                        sumFeilutbetaling = 0,
                        sumReduksjonFeilkonto = 0,
                    ),
                ),
            )
            it.hentTotalUtbetaling() shouldBe Månedsbeløp(
                listOf(
                    MånedBeløp(februar(2021), Beløp(10000)),
                    MånedBeløp(mars(2021), Beløp(10000)),
                ),
            )
        }
    }

    @Test
    fun `mapper simulerte etterbetalinger`() {
        val simuleringXml = simuleringXml {
            datoBeregnet = "2021-04-14"
            belop = "19611.00"
            periode {
                periodeFom = "2021-02-01"
                periodeTom = "2021-02-28"
                stoppnivå {
                    forfall = "2021-04-14"
                    ordinær(30000)
                    skattedetalj(-4610)
                    kreditTidligereUtbetalt(-20779)
                }
            }
            periode {
                periodeFom = "2021-03-01"
                periodeTom = "2021-03-31"
                stoppnivå {
                    forfall = "2021-03-10"
                    ordinær(30000)
                    skattedetalj(-4610)
                }
            }
        }
        val responseMedEtterbetaling = xmlMapper.readValue(
            simuleringXml,
            GrensesnittResponse::class.java,
        ).response

        val request = utbetalingForSimulering()
        val actualSimulering = responseMedEtterbetaling.toSimulering(
            request = request,
            clock = fixedClock,
            soapRequest = SimuleringRequestBuilder(request).build(),
        ).getOrFail()
        val expectedSimulering = Simulering(
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
                                konto = kontoYtel,
                                belop = 30000,
                                tilbakeforing = false,
                                sats = 30000,
                                typeSats = "MND",
                                antallSats = 1,
                                uforegrad = 100,
                                klassekode = KlasseKode.SUUFORE,
                                klassekodeBeskrivelse = suBeskrivelse,
                                klasseType = KlasseType.YTEL,
                            ),
                            SimulertDetaljer(
                                faktiskFraOgMed = 1.februar(2021),
                                faktiskTilOgMed = 28.februar(2021),
                                konto = kontoYtel,
                                belop = -20779,
                                tilbakeforing = true,
                                sats = 20779,
                                typeSats = "MND",
                                antallSats = 0,
                                uforegrad = 100,
                                klassekode = KlasseKode.SUUFORE,
                                klassekodeBeskrivelse = suBeskrivelse,
                                klasseType = KlasseType.YTEL,
                            ),
                        ),
                    ),
                ),
                SimulertMåned(
                    måned = mars(2021),
                    utbetaling =
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
                                konto = kontoYtel,
                                belop = 30000,
                                tilbakeforing = false,
                                sats = 30000,
                                typeSats = typeSats,
                                antallSats = 1,
                                uforegrad = 100,
                                klassekode = KlasseKode.SUUFORE,
                                klassekodeBeskrivelse = suBeskrivelse,
                                klasseType = KlasseType.YTEL,
                            ),
                        ),
                    ),
                ),
            ),
            rawResponse = simuleringXml,
        )
        actualSimulering.shouldBeEqualToIgnoringFields(expectedSimulering, Simulering::rawResponse)
        actualSimulering.also {
            it.erAlleMånederUtenUtbetaling() shouldBe false
            it.hentTilUtbetaling() shouldBe Månedsbeløp(
                listOf(
                    MånedBeløp(februar(2021), Beløp(9221)),
                    MånedBeløp(mars(2021), Beløp(30000)),
                ),
            )
            it.hentTotalUtbetaling() shouldBe Månedsbeløp(
                listOf(
                    MånedBeløp(februar(2021), Beløp(30000)),
                    MånedBeløp(mars(2021), Beløp(30000)),
                ),
            )
            it.hentUtbetalteBeløp() shouldBe Månedsbeløp(
                listOf(
                    MånedBeløp(februar(2021), Beløp(20779)),
                ),
            )
            it.hentFeilutbetalteBeløp() shouldBe Månedsbeløp(emptyList())
            it.kontooppstilling() shouldBe mapOf(
                februar(2021).tilPeriode() to Kontooppstilling(
                    debetYtelse = Kontobeløp.Debet(30000),
                    kreditYtelse = Kontobeløp.Kredit(20779),
                    debetFeilkonto = Kontobeløp.Debet(0),
                    kreditFeilkonto = Kontobeløp.Kredit(0),
                    debetMotpostFeilkonto = Kontobeløp.Debet(0),
                    kreditMotpostFeilkonto = Kontobeløp.Kredit(0),
                ).also {
                    it.sumUtbetaling shouldBe Kontobeløp.Summert(9221)
                    it.sumFeilkonto shouldBe Kontobeløp.Summert(0)
                    it.sumMotpostFeilkonto shouldBe Kontobeløp.Summert(0)
                },
                mars(2021).tilPeriode() to Kontooppstilling(
                    debetYtelse = Kontobeløp.Debet(30000),
                    kreditYtelse = Kontobeløp.Kredit(0),
                    debetFeilkonto = Kontobeløp.Debet(0),
                    kreditFeilkonto = Kontobeløp.Kredit(0),
                    debetMotpostFeilkonto = Kontobeløp.Debet(0),
                    kreditMotpostFeilkonto = Kontobeløp.Kredit(0),
                ).also {
                    it.sumUtbetaling shouldBe Kontobeløp.Summert(30000)
                    it.sumFeilkonto shouldBe Kontobeløp.Summert(0)
                    it.sumMotpostFeilkonto shouldBe Kontobeløp.Summert(0)
                },
            )
            it.oppsummering() shouldBe SimuleringsOppsummering(
                totalOppsummering = PeriodeOppsummering(
                    periode = februar(2021)..mars(2021),
                    sumTilUtbetaling = 39221,
                    sumEtterbetaling = 9221,
                    sumFramtidigUtbetaling = 30000,
                    sumTotalUtbetaling = 60000,
                    sumTidligereUtbetalt = 20779,
                    sumFeilutbetaling = 0,
                    sumReduksjonFeilkonto = 0,
                ),
                periodeOppsummering = listOf(
                    PeriodeOppsummering(
                        periode = februar(2021),
                        sumTilUtbetaling = 9221,
                        sumEtterbetaling = 9221,
                        sumFramtidigUtbetaling = 0,
                        sumTotalUtbetaling = 30000,
                        sumTidligereUtbetalt = 20779,
                        sumFeilutbetaling = 0,
                        sumReduksjonFeilkonto = 0,
                    ),
                    PeriodeOppsummering(
                        periode = mars(2021),
                        sumTilUtbetaling = 30000,
                        sumEtterbetaling = 0,
                        sumFramtidigUtbetaling = 30000,
                        sumTotalUtbetaling = 30000,
                        sumTidligereUtbetalt = 0,
                        sumFeilutbetaling = 0,
                        sumReduksjonFeilkonto = 0,
                    ),
                ),
            )
            it.hentTotalUtbetaling() shouldBe Månedsbeløp(
                listOf(
                    MånedBeløp(februar(2021), Beløp(30000)),
                    MånedBeløp(mars(2021), Beløp(30000)),
                ),
            )
        }
    }

    @Test
    fun `filtrerer vekk detaljer som er ukjent eller uinteressant`() {
        val simuleringXml = simuleringXml {
            datoBeregnet = "2021-04-14"
            belop = "10390.00"
            periode {
                periodeFom = "2021-04-01"
                periodeTom = "2021-04-30"
                stoppnivå {
                    forfall = "2021-04-19"
                    ordinær(20779)
                    // Skatt filtreres alltid vekk
                    skattedetalj(-10389)
                    // Ukjent klassekode og typeKlasse filtreres vekk
                    ordinær(10389, klassekode = "TULL", typeKlasse = "TØYS")
                    // Ukjent klassekode filtreres vekk
                    ordinær(10389, klassekode = "TULL")
                    // Ukjent typeKlasse filtreres vekk
                    ordinær(10389, typeKlasse = "TØYS")
                }
            }
        }
        val responseMedFremtidigUtbetaling = xmlMapper.readValue(
            simuleringXml,
            GrensesnittResponse::class.java,
        ).response

        val request = utbetalingForSimulering()
        val expectedSimulering = Simulering(
            gjelderId = fnr,
            gjelderNavn = navn,
            datoBeregnet = 14.april(2021),
            nettoBeløp = 10390,
            måneder = listOf(
                SimulertMåned(
                    måned = april(2021),
                    utbetaling =
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
                                konto = kontoYtel,
                                belop = 20779,
                                tilbakeforing = false,
                                sats = 20779,
                                typeSats = typeSats,
                                antallSats = 1,
                                uforegrad = 100,
                                klassekode = KlasseKode.SUUFORE,
                                klassekodeBeskrivelse = suBeskrivelse,
                                klasseType = KlasseType.YTEL,
                            ),
                        ),
                    ),
                ),
            ),
            rawResponse = simuleringXml,
        )
        val actualSimulering = responseMedFremtidigUtbetaling.toSimulering(
            request = request,
            clock = fixedClock,
            soapRequest = SimuleringRequestBuilder(request).build(),
        ).getOrFail()
        actualSimulering.shouldBeEqualToIgnoringFields(expectedSimulering, Simulering::rawResponse)
        actualSimulering.also {
            it.kontooppstilling() shouldBe mapOf(
                april(2021).tilPeriode() to Kontooppstilling(
                    debetYtelse = Kontobeløp.Debet(20779),
                    kreditYtelse = Kontobeløp.Kredit(0),
                    debetFeilkonto = Kontobeløp.Debet(0),
                    kreditFeilkonto = Kontobeløp.Kredit(0),
                    debetMotpostFeilkonto = Kontobeløp.Debet(0),
                    kreditMotpostFeilkonto = Kontobeløp.Kredit(0),
                ).also {
                    it.sumUtbetaling shouldBe Kontobeløp.Summert(20779)
                    it.sumFeilkonto shouldBe Kontobeløp.Summert(0)
                    it.sumMotpostFeilkonto shouldBe Kontobeløp.Summert(0)
                },
            )
        }
    }

    @Test
    fun `feiler ved flere utbetalingsperioder for samme fagsystemId`() {
        val simuleringXml = simuleringXml {
            periode {
                stoppnivå {}
                stoppnivå {}
            }
        }
        val responseMedFremtidigUtbetaling = xmlMapper.readValue(
            simuleringXml,
            GrensesnittResponse::class.java,
        ).response

        val request = utbetalingForSimulering()
        val logMock = mock<Logger>()
        val sikkerLoggMock = mock<Logger>()
        val soapRequest = SimuleringRequestBuilder(request).build()
        responseMedFremtidigUtbetaling.toSimulering(
            request = request,
            clock = fixedClock,
            soapRequest = soapRequest,
            log = logMock,
            sikkerLogg = sikkerLoggMock,
        ) shouldBe SimuleringFeilet.TekniskFeil.left()
        verify(logMock).error(
            // Simulering inneholder flere utbetalinger for samme sak $saksnummer. Se sikkerlogg for flere detaljer og feilmelding.
            argThat { it shouldBe "Kunne ikke mappe SimulerBeregningResponse til Simulering for saksnummer $saksnummer. Se sikkerlogg for stacktrace og context." },
        )
        verify(sikkerLoggMock).error(
            argThat {
                it shouldContain "Kunne ikke mappe SimulerBeregningResponse til Simulering for saksnummer $saksnummer."
            },
            argThat<Throwable> {
                it shouldBe IllegalStateException("Simulering inneholder flere utbetalinger for samme sak $saksnummer.")
            },
        )
    }

    @Test
    fun `filtrerer vekk andre fagsystemid-er`() {
        val simuleringXml = simuleringXml {
            periode {
                stoppnivå {
                    // Forventer at denne filtreres vekk
                    fagsystemId = "1000"
                }
                stoppnivå {}
            }
        }
        val responseMedFremtidigUtbetaling = xmlMapper.readValue(
            simuleringXml,
            GrensesnittResponse::class.java,
        ).response

        val request = utbetalingForSimulering()
        responseMedFremtidigUtbetaling.toSimulering(
            request = request,
            clock = fixedClock,
            soapRequest = SimuleringRequestBuilder(request).build(),
        ).getOrFail().måneder.map { it.utbetaling!!.fagSystemId } shouldBe listOf(fagsystemId)
    }

    @Test
    fun `filtrerer vekk andre kodeFagomraade-er`() {
        val simuleringXml = simuleringXml {
            periode {
                stoppnivå {
                    // Forventer at denne filtreres vekk
                    kodeFagomraade = "UFORE"
                }
                stoppnivå {}
            }
        }
        val responseMedFremtidigUtbetaling = xmlMapper.readValue(
            simuleringXml,
            GrensesnittResponse::class.java,
        ).response

        val request = utbetalingForSimulering()
        responseMedFremtidigUtbetaling.toSimulering(
            request = request,
            clock = fixedClock,
            soapRequest = SimuleringRequestBuilder(request).build(),
        ).getOrFail().måneder.map { it.utbetaling!! }.size.shouldBe(1)
    }

    @Test
    fun `mapping med åpen feilkonto`() {
        // TODO jah: Ved reduksjon/annulering av en allerede feilkonto, vil fortegnene på FEIL/MOTP være snudd. I tillegg ser det ikke ut som tilbakeforing kun er i bruk ved reduksjon og ikke annullering?
        val rawResponse = jsonMedÅpenFeilkonto
        val responseMedÅpenFeilkonto = deserialize<SimulerBeregningResponse>(
            rawResponse,
        )

        val request = utbetalingForSimulering()
        responseMedÅpenFeilkonto.toSimulering(
            request = request,
            clock = fixedClock,
            soapRequest = SimuleringRequestBuilder(request).build(),
        ).getOrFail().also {
            it.erAlleMånederUtenUtbetaling() shouldBe false
            it.hentTilUtbetaling() shouldBe Månedsbeløp(emptyList())
            it.hentTotalUtbetaling() shouldBe Månedsbeløp(
                listOf(
                    MånedBeløp(august(2022), Beløp(21181)),
                ),
            )
            it.hentUtbetalteBeløp() shouldBe Månedsbeløp(
                listOf(
                    MånedBeløp(august(2022), Beløp(21181)),
                ),
            )
            it.hentFeilutbetalteBeløp() shouldBe Månedsbeløp(emptyList())
            it.kontooppstilling() shouldBe mapOf(
                august(2022).tilPeriode() to Kontooppstilling(
                    debetYtelse = Kontobeløp.Debet(21181),
                    kreditYtelse = Kontobeløp.Kredit(21181),
                    debetFeilkonto = Kontobeløp.Debet(0),
                    kreditFeilkonto = Kontobeløp.Kredit(21181),
                    debetMotpostFeilkonto = Kontobeløp.Debet(21181),
                    kreditMotpostFeilkonto = Kontobeløp.Kredit(0),
                ).also {
                    it.sumUtbetaling shouldBe Kontobeløp.Summert(0)
                    it.sumFeilkonto shouldBe Kontobeløp.Summert(-21181)
                    it.sumMotpostFeilkonto shouldBe Kontobeløp.Summert(21181)
                },
            )
            it.oppsummering() shouldBe SimuleringsOppsummering(
                totalOppsummering = PeriodeOppsummering(
                    periode = august(2022),
                    sumTilUtbetaling = 0,
                    sumEtterbetaling = 0,
                    sumFramtidigUtbetaling = 0,
                    sumTotalUtbetaling = 21181,
                    sumTidligereUtbetalt = 21181,
                    sumFeilutbetaling = 0,
                    sumReduksjonFeilkonto = 21181,
                ),
                periodeOppsummering = listOf(
                    PeriodeOppsummering(
                        periode = august(2022),
                        sumTilUtbetaling = 0,
                        sumEtterbetaling = 0,
                        sumFramtidigUtbetaling = 0,
                        sumTotalUtbetaling = 21181,
                        sumTidligereUtbetalt = 21181,
                        sumFeilutbetaling = 0,
                        sumReduksjonFeilkonto = 21181,
                    ),
                ),
            )
            it.hentTotalUtbetaling() shouldBe Månedsbeløp(
                listOf(
                    MånedBeløp(august(2022), Beløp(21181)),
                ),
            )
        }
    }

    private val jsonMedÅpenFeilkonto = """
        {
          "simulering": {
            "gjelderId": "$fnr",
            "gjelderNavn": "$navn",
            "datoBeregnet": "2022-11-17",
            "kodeFaggruppe": "INNT",
            "belop": 0.00,
            "beregningsPeriode": [
              {
                "periodeFom": "2022-08-01",
                "periodeTom": "2022-08-31",
                "beregningStoppnivaa": [
                  {
                    "kodeFagomraade": "SUUFORE",
                    "stoppNivaaId": 1,
                    "behandlendeEnhet": "8020",
                    "oppdragsId": 60937907,
                    "fagsystemId": "$fagsystemId",
                    "kid": "",
                    "utbetalesTilId": "$fnr",
                    "utbetalesTilNavn": "$navn",
                    "bilagsType": "U",
                    "forfall": "2022-11-17",
                    "feilkonto": true,
                    "beregningStoppnivaaDetaljer": [
                      {
                        "faktiskFom": "2022-08-01",
                        "faktiskTom": "2022-08-31",
                        "kontoStreng": "0630986",
                        "behandlingskode": "0",
                        "belop": -21181.00,
                        "trekkVedtakId": 0,
                        "stonadId": "",
                        "korrigering": "",
                        "tilbakeforing": false,
                        "linjeId": 0,
                        "sats": 0.00,
                        "typeSats": "",
                        "antallSats": 0.00,
                        "saksbehId": "K231B214",
                        "uforeGrad": 0,
                        "kravhaverId": "",
                        "delytelseId": "",
                        "bostedsenhet": "8020",
                        "skykldnerId": "",
                        "klassekode": "KL_KODE_FEIL_INNT",
                        "klasseKodeBeskrivelse": "Feilutbetaling Inntektsytelser",
                        "typeKlasse": "FEIL",
                        "typeKlasseBeskrivelse": "Klassetype for feilkontoer",
                        "refunderesOrgNr": ""
                      },
                      {
                        "faktiskFom": "2022-08-01",
                        "faktiskTom": "2022-08-31",
                        "kontoStreng": "0902900",
                        "behandlingskode": "0",
                        "belop": 21181.00,
                        "trekkVedtakId": 0,
                        "stonadId": "",
                        "korrigering": "",
                        "tilbakeforing": false,
                        "linjeId": 0,
                        "sats": 0.00,
                        "typeSats": "",
                        "antallSats": 0.00,
                        "saksbehId": "K231B214",
                        "uforeGrad": 0,
                        "kravhaverId": "",
                        "delytelseId": "",
                        "bostedsenhet": "8020",
                        "skykldnerId": "",
                        "klassekode": "TBMOTOBS",
                        "klasseKodeBeskrivelse": "Feilutbetaling motkonto til OBS konto",
                        "typeKlasse": "MOTP",
                        "typeKlasseBeskrivelse": "Klassetype for motposteringskonto",
                        "refunderesOrgNr": ""
                      },
                      {
                        "faktiskFom": "2022-08-01",
                        "faktiskTom": "2022-08-31",
                        "kontoStreng": "4952000",
                        "behandlingskode": "2",
                        "belop": -21181.00,
                        "trekkVedtakId": 0,
                        "stonadId": "",
                        "korrigering": "",
                        "tilbakeforing": false,
                        "linjeId": 0,
                        "sats": 0.00,
                        "typeSats": "",
                        "antallSats": 0.00,
                        "saksbehId": "K231B214",
                        "uforeGrad": 0,
                        "kravhaverId": "",
                        "delytelseId": "",
                        "bostedsenhet": "8020",
                        "skykldnerId": "",
                        "klassekode": "SUUFORE",
                        "klasseKodeBeskrivelse": "Supplerende stønad Uføre",
                        "typeKlasse": "YTEL",
                        "typeKlasseBeskrivelse": "Klassetype for ytelseskonti",
                        "refunderesOrgNr": ""
                      },
                      {
                        "faktiskFom": "2022-08-01",
                        "faktiskTom": "2022-08-31",
                        "kontoStreng": "4952000",
                        "behandlingskode": "2",
                        "belop": 21181.00,
                        "trekkVedtakId": 0,
                        "stonadId": "",
                        "korrigering": "",
                        "tilbakeforing": false,
                        "linjeId": 4,
                        "sats": 21181.00,
                        "typeSats": "MND",
                        "antallSats": 1.00,
                        "saksbehId": "SU",
                        "uforeGrad": 100,
                        "kravhaverId": "",
                        "delytelseId": "da87bf28-50f4-4bee-bc05-a62333",
                        "bostedsenhet": "8020",
                        "skykldnerId": "",
                        "klassekode": "SUUFORE",
                        "klasseKodeBeskrivelse": "Supplerende stønad Uføre",
                        "typeKlasse": "YTEL",
                        "typeKlasseBeskrivelse": "Klassetype for ytelseskonti",
                        "refunderesOrgNr": ""
                      }
                    ]
                  }
                ]
              }
            ]
          },
          "infomelding": null
        }
    """.trimIndent()

    @Test
    fun `mapping med åpen feilkonto annullering og etterbetaling`() {
        // TODO jah: Ved reduksjon/annulering av en allerede feilkonto, vil fortegnene på FEIL/MOTP være snudd. I tillegg ser det ikke ut som tilbakeforing kun er i bruk ved reduksjon og ikke annullering?
        val rawResponse = jsonMedÅpenFeilkontoOgEtterbetaling
        val responseMedÅpenFeilkonto = deserialize<SimulerBeregningResponse>(
            rawResponse,
        )

        val request = utbetalingForSimulering()
        responseMedÅpenFeilkonto.toSimulering(
            request = request,
            clock = fixedClock,
            soapRequest = SimuleringRequestBuilder(request).build(),
        ).getOrFail().also {
            it.erAlleMånederUtenUtbetaling() shouldBe false
            it.hentTilUtbetaling() shouldBe Månedsbeløp(
                listOf(
                    MånedBeløp(august(2022), Beløp(1858)),
                ),
            )
            it.hentTotalUtbetaling() shouldBe Månedsbeløp(
                listOf(
                    MånedBeløp(august(2022), Beløp(23039)),
                ),
            )
            it.hentUtbetalteBeløp() shouldBe Månedsbeløp(
                listOf(
                    MånedBeløp(august(2022), Beløp(21181)),
                ),
            )
            it.hentFeilutbetalteBeløp() shouldBe Månedsbeløp(emptyList())
            it.kontooppstilling() shouldBe mapOf(
                august(2022).tilPeriode() to Kontooppstilling(
                    debetYtelse = Kontobeløp.Debet(23039),
                    kreditYtelse = Kontobeløp.Kredit(21181),
                    debetFeilkonto = Kontobeløp.Debet(0),
                    kreditFeilkonto = Kontobeløp.Kredit(21181),
                    debetMotpostFeilkonto = Kontobeløp.Debet(21181),
                    kreditMotpostFeilkonto = Kontobeløp.Kredit(0),
                ).also {
                    it.sumUtbetaling shouldBe Kontobeløp.Summert(1858)
                    it.sumFeilkonto shouldBe Kontobeløp.Summert(-21181)
                    it.sumMotpostFeilkonto shouldBe Kontobeløp.Summert(21181)
                },
            )
            it.oppsummering() shouldBe SimuleringsOppsummering(
                totalOppsummering = PeriodeOppsummering(
                    periode = august(2022),
                    sumTilUtbetaling = 1858,
                    sumEtterbetaling = 1858,
                    sumFramtidigUtbetaling = 0,
                    sumTotalUtbetaling = 23039,
                    sumTidligereUtbetalt = 21181,
                    sumFeilutbetaling = 0,
                    sumReduksjonFeilkonto = 21181,
                ),
                periodeOppsummering = listOf(
                    PeriodeOppsummering(
                        periode = august(2022),
                        sumTilUtbetaling = 1858,
                        sumEtterbetaling = 1858,
                        sumFramtidigUtbetaling = 0,
                        sumTotalUtbetaling = 23039,
                        sumTidligereUtbetalt = 21181,
                        sumFeilutbetaling = 0,
                        sumReduksjonFeilkonto = 21181,
                    ),
                ),
            )
        }
    }

    private val jsonMedÅpenFeilkontoOgEtterbetaling = """
        {
          "simulering": {
            "gjelderId": "$fnr",
            "gjelderNavn": "$navn",
            "datoBeregnet": "2022-11-17",
            "kodeFaggruppe": "INNT",
            "belop": 1673.00,
            "beregningsPeriode": [
              {
                "periodeFom": "2022-08-01",
                "periodeTom": "2022-08-31",
                "beregningStoppnivaa": [
                  {
                    "kodeFagomraade": "SUUFORE",
                    "stoppNivaaId": 1,
                    "behandlendeEnhet": "8020",
                    "oppdragsId": 60937907,
                    "fagsystemId": "$fagsystemId",
                    "kid": "",
                    "utbetalesTilId": "$fnr",
                    "utbetalesTilNavn": "$navn",
                    "bilagsType": "U",
                    "forfall": "2022-11-17",
                    "feilkonto": true,
                    "beregningStoppnivaaDetaljer": [
                      {
                        "faktiskFom": "2022-08-01",
                        "faktiskTom": "2022-08-31",
                        "kontoStreng": "0630986",
                        "behandlingskode": "0",
                        "belop": -21181.00,
                        "trekkVedtakId": 0,
                        "stonadId": "",
                        "korrigering": "",
                        "tilbakeforing": false,
                        "linjeId": 0,
                        "sats": 0.00,
                        "typeSats": "",
                        "antallSats": 0.00,
                        "saksbehId": "K231B214",
                        "uforeGrad": 0,
                        "kravhaverId": "",
                        "delytelseId": "",
                        "bostedsenhet": "8020",
                        "skykldnerId": "",
                        "klassekode": "KL_KODE_FEIL_INNT",
                        "klasseKodeBeskrivelse": "Feilutbetaling Inntektsytelser",
                        "typeKlasse": "FEIL",
                        "typeKlasseBeskrivelse": "Klassetype for feilkontoer",
                        "refunderesOrgNr": ""
                      },
                      {
                        "faktiskFom": "2022-08-01",
                        "faktiskTom": "2022-08-31",
                        "kontoStreng": "0902900",
                        "behandlingskode": "0",
                        "belop": 21181.00,
                        "trekkVedtakId": 0,
                        "stonadId": "",
                        "korrigering": "",
                        "tilbakeforing": false,
                        "linjeId": 0,
                        "sats": 0.00,
                        "typeSats": "",
                        "antallSats": 0.00,
                        "saksbehId": "K231B214",
                        "uforeGrad": 0,
                        "kravhaverId": "",
                        "delytelseId": "",
                        "bostedsenhet": "8020",
                        "skykldnerId": "",
                        "klassekode": "TBMOTOBS",
                        "klasseKodeBeskrivelse": "Feilutbetaling motkonto til OBS konto",
                        "typeKlasse": "MOTP",
                        "typeKlasseBeskrivelse": "Klassetype for motposteringskonto",
                        "refunderesOrgNr": ""
                      },
                      {
                        "faktiskFom": "2022-08-01",
                        "faktiskTom": "2022-08-31",
                        "kontoStreng": "4952000",
                        "behandlingskode": "2",
                        "belop": -21181.00,
                        "trekkVedtakId": 0,
                        "stonadId": "",
                        "korrigering": "",
                        "tilbakeforing": false,
                        "linjeId": 0,
                        "sats": 0.00,
                        "typeSats": "",
                        "antallSats": 0.00,
                        "saksbehId": "K231B214",
                        "uforeGrad": 0,
                        "kravhaverId": "",
                        "delytelseId": "",
                        "bostedsenhet": "8020",
                        "skykldnerId": "",
                        "klassekode": "SUUFORE",
                        "klasseKodeBeskrivelse": "Supplerende stønad Uføre",
                        "typeKlasse": "YTEL",
                        "typeKlasseBeskrivelse": "Klassetype for ytelseskonti",
                        "refunderesOrgNr": ""
                      },
                      {
                        "faktiskFom": "2022-08-01",
                        "faktiskTom": "2022-08-31",
                        "kontoStreng": "0510000",
                        "behandlingskode": "0",
                        "belop": -185.00,
                        "trekkVedtakId": 12333856,
                        "stonadId": "",
                        "korrigering": "",
                        "tilbakeforing": false,
                        "linjeId": 0,
                        "sats": 0.00,
                        "typeSats": "MND",
                        "antallSats": 31.00,
                        "saksbehId": "SU",
                        "uforeGrad": 0,
                        "kravhaverId": "",
                        "delytelseId": "",
                        "bostedsenhet": "8020",
                        "skykldnerId": "",
                        "klassekode": "FSKTSKAT",
                        "klasseKodeBeskrivelse": "Forskuddskatt",
                        "typeKlasse": "SKAT",
                        "typeKlasseBeskrivelse": "Klassetype for skatt",
                        "refunderesOrgNr": ""
                      },
                      {
                        "faktiskFom": "2022-08-01",
                        "faktiskTom": "2022-08-31",
                        "kontoStreng": "4952000",
                        "behandlingskode": "2",
                        "belop": 23039.00,
                        "trekkVedtakId": 0,
                        "stonadId": "",
                        "korrigering": "",
                        "tilbakeforing": false,
                        "linjeId": 4,
                        "sats": 23039.00,
                        "typeSats": "MND",
                        "antallSats": 1.00,
                        "saksbehId": "SU",
                        "uforeGrad": 100,
                        "kravhaverId": "",
                        "delytelseId": "39e2f790-3c75-4e70-9889-a768bb",
                        "bostedsenhet": "8020",
                        "skykldnerId": "",
                        "klassekode": "SUUFORE",
                        "klasseKodeBeskrivelse": "Supplerende stønad Uføre",
                        "typeKlasse": "YTEL",
                        "typeKlasseBeskrivelse": "Klassetype for ytelseskonti",
                        "refunderesOrgNr": ""
                      }
                    ]
                  }
                ]
              }
            ]
          },
          "infomelding": null
        }
    """.trimIndent()

    @Test
    fun `mapping med reduksjon av åpen feilkonto og ikke trimmet verdier`() {
        // TODO jah: Ved reduksjon/annulering av en allerede feilkonto, vil fortegnene på FEIL/MOTP være snudd. I tillegg ser det ikke ut som tilbakeforing kun er i bruk ved reduksjon og ikke annullering?
        val rawResponse = jsonMedReduksjonAvFeilkonto
        val responseMedÅpenFeilkonto = deserialize<SimulerBeregningResponse>(
            rawResponse,
        )

        val request = utbetalingForSimulering()
        responseMedÅpenFeilkonto.toSimulering(
            request = request,
            clock = fixedClock,
            soapRequest = SimuleringRequestBuilder(request).build(),
        ).getOrFail().also {
            it.erAlleMånederUtenUtbetaling() shouldBe false
            it.hentTilUtbetaling() shouldBe Månedsbeløp(emptyList())
            it.hentTotalUtbetaling() shouldBe Månedsbeløp(
                listOf(
                    MånedBeløp(januar(2022), Beløp(8989)),
                ),
            )
            it.hentUtbetalteBeløp() shouldBe Månedsbeløp(
                listOf(
                    MånedBeløp(januar(2022), Beløp(8989)),
                ),
            )
            it.hentFeilutbetalteBeløp() shouldBe Månedsbeløp(emptyList())
            it.kontooppstilling() shouldBe mapOf(
                januar(2022) to Kontooppstilling(
                    debetYtelse = Kontobeløp.Debet(8989),
                    kreditYtelse = Kontobeløp.Kredit(8989),
                    debetFeilkonto = Kontobeløp.Debet(0),
                    kreditFeilkonto = Kontobeløp.Kredit(2000),
                    debetMotpostFeilkonto = Kontobeløp.Debet(2000),
                    kreditMotpostFeilkonto = Kontobeløp.Kredit(0),
                ).also {
                    it.sumUtbetaling shouldBe Kontobeløp.Summert(0)
                    it.sumFeilkonto shouldBe Kontobeløp.Summert(-2000)
                    it.sumMotpostFeilkonto shouldBe Kontobeløp.Summert(2000)
                },
            )
            it.oppsummering() shouldBe SimuleringsOppsummering(
                totalOppsummering = PeriodeOppsummering(
                    periode = januar(2022),
                    sumTilUtbetaling = 0,
                    sumEtterbetaling = 0,
                    sumFramtidigUtbetaling = 0,
                    sumTotalUtbetaling = 8989,
                    sumTidligereUtbetalt = 8989,
                    sumFeilutbetaling = 0,
                    sumReduksjonFeilkonto = 2000,
                ),
                periodeOppsummering = listOf(
                    PeriodeOppsummering(
                        periode = januar(2022),
                        sumTilUtbetaling = 0,
                        sumEtterbetaling = 0,
                        sumFramtidigUtbetaling = 0,
                        sumTotalUtbetaling = 8989,
                        sumTidligereUtbetalt = 8989,
                        sumFeilutbetaling = 0,
                        sumReduksjonFeilkonto = 2000,
                    ),
                ),
            )
        }
    }

    @Test
    fun `Tilbakeføring utligner hverandre`() {
        val rawXml = """
                <SimulerBeregningResponse>
                    <simulering>
                        <gjelderId>12345678901</gjelderId>
                        <gjelderNavn>Fiskus Fiske</gjelderNavn>
                        <datoBeregnet>2023-06-01</datoBeregnet>
                        <kodeFaggruppe>INNT</kodeFaggruppe>
                        <belop>2480.00</belop>
                        <beregningsPeriode>
                            <periodeFom>2023-05-01</periodeFom>
                            <periodeTom>2023-05-31</periodeTom>
                            <beregningStoppnivaa>
                                <kodeFagomraade>SUUFORE</kodeFagomraade>
                                <stoppNivaaId>1</stoppNivaaId>
                                <behandlendeEnhet>8020</behandlendeEnhet>
                                <oppdragsId>123123</oppdragsId>
                                <fagsystemId>2021</fagsystemId>
                                <kid></kid>
                                <utbetalesTilId>12345678901</utbetalesTilId>
                                <utbetalesTilNavn>Fiskus Fiske</utbetalesTilNavn>
                                <bilagsType>U</bilagsType>
                                <forfall>2023-06-01</forfall>
                                <feilkonto>false</feilkonto>
                                <beregningStoppnivaaDetaljer>
                                    <faktiskFom>2023-05-01</faktiskFom>
                                    <faktiskTom>2023-05-31</faktiskTom>
                                    <kontoStreng>0510000</kontoStreng>
                                    <behandlingskode>0</behandlingskode>
                                    <belop>2480.00</belop>
                                    <trekkVedtakId>0</trekkVedtakId>
                                    <stonadId></stonadId>
                                    <korrigering></korrigering>
                                    <tilbakeforing>false</tilbakeforing>
                                    <linjeId>0</linjeId>
                                    <sats>100.00</sats>
                                    <typeSats>SALP</typeSats>
                                    <antallSats>0.00</antallSats>
                                    <saksbehId>Saksbehandlersen</saksbehId>
                                    <uforeGrad>0</uforeGrad>
                                    <kravhaverId></kravhaverId>
                                    <delytelseId></delytelseId>
                                    <bostedsenhet>8020</bostedsenhet>
                                    <skykldnerId></skykldnerId>
                                    <klassekode>FSKTSKAT</klassekode>
                                    <klasseKodeBeskrivelse>Forskuddskatt</klasseKodeBeskrivelse>
                                    <typeKlasse>SKAT</typeKlasse>
                                    <typeKlasseBeskrivelse>Klassetype for skatt</typeKlasseBeskrivelse>
                                    <refunderesOrgNr></refunderesOrgNr>
                                </beregningStoppnivaaDetaljer>
                                <beregningStoppnivaaDetaljer>
                                    <faktiskFom>2023-05-01</faktiskFom>
                                    <faktiskTom>2023-05-31</faktiskTom>
                                    <kontoStreng>0510000</kontoStreng>
                                    <behandlingskode>0</behandlingskode>
                                    <belop>-2480.00</belop>
                                    <trekkVedtakId>12341234</trekkVedtakId>
                                    <stonadId></stonadId>
                                    <korrigering></korrigering>
                                    <tilbakeforing>false</tilbakeforing>
                                    <linjeId>0</linjeId>
                                    <sats>100.00</sats>
                                    <typeSats>SALP</typeSats>
                                    <antallSats>0.00</antallSats>
                                    <saksbehId>Saksbehandlersen</saksbehId>
                                    <uforeGrad>0</uforeGrad>
                                    <kravhaverId></kravhaverId>
                                    <delytelseId></delytelseId>
                                    <bostedsenhet>8020</bostedsenhet>
                                    <skykldnerId></skykldnerId>
                                    <klassekode>FSKTSKAT</klassekode>
                                    <klasseKodeBeskrivelse>Forskuddskatt</klasseKodeBeskrivelse>
                                    <typeKlasse>SKAT</typeKlasse>
                                    <typeKlasseBeskrivelse>Klassetype for skatt</typeKlasseBeskrivelse>
                                    <refunderesOrgNr></refunderesOrgNr>
                                </beregningStoppnivaaDetaljer>
                                <beregningStoppnivaaDetaljer>
                                    <faktiskFom>2023-05-01</faktiskFom>
                                    <faktiskTom>2023-05-31</faktiskTom>
                                    <kontoStreng>4952000</kontoStreng>
                                    <behandlingskode>2</behandlingskode>
                                    <belop>2480.00</belop>
                                    <trekkVedtakId>0</trekkVedtakId>
                                    <stonadId></stonadId>
                                    <korrigering></korrigering>
                                    <tilbakeforing>false</tilbakeforing>
                                    <linjeId>8</linjeId>
                                    <sats>2480.00</sats>
                                    <typeSats>MND</typeSats>
                                    <antallSats>1.00</antallSats>
                                    <saksbehId>SU</saksbehId>
                                    <uforeGrad>100</uforeGrad>
                                    <kravhaverId></kravhaverId>
                                    <delytelseId>3e6300be-47f7-4be5-b69b-ab36a5</delytelseId>
                                    <bostedsenhet>8020</bostedsenhet>
                                    <skykldnerId></skykldnerId>
                                    <klassekode>SUUFORE</klassekode>
                                    <klasseKodeBeskrivelse>Supplerende stønad Uføre</klasseKodeBeskrivelse>
                                    <typeKlasse>YTEL</typeKlasse>
                                    <typeKlasseBeskrivelse>Klassetype for ytelseskonti</typeKlasseBeskrivelse>
                                    <refunderesOrgNr></refunderesOrgNr>
                                </beregningStoppnivaaDetaljer>
                                <beregningStoppnivaaDetaljer>
                                    <faktiskFom>2023-05-01</faktiskFom>
                                    <faktiskTom>2023-05-31</faktiskTom>
                                    <kontoStreng>4952000</kontoStreng>
                                    <behandlingskode>2</behandlingskode>
                                    <belop>2174.00</belop>
                                    <trekkVedtakId>0</trekkVedtakId>
                                    <stonadId></stonadId>
                                    <korrigering></korrigering>
                                    <tilbakeforing>true</tilbakeforing>
                                    <linjeId>5</linjeId>
                                    <sats>2174.00</sats>
                                    <typeSats>MND</typeSats>
                                    <antallSats>0.00</antallSats>
                                    <saksbehId>Saksbehandlersen</saksbehId>
                                    <uforeGrad>100</uforeGrad>
                                    <kravhaverId></kravhaverId>
                                    <delytelseId></delytelseId>
                                    <bostedsenhet>8020</bostedsenhet>
                                    <skykldnerId></skykldnerId>
                                    <klassekode>SUUFORE</klassekode>
                                    <klasseKodeBeskrivelse>Supplerende stønad Uføre</klasseKodeBeskrivelse>
                                    <typeKlasse>YTEL</typeKlasse>
                                    <typeKlasseBeskrivelse>Klassetype for ytelseskonti</typeKlasseBeskrivelse>
                                    <refunderesOrgNr></refunderesOrgNr>
                                </beregningStoppnivaaDetaljer>
                                <beregningStoppnivaaDetaljer>
                                    <faktiskFom>2023-05-01</faktiskFom>
                                    <faktiskTom>2023-05-31</faktiskTom>
                                    <kontoStreng>4952000</kontoStreng>
                                    <behandlingskode>2</behandlingskode>
                                    <belop>-2174.00</belop>
                                    <trekkVedtakId>0</trekkVedtakId>
                                    <stonadId></stonadId>
                                    <korrigering></korrigering>
                                    <tilbakeforing>true</tilbakeforing>
                                    <linjeId>7</linjeId>
                                    <sats>2174.00</sats>
                                    <typeSats>MND</typeSats>
                                    <antallSats>0.00</antallSats>
                                    <saksbehId>Saksbehandlersen</saksbehId>
                                    <uforeGrad>100</uforeGrad>
                                    <kravhaverId></kravhaverId>
                                    <delytelseId></delytelseId>
                                    <bostedsenhet>8020</bostedsenhet>
                                    <skykldnerId></skykldnerId>
                                    <klassekode>SUUFORE</klassekode>
                                    <klasseKodeBeskrivelse>Supplerende stønad Uføre</klasseKodeBeskrivelse>
                                    <typeKlasse>YTEL</typeKlasse>
                                    <typeKlasseBeskrivelse>Klassetype for ytelseskonti</typeKlasseBeskrivelse>
                                    <refunderesOrgNr></refunderesOrgNr>
                                </beregningStoppnivaaDetaljer>
                            </beregningStoppnivaa>
                        </beregningsPeriode>
                    </simulering>
                </SimulerBeregningResponse>
            """
        val response = xmlMapper.readValue<SimulerBeregningResponse>(rawXml)

        response.simulering.belop shouldBe BigDecimal("2480.00")

        val request = utbetalingForSimulering(
            saksnummer = Saksnummer.parse("2021"),
        )
        val actualSimulering = response.toSimulering(
            request = request,
            clock = fixedClock,
            soapRequest = SimuleringRequestBuilder(request).build(),
        ).getOrFail()

        actualSimulering.hentTotalUtbetaling() shouldBe Månedsbeløp(listOf(MånedBeløp(mai(2023), Beløp(2480))))
    }

    private val jsonMedReduksjonAvFeilkonto = """
        {
          "simulering": {
            "gjelderId": "$fnr",
            "gjelderNavn": "$navn",
            "datoBeregnet": "2022-11-29",
            "kodeFaggruppe": "INNT    ",
            "belop": 0.00,
            "beregningsPeriode": [
              {
                "periodeFom": "2022-01-01",
                "periodeTom": "2022-01-31",
                "beregningStoppnivaa": [
                  {
                    "kodeFagomraade": "SUUFORE ",
                    "stoppNivaaId": 1,
                    "behandlendeEnhet": "8020         ",
                    "oppdragsId": 62847682,
                    "fagsystemId": "$fagsystemId                      ",
                    "kid": "",
                    "utbetalesTilId": "$fnr",
                    "utbetalesTilNavn": "$navn",
                    "bilagsType": "U ",
                    "forfall": "2022-11-29",
                    "feilkonto": true,
                    "beregningStoppnivaaDetaljer": [
                      {
                        "faktiskFom": "2022-01-01",
                        "faktiskTom": "2022-01-31",
                        "kontoStreng": "0902900            ",
                        "behandlingskode": "0",
                        "belop": 2000.00,
                        "trekkVedtakId": 0,
                        "stonadId": "          ",
                        "korrigering": " ",
                        "tilbakeforing": true,
                        "linjeId": 0,
                        "sats": 0.00,
                        "typeSats": "    ",
                        "antallSats": 0.00,
                        "saksbehId": "K231B214",
                        "uforeGrad": 0,
                        "kravhaverId": "           ",
                        "delytelseId": "                              ",
                        "bostedsenhet": "8020         ",
                        "skykldnerId": "           ",
                        "klassekode": "TBMOTOBS            ",
                        "klasseKodeBeskrivelse": "Feilutbetaling motkonto til OBS konto             ",
                        "typeKlasse": "MOTP",
                        "typeKlasseBeskrivelse": "Klassetype for motposteringskonto                 ",
                        "refunderesOrgNr": "           "
                      },
                      {
                        "faktiskFom": "2022-01-01",
                        "faktiskTom": "2022-01-31",
                        "kontoStreng": "4952000            ",
                        "behandlingskode": "2",
                        "belop": -2000.00,
                        "trekkVedtakId": 0,
                        "stonadId": "          ",
                        "korrigering": " ",
                        "tilbakeforing": true,
                        "linjeId": 0,
                        "sats": 0.00,
                        "typeSats": "    ",
                        "antallSats": 0.00,
                        "saksbehId": "K231B214",
                        "uforeGrad": 0,
                        "kravhaverId": "           ",
                        "delytelseId": "                              ",
                        "bostedsenhet": "8020         ",
                        "skykldnerId": "           ",
                        "klassekode": "SUUFORE             ",
                        "klasseKodeBeskrivelse": "Supplerende stønad Uføre                          ",
                        "typeKlasse": "YTEL",
                        "typeKlasseBeskrivelse": "Klassetype for ytelseskonti                       ",
                        "refunderesOrgNr": "           "
                      },
                      {
                        "faktiskFom": "2022-01-01",
                        "faktiskTom": "2022-01-31",
                        "kontoStreng": "0630986            ",
                        "behandlingskode": "0",
                        "belop": -2000.00,
                        "trekkVedtakId": 0,
                        "stonadId": "          ",
                        "korrigering": " ",
                        "tilbakeforing": true,
                        "linjeId": 0,
                        "sats": 0.00,
                        "typeSats": "    ",
                        "antallSats": 0.00,
                        "saksbehId": "K231B214",
                        "uforeGrad": 0,
                        "kravhaverId": "           ",
                        "delytelseId": "                              ",
                        "bostedsenhet": "8020         ",
                        "skykldnerId": "           ",
                        "klassekode": "KL_KODE_FEIL_INNT   ",
                        "klasseKodeBeskrivelse": "Feilutbetaling Inntektsytelser                    ",
                        "typeKlasse": "FEIL",
                        "typeKlasseBeskrivelse": "Klassetype for feilkontoer                        ",
                        "refunderesOrgNr": "           "
                      },
                      {
                        "faktiskFom": "2022-01-01",
                        "faktiskTom": "2022-01-31",
                        "kontoStreng": "4952000            ",
                        "behandlingskode": "2",
                        "belop": 8989.00,
                        "trekkVedtakId": 0,
                        "stonadId": "          ",
                        "korrigering": " ",
                        "tilbakeforing": false,
                        "linjeId": 16,
                        "sats": 8989.00,
                        "typeSats": "MND ",
                        "antallSats": 1.00,
                        "saksbehId": "SU      ",
                        "uforeGrad": 100,
                        "kravhaverId": "           ",
                        "delytelseId": "716c51b0-4d73-4e26-b345-0cf684",
                        "bostedsenhet": "8020         ",
                        "skykldnerId": "           ",
                        "klassekode": "SUUFORE             ",
                        "klasseKodeBeskrivelse": "Supplerende stønad Uføre                          ",
                        "typeKlasse": "YTEL",
                        "typeKlasseBeskrivelse": "Klassetype for ytelseskonti                       ",
                        "refunderesOrgNr": "           "
                      },
                      {
                        "faktiskFom": "2022-01-01",
                        "faktiskTom": "2022-01-31",
                        "kontoStreng": "4952000            ",
                        "behandlingskode": "2",
                        "belop": -6989.00,
                        "trekkVedtakId": 0,
                        "stonadId": "          ",
                        "korrigering": " ",
                        "tilbakeforing": true,
                        "linjeId": 8,
                        "sats": 0.00,
                        "typeSats": "    ",
                        "antallSats": 0.00,
                        "saksbehId": "K231B215",
                        "uforeGrad": 100,
                        "kravhaverId": "           ",
                        "delytelseId": "                              ",
                        "bostedsenhet": "8020         ",
                        "skykldnerId": "           ",
                        "klassekode": "SUUFORE             ",
                        "klasseKodeBeskrivelse": "Supplerende stønad Uføre                          ",
                        "typeKlasse": "YTEL",
                        "typeKlasseBeskrivelse": "Klassetype for ytelseskonti                       ",
                        "refunderesOrgNr": "           "
                      }
                    ]
                  }
                ]
              }
            ]
          },
          "infomelding": null
        }
    """.trimIndent()
}
