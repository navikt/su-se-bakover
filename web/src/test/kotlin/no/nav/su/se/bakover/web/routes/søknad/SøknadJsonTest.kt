package no.nav.su.se.bakover.web.routes.søknad

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
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
                "ektemakeEllerSamboerUnder67År":true,
                "ektemakeEllerSamboerUførFlyktning":false
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
                "oppholdstillatelseMindreEnnTreMåneder":false,
                "oppholdstillatelseForlengelse":true,
                "statsborgerskapAndreLand":false,
                "statsborgerskapAndreLandFritekst":null
            },
            "inntektOgPensjon":{
                "forventetInntekt":2500,
                "tjenerPengerIUtlandetBeløp":20,
                "andreYtelserINav":"sosialstønad",
                "andreYtelserINavBeløp":33,
                "søktAndreYtelserIkkeBehandletBegrunnelse":"uføre",
                "sosialstønadBeløp":7000.0,
                "trygdeytelseIUtlandet": [
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
                "harFullmektigEllerVerge":"verge"
            }
          },
          "søknadTrukket": null
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
}
