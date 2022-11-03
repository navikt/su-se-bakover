package no.nav.su.se.bakover.domain.søknad

import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.domain.person.Person
import no.nav.su.se.bakover.domain.sak.Saksnummer
import no.nav.su.se.bakover.domain.søknadinnhold.Boforhold
import no.nav.su.se.bakover.domain.søknadinnhold.EktefellePartnerSamboer
import no.nav.su.se.bakover.domain.søknadinnhold.InnlagtPåInstitusjon
import no.nav.su.se.bakover.domain.søknadinnhold.OppgittAdresse
import no.nav.su.se.bakover.domain.søknadinnhold.Personopplysninger
import no.nav.su.se.bakover.domain.søknadinnhold.SøknadInnhold
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.fnrOver67
import no.nav.su.se.bakover.test.fnrUnder67
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.søknad.boforhold
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.time.Clock
import java.util.UUID

class SøknadPdfInnholdJsonTest {

    private val søknadsId = UUID.randomUUID()
    private fun søknadPdfInnhold(
        saksnummer: Saksnummer = Saksnummer(2021),
        navn: Person.Navn = Person.Navn(fornavn = "Tore", mellomnavn = "Johnas", etternavn = "Strømøy"),
        søknadOpprettet: Tidspunkt = 1.januar(2021).startOfDay(),
        fnr: Fnr = fnrUnder67,
        epsFnr: Fnr = fnrOver67,
        søknadInnhold: SøknadInnhold = no.nav.su.se.bakover.test.søknad.søknadinnhold(
            personopplysninger = Personopplysninger(fnr),
            boforhold = boforhold(
                ektefellePartnerSamboer = EktefellePartnerSamboer(
                    erUførFlyktning = false,
                    fnr = epsFnr,
                ),
            ),
        ),
        clock: Clock = fixedClock,
    ) = SøknadPdfInnhold.create(
        saksnummer = saksnummer,
        søknadsId = søknadsId,
        navn = navn,
        søknadOpprettet = søknadOpprettet,
        søknadInnhold = søknadInnhold,
        clock = clock,
    )

    @Test
    fun `søker har ikke fast bosted matcher json`() {
        //language=JSON
        val forventetJson =
            """
                {
                  "saksnummer": 2021,
                  "søknadsId": "$søknadsId",
                  "navn": {
                    "fornavn": "Tore",
                    "mellomnavn": "Johnas",
                    "etternavn": "Strømøy"
                  },
                  "dagensDatoOgTidspunkt": "01.01.2021 02:02",
                  "søknadOpprettet": "01.01.2021",
                  "søknadInnhold": {
                      "type": "uføre",
                      "uførevedtak":{
                          "harUførevedtak":true
                      },
                      "personopplysninger":{
                          "fnr":"$fnrUnder67"
                      },
                      "flyktningsstatus":{
                          "registrertFlyktning":false
                      },
                      "boforhold": {
                          "borOgOppholderSegINorge": true,
                          "delerBolig": true,
                          "delerBoligMed": "EKTEMAKE_SAMBOER",
                          "ektefellePartnerSamboer": {
                              "erUførFlyktning": false,
                              "fnr": "$fnrOver67"
                          },
                          "innlagtPåInstitusjon": {
                              "datoForInnleggelse": "2020-01-01",
                              "datoForUtskrivelse": "2020-01-31",
                              "fortsattInnlagt": false
                          },
                          "oppgittAdresse": {
                            "type": "IngenAdresse",
                            "grunn": "HAR_IKKE_FAST_BOSTED"
                          }
                      },
                      "utenlandsopphold": {
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
                          "oppholdstillatelseType": "MIDLERTIDIG",
                          "statsborgerskapAndreLand":false,
                          "statsborgerskapAndreLandFritekst":null
                      },
                      "inntektOgPensjon":{
                          "forventetInntekt":2500,
                          "andreYtelserINav":"sosialstønad",
                          "andreYtelserINavBeløp":33,
                          "søktAndreYtelserIkkeBehandletBegrunnelse":"uføre",
                          "trygdeytelseIUtlandet": [
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
                      "forNav":{
                          "harFullmektigEllerVerge": "VERGE",
                          "type": "DigitalSøknad"
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
                            "trygdeytelseIUtlandet": null,
                            "pensjon": null
                        }
                      }
                    }
                }
            """.trimIndent()

        JSONAssert.assertEquals(forventetJson, serialize(søknadPdfInnhold()), true)
    }

    @Test
    fun `søker bor på annen adresse matcher json`() {
        val søknadPdfInnhold = søknadPdfInnhold(
            søknadInnhold = no.nav.su.se.bakover.test.søknad.søknadinnhold(
                personopplysninger = Personopplysninger(fnrUnder67),
                boforhold = Boforhold.tryCreate(
                    borOgOppholderSegINorge = true,
                    delerBolig = true,
                    delerBoligMed = Boforhold.DelerBoligMed.EKTEMAKE_SAMBOER,
                    ektefellePartnerSamboer = EktefellePartnerSamboer(
                        erUførFlyktning = false,
                        fnr = fnrOver67,
                    ),
                    innlagtPåInstitusjon = InnlagtPåInstitusjon(
                        datoForInnleggelse = 1.januar(2020),
                        datoForUtskrivelse = 31.januar(2020),
                        fortsattInnlagt = false,
                    ),
                    oppgittAdresse = OppgittAdresse.IngenAdresse(OppgittAdresse.IngenAdresse.IngenAdresseGrunn.BOR_PÅ_ANNEN_ADRESSE),
                ).getOrFail(),
            ),
        )

        //language=JSON
        val forventetJson =
            """
                {
                  "saksnummer": 2021,
                  "søknadsId": "$søknadsId",
                  "navn": {
                    "fornavn": "Tore",
                    "mellomnavn": "Johnas",
                    "etternavn": "Strømøy"
                  },
                  "dagensDatoOgTidspunkt": "01.01.2021 02:02",
                  "søknadOpprettet": "01.01.2021",
                  "søknadInnhold": {
                      "type": "uføre",
                      "uførevedtak":{
                          "harUførevedtak":true
                      },
                      "personopplysninger":{
                          "fnr":"$fnrUnder67"
                      },
                      "flyktningsstatus":{
                          "registrertFlyktning":false
                      },
                      "boforhold": {
                          "borOgOppholderSegINorge": true,
                          "delerBolig": true,
                          "delerBoligMed": "EKTEMAKE_SAMBOER",
                          "ektefellePartnerSamboer": {
                              "erUførFlyktning": false,
                              "fnr": "$fnrOver67"
                          },
                          "innlagtPåInstitusjon": {
                              "datoForInnleggelse": "2020-01-01",
                              "datoForUtskrivelse": "2020-01-31",
                              "fortsattInnlagt": false
                          },
                          "oppgittAdresse": {
                            "type": "IngenAdresse",
                            "grunn": "BOR_PÅ_ANNEN_ADRESSE"
                          }
                      },
                      "utenlandsopphold": {
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
                          "oppholdstillatelseType": "MIDLERTIDIG",
                          "statsborgerskapAndreLand":false,
                          "statsborgerskapAndreLandFritekst":null
                      },
                      "inntektOgPensjon":{
                          "forventetInntekt":2500,
                          "andreYtelserINav":"sosialstønad",
                          "andreYtelserINavBeløp":33,
                          "søktAndreYtelserIkkeBehandletBegrunnelse":"uføre",
                          "trygdeytelseIUtlandet": [
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
                      "forNav":{
                          "harFullmektigEllerVerge": "VERGE",
                          "type": "DigitalSøknad"
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
                            "trygdeytelseIUtlandet": null,
                            "pensjon": null
                        }
                      }
                    }
                }
            """.trimIndent()

        JSONAssert.assertEquals(forventetJson, serialize(søknadPdfInnhold), true)
    }

    @Test
    fun `søker oppgir adresse matcher json`() {
        val søknadPdfInnhold = søknadPdfInnhold(
            søknadInnhold = no.nav.su.se.bakover.test.søknad.søknadinnhold(
                personopplysninger = Personopplysninger(fnrUnder67),
                boforhold = Boforhold.tryCreate(
                    borOgOppholderSegINorge = true,
                    delerBolig = true,
                    delerBoligMed = Boforhold.DelerBoligMed.EKTEMAKE_SAMBOER,
                    ektefellePartnerSamboer = EktefellePartnerSamboer(
                        erUførFlyktning = false,
                        fnr = fnrOver67,
                    ),
                    innlagtPåInstitusjon = InnlagtPåInstitusjon(
                        datoForInnleggelse = 1.januar(2020),
                        datoForUtskrivelse = 31.januar(2020),
                        fortsattInnlagt = false,
                    ),
                    oppgittAdresse = OppgittAdresse.BorPåAdresse(
                        adresselinje = "Oslogata 12",
                        postnummer = "0050",
                        poststed = "OSLO",
                        bruksenhet = null,
                    ),
                ).getOrFail(),
            ),
        )

        //language=JSON
        val forventetJson =
            """
                {
                  "saksnummer": 2021,
                  "søknadsId": "$søknadsId",
                  "navn": {
                    "fornavn": "Tore",
                    "mellomnavn": "Johnas",
                    "etternavn": "Strømøy"
                  },
                  "dagensDatoOgTidspunkt": "01.01.2021 02:02",
                  "søknadOpprettet": "01.01.2021",
                  "søknadInnhold": {
                      "type": "uføre",
                      "uførevedtak":{
                          "harUførevedtak":true
                      },
                      "personopplysninger":{
                          "fnr":"$fnrUnder67"
                      },
                      "flyktningsstatus":{
                          "registrertFlyktning":false
                      },
                      "boforhold": {
                          "borOgOppholderSegINorge": true,
                          "delerBolig": true,
                          "delerBoligMed": "EKTEMAKE_SAMBOER",
                          "ektefellePartnerSamboer": {
                              "erUførFlyktning": false,
                              "fnr": "$fnrOver67"
                          },
                          "innlagtPåInstitusjon": {
                              "datoForInnleggelse": "2020-01-01",
                              "datoForUtskrivelse": "2020-01-31",
                              "fortsattInnlagt": false
                          },
                          "oppgittAdresse": {
                            "type":"BorPåAdresse",
                            "poststed":"OSLO",
                            "postnummer":"0050",
                            "adresselinje":"Oslogata 12",
                            "bruksenhet": null
                          }
                      },
                      "utenlandsopphold": {
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
                          "oppholdstillatelseType": "MIDLERTIDIG",
                          "statsborgerskapAndreLand":false,
                          "statsborgerskapAndreLandFritekst":null
                      },
                      "inntektOgPensjon":{
                          "forventetInntekt":2500,
                          "andreYtelserINav":"sosialstønad",
                          "andreYtelserINavBeløp":33,
                          "søktAndreYtelserIkkeBehandletBegrunnelse":"uføre",
                          "trygdeytelseIUtlandet": [
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
                      "forNav":{
                          "harFullmektigEllerVerge": "VERGE",
                          "type": "DigitalSøknad"
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
                            "trygdeytelseIUtlandet": null,
                            "pensjon": null
                        }
                      }
                    }
                }
            """.trimIndent()

        JSONAssert.assertEquals(forventetJson, serialize(søknadPdfInnhold), true)
    }
}
