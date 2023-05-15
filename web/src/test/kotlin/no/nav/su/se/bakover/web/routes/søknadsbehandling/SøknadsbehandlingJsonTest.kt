package no.nav.su.se.bakover.web.routes.søknadsbehandling

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.fixedClock
import no.nav.su.se.bakover.common.periode.januar
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.søknadsbehandling.IverksattSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.VilkårsvurdertSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Stønadsperiode
import no.nav.su.se.bakover.domain.vilkår.FormueVilkår
import no.nav.su.se.bakover.domain.vilkår.UføreVilkår
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.iverksattSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.nySøknadsbehandlingUføre
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

internal class SøknadsbehandlingJsonTest {

    @Test
    fun `should serialize to json string`() {
        val søknadsbehandling = iverksattSøknadsbehandlingUføre(
            clock = TikkendeKlokke(1.april(2021).fixedClock()),
            stønadsperiode = Stønadsperiode.create(januar(2021)),
        ).second as IverksattSøknadsbehandling.Innvilget

        //language=JSON
        val expected = """
            {
              "id": "${søknadsbehandling.id}",
              "søknad": {
                "id": "${søknadsbehandling.søknad.id}",
                "sakId": "${søknadsbehandling.sakId}",
                "søknadInnhold": {
                  "type": "uføre",
                  "uførevedtak": {
                    "harUførevedtak": true
                  },
                  "flyktningsstatus": {
                    "registrertFlyktning": false
                  },
                  "personopplysninger": {
                    "fnr": "${søknadsbehandling.fnr}"
                  },
                  "boforhold": {
                    "borOgOppholderSegINorge": true,
                    "delerBoligMedVoksne": true,
                    "delerBoligMed": "EKTEMAKE_SAMBOER",
                    "ektefellePartnerSamboer": {
                      "erUførFlyktning": false,
                      "fnr": "${søknadsbehandling.søknad.søknadInnhold.boforhold.ektefellePartnerSamboer!!.fnr}"
                    },
                    "innlagtPåInstitusjon": {
                      "datoForInnleggelse": "2020-01-01",
                      "datoForUtskrivelse": "2020-01-31",
                      "fortsattInnlagt": false
                    },
                    "borPåAdresse": null,
                    "ingenAdresseGrunn": "HAR_IKKE_FAST_BOSTED"
                  },
                  "utenlandsopphold": {
                    "registrertePerioder": [
                      {
                        "utreisedato": "2020-01-01",
                        "innreisedato": "2020-01-31"
                      },
                      {
                        "utreisedato": "2020-02-01",
                        "innreisedato": "2020-02-05"
                      }
                    ],
                    "planlagtePerioder": [
                      {
                        "utreisedato": "2020-07-01",
                        "innreisedato": "2020-07-31"
                      }
                    ]
                  },
                  "oppholdstillatelse": {
                    "erNorskStatsborger": false,
                    "harOppholdstillatelse": true,
                    "typeOppholdstillatelse": "midlertidig",
                    "statsborgerskapAndreLand": false,
                    "statsborgerskapAndreLandFritekst": null
                  },
                  "inntektOgPensjon": {
                    "forventetInntekt": 2500,
                    "andreYtelserINav": "sosialstønad",
                    "andreYtelserINavBeløp": 33,
                    "søktAndreYtelserIkkeBehandletBegrunnelse": "uføre",
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
                    "pensjon": [
                      {
                        "ordning": "KLP",
                        "beløp": 2000.0
                      },
                      {
                        "ordning": "SPK",
                        "beløp": 5000.0
                      }
                    ]
                  },
                  "formue": {
                    "eierBolig": true,
                    "borIBolig": false,
                    "verdiPåBolig": 600000,
                    "boligBrukesTil": "Mine barn bor der",
                    "depositumsBeløp": 1000.0,
                    "verdiPåEiendom": 3,
                    "eiendomBrukesTil": "",
                    "kjøretøy": [
                      {
                        "verdiPåKjøretøy": 25000,
                        "kjøretøyDeEier": "bil"
                      }
                    ],
                    "innskuddsBeløp": 25000,
                    "verdipapirBeløp": 25000,
                    "skylderNoenMegPengerBeløp": 25000,
                    "kontanterBeløp": 25000
                  },
                  "forNav": {
                    "type": "DigitalSøknad",
                    "harFullmektigEllerVerge": "verge"
                  },
                  "ektefelle": {
                    "formue": {
                      "eierBolig": true,
                      "borIBolig": false,
                      "verdiPåBolig": 0,
                      "boligBrukesTil": "",
                      "depositumsBeløp": 0,
                      "verdiPåEiendom": 0,
                      "eiendomBrukesTil": "",
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
                },
                "opprettet": "${søknadsbehandling.søknad.opprettet}",
                "lukket": null
              },
              "beregning": {
                "id": "${søknadsbehandling.beregning.getId()}",
                "opprettet": "${søknadsbehandling.beregning.getOpprettet()}",
                "fraOgMed": "2021-01-01",
                "tilOgMed": "2021-01-31",
                "månedsberegninger": [
                  {
                    "fraOgMed": "2021-01-01",
                    "tilOgMed": "2021-01-31",
                    "sats": "HØY",
                    "grunnbeløp": 101351,
                    "beløp": 20946,
                    "fradrag": [
                      {
                        "periode": {
                          "fraOgMed": "2021-01-01",
                          "tilOgMed": "2021-01-31"
                        },
                        "type": "ForventetInntekt",
                        "beskrivelse": null,
                        "beløp": 0.0,
                        "utenlandskInntekt": null,
                        "tilhører": "BRUKER"
                      }
                    ],
                    "satsbeløp": 20946,
                    "epsFribeløp": 0.0,
                    "epsInputFradrag": [],
                    "merknader": []
                  }
                ],
                "fradrag": [
                  {
                    "periode": {
                      "fraOgMed": "2021-01-01",
                      "tilOgMed": "2021-01-31"
                    },
                    "type": "ForventetInntekt",
                    "beskrivelse": null,
                    "beløp": 0.0,
                    "utenlandskInntekt": null,
                    "tilhører": "BRUKER"
                  }
                ],
                "begrunnelse": null
              },
              "status": "IVERKSATT_INNVILGET",
              "simulering": {                  
                  "totalOppsummering": {
                    "fraOgMed": "2021-01-01",
                    "tilOgMed": "2021-01-31",
                    "sumTilUtbetaling": 20946,
                    "sumEtterbetaling": 20946,
                    "sumFramtidigUtbetaling": 0,
                    "sumTotalUtbetaling": 20946,
                    "sumTidligereUtbetalt": 0,
                    "sumFeilutbetaling": 0,
                    "sumReduksjonFeilkonto": 0
                  },
                  "periodeOppsummering": [
                    {
                      "fraOgMed": "2021-01-01",
                      "tilOgMed": "2021-01-31",
                      "sumTilUtbetaling": 20946,
                      "sumEtterbetaling": 20946,
                      "sumFramtidigUtbetaling": 0,
                      "sumTotalUtbetaling": 20946,
                      "sumTidligereUtbetalt": 0,
                      "sumFeilutbetaling": 0,
                      "sumReduksjonFeilkonto": 0
                    }                    
                  ]
              },
              "opprettet": "${søknadsbehandling.opprettet}",
              "attesteringer": [
                {
                  "attestant": "attestant",
                  "underkjennelse": null,
                  "opprettet": "${søknadsbehandling.attesteringer.hentSisteAttestering().opprettet}"
                }
              ],
              "saksbehandler": "saksbehandler",
              "fritekstTilBrev": "",
              "sakId": "${søknadsbehandling.sakId}",
              "stønadsperiode": {
                "periode": {
                  "fraOgMed": "2021-01-01",
                  "tilOgMed": "2021-01-31"
                }
              },
              "grunnlagsdataOgVilkårsvurderinger": {
                "uføre": {
                  "vurderinger": [
                    {
                      "id": "${
            (
                søknadsbehandling.vilkårsvurderinger.uføreVilkår()
                    .getOrFail() as UføreVilkår.Vurdert
                ).vurderingsperioder.single().id
        }",
                      "opprettet": "${
            (
                søknadsbehandling.vilkårsvurderinger.uføreVilkår()
                    .getOrFail() as UføreVilkår.Vurdert
                ).vurderingsperioder.single().opprettet
        }",
                      "resultat": "VilkårOppfylt",
                      "grunnlag": {
                        "id": "${
            (
                søknadsbehandling.vilkårsvurderinger.uføreVilkår()
                    .getOrFail() as UføreVilkår.Vurdert
                ).vurderingsperioder.single().grunnlag!!.id
        }",
                        "opprettet": "${
            (
                søknadsbehandling.vilkårsvurderinger.uføreVilkår()
                    .getOrFail() as UføreVilkår.Vurdert
                ).vurderingsperioder.single().grunnlag!!.opprettet
        }",
                        "periode": {
                          "fraOgMed": "2021-01-01",
                          "tilOgMed": "2021-01-31"
                        },
                        "uføregrad": 100,
                        "forventetInntekt": 0
                      },
                      "periode": {
                        "fraOgMed": "2021-01-01",
                        "tilOgMed": "2021-01-31"
                      }
                    }
                  ],
                  "resultat": "VilkårOppfylt"
                },
                "lovligOpphold": {
                  "vurderinger": [
                    {
                      "periode": {
                        "fraOgMed": "2021-01-01",
                        "tilOgMed": "2021-01-31"
                      },
                      "resultat": "VilkårOppfylt"
                    }
                  ],
                  "resultat": "VilkårOppfylt"
                },
                "fradrag": [],
                "bosituasjon": [
                  {
                    "type": "ENSLIG",
                    "fnr": null,
                    "delerBolig": false,
                    "ektemakeEllerSamboerUførFlyktning": null,
                    "sats": "HØY",
                    "periode": {
                      "fraOgMed": "2021-01-01",
                      "tilOgMed": "2021-01-31"
                    }
                  }
                ],
                "formue": {
                  "vurderinger": [
                    {
                      "id": "${(søknadsbehandling.vilkårsvurderinger.formueVilkår() as FormueVilkår.Vurdert).vurderingsperioder.single().id}",
                      "opprettet": "${(søknadsbehandling.vilkårsvurderinger.formueVilkår() as FormueVilkår.Vurdert).vurderingsperioder.single().opprettet}",
                      "resultat": "VilkårOppfylt",
                      "grunnlag": {
                        "epsFormue": null,
                        "søkersFormue": {
                          "verdiIkkePrimærbolig": 0,
                          "verdiEiendommer": 0,
                          "verdiKjøretøy": 0,
                          "innskudd": 0,
                          "verdipapir": 0,
                          "pengerSkyldt": 0,
                          "kontanter": 0,
                          "depositumskonto": 0
                        }
                      },
                      "periode": {
                        "fraOgMed": "2021-01-01",
                        "tilOgMed": "2021-01-31"
                      }
                    }
                  ],
                  "resultat": "VilkårOppfylt",
                  "formuegrenser": [
                    {
                      "gyldigFra": "2020-05-01",
                      "beløp": 50676
                    }
                  ]
                },
                "utenlandsopphold": {
                  "vurderinger": [
                    {
                      "status": "SkalHoldeSegINorge",
                      "periode": {
                        "fraOgMed": "2021-01-01",
                        "tilOgMed": "2021-01-31"
                      }
                    }
                  ],
                  "status": "SkalHoldeSegINorge"
                },
                "opplysningsplikt": {
                  "vurderinger": [
                    {
                      "periode": {
                        "fraOgMed": "2021-01-01",
                        "tilOgMed": "2021-01-31"
                      },
                      "beskrivelse": "TilstrekkeligDokumentasjon"
                    }
                  ]
                },
                "pensjon": null,
                "familiegjenforening": null,
                "flyktning": {
                  "vurderinger": [
                    {
                      "resultat": "VilkårOppfylt",
                      "periode": {
                        "fraOgMed": "2021-01-01",
                        "tilOgMed": "2021-01-31"
                      }
                    }
                  ],
                  "resultat": "VilkårOppfylt"
                },
                "personligOppmøte": {
                  "vurderinger": [
                    {
                      "resultat": "VilkårOppfylt",
                      "vurdering": "IkkeMøttMenMidlertidigUnntakFraOppmøteplikt",
                      "periode": {
                        "fraOgMed": "2021-01-01",
                        "tilOgMed": "2021-01-31"
                      }
                    }
                  ],
                  "resultat": "VilkårOppfylt"
                },
                  "fastOpphold": {
                  "vurderinger": [
                    {
                      "periode": {
                        "fraOgMed": "2021-01-01",
                        "tilOgMed": "2021-01-31"
                      },
                      "resultat": "VilkårOppfylt"
                    }
                  ],
                  "resultat": "VilkårOppfylt"
                },
                 "institusjonsopphold":{
                    "vurderingsperioder": [
                      {
                        "periode": {
                          "fraOgMed": "2021-01-01",
                          "tilOgMed": "2021-01-31"
                        },
                        "vurdering": "VilkårOppfylt"
                      }
                   ],
                   "resultat": "VilkårOppfylt"
                 }
              },
              "erLukket": false,
              "simuleringForAvkortingsvarsel": null,
              "sakstype": "uføre",
              "aldersvurdering": {
                "harSaksbehandlerAvgjort": false,
                "maskinellVurderingsresultat": "RETT_PÅ_UFØRE"
              },
              "harSkattegrunnlag": false
            }
        """.trimIndent()
        JSONAssert.assertEquals(expected, serialize(søknadsbehandling.toJson(satsFactoryTestPåDato())), true)
        deserialize<BehandlingJson>(expected) shouldBe søknadsbehandling.toJson(satsFactoryTestPåDato())
    }

    @Test
    fun nullables() {
        val søknadsbehandling = nySøknadsbehandlingUføre().second as VilkårsvurdertSøknadsbehandling.Uavklart

        //language=JSON
        val expected = """
            {
              "id": "${søknadsbehandling.id}",
              "søknad": {
                "id": "${søknadsbehandling.søknad.id}",
                "sakId": "${søknadsbehandling.sakId}",
                "søknadInnhold": {
                  "type": "uføre",
                  "uførevedtak": {
                    "harUførevedtak": true
                  },
                  "flyktningsstatus": {
                    "registrertFlyktning": false
                  },
                  "personopplysninger": {
                    "fnr": "${søknadsbehandling.fnr}"
                  },
                  "boforhold": {
                    "borOgOppholderSegINorge": true,
                    "delerBoligMedVoksne": true,
                    "delerBoligMed": "EKTEMAKE_SAMBOER",
                    "ektefellePartnerSamboer": {
                      "erUførFlyktning": false,
                      "fnr": "${søknadsbehandling.søknad.søknadInnhold.boforhold.ektefellePartnerSamboer!!.fnr}"
                    },
                    "innlagtPåInstitusjon": {
                      "datoForInnleggelse": "2020-01-01",
                      "datoForUtskrivelse": "2020-01-31",
                      "fortsattInnlagt": false
                    },
                    "borPåAdresse": null,
                    "ingenAdresseGrunn": "HAR_IKKE_FAST_BOSTED"
                  },
                  "utenlandsopphold": {
                    "registrertePerioder": [
                      {
                        "utreisedato": "2020-01-01",
                        "innreisedato": "2020-01-31"
                      },
                      {
                        "utreisedato": "2020-02-01",
                        "innreisedato": "2020-02-05"
                      }
                    ],
                    "planlagtePerioder": [
                      {
                        "utreisedato": "2020-07-01",
                        "innreisedato": "2020-07-31"
                      }
                    ]
                  },
                  "oppholdstillatelse": {
                    "erNorskStatsborger": false,
                    "harOppholdstillatelse": true,
                    "typeOppholdstillatelse": "midlertidig",
                    "statsborgerskapAndreLand": false,
                    "statsborgerskapAndreLandFritekst": null
                  },
                  "inntektOgPensjon": {
                    "forventetInntekt": 2500,
                    "andreYtelserINav": "sosialstønad",
                    "andreYtelserINavBeløp": 33,
                    "søktAndreYtelserIkkeBehandletBegrunnelse": "uføre",
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
                    "pensjon": [
                      {
                        "ordning": "KLP",
                        "beløp": 2000.0
                      },
                      {
                        "ordning": "SPK",
                        "beløp": 5000.0
                      }
                    ]
                  },
                  "formue": {
                    "eierBolig": true,
                    "borIBolig": false,
                    "verdiPåBolig": 600000,
                    "boligBrukesTil": "Mine barn bor der",
                    "depositumsBeløp": 1000.0,
                    "verdiPåEiendom": 3,
                    "eiendomBrukesTil": "",
                    "kjøretøy": [
                      {
                        "verdiPåKjøretøy": 25000,
                        "kjøretøyDeEier": "bil"
                      }
                    ],
                    "innskuddsBeløp": 25000,
                    "verdipapirBeløp": 25000,
                    "skylderNoenMegPengerBeløp": 25000,
                    "kontanterBeløp": 25000
                  },
                  "forNav": {
                    "type": "DigitalSøknad",
                    "harFullmektigEllerVerge": "verge"
                  },
                  "ektefelle": {
                    "formue": {
                      "eierBolig": true,
                      "borIBolig": false,
                      "verdiPåBolig": 0,
                      "boligBrukesTil": "",
                      "depositumsBeløp": 0,
                      "verdiPåEiendom": 0,
                      "eiendomBrukesTil": "",
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
                },
                "opprettet": "${søknadsbehandling.søknad.opprettet}",
                "lukket": null
              },
              "beregning": null,
              "status": "OPPRETTET",
              "simulering": null,
              "opprettet": "${søknadsbehandling.opprettet}",
              "attesteringer": [],
              "saksbehandler": "saksbehandler",
              "fritekstTilBrev": "",
              "sakId": "${søknadsbehandling.sakId}",
              "stønadsperiode": {
                "periode": {
                  "fraOgMed": "2021-01-01",
                  "tilOgMed": "2021-12-31"
                }
              },
              "grunnlagsdataOgVilkårsvurderinger": {
                "uføre": null,
                "lovligOpphold": null,
                "fradrag": [],
                "bosituasjon": [],
                "formue": {
                  "vurderinger": [],
                  "resultat": null,
                  "formuegrenser": [
                    {
                      "gyldigFra": "2020-05-01",
                      "beløp": 50676
                    }
                  ]
                },
                "utenlandsopphold": null,
                "opplysningsplikt": {
                  "vurderinger": [
                    {
                      "periode": {
                        "fraOgMed": "2021-01-01",
                        "tilOgMed": "2021-12-31"
                      },
                      "beskrivelse": "TilstrekkeligDokumentasjon"
                    }
                  ]
                },
                "pensjon": null,
                "familiegjenforening": null,
                "flyktning": null,
                "fastOpphold": null,
                "personligOppmøte": null,
                "institusjonsopphold": null
              },
              "erLukket": false,
              "simuleringForAvkortingsvarsel": null,
              "sakstype": "uføre",
              "aldersvurdering": {
                "harSaksbehandlerAvgjort": false,
                "maskinellVurderingsresultat": "RETT_PÅ_UFØRE"
              },
              "harSkattegrunnlag": false
            }
        """.trimIndent()

        JSONAssert.assertEquals(expected, serialize(søknadsbehandling.toJson(satsFactoryTestPåDato())), true)
        deserialize<BehandlingJson>(expected) shouldBe søknadsbehandling.toJson(satsFactoryTestPåDato())
    }
}
