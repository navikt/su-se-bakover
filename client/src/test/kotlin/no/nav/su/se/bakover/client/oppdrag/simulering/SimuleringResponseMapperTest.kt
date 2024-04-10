package no.nav.su.se.bakover.client.oppdrag.simulering

import arrow.core.left
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import no.nav.su.se.bakover.common.Beløp
import no.nav.su.se.bakover.common.MånedBeløp
import no.nav.su.se.bakover.common.Månedsbeløp
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.tid.april
import no.nav.su.se.bakover.common.domain.tid.februar
import no.nav.su.se.bakover.common.domain.tid.mars
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.april
import no.nav.su.se.bakover.common.tid.periode.august
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.common.tid.periode.mars
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.simulering.SimuleringResponseData.Companion.simuleringXml
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
import økonomi.domain.simulering.SimuleringFeilet
import økonomi.domain.simulering.SimuleringsOppsummering
import økonomi.domain.simulering.SimulertDetaljer
import økonomi.domain.simulering.SimulertMåned
import økonomi.domain.simulering.SimulertUtbetaling

@Suppress("HttpUrlsUsage")
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
    fun `kan mappe uten response`() {
        mapSimuleringResponse(
            saksnummer = no.nav.su.se.bakover.test.saksnummer,
            fnr = no.nav.su.se.bakover.test.fnr,
            simuleringsperiode = Periode.create(fraOgMed = 1.april(2021), tilOgMed = 30.april(2021)),
            soapRequest = "ignore-me",
            soapResponse = """
                <Envelope namespace="http://schemas.xmlsoap.org/soap/envelope/">
                <Body>
                <simulerBeregningResponse xmlns="http://nav.no/system/os/tjenester/simulerFpService/simulerFpServiceGrensesnitt">
                </simulerBeregningResponse>
                </Body>
                </Envelope>
            """.trimIndent(),
            clock = fixedClock,
        ).getOrFail()
    }

    @Test
    fun `kan mappe tom simulering`() {
        mapSimuleringResponse(
            saksnummer = no.nav.su.se.bakover.test.saksnummer,
            fnr = no.nav.su.se.bakover.test.fnr,
            simuleringsperiode = Periode.create(fraOgMed = 1.april(2021), tilOgMed = 30.april(2021)),
            soapRequest = "ignore-me",
            soapResponse = """
                <Envelope namespace="http://schemas.xmlsoap.org/soap/envelope/">
                <Body>
                <simulerBeregningResponse xmlns="http://nav.no/system/os/tjenester/simulerFpService/simulerFpServiceGrensesnitt">
                  <response xmlns="">
                  </response>
                </simulerBeregningResponse>
                </Body>
                </Envelope>
            """.trimIndent(),
            clock = fixedClock,
        ).getOrFail()
    }

    @Test
    fun `kan mappe ingen perioder`() {
        mapSimuleringResponse(
            saksnummer = no.nav.su.se.bakover.test.saksnummer,
            fnr = no.nav.su.se.bakover.test.fnr,
            simuleringsperiode = Periode.create(fraOgMed = 1.april(2021), tilOgMed = 30.april(2021)),
            soapRequest = "ignore-me",
            soapResponse = """
                <Envelope namespace="http://schemas.xmlsoap.org/soap/envelope/">
                <Body>
                <simulerBeregningResponse xmlns="http://nav.no/system/os/tjenester/simulerFpService/simulerFpServiceGrensesnitt">
                 <response xmlns="">
                    <simulering>
                       <gjelderId>${no.nav.su.se.bakover.test.fnr}</gjelderId>
                       <gjelderNavn>navn</gjelderNavn>
                       <datoBeregnet>2521-04-07</datoBeregnet>
                       <kodeFaggruppe>INNT</kodeFaggruppe>
                       <belop>10390.00</belop>
                    </simulering>
                </response>
                </simulerBeregningResponse>
                </Body>
                </Envelope>
            """.trimIndent(),
            clock = fixedClock,
        ).getOrFail()
    }

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
        val actualSimulering = mapSimuleringResponse(
            saksnummer = no.nav.su.se.bakover.test.saksnummer,
            fnr = no.nav.su.se.bakover.test.fnr,
            simuleringsperiode = Periode.create(fraOgMed = 1.april(2021), tilOgMed = 30.april(2021)),
            soapRequest = "ignore-me",
            soapResponse = rawResponse,
            clock = fixedClock,
        ).getOrFail()
        // TODO jah: Bør vel stemme med simuleringsdataene?
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
        val actualSimulering = mapSimuleringResponse(
            saksnummer = no.nav.su.se.bakover.test.saksnummer,
            fnr = no.nav.su.se.bakover.test.fnr,
            simuleringsperiode = Periode.create(fraOgMed = 1.april(2021), tilOgMed = 30.april(2021)),
            soapRequest = "ignore-me",
            soapResponse = simuleringXml,
            clock = fixedClock,
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
        val actualSimulering = mapSimuleringResponse(
            saksnummer = no.nav.su.se.bakover.test.saksnummer,
            fnr = no.nav.su.se.bakover.test.fnr,
            simuleringsperiode = Periode.create(fraOgMed = 1.april(2021), tilOgMed = 30.april(2021)),
            soapRequest = "ignore-me",
            soapResponse = simuleringXml,
            clock = fixedClock,
        ).getOrFail()

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
                februar(2021) to Kontooppstilling(
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
                mars(2021) to Kontooppstilling(
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
        val actualSimulering = mapSimuleringResponse(
            saksnummer = no.nav.su.se.bakover.test.saksnummer,
            fnr = no.nav.su.se.bakover.test.fnr,
            simuleringsperiode = Periode.create(fraOgMed = 1.april(2021), tilOgMed = 30.april(2021)),
            soapRequest = "ignore-me",
            soapResponse = simuleringXml,
            clock = fixedClock,
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
                februar(2021) to Kontooppstilling(
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
                mars(2021) to Kontooppstilling(
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
        val actualSimulering = mapSimuleringResponse(
            saksnummer = no.nav.su.se.bakover.test.saksnummer,
            fnr = no.nav.su.se.bakover.test.fnr,
            simuleringsperiode = Periode.create(fraOgMed = 1.april(2021), tilOgMed = 30.april(2021)),
            soapRequest = "ignore-me",
            soapResponse = simuleringXml,
            clock = fixedClock,
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
                stoppnivå {
                    ordinær(5000)
                }
                stoppnivå {
                    ordinær(5000)
                }
            }
        }
        val logMock = mock<Logger>()
        val sikkerLoggMock = mock<Logger>()
        mapSimuleringResponse(
            saksnummer = no.nav.su.se.bakover.test.saksnummer,
            fnr = no.nav.su.se.bakover.test.fnr,
            simuleringsperiode = Periode.create(fraOgMed = 1.april(2021), tilOgMed = 30.april(2021)),
            soapRequest = "ignore-me",
            soapResponse = simuleringXml,
            clock = fixedClock,
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
                    ordinær(5000)
                }
                stoppnivå {
                    fagsystemId = "2021"
                    ordinær(10000)
                }
            }
        }
        mapSimuleringResponse(
            saksnummer = Saksnummer(2021),
            fnr = no.nav.su.se.bakover.test.fnr,
            simuleringsperiode = Periode.create(fraOgMed = 1.april(2021), tilOgMed = 30.april(2021)),
            soapRequest = "ignore-me",
            soapResponse = simuleringXml,
            clock = fixedClock,
        ).getOrFail().måneder.map { it.utbetaling!!.fagSystemId } shouldBe listOf("2021")
    }

    @Test
    fun `filtrerer vekk andre kodeFagomraade-er`() {
        val simuleringXml = simuleringXml {
            periode {
                stoppnivå {
                    // Forventer at denne filtreres vekk
                    kodeFagomraade = "UFORE"
                    ordinær(5000)
                }
                stoppnivå {
                    ordinær(10000)
                }
            }
        }
        mapSimuleringResponse(
            saksnummer = no.nav.su.se.bakover.test.saksnummer,
            fnr = no.nav.su.se.bakover.test.fnr,
            simuleringsperiode = Periode.create(fraOgMed = 1.april(2021), tilOgMed = 30.april(2021)),
            soapRequest = "ignore-me",
            soapResponse = simuleringXml,
            clock = fixedClock,
        ).getOrFail().måneder.map { it.utbetaling!! }.size.shouldBe(1)
    }

    @Test
    fun `mapping med åpen feilkonto`() {
        // TODO jah: Ved reduksjon/annulering av en allerede feilkonto, vil fortegnene på FEIL/MOTP være snudd. I tillegg ser det ikke ut som tilbakeforing kun er i bruk ved reduksjon og ikke annullering?
        val rawResponse = xmlMedÅpnedFeilkonto

        mapSimuleringResponse(
            saksnummer = no.nav.su.se.bakover.test.saksnummer,
            fnr = no.nav.su.se.bakover.test.fnr,
            simuleringsperiode = Periode.create(fraOgMed = 1.april(2021), tilOgMed = 30.april(2021)),
            soapRequest = "ignore-me",
            soapResponse = rawResponse,
            clock = fixedClock,
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

    // TODO jah: Denne er konvert fra JSON til XML og deretter tilpasset. Prøv å få tak i en original.
    private val xmlMedÅpnedFeilkonto = """
<Envelope namespace="http://schemas.xmlsoap.org/soap/envelope/">
<Body>
<simulerBeregningResponse>
<response>
<simulering>
    <gjelderId>$fnr</gjelderId>
    <gjelderNavn>$navn</gjelderNavn>
    <datoBeregnet>2022-11-17</datoBeregnet>
    <kodeFaggruppe>INNT</kodeFaggruppe>
    <belop>0</belop>
    <beregningsPeriode>
        <periodeFom>2022-08-01</periodeFom>
        <periodeTom>2022-08-31</periodeTom>
        <beregningStoppnivaa>
            <kodeFagomraade>SUUFORE</kodeFagomraade>
            <stoppNivaaId>1</stoppNivaaId>
            <behandlendeEnhet>8020</behandlendeEnhet>
            <oppdragsId>60937907</oppdragsId>
            <fagsystemId>$fagsystemId</fagsystemId>
            <kid></kid>
            <utbetalesTilId>$fnr</utbetalesTilId>
            <utbetalesTilNavn>$navn</utbetalesTilNavn>
            <bilagsType>U</bilagsType>
            <forfall>2022-11-17</forfall>
            <feilkonto>true</feilkonto>
            <beregningStoppnivaaDetaljer>
                <faktiskFom>2022-08-01</faktiskFom>
                <faktiskTom>2022-08-31</faktiskTom>
                <kontoStreng>0630986</kontoStreng>
                <behandlingskode>0</behandlingskode>
                <belop>-21181</belop>
                <trekkVedtakId>0</trekkVedtakId>
                <stonadId></stonadId>
                <korrigering></korrigering>
                <tilbakeforing>false</tilbakeforing>
                <linjeId>0</linjeId>
                <sats>0</sats>
                <typeSats></typeSats>
                <antallSats>0</antallSats>
                <saksbehId>K231B214</saksbehId>
                <uforeGrad>0</uforeGrad>
                <kravhaverId></kravhaverId>
                <delytelseId></delytelseId>
                <bostedsenhet>8020</bostedsenhet>
                <skykldnerId></skykldnerId>
                <klassekode>KL_KODE_FEIL_INNT</klassekode>
                <klasseKodeBeskrivelse>Feilutbetaling Inntektsytelser</klasseKodeBeskrivelse>
                <typeKlasse>FEIL</typeKlasse>
                <typeKlasseBeskrivelse>Klassetype for feilkontoer</typeKlasseBeskrivelse>
                <refunderesOrgNr></refunderesOrgNr>
            </beregningStoppnivaaDetaljer>
            <beregningStoppnivaaDetaljer>
                <faktiskFom>2022-08-01</faktiskFom>
                <faktiskTom>2022-08-31</faktiskTom>
                <kontoStreng>0902900</kontoStreng>
                <behandlingskode>0</behandlingskode>
                <belop>21181</belop>
                <trekkVedtakId>0</trekkVedtakId>
                <stonadId></stonadId>
                <korrigering></korrigering>
                <tilbakeforing>false</tilbakeforing>
                <linjeId>0</linjeId>
                <sats>0</sats>
                <typeSats></typeSats>
                <antallSats>0</antallSats>
                <saksbehId>K231B214</saksbehId>
                <uforeGrad>0</uforeGrad>
                <kravhaverId></kravhaverId>
                <delytelseId></delytelseId>
                <bostedsenhet>8020</bostedsenhet>
                <skykldnerId></skykldnerId>
                <klassekode>TBMOTOBS</klassekode>
                <klasseKodeBeskrivelse>Feilutbetaling motkonto til OBS konto</klasseKodeBeskrivelse>
                <typeKlasse>MOTP</typeKlasse>
                <typeKlasseBeskrivelse>Klassetype for motposteringskonto</typeKlasseBeskrivelse>
                <refunderesOrgNr></refunderesOrgNr>
            </beregningStoppnivaaDetaljer>
            <beregningStoppnivaaDetaljer>
                <faktiskFom>2022-08-01</faktiskFom>
                <faktiskTom>2022-08-31</faktiskTom>
                <kontoStreng>4952000</kontoStreng>
                <behandlingskode>2</behandlingskode>
                <belop>-21181</belop>
                <trekkVedtakId>0</trekkVedtakId>
                <stonadId></stonadId>
                <korrigering></korrigering>
                <tilbakeforing>false</tilbakeforing>
                <linjeId>0</linjeId>
                <sats>0</sats>
                <typeSats></typeSats>
                <antallSats>0</antallSats>
                <saksbehId>K231B214</saksbehId>
                <uforeGrad>0</uforeGrad>
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
                <faktiskFom>2022-08-01</faktiskFom>
                <faktiskTom>2022-08-31</faktiskTom>
                <kontoStreng>4952000</kontoStreng>
                <behandlingskode>2</behandlingskode>
                <belop>21181</belop>
                <trekkVedtakId>0</trekkVedtakId>
                <stonadId></stonadId>
                <korrigering></korrigering>
                <tilbakeforing>false</tilbakeforing>
                <linjeId>4</linjeId>
                <sats>21181</sats>
                <typeSats>MND</typeSats>
                <antallSats>1</antallSats>
                <saksbehId>SU</saksbehId>
                <uforeGrad>100</uforeGrad>
                <kravhaverId></kravhaverId>
                <delytelseId>da87bf28-50f4-4bee-bc05-a62333</delytelseId>
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
</response>
</simulerBeregningResponse>
</Body>
</Envelope>
    """.trimIndent()

    @Test
    fun `mapping med åpen feilkonto annullering og etterbetaling`() {
        // TODO jah: Ved reduksjon/annulering av en allerede feilkonto, vil fortegnene på FEIL/MOTP være snudd. I tillegg ser det ikke ut som tilbakeforing kun er i bruk ved reduksjon og ikke annullering?
        val rawResponse = xmlMedÅpenFeilkontoOgEtterbetaling
        mapSimuleringResponse(
            saksnummer = saksnummer,
            fnr = no.nav.su.se.bakover.test.fnr,
            simuleringsperiode = Periode.create(fraOgMed = 1.april(2021), tilOgMed = 30.april(2021)),
            soapRequest = "ignore-me",
            soapResponse = rawResponse,
            clock = fixedClock,
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

// TODO jah: Denne er konvert fra JSON til XML og deretter tilpasset. Prøv å få tak i en original.
    private val xmlMedÅpenFeilkontoOgEtterbetaling = """
      <Envelope namespace="http://schemas.xmlsoap.org/soap/envelope/">
      <Body>
      <simulerBeregningResponse>
<response>
<simulering>
    <gjelderId>$fnr</gjelderId>
    <gjelderNavn>$navn</gjelderNavn>
    <datoBeregnet>2022-11-17</datoBeregnet>
    <kodeFaggruppe>INNT</kodeFaggruppe>
    <belop>1673</belop>
    <beregningsPeriode>
        <periodeFom>2022-08-01</periodeFom>
        <periodeTom>2022-08-31</periodeTom>
        <beregningStoppnivaa>
            <kodeFagomraade>SUUFORE</kodeFagomraade>
            <stoppNivaaId>1</stoppNivaaId>
            <behandlendeEnhet>8020</behandlendeEnhet>
            <oppdragsId>60937907</oppdragsId>
            <fagsystemId>$fagsystemId</fagsystemId>
            <kid></kid>
            <utbetalesTilId>$fnr</utbetalesTilId>
            <utbetalesTilNavn>$navn</utbetalesTilNavn>
            <bilagsType>U</bilagsType>
            <forfall>2022-11-17</forfall>
            <feilkonto>true</feilkonto>
            <beregningStoppnivaaDetaljer>
                <faktiskFom>2022-08-01</faktiskFom>
                <faktiskTom>2022-08-31</faktiskTom>
                <kontoStreng>0630986</kontoStreng>
                <behandlingskode>0</behandlingskode>
                <belop>-21181</belop>
                <trekkVedtakId>0</trekkVedtakId>
                <stonadId></stonadId>
                <korrigering></korrigering>
                <tilbakeforing>false</tilbakeforing>
                <linjeId>0</linjeId>
                <sats>0</sats>
                <typeSats></typeSats>
                <antallSats>0</antallSats>
                <saksbehId>K231B214</saksbehId>
                <uforeGrad>0</uforeGrad>
                <kravhaverId></kravhaverId>
                <delytelseId></delytelseId>
                <bostedsenhet>8020</bostedsenhet>
                <skykldnerId></skykldnerId>
                <klassekode>KL_KODE_FEIL_INNT</klassekode>
                <klasseKodeBeskrivelse>Feilutbetaling Inntektsytelser</klasseKodeBeskrivelse>
                <typeKlasse>FEIL</typeKlasse>
                <typeKlasseBeskrivelse>Klassetype for feilkontoer</typeKlasseBeskrivelse>
                <refunderesOrgNr></refunderesOrgNr>
            </beregningStoppnivaaDetaljer>
            <beregningStoppnivaaDetaljer>
                <faktiskFom>2022-08-01</faktiskFom>
                <faktiskTom>2022-08-31</faktiskTom>
                <kontoStreng>0902900</kontoStreng>
                <behandlingskode>0</behandlingskode>
                <belop>21181</belop>
                <trekkVedtakId>0</trekkVedtakId>
                <stonadId></stonadId>
                <korrigering></korrigering>
                <tilbakeforing>false</tilbakeforing>
                <linjeId>0</linjeId>
                <sats>0</sats>
                <typeSats></typeSats>
                <antallSats>0</antallSats>
                <saksbehId>K231B214</saksbehId>
                <uforeGrad>0</uforeGrad>
                <kravhaverId></kravhaverId>
                <delytelseId></delytelseId>
                <bostedsenhet>8020</bostedsenhet>
                <skykldnerId></skykldnerId>
                <klassekode>TBMOTOBS</klassekode>
                <klasseKodeBeskrivelse>Feilutbetaling motkonto til OBS konto</klasseKodeBeskrivelse>
                <typeKlasse>MOTP</typeKlasse>
                <typeKlasseBeskrivelse>Klassetype for motposteringskonto</typeKlasseBeskrivelse>
                <refunderesOrgNr></refunderesOrgNr>
            </beregningStoppnivaaDetaljer>
            <beregningStoppnivaaDetaljer>
                <faktiskFom>2022-08-01</faktiskFom>
                <faktiskTom>2022-08-31</faktiskTom>
                <kontoStreng>4952000</kontoStreng>
                <behandlingskode>2</behandlingskode>
                <belop>-21181</belop>
                <trekkVedtakId>0</trekkVedtakId>
                <stonadId></stonadId>
                <korrigering></korrigering>
                <tilbakeforing>false</tilbakeforing>
                <linjeId>0</linjeId>
                <sats>0</sats>
                <typeSats></typeSats>
                <antallSats>0</antallSats>
                <saksbehId>K231B214</saksbehId>
                <uforeGrad>0</uforeGrad>
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
                <faktiskFom>2022-08-01</faktiskFom>
                <faktiskTom>2022-08-31</faktiskTom>
                <kontoStreng>0510000</kontoStreng>
                <behandlingskode>0</behandlingskode>
                <belop>-185</belop>
                <trekkVedtakId>12333856</trekkVedtakId>
                <stonadId></stonadId>
                <korrigering></korrigering>
                <tilbakeforing>false</tilbakeforing>
                <linjeId>0</linjeId>
                <sats>0</sats>
                <typeSats>MND</typeSats>
                <antallSats>31</antallSats>
                <saksbehId>SU</saksbehId>
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
                <faktiskFom>2022-08-01</faktiskFom>
                <faktiskTom>2022-08-31</faktiskTom>
                <kontoStreng>4952000</kontoStreng>
                <behandlingskode>2</behandlingskode>
                <belop>23039</belop>
                <trekkVedtakId>0</trekkVedtakId>
                <stonadId></stonadId>
                <korrigering></korrigering>
                <tilbakeforing>false</tilbakeforing>
                <linjeId>4</linjeId>
                <sats>23039</sats>
                <typeSats>MND</typeSats>
                <antallSats>1</antallSats>
                <saksbehId>SU</saksbehId>
                <uforeGrad>100</uforeGrad>
                <kravhaverId></kravhaverId>
                <delytelseId>39e2f790-3c75-4e70-9889-a768bb</delytelseId>
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
</response>
</simulerBeregningResponse>
</Body>
</Envelope>
    """.trimIndent()

    @Test
    fun `mapping med reduksjon av åpen feilkonto og ikke trimmet verdier`() {
        // TODO jah: Ved reduksjon/annulering av en allerede feilkonto, vil fortegnene på FEIL/MOTP være snudd. I tillegg ser det ikke ut som tilbakeforing kun er i bruk ved reduksjon og ikke annullering?
        val rawResponse = soapMedReduksjonAvFeilkonto
        mapSimuleringResponse(
            saksnummer = no.nav.su.se.bakover.test.saksnummer,
            fnr = no.nav.su.se.bakover.test.fnr,
            simuleringsperiode = Periode.create(fraOgMed = 1.april(2021), tilOgMed = 30.april(2021)),
            soapRequest = "ignore-me",
            soapResponse = rawResponse,
            clock = fixedClock,
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
                <Envelope namespace="http://schemas.xmlsoap.org/soap/envelope/">
                <Body>
                <simulerBeregningResponse xmlns="http://nav.no/system/os/tjenester/simulerFpService/simulerFpServiceGrensesnitt">
                    <response xmlns="">
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
                    </response>
                </simulerBeregningResponse>
                </Body>
                </Envelope>
            """
        val actualSimulering = mapSimuleringResponse(
            saksnummer = Saksnummer(2021),
            fnr = Fnr.tryCreate("12345678901")!!,
            simuleringsperiode = Periode.create(fraOgMed = 1.april(2021), tilOgMed = 30.april(2021)),
            soapRequest = "ignore-me",
            soapResponse = rawXml,
            clock = fixedClock,
        ).getOrFail()

        actualSimulering.nettoBeløp shouldBe 2480
        actualSimulering.hentTotalUtbetaling() shouldBe Månedsbeløp(listOf(MånedBeløp(mai(2023), Beløp(2480))))
    }

    // TODO jah: Konvert fra JSON til XML. Og tilpasset. Bør finne en original i preprod.
    private val soapMedReduksjonAvFeilkonto = """
      <Envelope namespace="http://schemas.xmlsoap.org/soap/envelope/">
      <Body>
       <simulerBeregningResponse>
<response>
<simulering>
    <gjelderId>$fnr</gjelderId>
    <gjelderNavn>$navn</gjelderNavn>
    <datoBeregnet>2022-11-29</datoBeregnet>
    <kodeFaggruppe>INNT    </kodeFaggruppe>
    <belop>0</belop>
    <beregningsPeriode>
        <periodeFom>2022-01-01</periodeFom>
        <periodeTom>2022-01-31</periodeTom>
        <beregningStoppnivaa>
            <kodeFagomraade>SUUFORE </kodeFagomraade>
            <stoppNivaaId>1</stoppNivaaId>
            <behandlendeEnhet>8020         </behandlendeEnhet>
            <oppdragsId>62847682</oppdragsId>
            <fagsystemId>$fagsystemId                      </fagsystemId>
            <kid></kid>
            <utbetalesTilId>$fnr</utbetalesTilId>
            <utbetalesTilNavn>$navn</utbetalesTilNavn>
            <bilagsType>U </bilagsType>
            <forfall>2022-11-29</forfall>
            <feilkonto>true</feilkonto>
            <beregningStoppnivaaDetaljer>
                <faktiskFom>2022-01-01</faktiskFom>
                <faktiskTom>2022-01-31</faktiskTom>
                <kontoStreng>0902900            </kontoStreng>
                <behandlingskode>0</behandlingskode>
                <belop>2000</belop>
                <trekkVedtakId>0</trekkVedtakId>
                <stonadId></stonadId>
                <korrigering></korrigering>
                <tilbakeforing>true</tilbakeforing>
                <linjeId>0</linjeId>
                <sats>0</sats>
                <typeSats></typeSats>
                <antallSats>0</antallSats>
                <saksbehId>K231B214</saksbehId>
                <uforeGrad>0</uforeGrad>
                <kravhaverId></kravhaverId>
                <delytelseId></delytelseId>
                <bostedsenhet>8020         </bostedsenhet>
                <skykldnerId></skykldnerId>
                <klassekode>TBMOTOBS            </klassekode>
                <klasseKodeBeskrivelse>Feilutbetaling motkonto til OBS konto             </klasseKodeBeskrivelse>
                <typeKlasse>MOTP</typeKlasse>
                <typeKlasseBeskrivelse>Klassetype for motposteringskonto                 </typeKlasseBeskrivelse>
                <refunderesOrgNr></refunderesOrgNr>
            </beregningStoppnivaaDetaljer>
            <beregningStoppnivaaDetaljer>
                <faktiskFom>2022-01-01</faktiskFom>
                <faktiskTom>2022-01-31</faktiskTom>
                <kontoStreng>4952000            </kontoStreng>
                <behandlingskode>2</behandlingskode>
                <belop>-2000</belop>
                <trekkVedtakId>0</trekkVedtakId>
                <stonadId></stonadId>
                <korrigering></korrigering>
                <tilbakeforing>true</tilbakeforing>
                <linjeId>0</linjeId>
                <sats>0</sats>
                <typeSats></typeSats>
                <antallSats>0</antallSats>
                <saksbehId>K231B214</saksbehId>
                <uforeGrad>0</uforeGrad>
                <kravhaverId></kravhaverId>
                <delytelseId></delytelseId>
                <bostedsenhet>8020         </bostedsenhet>
                <skykldnerId></skykldnerId>
                <klassekode>SUUFORE             </klassekode>
                <klasseKodeBeskrivelse>Supplerende stønad Uføre                          </klasseKodeBeskrivelse>
                <typeKlasse>YTEL</typeKlasse>
                <typeKlasseBeskrivelse>Klassetype for ytelseskonti                       </typeKlasseBeskrivelse>
                <refunderesOrgNr></refunderesOrgNr>
            </beregningStoppnivaaDetaljer>
            <beregningStoppnivaaDetaljer>
                <faktiskFom>2022-01-01</faktiskFom>
                <faktiskTom>2022-01-31</faktiskTom>
                <kontoStreng>0630986            </kontoStreng>
                <behandlingskode>0</behandlingskode>
                <belop>-2000</belop>
                <trekkVedtakId>0</trekkVedtakId>
                <stonadId></stonadId>
                <korrigering></korrigering>
                <tilbakeforing>true</tilbakeforing>
                <linjeId>0</linjeId>
                <sats>0</sats>
                <typeSats></typeSats>
                <antallSats>0</antallSats>
                <saksbehId>K231B214</saksbehId>
                <uforeGrad>0</uforeGrad>
                <kravhaverId></kravhaverId>
                <delytelseId></delytelseId>
                <bostedsenhet>8020         </bostedsenhet>
                <skykldnerId></skykldnerId>
                <klassekode>KL_KODE_FEIL_INNT   </klassekode>
                <klasseKodeBeskrivelse>Feilutbetaling Inntektsytelser                    </klasseKodeBeskrivelse>
                <typeKlasse>FEIL</typeKlasse>
                <typeKlasseBeskrivelse>Klassetype for feilkontoer                        </typeKlasseBeskrivelse>
                <refunderesOrgNr></refunderesOrgNr>
            </beregningStoppnivaaDetaljer>
            <beregningStoppnivaaDetaljer>
                <faktiskFom>2022-01-01</faktiskFom>
                <faktiskTom>2022-01-31</faktiskTom>
                <kontoStreng>4952000            </kontoStreng>
                <behandlingskode>2</behandlingskode>
                <belop>8989</belop>
                <trekkVedtakId>0</trekkVedtakId>
                <stonadId></stonadId>
                <korrigering></korrigering>
                <tilbakeforing>false</tilbakeforing>
                <linjeId>16</linjeId>
                <sats>8989</sats>
                <typeSats>MND </typeSats>
                <antallSats>1</antallSats>
                <saksbehId>SU      </saksbehId>
                <uforeGrad>100</uforeGrad>
                <kravhaverId></kravhaverId>
                <delytelseId>716c51b0-4d73-4e26-b345-0cf684</delytelseId>
                <bostedsenhet>8020         </bostedsenhet>
                <skykldnerId></skykldnerId>
                <klassekode>SUUFORE             </klassekode>
                <klasseKodeBeskrivelse>Supplerende stønad Uføre                          </klasseKodeBeskrivelse>
                <typeKlasse>YTEL</typeKlasse>
                <typeKlasseBeskrivelse>Klassetype for ytelseskonti                       </typeKlasseBeskrivelse>
                <refunderesOrgNr></refunderesOrgNr>
            </beregningStoppnivaaDetaljer>
            <beregningStoppnivaaDetaljer>
                <faktiskFom>2022-01-01</faktiskFom>
                <faktiskTom>2022-01-31</faktiskTom>
                <kontoStreng>4952000            </kontoStreng>
                <behandlingskode>2</behandlingskode>
                <belop>-6989</belop>
                <trekkVedtakId>0</trekkVedtakId>
                <stonadId></stonadId>
                <korrigering></korrigering>
                <tilbakeforing>true</tilbakeforing>
                <linjeId>8</linjeId>
                <sats>0</sats>
                <typeSats></typeSats>
                <antallSats>0</antallSats>
                <saksbehId>K231B215</saksbehId>
                <uforeGrad>100</uforeGrad>
                <kravhaverId></kravhaverId>
                <delytelseId></delytelseId>
                <bostedsenhet>8020         </bostedsenhet>
                <skykldnerId></skykldnerId>
                <klassekode>SUUFORE             </klassekode>
                <klasseKodeBeskrivelse>Supplerende stønad Uføre                          </klasseKodeBeskrivelse>
                <typeKlasse>YTEL</typeKlasse>
                <typeKlasseBeskrivelse>Klassetype for ytelseskonti                       </typeKlasseBeskrivelse>
                <refunderesOrgNr></refunderesOrgNr>
            </beregningStoppnivaaDetaljer>
        </beregningStoppnivaa>
    </beregningsPeriode>
</simulering>
</response>
</simulerBeregningResponse>
</Body>
</Envelope>
    """.trimIndent()
}
