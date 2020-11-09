package no.nav.su.se.bakover.web.routes.søknad

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.oktober
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.fnrUnder67
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.time.format.DateTimeFormatter
import java.util.UUID

internal class SøknadJsonTest {
    companion object {
        val søknadId = UUID.randomUUID()
        val søknad = Søknad(
            sakId = UUID.randomUUID(),
            opprettet = Tidspunkt.EPOCH,
            id = søknadId,
            søknadInnhold = SøknadInnholdTestdataBuilder.build()
        )
        val opprettetTidspunkt = DateTimeFormatter.ISO_INSTANT.format(søknad.opprettet)

        //language=JSON
        val søknadJsonString =
            """
        {
          "id": "$søknadId",
          "opprettet": "$opprettetTidspunkt",
          "søknadInnhold": {
            "uførevedtak":{
                "harUførevedtak":true
            },
            "personopplysninger":{
                "fnr":"12345678910"
            },
            "flyktningsstatus":{
                "registrertFlyktning":false
            },
            "boforhold":{
                "borOgOppholderSegINorge":true,
                "delerBoligMedVoksne":true,
                "delerBoligMed":"EKTEMAKE_SAMBOER",
                "ektefellePartnerSamboer":{
                    "type": "MedFnr",
                    "erUførFlyktning": false,
                    "fnr": "${fnrUnder67()}"
                },
                "innlagtPåInstitusjon": {
                    "datoForInnleggelse": "2020-01-01",
                    "datoForUtskrivelse": "2020-01-31",
                    "fortsattInnlagt": false
                }
            },
            "utenlandsopphold":{
                "registrertePerioder":[
                    {
                        "utreisedato":"2020-01-01",
                        "innreisedato":"2020-01-31"
                    },
                    {
                        "utreisedato":"2020-02-01",
                        "innreisedato":"2020-02-05"
                    }
                ],
                "planlagtePerioder":[
                    {
                        "utreisedato":"2020-07-01",
                        "innreisedato":"2020-07-31"
                    }
                ]
            },
            "oppholdstillatelse":{
                "erNorskStatsborger":false,
                "harOppholdstillatelse":true,
                "typeOppholdstillatelse":"midlertidig",
                "statsborgerskapAndreLand":false,
                "statsborgerskapAndreLandFritekst":null
            },
            "inntektOgPensjon":{
                "forventetInntekt":2500,
                "andreYtelserINav":"sosialstønad",
                "andreYtelserINavBeløp":33,
                "søktAndreYtelserIkkeBehandletBegrunnelse":"uføre",
                "sosialstønadBeløp":7000.0,
                "trygdeytelserIUtlandet": [
                    {
                        "beløp": 200,
                        "type": "trygd",
                        "fra": "En trygdeutgiver"
                    },
                    {
                        "beløp": 500,
                        "type": "Annen trygd",
                        "fra": "En annen trygdeutgiver"
                    }
                ],
                "pensjon":[
                    {
                        "ordning":"KLP",
                        "beløp":2000.0
                    },
                    {
                        "ordning":"SPK",
                        "beløp":5000.0
                    }
                ]
            },
            "formue":{
                "borIBolig":false,
                "verdiPåBolig":600000,
                "boligBrukesTil":"Mine barn bor der",
                "depositumsBeløp":1000.0,
                "kontonummer":"12345678912",
                "verdiPåEiendom":3,
                "eiendomBrukesTil":"",
                "kjøretøy": [
                    {
                        "verdiPåKjøretøy":  25000,
                        "kjøretøyDeEier":  "bil"
                    }
                ],
                "innskuddsBeløp":25000,
                "verdipapirBeløp":25000,
                "skylderNoenMegPengerBeløp":25000,
                "kontanterBeløp":25000
            },
            "forNav":{
                "harFullmektigEllerVerge":"verge",
                "type": "DigitalSøknad"
            },
            "ektefelle": {
                "formue": {
                "borIBolig":false,
                "verdiPåBolig": 0,
                "boligBrukesTil":"",
                "depositumsBeløp":0,
                "kontonummer":"11111111111",
                "verdiPåEiendom":0,
                "eiendomBrukesTil":"",
                "kjøretøy": [],
                "innskuddsBeløp": 0,
                "verdipapirBeløp": 0,
                "skylderNoenMegPengerBeløp": 0,
                "kontanterBeløp": 0
                },
              "inntektOgPensjon": {
                  "forventetInntekt": null,
                  "andreYtelserINav": null,
                  "andreYtelserINavBeløp": null,
                  "søktAndreYtelserIkkeBehandletBegrunnelse": null,
                  "sosialstønadBeløp": null,
                  "trygdeytelserIUtlandet": null,
                  "pensjon": null
              }
            }
          },
          "lukket": null
        }
            """.trimIndent()
    }

    @Test
    fun `should serialize to json string`() {
        JSONAssert.assertEquals(søknadJsonString, serialize(søknad.toJson()), true)
    }

    @Test
    fun `should deserialize json string`() {
        deserialize<SøknadJson>(søknadJsonString) shouldBe søknad.toJson()
    }

    @Test
    fun `serialiserer og deserialiserer lukket`() {
        val trukket = Søknad.Lukket(
            tidspunkt = 1.oktober(2020).startOfDay(),
            saksbehandler = "Z123",
            type = Søknad.LukketType.TRUKKET
        )
        //language=json
        val expectedJson = """
            {
                "tidspunkt":"2020-10-01T00:00:00Z",
                "saksbehandler":"Z123",
                "type":"TRUKKET"
            }
        """.trimIndent()
        JSONAssert.assertEquals(expectedJson, serialize(trukket.toJson()), true)
        deserialize<Søknad.Lukket>(expectedJson) shouldBe trukket
    }
}
