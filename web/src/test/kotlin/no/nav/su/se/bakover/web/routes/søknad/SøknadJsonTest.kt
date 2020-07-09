package no.nav.su.se.bakover.web.routes.søknad

import io.kotest.assertions.json.shouldMatchJson
import io.kotest.matchers.shouldBe
import no.nav.su.meldinger.kafka.soknad.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.web.deserialize
import no.nav.su.se.bakover.web.serialize
import org.junit.jupiter.api.Test
import java.util.UUID

internal class SøknadJsonTest {

    private val søknadId = UUID.randomUUID()
    private val søknad = Søknad(
        id = søknadId,
        søknadInnhold = SøknadInnholdTestdataBuilder.build()
    )

    //language=JSON
    private val søknadJsonString = """
        {
          "id": "$søknadId",
          "søknadInnhold": {
            "uførevedtak":{
                "harUførevedtak":true
            },
            "personopplysninger":{
                "fnr":"12345678910",
                "fornavn":"Ola",
                "mellomnavn":"Erik",
                "etternavn":"Nordmann",
                "telefonnummer":"12345678",
                "gateadresse":"Oslogata 12",
                "postnummer":"0050",
                "poststed":"Oslo",
                "bruksenhet":"U1H20",
                "bokommune":"Oslo",
                "statsborgerskap":"NOR"
            },
            "flyktningsstatus":{
                "registrertFlyktning":false
            },
            "boforhold":{
                "borOgOppholderSegINorge":true,
                "delerBoligMedVoksne":true,
                "delerBoligMed":"ektemake-eller-samboer",
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
                "trygdeytelserIUtlandetBeløp":2,
                "trygdeytelserIUtlandet":"en-eller-annen-ytelse",
                "trygdeytelserIUtlandetFra":"Utlandet",
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
                "verdiPåKjøretøy":25000,
                "kjøretøyDeEier":"bil",
                "innskuddsBeløp":25000,
                "verdipapirBeløp":25000,
                "skylderNoenMegPengerBeløp":25000,
                "kontanterBeløp":25000
            },
            "forNav":{
                "harFullmektigEllerVerge":"verge"
            }
          }
        }
    """.trimIndent()

    @Test
    fun `should serialize to json string`() {
        serialize(søknad.toDto().toJson()) shouldMatchJson søknadJsonString
    }

    @Test
    fun `should deserialize json string`() {
        deserialize<SøknadJson>(søknadJsonString) shouldBe søknad.toDto().toJson()
    }
}
