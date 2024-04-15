package no.nav.su.se.bakover.web.regulering

import io.kotest.matchers.shouldBe
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.common.domain.tid.fixedClock
import no.nav.su.se.bakover.common.domain.tid.mai
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.test.applicationConfig
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedClockAt
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import no.nav.su.se.bakover.web.komponenttest.AppComponents
import no.nav.su.se.bakover.web.komponenttest.testSusebakover
import no.nav.su.se.bakover.web.sak.hent.hentReguleringMedId
import no.nav.su.se.bakover.web.sak.hent.hentReguleringer
import no.nav.su.se.bakover.web.sak.hent.hentSakForFnr
import no.nav.su.se.bakover.web.sak.hent.hentSakId
import no.nav.su.se.bakover.web.søknadsbehandling.GrunnlagJson
import no.nav.su.se.bakover.web.søknadsbehandling.ReguleringJson
import no.nav.su.se.bakover.web.søknadsbehandling.fradrag.leggTilFradrag
import no.nav.su.se.bakover.web.søknadsbehandling.opprettInnvilgetSøknadsbehandling
import org.junit.jupiter.api.Test

internal class ReguleringIT {

    @Test
    fun `automatisk regulering`() {
        val fnrForSakSomSkalReguleres = Fnr.generer().toString()
        withMigratedDb { dataSource ->
            testApplication {
                val appComponents = AppComponents.from(
                    dataSource = dataSource,
                    clockParam = fixedClock,
                    applicationConfig = applicationConfig(),
                )
                application {
                    testSusebakover(appComponents = appComponents)
                }
                opprettInnvilgetSøknadsbehandling(
                    fnr = fnrForSakSomSkalReguleres,
                    client = this.client,
                    appComponents = appComponents,
                )
            }

            testApplication {
                val appComponents = AppComponents.from(
                    dataSource = dataSource,
                    clockParam = fixedClockAt(21.mai(2021)),
                    applicationConfig = applicationConfig(),
                )
                application {
                    testSusebakover(appComponents = appComponents)
                }
                val fnrForSakSomIkkeSkalBliRegulert = Fnr.generer().toString()
                opprettInnvilgetSøknadsbehandling(
                    fnr = fnrForSakSomIkkeSkalBliRegulert,
                    client = this.client,
                    appComponents = appComponents,
                )
                regulerAutomatisk(mai(2021), this.client)

                val sakMedRegulering = hentSakForFnr(fnrForSakSomSkalReguleres, client = this.client)
                val sakUtenRegulering = hentSakForFnr(fnrForSakSomIkkeSkalBliRegulert, client = this.client)
                hentReguleringer(sakUtenRegulering) shouldBe "[]"
                val reguleringen = ReguleringJson.hentSingleReglering(hentReguleringer(sakMedRegulering))
                verifyIverksattReguleringFraAutomatisk(reguleringen, fnrForSakSomSkalReguleres)
            }
        }
    }

    @Test
    fun `manuell regulering`() {
        val fnrForSakSomSkalReguleres = Fnr.generer().toString()
        withMigratedDb { dataSource ->
            testApplication {
                val appComponents = AppComponents.from(
                    dataSource = dataSource,
                    clockParam = fixedClock,
                    applicationConfig = applicationConfig(),
                )
                application { testSusebakover(appComponents = appComponents) }
                opprettInnvilgetSøknadsbehandling(
                    fnr = fnrForSakSomSkalReguleres,
                    client = this.client,
                    appComponents = appComponents,
                    fradrag = { sakId, behandlingId ->
                        leggTilFradrag(
                            sakId = sakId,
                            behandlingId = behandlingId,
                            client = this.client,
                            body = {
                                //language=json
                                """{
                                  "fradrag": [
                                    {
                                      "periode": {"fraOgMed": "2021-01-01","tilOgMed": "2021-12-31"},
                                      "type": "Alderspensjon",
                                      "beløp": 10000.0,
                                      "utenlandskInntekt": null,
                                      "tilhører": "BRUKER"
                                    }
                                  ]
                                }
                                """.trimIndent()
                            },
                        )
                    },
                )
            }
            testApplication {
                val appComponents = AppComponents.from(
                    dataSource = dataSource,
                    clockParam = 21.mai(2021).fixedClock(),
                    applicationConfig = applicationConfig(),
                )
                application { testSusebakover(appComponents = appComponents) }
                regulerAutomatisk(mai(2021), this.client)
                val sak = hentSakForFnr(fnrForSakSomSkalReguleres, client = this.client)
                val sakId = hentSakId(sak)
                val reguleringen = ReguleringJson.hentSingleReglering(hentReguleringer(sak))
                val reguleringsId = ReguleringJson.id(reguleringen)
                val uføregrunnlag = ReguleringJson.hentSingleUføregrunnlag(reguleringen)
                val uføregrunnlagId = GrunnlagJson.id(uføregrunnlag)
                val uføregrunnlagOpprettet = GrunnlagJson.opprettet(uføregrunnlag)

                manuellRegulering(
                    reguleringsId = reguleringsId,
                    //language=json
                    oppdatertUføre = """[{
                          "forventetInntekt":25,
                          "opprettet":"$uføregrunnlagOpprettet",
                          "uføregrad":100,
                          "id":"$uføregrunnlagId",
                          "periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}
                       }]
                    """.trimIndent(),
                    //language=json
                    oppdatertFradrag = """[{
                            "beløp":10050,
                            "tilhører":"BRUKER",
                            "utenlandskInntekt":null,
                            "type":"Alderspensjon",
                            "beskrivelse":null,
                            "periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"}
                         }]
                    """.trimIndent(),
                    client = this.client,
                )
                val iverksattReg =
                    hentSakForFnr(fnrForSakSomSkalReguleres, client = this.client).hentReguleringMedId(reguleringsId)
                verifyRegulering(
                    iverksattReg,
                    expectedId = reguleringsId,
                    expectedSakId = sakId,
                    expectedFnr = fnrForSakSomSkalReguleres,
                )
            }
        }
    }
}
