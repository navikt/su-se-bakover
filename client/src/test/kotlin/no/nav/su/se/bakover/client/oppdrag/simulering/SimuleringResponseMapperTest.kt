package no.nav.su.se.bakover.client.oppdrag.simulering

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.oppdrag.XmlMapper
import no.nav.su.se.bakover.common.application.Beløp
import no.nav.su.se.bakover.common.application.MånedBeløp
import no.nav.su.se.bakover.common.application.Månedsbeløp
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.periode.april
import no.nav.su.se.bakover.common.periode.august
import no.nav.su.se.bakover.common.periode.februar
import no.nav.su.se.bakover.common.periode.januar
import no.nav.su.se.bakover.common.periode.mars
import no.nav.su.se.bakover.domain.oppdrag.simulering.KlasseKode
import no.nav.su.se.bakover.domain.oppdrag.simulering.KlasseType
import no.nav.su.se.bakover.domain.oppdrag.simulering.Kontobeløp
import no.nav.su.se.bakover.domain.oppdrag.simulering.Kontooppstilling
import no.nav.su.se.bakover.domain.oppdrag.simulering.PeriodeOppsummering
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringsOppsummering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertDetaljer
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertPeriode
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertUtbetaling
import no.nav.su.se.bakover.test.SimuleringResponseData.Companion.simuleringXml
import no.nav.su.se.bakover.test.fixedClock
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.SimulerBeregningResponse
import org.junit.jupiter.api.Test
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
        val rawXml = simuleringXml {
            datoBeregnet = "2021-04-14"
            belop = "10390"
            periode {
                periodeFom = "2021-04-01"
                periodeTom = "2021-04-30"
                stoppnivå {
                    forfall = "2021-04-19"
                    ordinær(20779)
                }
            }
        }
        val responseMedFremtidigUtbetaling = XmlMapper.readValue(
            rawXml,
            GrensesnittResponse::class.java,
        ).response

        SimuleringResponseMapper(
            rawXml = rawXml,
            oppdragResponse = responseMedFremtidigUtbetaling,
            clock = fixedClock,
            saksnummer = saksnummer,
        ).simulering shouldBe Simulering(
            gjelderId = fnr,
            gjelderNavn = navn,
            datoBeregnet = 14.april(2021),
            nettoBeløp = 10390,
            periodeList = listOf(
                SimulertPeriode(
                    fraOgMed = 1.april(2021),
                    tilOgMed = 30.april(2021),
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
                                uforegrad = 0,
                                klassekode = KlasseKode.SUUFORE,
                                klassekodeBeskrivelse = suBeskrivelse,
                                klasseType = KlasseType.YTEL,
                            ),
                        ),
                    ),
                ),
            ),
            rawXml = rawXml,
        ).also {
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
            belop = "10390"
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
        val responseMedFremtidigUtbetaling = XmlMapper.readValue(
            simuleringXml,
            GrensesnittResponse::class.java,
        ).response
        SimuleringResponseMapper(
            simuleringXml,
            responseMedFremtidigUtbetaling,
            fixedClock,
            saksnummer,
        ).simulering shouldBe Simulering(
            gjelderId = fnr,
            gjelderNavn = navn,
            datoBeregnet = 14.april(2021),
            nettoBeløp = 10390,
            periodeList = listOf(
                SimulertPeriode(
                    fraOgMed = 1.april(2021),
                    tilOgMed = 30.april(2021),
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
                                uforegrad = 0,
                                klassekode = KlasseKode.SUALDER,
                                klassekodeBeskrivelse = suBeskrivelse,
                                klasseType = KlasseType.YTEL,
                            ),
                        ),
                    ),
                ),
            ),
            rawXml = simuleringXml,
        ).also {
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

    //language=xml
    private val xmlResponseMedFremtidigUtbetaling = """
    <simulerBeregningResponse xmlns="http://nav.no/system/os/tjenester/simulerFpService/simulerFpServiceGrensesnitt">
             <response xmlns="">
                <simulering>
                   <gjelderId>$fnr</gjelderId>
                   <gjelderNavn>$navn</gjelderNavn>
                   <datoBeregnet>2021-04-14</datoBeregnet>
                   <kodeFaggruppe>INNT</kodeFaggruppe>
                   <belop>10390.00</belop>
                   <beregningsPeriode xmlns="http://nav.no/system/os/entiteter/beregningSkjema">
                      <periodeFom xmlns="">2021-04-01</periodeFom>
                      <periodeTom xmlns="">2021-04-30</periodeTom>
                      <beregningStoppnivaa>
                         <kodeFagomraade xmlns="">SUUFORE</kodeFagomraade>
                         <stoppNivaaId xmlns="">1</stoppNivaaId>
                         <behandlendeEnhet xmlns="">8020</behandlendeEnhet>
                         <oppdragsId xmlns="">53387554</oppdragsId>
                         <fagsystemId xmlns="">$fagsystemId</fagsystemId>
                         <kid xmlns=""/>
                         <utbetalesTilId xmlns="">$fnr</utbetalesTilId>
                         <utbetalesTilNavn xmlns="">$navn</utbetalesTilNavn>
                         <bilagsType xmlns="">U</bilagsType>
                         <forfall xmlns="">2021-04-19</forfall>
                         <feilkonto xmlns="">false</feilkonto>
                         <beregningStoppnivaaDetaljer>
                            <faktiskFom xmlns="">2021-04-01</faktiskFom>
                            <faktiskTom xmlns="">2021-04-30</faktiskTom>
                            <kontoStreng xmlns="">konto</kontoStreng>
                            <behandlingskode xmlns="">2</behandlingskode>
                            <belop xmlns="">20779.00</belop>
                            <trekkVedtakId xmlns="">0</trekkVedtakId>
                            <stonadId xmlns=""></stonadId>
                            <korrigering xmlns=""></korrigering>
                            <tilbakeforing xmlns="">false</tilbakeforing>
                            <linjeId xmlns="">3</linjeId>
                            <sats xmlns="">20779.00</sats>
                            <typeSats xmlns="">MND</typeSats>
                            <antallSats xmlns="">1.00</antallSats>
                            <saksbehId xmlns="">Z123</saksbehId>
                            <uforeGrad xmlns="">0</uforeGrad>
                            <kravhaverId xmlns=""></kravhaverId>
                            <delytelseId xmlns="">0adee7fd-f21b-4fcb-9dc0-e2234a</delytelseId>
                            <bostedsenhet xmlns="">8020</bostedsenhet>
                            <skykldnerId xmlns=""></skykldnerId>
                            <klassekode xmlns="">SUUFORE</klassekode>
                            <klasseKodeBeskrivelse xmlns="">Supplerende stønad Uføre</klasseKodeBeskrivelse>
                            <typeKlasse xmlns="">YTEL</typeKlasse>
                            <typeKlasseBeskrivelse xmlns="">Klassetype for ytelseskonti</typeKlasseBeskrivelse>
                            <refunderesOrgNr xmlns=""></refunderesOrgNr>
                         </beregningStoppnivaaDetaljer>
                         <beregningStoppnivaaDetaljer>
                            <faktiskFom xmlns="">2021-04-01</faktiskFom>
                            <faktiskTom xmlns="">2021-04-30</faktiskTom>
                            <kontoStreng xmlns="">0510000</kontoStreng>
                            <behandlingskode xmlns="">0</behandlingskode>
                            <belop xmlns="">-10389.00</belop>
                            <trekkVedtakId xmlns="">11845513</trekkVedtakId>
                            <stonadId xmlns=""></stonadId>
                            <korrigering xmlns=""></korrigering>
                            <tilbakeforing xmlns="">false</tilbakeforing>
                            <linjeId xmlns="">0</linjeId>
                            <sats xmlns="">0.00</sats>
                            <typeSats xmlns="">MND</typeSats>
                            <antallSats xmlns="">30.00</antallSats>
                            <saksbehId xmlns="">Z123</saksbehId>
                            <uforeGrad xmlns="">0</uforeGrad>
                            <kravhaverId xmlns=""></kravhaverId>
                            <delytelseId xmlns=""></delytelseId>
                            <bostedsenhet xmlns="">8020</bostedsenhet>
                            <skykldnerId xmlns=""></skykldnerId>
                            <klassekode xmlns="">FSKTSKAT</klassekode>
                            <klasseKodeBeskrivelse xmlns="">Forskuddskatt</klasseKodeBeskrivelse>
                            <typeKlasse xmlns="">SKAT</typeKlasse>
                            <typeKlasseBeskrivelse xmlns="">Klassetype for skatt</typeKlasseBeskrivelse>
                            <refunderesOrgNr xmlns=""></refunderesOrgNr>
                         </beregningStoppnivaaDetaljer>
                      </beregningStoppnivaa>
                   </beregningsPeriode>
                </simulering>
                <infomelding>
                   <beskrMelding>Simulering er utført uten skattevedtak. Nominell sats benyttet.</beskrMelding>
                </infomelding>
             </response>
          </simulerBeregningResponse>
    """.trimIndent()

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
                    debetFeilutbetaling(10779)
                    ordinær(10000)
                    feilutbetaling(10779)
                    motposteringskonto(-10779)
                    tidligereUtbetalt(-20779)
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
        val responseMedFeilutbetaling = XmlMapper.readValue(
            simuleringXml,
            GrensesnittResponse::class.java,
        ).response

        SimuleringResponseMapper(
            simuleringXml,
            responseMedFeilutbetaling,
            fixedClock,
            saksnummer,
        ).simulering shouldBe Simulering(
            gjelderId = fnr,
            gjelderNavn = navn,
            datoBeregnet = 14.april(2021),
            nettoBeløp = 5000,
            periodeList = listOf(
                SimulertPeriode(
                    fraOgMed = 1.februar(2021),
                    tilOgMed = 28.februar(2021),
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
                SimulertPeriode(
                    fraOgMed = 1.mars(2021),
                    tilOgMed = 31.mars(2021),
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
            rawXml = simuleringXml,
        ).also {
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
        val rawXml = simuleringXml {
            datoBeregnet = "2021-04-14"
            belop = "19611"
            periode {
                periodeFom = "2021-02-01"
                periodeTom = "2021-02-28"
                stoppnivå {
                    forfall = "2021-04-14"
                    ordinær(30000)
                    skattedetalj(-4610)
                    tidligereUtbetalt(-20779)
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
        val responseMedEtterbetaling = XmlMapper.readValue(
            rawXml,
            GrensesnittResponse::class.java,
        ).response

        SimuleringResponseMapper(
            rawXml,
            responseMedEtterbetaling,
            fixedClock,
            saksnummer,
        ).simulering shouldBe Simulering(
            gjelderId = fnr,
            gjelderNavn = navn,
            datoBeregnet = 14.april(2021),
            nettoBeløp = 19611,
            periodeList = listOf(
                SimulertPeriode(
                    fraOgMed = 1.februar(2021),
                    tilOgMed = 28.februar(2021),
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
                                konto = kontoYtel,
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
                SimulertPeriode(
                    fraOgMed = 1.mars(2021),
                    tilOgMed = 31.mars(2021),
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
                                uforegrad = 0,
                                klassekode = KlasseKode.SUUFORE,
                                klassekodeBeskrivelse = suBeskrivelse,
                                klasseType = KlasseType.YTEL,
                            ),
                        ),
                    ),
                ),
            ),
            rawXml = rawXml,
        ).also {
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

    //language=xml
    private val xmlResponseMedEtterbetaling = """
     <simulerBeregningResponse xmlns="http://nav.no/system/os/tjenester/simulerFpService/simulerFpServiceGrensesnitt">
         <response xmlns="">
            <simulering>
               <gjelderId>$fnr</gjelderId>
               <gjelderNavn>$navn</gjelderNavn>
               <datoBeregnet>2021-04-14</datoBeregnet>
               <kodeFaggruppe>INNT</kodeFaggruppe>
               <belop>19611.00</belop>
               <beregningsPeriode xmlns="http://nav.no/system/os/entiteter/beregningSkjema">
                  <periodeFom xmlns="">2021-02-01</periodeFom>
                  <periodeTom xmlns="">2021-02-28</periodeTom>
                  <beregningStoppnivaa>
                     <kodeFagomraade xmlns="">SUUFORE</kodeFagomraade>
                     <stoppNivaaId xmlns="">1</stoppNivaaId>
                     <behandlendeEnhet xmlns="">8020</behandlendeEnhet>
                     <oppdragsId xmlns="">53387554</oppdragsId>
                     <fagsystemId xmlns="">$fagsystemId</fagsystemId>
                     <kid xmlns=""/>
                     <utbetalesTilId xmlns="">$fnr</utbetalesTilId>
                     <utbetalesTilNavn xmlns="">$navn</utbetalesTilNavn>
                     <bilagsType xmlns="">U</bilagsType>
                     <forfall xmlns="">2021-04-14</forfall>
                     <feilkonto xmlns="">false</feilkonto>
                     <beregningStoppnivaaDetaljer>
                        <faktiskFom xmlns="">2021-02-01</faktiskFom>
                        <faktiskTom xmlns="">2021-02-28</faktiskTom>
                        <kontoStreng xmlns="">konto</kontoStreng>
                        <behandlingskode xmlns="">2</behandlingskode>
                        <belop xmlns="">30000.00</belop>
                        <trekkVedtakId xmlns="">0</trekkVedtakId>
                        <stonadId xmlns=""></stonadId>
                        <korrigering xmlns=""></korrigering>
                        <tilbakeforing xmlns="">false</tilbakeforing>
                        <linjeId xmlns="">3</linjeId>
                        <sats xmlns="">30000.00</sats>
                        <typeSats xmlns="">MND</typeSats>
                        <antallSats xmlns="">1.00</antallSats>
                        <saksbehId xmlns="">Z123</saksbehId>
                        <uforeGrad xmlns="">0</uforeGrad>
                        <kravhaverId xmlns=""></kravhaverId>
                        <delytelseId xmlns="">0adee7fd-f21b-4fcb-9dc0-e2234a</delytelseId>
                        <bostedsenhet xmlns="">8020</bostedsenhet>
                        <skykldnerId xmlns=""></skykldnerId>
                        <klassekode xmlns="">SUUFORE</klassekode>
                        <klasseKodeBeskrivelse xmlns="">Supplerende stønad Uføre</klasseKodeBeskrivelse>
                        <typeKlasse xmlns="">YTEL</typeKlasse>
                        <typeKlasseBeskrivelse xmlns="">Klassetype for ytelseskonti</typeKlasseBeskrivelse>
                        <refunderesOrgNr xmlns=""></refunderesOrgNr>
                     </beregningStoppnivaaDetaljer>
                     <beregningStoppnivaaDetaljer>
                        <faktiskFom xmlns="">2021-02-01</faktiskFom>
                        <faktiskTom xmlns="">2021-02-28</faktiskTom>
                        <kontoStreng xmlns="">0510000</kontoStreng>
                        <behandlingskode xmlns="">0</behandlingskode>
                        <belop xmlns="">-4610.00</belop>
                        <trekkVedtakId xmlns="">11845513</trekkVedtakId>
                        <stonadId xmlns=""></stonadId>
                        <korrigering xmlns=""></korrigering>
                        <tilbakeforing xmlns="">false</tilbakeforing>
                        <linjeId xmlns="">0</linjeId>
                        <sats xmlns="">0.00</sats>
                        <typeSats xmlns="">MND</typeSats>
                        <antallSats xmlns="">28.00</antallSats>
                        <saksbehId xmlns="">Z123</saksbehId>
                        <uforeGrad xmlns="">0</uforeGrad>
                        <kravhaverId xmlns=""></kravhaverId>
                        <delytelseId xmlns=""></delytelseId>
                        <bostedsenhet xmlns="">8020</bostedsenhet>
                        <skykldnerId xmlns=""></skykldnerId>
                        <klassekode xmlns="">FSKTSKAT</klassekode>
                        <klasseKodeBeskrivelse xmlns="">Forskuddskatt</klasseKodeBeskrivelse>
                        <typeKlasse xmlns="">SKAT</typeKlasse>
                        <typeKlasseBeskrivelse xmlns="">Klassetype for skatt</typeKlasseBeskrivelse>
                        <refunderesOrgNr xmlns=""></refunderesOrgNr>
                     </beregningStoppnivaaDetaljer>
                     <beregningStoppnivaaDetaljer>
                        <faktiskFom xmlns="">2021-02-01</faktiskFom>
                        <faktiskTom xmlns="">2021-02-28</faktiskTom>
                        <kontoStreng xmlns="">konto</kontoStreng>
                        <behandlingskode xmlns="">2</behandlingskode>
                        <belop xmlns="">-20779.00</belop>
                        <trekkVedtakId xmlns="">0</trekkVedtakId>
                        <stonadId xmlns=""></stonadId>
                        <korrigering xmlns=""></korrigering>
                        <tilbakeforing xmlns="">true</tilbakeforing>
                        <linjeId xmlns="">1</linjeId>
                        <sats xmlns="">0.00</sats>
                        <typeSats xmlns=""></typeSats>
                        <antallSats xmlns="">0.00</antallSats>
                        <saksbehId xmlns="">K231B215</saksbehId>
                        <uforeGrad xmlns="">0</uforeGrad>
                        <kravhaverId xmlns=""></kravhaverId>
                        <delytelseId xmlns=""></delytelseId>
                        <bostedsenhet xmlns="">8020</bostedsenhet>
                        <skykldnerId xmlns=""></skykldnerId>
                        <klassekode xmlns="">SUUFORE</klassekode>
                        <klasseKodeBeskrivelse xmlns="">Supplerende stønad Uføre</klasseKodeBeskrivelse>
                        <typeKlasse xmlns="">YTEL</typeKlasse>
                        <typeKlasseBeskrivelse xmlns="">Klassetype for ytelseskonti</typeKlasseBeskrivelse>
                        <refunderesOrgNr xmlns=""></refunderesOrgNr>
                     </beregningStoppnivaaDetaljer>
                  </beregningStoppnivaa>
               </beregningsPeriode>
               <beregningsPeriode xmlns="http://nav.no/system/os/entiteter/beregningSkjema">
                  <periodeFom xmlns="">2021-03-01</periodeFom>
                  <periodeTom xmlns="">2021-03-31</periodeTom>
                  <beregningStoppnivaa>
                     <kodeFagomraade xmlns="">SUUFORE</kodeFagomraade>
                     <stoppNivaaId xmlns="">2</stoppNivaaId>
                     <behandlendeEnhet xmlns="">8020</behandlendeEnhet>
                     <oppdragsId xmlns="">53387554</oppdragsId>
                     <fagsystemId xmlns="">$fagsystemId</fagsystemId>
                     <kid xmlns=""/>
                     <utbetalesTilId xmlns="">$fnr</utbetalesTilId>
                     <utbetalesTilNavn xmlns="">$navn</utbetalesTilNavn>
                     <bilagsType xmlns="">U</bilagsType>
                     <forfall xmlns="">2021-03-10</forfall>
                     <feilkonto xmlns="">false</feilkonto>
                     <beregningStoppnivaaDetaljer>
                        <faktiskFom xmlns="">2021-03-01</faktiskFom>
                        <faktiskTom xmlns="">2021-03-31</faktiskTom>
                        <kontoStreng xmlns="">konto</kontoStreng>
                        <behandlingskode xmlns="">2</behandlingskode>
                        <belop xmlns="">30000.00</belop>
                        <trekkVedtakId xmlns="">0</trekkVedtakId>
                        <stonadId xmlns=""></stonadId>
                        <korrigering xmlns=""></korrigering>
                        <tilbakeforing xmlns="">false</tilbakeforing>
                        <linjeId xmlns="">3</linjeId>
                        <sats xmlns="">30000.00</sats>
                        <typeSats xmlns="">MND</typeSats>
                        <antallSats xmlns="">1.00</antallSats>
                        <saksbehId xmlns="">Z123</saksbehId>
                        <uforeGrad xmlns="">0</uforeGrad>
                        <kravhaverId xmlns=""></kravhaverId>
                        <delytelseId xmlns="">0adee7fd-f21b-4fcb-9dc0-e2234a</delytelseId>
                        <bostedsenhet xmlns="">8020</bostedsenhet>
                        <skykldnerId xmlns=""></skykldnerId>
                        <klassekode xmlns="">SUUFORE</klassekode>
                        <klasseKodeBeskrivelse xmlns="">Supplerende stønad Uføre</klasseKodeBeskrivelse>
                        <typeKlasse xmlns="">YTEL</typeKlasse>
                        <typeKlasseBeskrivelse xmlns="">Klassetype for ytelseskonti</typeKlasseBeskrivelse>
                        <refunderesOrgNr xmlns=""></refunderesOrgNr>
                     </beregningStoppnivaaDetaljer>
                     <beregningStoppnivaaDetaljer>
                        <faktiskFom xmlns="">2021-03-01</faktiskFom>
                        <faktiskTom xmlns="">2021-03-31</faktiskTom>
                        <kontoStreng xmlns="">0510000</kontoStreng>
                        <behandlingskode xmlns="">0</behandlingskode>
                        <belop xmlns="">-15000.00</belop>
                        <trekkVedtakId xmlns="">11845513</trekkVedtakId>
                        <stonadId xmlns=""></stonadId>
                        <korrigering xmlns=""></korrigering>
                        <tilbakeforing xmlns="">false</tilbakeforing>
                        <linjeId xmlns="">0</linjeId>
                        <sats xmlns="">0.00</sats>
                        <typeSats xmlns="">MND</typeSats>
                        <antallSats xmlns="">31.00</antallSats>
                        <saksbehId xmlns="">Z123</saksbehId>
                        <uforeGrad xmlns="">0</uforeGrad>
                        <kravhaverId xmlns=""></kravhaverId>
                        <delytelseId xmlns=""></delytelseId>
                        <bostedsenhet xmlns="">8020</bostedsenhet>
                        <skykldnerId xmlns=""></skykldnerId>
                        <klassekode xmlns="">FSKTSKAT</klassekode>
                        <klasseKodeBeskrivelse xmlns="">Forskuddskatt</klasseKodeBeskrivelse>
                        <typeKlasse xmlns="">SKAT</typeKlasse>
                        <typeKlasseBeskrivelse xmlns="">Klassetype for skatt</typeKlasseBeskrivelse>
                        <refunderesOrgNr xmlns=""></refunderesOrgNr>
                     </beregningStoppnivaaDetaljer>
                  </beregningStoppnivaa>
               </beregningsPeriode>
            </simulering>
            <infomelding>
               <beskrMelding>Simulering er utført uten skattevedtak. Nominell sats benyttet.</beskrMelding>
            </infomelding>
         </response>
      </simulerBeregningResponse>
    """.trimIndent()

    @Test
    fun `filtrerer vekk detaljer som er ukjent eller uinteressant`() {
        val responseMedFremtidigUtbetaling = XmlMapper.readValue(
            xmlResponseMedUinteressanteKoder,
            GrensesnittResponse::class.java,
        ).response

        val rawXml = xmlResponseMedUinteressanteKoder
        SimuleringResponseMapper(
            rawXml,
            responseMedFremtidigUtbetaling,
            fixedClock,
            saksnummer,
        ).simulering shouldBe Simulering(
            gjelderId = fnr,
            gjelderNavn = navn,
            datoBeregnet = 14.april(2021),
            nettoBeløp = 10390,
            periodeList = listOf(
                SimulertPeriode(
                    fraOgMed = 1.april(2021),
                    tilOgMed = 30.april(2021),
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
                                uforegrad = 0,
                                klassekode = KlasseKode.SUUFORE,
                                klassekodeBeskrivelse = suBeskrivelse,
                                klasseType = KlasseType.YTEL,
                            ),
                        ),
                    ),
                ),
            ),
            rawXml = rawXml,
        ).also {
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

    //language=xml
    private val xmlResponseMedAnnenFagsystemId = """
     <simulerBeregningResponse xmlns="http://nav.no/system/os/tjenester/simulerFpService/simulerFpServiceGrensesnitt">
         <response xmlns="">
            <simulering>
               <gjelderId>$fnr</gjelderId>
               <gjelderNavn>$navn</gjelderNavn>
               <datoBeregnet>2021-04-14</datoBeregnet>
               <kodeFaggruppe>INNT</kodeFaggruppe>
               <belop>19611.00</belop>
               <beregningsPeriode xmlns="http://nav.no/system/os/entiteter/beregningSkjema">
                  <periodeFom xmlns="">2021-02-01</periodeFom>
                  <periodeTom xmlns="">2021-02-28</periodeTom>
                  <beregningStoppnivaa>
                     <kodeFagomraade xmlns="">SUUFORE</kodeFagomraade>
                     <stoppNivaaId xmlns="">1</stoppNivaaId>
                     <behandlendeEnhet xmlns="">8020</behandlendeEnhet>
                     <oppdragsId xmlns="">53387554</oppdragsId>
                     <fagsystemId xmlns="">$fagsystemId</fagsystemId>
                     <kid xmlns=""/>
                     <utbetalesTilId xmlns="">$fnr</utbetalesTilId>
                     <utbetalesTilNavn xmlns="">$navn</utbetalesTilNavn>
                     <bilagsType xmlns="">U</bilagsType>
                     <forfall xmlns="">2021-04-14</forfall>
                     <feilkonto xmlns="">false</feilkonto>
                     <beregningStoppnivaaDetaljer>
                        <faktiskFom xmlns="">2021-02-01</faktiskFom>
                        <faktiskTom xmlns="">2021-02-28</faktiskTom>
                        <kontoStreng xmlns="">konto</kontoStreng>
                        <behandlingskode xmlns="">2</behandlingskode>
                        <belop xmlns="">30000.00</belop>
                        <trekkVedtakId xmlns="">0</trekkVedtakId>
                        <stonadId xmlns=""></stonadId>
                        <korrigering xmlns=""></korrigering>
                        <tilbakeforing xmlns="">false</tilbakeforing>
                        <linjeId xmlns="">3</linjeId>
                        <sats xmlns="">30000.00</sats>
                        <typeSats xmlns="">MND</typeSats>
                        <antallSats xmlns="">1.00</antallSats>
                        <saksbehId xmlns="">Z123</saksbehId>
                        <uforeGrad xmlns="">0</uforeGrad>
                        <kravhaverId xmlns=""></kravhaverId>
                        <delytelseId xmlns="">0adee7fd-f21b-4fcb-9dc0-e2234a</delytelseId>
                        <bostedsenhet xmlns="">8020</bostedsenhet>
                        <skykldnerId xmlns=""></skykldnerId>
                        <klassekode xmlns="">SUUFORE</klassekode>
                        <klasseKodeBeskrivelse xmlns="">Supplerende stønad Uføre</klasseKodeBeskrivelse>
                        <typeKlasse xmlns="">YTEL</typeKlasse>
                        <typeKlasseBeskrivelse xmlns="">Klassetype for ytelseskonti</typeKlasseBeskrivelse>
                        <refunderesOrgNr xmlns=""></refunderesOrgNr>
                     </beregningStoppnivaaDetaljer>
                  </beregningStoppnivaa>
                  <beregningStoppnivaa>
                     <kodeFagomraade xmlns="">SUUFORE</kodeFagomraade>
                     <stoppNivaaId xmlns="">1</stoppNivaaId>
                     <behandlendeEnhet xmlns="">8020</behandlendeEnhet>
                     <oppdragsId xmlns="">53387554</oppdragsId>
                     <fagsystemId xmlns="">forventer-at-ikke-relevant-fagsystemid-filtreres-bort</fagsystemId>
                     <kid xmlns=""/>
                     <utbetalesTilId xmlns="">$fnr</utbetalesTilId>
                     <utbetalesTilNavn xmlns="">$navn</utbetalesTilNavn>
                     <bilagsType xmlns="">U</bilagsType>
                     <forfall xmlns="">2021-04-14</forfall>
                     <feilkonto xmlns="">false</feilkonto>
                     <beregningStoppnivaaDetaljer>
                        <faktiskFom xmlns="">2021-02-01</faktiskFom>
                        <faktiskTom xmlns="">2021-02-28</faktiskTom>
                        <kontoStreng xmlns="">konto</kontoStreng>
                        <behandlingskode xmlns="">2</behandlingskode>
                        <belop xmlns="">30000.00</belop>
                        <trekkVedtakId xmlns="">0</trekkVedtakId>
                        <stonadId xmlns=""></stonadId>
                        <korrigering xmlns=""></korrigering>
                        <tilbakeforing xmlns="">false</tilbakeforing>
                        <linjeId xmlns="">3</linjeId>
                        <sats xmlns="">30000.00</sats>
                        <typeSats xmlns="">MND</typeSats>
                        <antallSats xmlns="">1.00</antallSats>
                        <saksbehId xmlns="">Z123</saksbehId>
                        <uforeGrad xmlns="">0</uforeGrad>
                        <kravhaverId xmlns=""></kravhaverId>
                        <delytelseId xmlns="">0adee7fd-f21b-4fcb-9dc0-e2234a</delytelseId>
                        <bostedsenhet xmlns="">8020</bostedsenhet>
                        <skykldnerId xmlns=""></skykldnerId>
                        <klassekode xmlns="">SUUFORE</klassekode>
                        <klasseKodeBeskrivelse xmlns="">Supplerende stønad Uføre</klasseKodeBeskrivelse>
                        <typeKlasse xmlns="">YTEL</typeKlasse>
                        <typeKlasseBeskrivelse xmlns="">Klassetype for ytelseskonti</typeKlasseBeskrivelse>
                        <refunderesOrgNr xmlns=""></refunderesOrgNr>
                     </beregningStoppnivaaDetaljer>
                  </beregningStoppnivaa>
               </beregningsPeriode>
            </simulering>
            <infomelding>
               <beskrMelding>Simulering er utført uten skattevedtak. Nominell sats benyttet.</beskrMelding>
            </infomelding>
         </response>
      </simulerBeregningResponse>
    """.trimIndent()

    @Test
    fun `filtrerer vekk andre fagsystemid-er`() {
        val simuleringXml = simuleringXml {
            datoBeregnet = "2021-04-14"
            belop = "10390.00"
            periode {
                periodeFom = "2021-04-01"
                periodeTom = "2021-04-30"
                stoppnivå {
                    forfall = "2021-01-01"
                    // Forventer at denne filtreres vekk
                    kodeFagomraade = "UFORE"
                    ordinær(2)
                    skattedetalj(-1)
                }
                stoppnivå {
                    forfall = "2021-04-19"
                    ordinær(20779)
                    skattedetalj(-5000)
                }
            }
        }
        val responseMedFremtidigUtbetaling = XmlMapper.readValue(
            simuleringXml,
            GrensesnittResponse::class.java,
        ).response

        SimuleringResponseMapper(
            simuleringXml,
            responseMedFremtidigUtbetaling,
            fixedClock,
            saksnummer,
        ).simulering shouldBe Simulering(
            gjelderId = fnr,
            gjelderNavn = navn,
            datoBeregnet = 14.april(2021),
            nettoBeløp = 10390,
            periodeList = listOf(
                SimulertPeriode(
                    fraOgMed = 1.april(2021),
                    tilOgMed = 30.april(2021),
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
            rawXml = simuleringXml,
        ).also {
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

    //language=xml
    private val xmlResponseMedUinteressanteKoder = """
    <simulerBeregningResponse xmlns="http://nav.no/system/os/tjenester/simulerFpService/simulerFpServiceGrensesnitt">
             <response xmlns="">
                <simulering>
                   <gjelderId>$fnr</gjelderId>
                   <gjelderNavn>$navn</gjelderNavn>
                   <datoBeregnet>2021-04-14</datoBeregnet>
                   <kodeFaggruppe>INNT</kodeFaggruppe>
                   <belop>10390.00</belop>
                   <beregningsPeriode xmlns="http://nav.no/system/os/entiteter/beregningSkjema">
                      <periodeFom xmlns="">2021-04-01</periodeFom>
                      <periodeTom xmlns="">2021-04-30</periodeTom>
                      <beregningStoppnivaa>
                         <kodeFagomraade xmlns="">SUUFORE</kodeFagomraade>
                         <stoppNivaaId xmlns="">1</stoppNivaaId>
                         <behandlendeEnhet xmlns="">8020</behandlendeEnhet>
                         <oppdragsId xmlns="">53387554</oppdragsId>
                         <fagsystemId xmlns="">$fagsystemId</fagsystemId>
                         <kid xmlns=""/>
                         <utbetalesTilId xmlns="">$fnr</utbetalesTilId>
                         <utbetalesTilNavn xmlns="">$navn</utbetalesTilNavn>
                         <bilagsType xmlns="">U</bilagsType>
                         <forfall xmlns="">2021-04-19</forfall>
                         <feilkonto xmlns="">false</feilkonto>
                         <beregningStoppnivaaDetaljer>
                            <faktiskFom xmlns="">2021-04-01</faktiskFom>
                            <faktiskTom xmlns="">2021-04-30</faktiskTom>
                            <kontoStreng xmlns="">konto</kontoStreng>
                            <behandlingskode xmlns="">2</behandlingskode>
                            <belop xmlns="">20779.00</belop>
                            <trekkVedtakId xmlns="">0</trekkVedtakId>
                            <stonadId xmlns=""></stonadId>
                            <korrigering xmlns=""></korrigering>
                            <tilbakeforing xmlns="">false</tilbakeforing>
                            <linjeId xmlns="">3</linjeId>
                            <sats xmlns="">20779.00</sats>
                            <typeSats xmlns="">MND</typeSats>
                            <antallSats xmlns="">1.00</antallSats>
                            <saksbehId xmlns="">Z123</saksbehId>
                            <uforeGrad xmlns="">0</uforeGrad>
                            <kravhaverId xmlns=""></kravhaverId>
                            <delytelseId xmlns="">0adee7fd-f21b-4fcb-9dc0-e2234a</delytelseId>
                            <bostedsenhet xmlns="">8020</bostedsenhet>
                            <skykldnerId xmlns=""></skykldnerId>
                            <klassekode xmlns="">SUUFORE</klassekode>
                            <klasseKodeBeskrivelse xmlns="">Supplerende stønad Uføre</klasseKodeBeskrivelse>
                            <typeKlasse xmlns="">YTEL</typeKlasse>
                            <typeKlasseBeskrivelse xmlns="">Klassetype for ytelseskonti</typeKlasseBeskrivelse>
                            <refunderesOrgNr xmlns=""></refunderesOrgNr>
                         </beregningStoppnivaaDetaljer>
                         <beregningStoppnivaaDetaljer>
                            <faktiskFom xmlns="">2021-04-01</faktiskFom>
                            <faktiskTom xmlns="">2021-04-30</faktiskTom>
                            <kontoStreng xmlns="">0510000</kontoStreng>
                            <behandlingskode xmlns="">0</behandlingskode>
                            <belop xmlns="">-10389.00</belop>
                            <trekkVedtakId xmlns="">11845513</trekkVedtakId>
                            <stonadId xmlns=""></stonadId>
                            <korrigering xmlns=""></korrigering>
                            <tilbakeforing xmlns="">false</tilbakeforing>
                            <linjeId xmlns="">0</linjeId>
                            <sats xmlns="">0.00</sats>
                            <typeSats xmlns="">MND</typeSats>
                            <antallSats xmlns="">30.00</antallSats>
                            <saksbehId xmlns="">Z123</saksbehId>
                            <uforeGrad xmlns="">0</uforeGrad>
                            <kravhaverId xmlns=""></kravhaverId>
                            <delytelseId xmlns=""></delytelseId>
                            <bostedsenhet xmlns="">8020</bostedsenhet>
                            <skykldnerId xmlns=""></skykldnerId>
                            <klassekode xmlns="">FSKTSKAT</klassekode>
                            <klasseKodeBeskrivelse xmlns="">Forskuddskatt</klasseKodeBeskrivelse>
                            <typeKlasse xmlns="">SKAT</typeKlasse>
                            <typeKlasseBeskrivelse xmlns="">Klassetype for skatt</typeKlasseBeskrivelse>
                            <refunderesOrgNr xmlns=""></refunderesOrgNr>
                         </beregningStoppnivaaDetaljer>
                         <beregningStoppnivaaDetaljer>
                            <faktiskFom xmlns="">2021-04-01</faktiskFom>
                            <faktiskTom xmlns="">2021-04-30</faktiskTom>
                            <kontoStreng xmlns="">0510000</kontoStreng>
                            <behandlingskode xmlns="">0</behandlingskode>
                            <belop xmlns="">-10389.00</belop>
                            <trekkVedtakId xmlns="">11845513</trekkVedtakId>
                            <stonadId xmlns=""></stonadId>
                            <korrigering xmlns=""></korrigering>
                            <tilbakeforing xmlns="">false</tilbakeforing>
                            <linjeId xmlns="">0</linjeId>
                            <sats xmlns="">0.00</sats>
                            <typeSats xmlns="">MND</typeSats>
                            <antallSats xmlns="">30.00</antallSats>
                            <saksbehId xmlns="">Z123</saksbehId>
                            <uforeGrad xmlns="">0</uforeGrad>
                            <kravhaverId xmlns=""></kravhaverId>
                            <delytelseId xmlns=""></delytelseId>
                            <bostedsenhet xmlns="">8020</bostedsenhet>
                            <skykldnerId xmlns=""></skykldnerId>
                            <klassekode xmlns="">TULL</klassekode>
                            <klasseKodeBeskrivelse xmlns="">Tull</klasseKodeBeskrivelse>
                            <typeKlasse xmlns="">TØYS</typeKlasse>
                            <typeKlasseBeskrivelse xmlns="">Tøys</typeKlasseBeskrivelse>
                            <refunderesOrgNr xmlns=""></refunderesOrgNr>
                         </beregningStoppnivaaDetaljer>
                      </beregningStoppnivaa>
                   </beregningsPeriode>
                </simulering>
                <infomelding>
                   <beskrMelding>Simulering er utført uten skattevedtak. Nominell sats benyttet.</beskrMelding>
                </infomelding>
             </response>
          </simulerBeregningResponse>
    """.trimIndent()

    @Test
    fun `mapping med åpen feilkonto`() {
        val responseMedÅpenFeilkonto = objectMapper.readValue(
            jsonMedÅpenFeilkonto,
            SimulerBeregningResponse::class.java,
        )
        val rawXml = jsonMedÅpenFeilkonto
        SimuleringResponseMapper(
            rawXml,
            oppdragResponse = responseMedÅpenFeilkonto,
            clock = fixedClock,
            saksnummer = saksnummer,
        ).simulering.also {
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
        val responseMedÅpenFeilkonto = objectMapper.readValue(
            jsonMedÅpenFeilkontoOgEtterbetaling,
            SimulerBeregningResponse::class.java,
        )
        val rawXml = jsonMedÅpenFeilkontoOgEtterbetaling
        SimuleringResponseMapper(
            rawXml = rawXml,
            oppdragResponse = responseMedÅpenFeilkonto,
            clock = fixedClock,
            saksnummer = saksnummer,
        ).simulering.also {
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
    fun `mapping med reduksjon av åpen feilkonto`() {
        val responseMedÅpenFeilkonto = objectMapper.readValue(
            jsonMedReduksjonAvFeilkonto,
            SimulerBeregningResponse::class.java,
        )
        val rawXml = jsonMedReduksjonAvFeilkonto
        SimuleringResponseMapper(
            rawXml = rawXml,
            oppdragResponse = responseMedÅpenFeilkonto,
            clock = fixedClock,
            saksnummer = saksnummer,
        ).simulering.also {
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
                januar(2022).tilPeriode() to Kontooppstilling(
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
