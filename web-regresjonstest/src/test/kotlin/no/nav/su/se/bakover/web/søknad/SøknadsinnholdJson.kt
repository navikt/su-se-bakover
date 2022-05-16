package no.nav.su.se.bakover.web.søknad

import no.nav.su.se.bakover.test.fixedLocalDate

fun digitalSøknadsinnholdUføreJson(
    fnr: String,
): String {
    return søknadsinnholdUføreJson(
        fnr = fnr,
        // language=JSON
        forNav = """
        {
          "harFullmektigEllerVerge":null,
          "type":"DigitalSøknad"
        }
        """.trimIndent(),
    )
}

fun papirsøknadsinnholdUføreJson(
    fnr: String,
    mottaksdato: String = fixedLocalDate.toString(),
): String {
    return søknadsinnholdUføreJson(
        fnr = fnr,
        // language=JSON
        forNav = """
        {
          "mottaksdatoForSøknad":"$mottaksdato",
          "grunnForPapirinnsending": "Annet",
          "annenGrunn":null,
          "type":"Papirsøknad"
        }
        """.trimIndent(),
    )
}

/**
 * Mapper til [no.nav.su.se.bakover.web.routes.søknad.SøknadsinnholdUføreJson]
 *
 * Foreløpig spiller det ikke så veldig mye for domenemodellen hva søknaden inneholder, siden vi i all hovedsak bare bruker den til visning.
 * Visse ting valideres og visse har en direkte påvirkning:
 * @param fnr Dette er fødselsnummeret vi slår opp i PDL og knytter en sak mot. Merk at vi benytter fødselsnummeret som kommer fra PDL (da vil oppslagsfødselsnummeret være historisk).
 * @param forNav Bestemmer mottaksdato avhengig om det er en digital eller papirsøknad. Eksterne integrasjoner som statistikk og metrikker.
 */
private fun søknadsinnholdUføreJson(
    fnr: String,
    forNav: String,
): String {
    //language=JSON
    return """
    {
      "type": "uføre",
      "uførevedtak":{
        "harUførevedtak":true
      },
      "personopplysninger":{
        "fnr":"$fnr"
      },
      "flyktningsstatus":{
        "registrertFlyktning":true
      },
      "boforhold":{
        "borOgOppholderSegINorge":true,
        "delerBoligMedVoksne":true,
        "delerBoligMed":null,
        "ektefellePartnerSamboer":null,
        "innlagtPåInstitusjon":{
          "datoForInnleggelse":"2020-01-01",
          "datoForUtskrivelse":"2020-01-31",
          "fortsattInnlagt":false
        },
        "borPåAdresse":null,
        "ingenAdresseGrunn":"HAR_IKKE_FAST_BOSTED"
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
        "trygdeytelserIUtlandet":[
          {
            "beløp":200,
            "type":"trygd",
            "valuta":"En valuta"
          },
          {
            "beløp":500,
            "type":"Annen trygd",
            "valuta":"En annen valuta"
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
        "eierBolig":true,
        "borIBolig":true,
        "verdiPåBolig":600000,
        "boligBrukesTil":"Mine barn bor der",
        "depositumsBeløp":1000.0,
        "verdiPåEiendom":3,
        "eiendomBrukesTil":"",
        "kjøretøy":[
          {
            "verdiPåKjøretøy":2500,
            "kjøretøyDeEier":"bil"
          }
        ],
        "innskuddsBeløp":3500,
        "verdipapirBeløp":4500,
        "skylderNoenMegPengerBeløp":1200,
        "kontanterBeløp":1300
      },
      "forNav":$forNav,
      "ektefelle":{
        "formue":{
          "eierBolig":true,
          "borIBolig":false,
          "verdiPåBolig":0,
          "boligBrukesTil":"",
          "depositumsBeløp":0,
          "verdiPåEiendom":0,
          "eiendomBrukesTil":"",
          "kjøretøy":[
            
          ],
          "innskuddsBeløp":0,
          "verdipapirBeløp":0,
          "skylderNoenMegPengerBeløp":0,
          "kontanterBeløp":0
        },
        "inntektOgPensjon":{
          "forventetInntekt":null,
          "andreYtelserINav":null,
          "andreYtelserINavBeløp":null,
          "søktAndreYtelserIkkeBehandletBegrunnelse":null,
          "trygdeytelserIUtlandet":null,
          "pensjon":null
        }
      }
    }
    """.trimIndent()
}
