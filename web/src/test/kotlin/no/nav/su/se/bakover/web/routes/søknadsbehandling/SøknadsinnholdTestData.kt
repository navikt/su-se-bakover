package no.nav.su.se.bakover.web.routes.søknadsbehandling

import no.nav.su.se.bakover.test.fnrOver67
import no.nav.su.se.bakover.test.fnrUnder67

//language=JSON
val alderssøknadsinnholdJson =
    """
{
  "type": "alder",
  "harSøktAlderspensjon": {
    "harSøktAlderspensjon": false
  },
  "oppholdstillatelseAlder": {
    "eøsborger": false,
    "familieforening": false
  },
  "oppholdstillatelse": {
    "erNorskStatsborger": false,
    "harOppholdstillatelse": true,
    "typeOppholdstillatelse": "midlertidig",
    "statsborgerskapAndreLand": false,
    "statsborgerskapAndreLandFritekst": null
  },
  "personopplysninger": {
    "fnr": "$fnrOver67"
  },
  "boforhold": {
    "borOgOppholderSegINorge": true,
    "delerBoligMedVoksne": true,
    "delerBoligMed": "EKTEMAKE_SAMBOER",
    "ektefellePartnerSamboer":{
      "erUførFlyktning": false,
      "fnr": "${fnrUnder67()}"
    },
    "innlagtPåInstitusjon": {
      "datoForInnleggelse": "2020-01-01",
      "datoForUtskrivelse": "2020-01-31",
      "fortsattInnlagt": false
    },
    "borPåAdresse": null,
    "ingenAdresseGrunn": "HAR_IKKE_FAST_BOSTED"
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
  "inntektOgPensjon":{
                "forventetInntekt":2500,
                "andreYtelserINav":"sosialstønad",
                "andreYtelserINavBeløp":33,
                "søktAndreYtelserIkkeBehandletBegrunnelse":"uføre",
                "trygdeytelserIUtlandet": [
                    {
                        "beløp": 200,
                        "type": "trygd",
                        "valuta": "En valuta"
                    },
                    {
                        "beløp": 500,
                        "type": "Annen trygd",
                        "valuta": "En annen valuta"
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
                "eierBolig": true,
                "borIBolig":false,
                "verdiPåBolig":600000,
                "boligBrukesTil":"Mine barn bor der",
                "depositumsBeløp":1000.0,
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
  "forNav": {
    "type": "DigitalSøknad",
    "harFullmektigEllerVerge": "verge"
  },
  "ektefelle": {
                "formue": {
                "eierBolig": true,
                "borIBolig":false,
                "verdiPåBolig": 0,
                "boligBrukesTil":"",
                "depositumsBeløp":0,
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
                  "trygdeytelserIUtlandet": null,
                  "pensjon": null
              }
            }
}
    """.trimIndent()
