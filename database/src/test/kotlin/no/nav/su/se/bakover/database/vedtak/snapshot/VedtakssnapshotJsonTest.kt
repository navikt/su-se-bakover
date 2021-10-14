package no.nav.su.se.bakover.database.vedtak.snapshot

import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.beregning.PersistertBeregning
import no.nav.su.se.bakover.database.beregning.PersistertFradrag
import no.nav.su.se.bakover.database.beregning.PersistertMånedsberegning
import no.nav.su.se.bakover.database.fixedLocalDate
import no.nav.su.se.bakover.database.fixedTidspunkt
import no.nav.su.se.bakover.database.oversendtUtbetalingUtenKvittering
import no.nav.su.se.bakover.database.vedtak.snapshot.VedtakssnapshotJson.Companion.toJson
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.behandling.withVilkårAvslått
import no.nav.su.se.bakover.domain.beregning.Sats.ORDINÆR
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragStrategyName.Enslig
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører.BRUKER
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype.Arbeidsinntekt
import no.nav.su.se.bakover.domain.beregning.fradrag.UtenlandskInntekt
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.simulering.KlasseKode
import no.nav.su.se.bakover.domain.oppdrag.simulering.KlasseType.YTEL
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertDetaljer
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertPeriode
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertUtbetaling
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.snapshot.Vedtakssnapshot
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.time.LocalDate
import java.util.UUID

internal class VedtakssnapshotJsonTest {

    private val sakId = "7a8b4a95-9736-4f79-bb38-e1d4a7c42799"
    private val saksnummer = 2021L
    private val behandlingId = "62478b8d-8c5a-4da4-8fcf-b48c9b426698"
    private val søknadId = "68c7dba7-6c5c-422f-862e-94ebae82f24d"
    private val vedtakssnapshotId = "06015ac6-07ef-4017-bd04-1e7b87b160fa"
    private val beregningId = "4111d5ee-0215-4d0f-94fc-0959f900ef2e"
    private val periode = Periode.create(LocalDate.of(2021, 1, 1), LocalDate.of(2021, 1, 31))
    private val stønadsperiode = Stønadsperiode.create(periode, "begrunnelsen")
    private val fnr = Fnr("12345678910")

    @Test
    fun `kan serialisere avslag`() {
        val avslagUtenBeregning = Søknadsbehandling.Iverksatt.Avslag.UtenBeregning(
            id = UUID.fromString(behandlingId),
            opprettet = fixedTidspunkt,
            sakId = UUID.fromString(sakId),
            saksnummer = Saksnummer(2021),
            søknad = Søknad.Journalført.MedOppgave.IkkeLukket(
                id = UUID.fromString(søknadId),
                opprettet = fixedTidspunkt,
                sakId = UUID.fromString(sakId),
                søknadInnhold = SøknadInnholdTestdataBuilder.build(),
                journalpostId = JournalpostId("journalpostId"),
                oppgaveId = OppgaveId("oppgaveId"),
            ),
            oppgaveId = OppgaveId("oppgaveId"),
            behandlingsinformasjon = Behandlingsinformasjon
                .lagTomBehandlingsinformasjon()
                .withAlleVilkårOppfylt()
                .withVilkårAvslått(),
            fnr = fnr,
            saksbehandler = NavIdentBruker.Saksbehandler("saksbehandler"),
            attesteringer = Attesteringshistorikk.empty()
                .leggTilNyAttestering(Attestering.Iverksatt(NavIdentBruker.Attestant("attestant"), fixedTidspunkt)),
            fritekstTilBrev = "",
            stønadsperiode = stønadsperiode,
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = Vilkårsvurderinger.IkkeVurdert,
        )

        val avslag = Vedtakssnapshot.Avslag(
            id = UUID.fromString(vedtakssnapshotId),
            opprettet = fixedTidspunkt,
            søknadsbehandling = avslagUtenBeregning,
            avslagsgrunner = listOf(Avslagsgrunn.PERSONLIG_OPPMØTE),
        )

        //language=JSON
        val expectedJson = """
            {
               "type":"avslag",
               "id":"$vedtakssnapshotId",
               "opprettet":"2021-01-01T01:02:03.456789Z",
               "avslagsgrunner":["PERSONLIG_OPPMØTE"],
               "behandling":{
                  "id":"$behandlingId",
                  "opprettet":"2021-01-01T01:02:03.456789Z",
                  "sakId":"$sakId",
                  "saksnummer":2021,
                  "fnr":"12345678910",
                  "status":"IVERKSATT_AVSLAG",
                  "saksbehandler":"saksbehandler",
                  "attestering":{
                     "type": "Iverksatt",
                     "attestant": "attestant",
                     "opprettet": "2021-01-01T01:02:03.456789Z"
                  },
                  "oppgaveId":"oppgaveId",
                  "beregning":null,
                  "behandlingsinformasjon":{
                     "uførhet":{
                        "status":"VilkårIkkeOppfylt",
                        "uføregrad":null,
                        "forventetInntekt":null,
                        "begrunnelse":null
                     },
                     "flyktning":{
                        "status":"VilkårOppfylt",
                        "begrunnelse":null
                     },
                     "lovligOpphold":{
                        "status":"VilkårOppfylt",
                        "begrunnelse":null
                     },
                     "fastOppholdINorge":{
                        "status":"VilkårOppfylt",
                        "begrunnelse":null
                     },
                     "institusjonsopphold":{
                        "status":"VilkårOppfylt",
                        "begrunnelse":null
                     },
                     "oppholdIUtlandet":{
                        "status":"SkalHoldeSegINorge",
                        "begrunnelse":null
                     },
                     "formue":{
                        "status":"VilkårOppfylt",
                        "verdier":{
                           "verdiIkkePrimærbolig":0,
                           "verdiEiendommer":0,
                           "verdiKjøretøy":0,
                           "innskudd":0,
                           "verdipapir":0,
                           "pengerSkyldt":0,
                           "kontanter":0,
                           "depositumskonto":0
                        },
                        "epsVerdier": null,
                        "begrunnelse":null
                     },
                     "personligOppmøte":{
                        "status":"MøttPersonlig",
                        "begrunnelse":null
                     },
                     "bosituasjon":{
                        "ektefelle":{
                        "type":"IngenEktefelle"
                     },
                        "delerBolig":false,
                        "ektemakeEllerSamboerUførFlyktning":null,
                        "begrunnelse":null
                     },
                     "ektefelle":{
                        "type":"IngenEktefelle"
                     }
                  },
                  "behandlingsresultat": {
                      "sats": "HØY",
                      "satsgrunn":"ENSLIG"
                  },
                  "søknad":{
                     "id":"$søknadId",
                     "opprettet":"2021-01-01T01:02:03.456789Z",
                     "sakId":"$sakId",
                     "søknadInnhold":{
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
                           "delerBolig":true,
                           "delerBoligMed":"EKTEMAKE_SAMBOER",
                           "ektefellePartnerSamboer":{
                              "erUførFlyktning":false,
                              "fnr":"01017001337"
                           },
                           "innlagtPåInstitusjon":{
                              "datoForInnleggelse":"2020-01-01",
                              "datoForUtskrivelse":"2020-01-31",
                              "fortsattInnlagt":false
                           },
                           "oppgittAdresse":{
                              "type":"IngenAdresse",
                              "grunn":"HAR_IKKE_FAST_BOSTED"
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
                           "oppholdstillatelseType":"MIDLERTIDIG",
                           "statsborgerskapAndreLand":false,
                           "statsborgerskapAndreLandFritekst":null
                        },
                        "inntektOgPensjon":{
                           "forventetInntekt":2500,
                           "andreYtelserINav":"sosialstønad",
                           "andreYtelserINavBeløp":33,
                           "søktAndreYtelserIkkeBehandletBegrunnelse":"uføre",
                           "sosialstønadBeløp":7000.0,
                           "trygdeytelseIUtlandet":[
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
                           "borIBolig":false,
                           "verdiPåBolig":600000,
                           "boligBrukesTil":"Mine barn bor der",
                           "depositumsBeløp":1000.0,
                           "kontonummer":"12345678912",
                           "verdiPåEiendom":3,
                           "eiendomBrukesTil":"",
                           "kjøretøy":[
                              {
                                 "verdiPåKjøretøy":25000,
                                 "kjøretøyDeEier":"bil"
                              }
                           ],
                           "innskuddsBeløp":25000,
                           "verdipapirBeløp":25000,
                           "skylderNoenMegPengerBeløp":25000,
                           "kontanterBeløp":25000
                        },
                        "forNav":{
                           "type":"DigitalSøknad",
                           "harFullmektigEllerVerge":"VERGE"
                        },
                        "ektefelle":{
                           "formue":{
                              "eierBolig":true,
                              "borIBolig":false,
                              "verdiPåBolig":0,
                              "boligBrukesTil":"",
                              "depositumsBeløp":0,
                              "kontonummer":"11111111111",
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
                              "sosialstønadBeløp":null,
                              "trygdeytelseIUtlandet":null,
                              "pensjon":null
                           }
                        }
                     },
                     "journalpostId":"journalpostId",
                     "oppgaveId":"oppgaveId"
                  },
                  "simulering": null
               }
            }
        """.trimIndent()

        val actualJson = objectMapper.writeValueAsString(avslag.toJson())
        JSONAssert.assertEquals(expectedJson, actualJson, true)
    }

    @Test
    fun `kan serialisere innvilgelse`() {
        val fradrag = listOf(
            PersistertFradrag(
                fradragstype = Arbeidsinntekt,
                månedsbeløp = 155.9,
                utenlandskInntekt = UtenlandskInntekt.create(
                    beløpIUtenlandskValuta = 12345,
                    valuta = "Simoleons",
                    kurs = 129.0,
                ),
                periode = periode,
                tilhører = BRUKER,
            ),
        )
        val innvilget = Søknadsbehandling.Iverksatt.Innvilget(
            id = UUID.fromString(behandlingId),
            opprettet = fixedTidspunkt,
            sakId = UUID.fromString(sakId),
            saksnummer = Saksnummer(2021),
            søknad = Søknad.Journalført.MedOppgave.IkkeLukket(
                id = UUID.fromString(søknadId),
                opprettet = fixedTidspunkt,
                sakId = UUID.fromString(sakId),
                søknadInnhold = SøknadInnholdTestdataBuilder.build(),
                journalpostId = JournalpostId("journalpostId"),
                oppgaveId = OppgaveId("oppgaveId"),
            ),
            oppgaveId = OppgaveId("oppgaveId"),
            behandlingsinformasjon = Behandlingsinformasjon
                .lagTomBehandlingsinformasjon()
                .withAlleVilkårOppfylt()
                .withVilkårAvslått(),
            fnr = fnr,
            beregning = PersistertBeregning(
                id = UUID.fromString(beregningId),
                opprettet = fixedTidspunkt,
                sats = ORDINÆR,
                månedsberegninger = listOf(
                    PersistertMånedsberegning(
                        periode = periode,
                        sats = ORDINÆR,
                        fradrag = fradrag,
                        sumYtelse = 3,
                        sumFradrag = 1.2,
                        benyttetGrunnbeløp = 66,
                        satsbeløp = 4.1,
                        fribeløpForEps = 0.0,
                    ),
                ),
                fradrag = fradrag,
                sumYtelse = 3,
                sumFradrag = 2.1,
                periode = periode,
                fradragStrategyName = Enslig,
                begrunnelse = "har en begrunnelse for beregning"
            ),
            simulering = Simulering(
                gjelderId = fnr,
                gjelderNavn = "gjelderNavn",
                datoBeregnet = fixedLocalDate,
                nettoBeløp = 42,
                periodeList = listOf(
                    SimulertPeriode(
                        fraOgMed = fixedLocalDate,
                        tilOgMed = fixedLocalDate.plusDays(30),
                        utbetaling = listOf(
                            SimulertUtbetaling(
                                fagSystemId = "fagSystemId",
                                utbetalesTilId = fnr,
                                utbetalesTilNavn = "utbetalesTilNavn",
                                forfall = fixedLocalDate,
                                feilkonto = false,
                                detaljer = listOf(
                                    SimulertDetaljer(
                                        faktiskFraOgMed = fixedLocalDate,
                                        faktiskTilOgMed = fixedLocalDate.plusDays(30),
                                        konto = "konto",
                                        belop = 1,
                                        tilbakeforing = false,
                                        sats = 2,
                                        typeSats = "typeSats",
                                        antallSats = 3,
                                        uforegrad = 4,
                                        klassekode = KlasseKode.SUUFORE,
                                        klassekodeBeskrivelse = "klassekodeBeskrivelse",
                                        klasseType = YTEL,
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            saksbehandler = NavIdentBruker.Saksbehandler("saksbehandler"),
            attesteringer = Attesteringshistorikk.empty()
                .leggTilNyAttestering(Attestering.Iverksatt(NavIdentBruker.Attestant("attestant"), fixedTidspunkt)),
            fritekstTilBrev = "",
            stønadsperiode = stønadsperiode,
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = Vilkårsvurderinger.IkkeVurdert,
        )
        val utbetaling = oversendtUtbetalingUtenKvittering(innvilget)
        val innvilgelse = Vedtakssnapshot.Innvilgelse(
            id = UUID.fromString(vedtakssnapshotId),
            opprettet = fixedTidspunkt,
            søknadsbehandling = innvilget,
            utbetaling = utbetaling,
        )

        //language=JSON
        val expectedJson = """
            {
               "type":"innvilgelse",
               "id":"$vedtakssnapshotId",
               "opprettet":"2021-01-01T01:02:03.456789Z",
               "behandling":{
                  "id":"$behandlingId",
                  "opprettet":"2021-01-01T01:02:03.456789Z",
                  "sakId":"$sakId",
                  "saksnummer":2021,
                  "fnr":"12345678910",
                  "status":"IVERKSATT_INNVILGET",
                  "saksbehandler":"saksbehandler",
                  "attestering":{
                     "type": "Iverksatt",
                     "attestant": "attestant",
                     "opprettet": "2021-01-01T01:02:03.456789Z"
                  },
                  "oppgaveId":"oppgaveId",
                  "beregning":{
                    "id":"$beregningId",
                    "opprettet":"2021-01-01T01:02:03.456789Z",
                    "sats":"ORDINÆR",
                    "månedsberegninger":[
                        {
                            "sumYtelse":3,
                            "sumFradrag":1.2,
                            "benyttetGrunnbeløp":66,
                            "sats":"ORDINÆR",
                            "satsbeløp":4.1,
                            "fradrag":[
                                {
                                    "fradragstype":"Arbeidsinntekt",
                                    "månedsbeløp":155.9,
                                    "utenlandskInntekt": {
                                        "beløpIUtenlandskValuta": 12345,
                                        "valuta": "Simoleons",
                                        "kurs": 129.0
                                    },
                                    "periode":{
                                        "fraOgMed":"2021-01-01",
                                        "tilOgMed":"2021-01-31"
                                    },
                                    "tilhører":"BRUKER"
                                }
                            ],
                            "periode":{
                                "fraOgMed":"2021-01-01",
                                "tilOgMed":"2021-01-31"
                            },
                            "fribeløpForEps": 0.0
                        }
                    ],
                    "fradrag":[
                      {
                        "fradragstype":"Arbeidsinntekt",
                        "månedsbeløp":155.9,
                        "utenlandskInntekt":{
                            "beløpIUtenlandskValuta": 12345,
                            "valuta": "Simoleons",
                            "kurs": 129.0
                        },
                        "periode":{
                            "fraOgMed":"2021-01-01",
                            "tilOgMed":"2021-01-31"
                        },
                        "tilhører":"BRUKER"
                      }
                    ],
                    "sumYtelse":3,
                    "sumFradrag":2.1,
                    "periode":{
                        "fraOgMed":"2021-01-01",
                        "tilOgMed":"2021-01-31"
                    },
                    "fradragStrategyName":"Enslig",
                    "begrunnelse": "har en begrunnelse for beregning"
                },
                  "behandlingsinformasjon":{
                     "uførhet":{
                        "status":"VilkårIkkeOppfylt",
                        "uføregrad":null,
                        "forventetInntekt":null,
                        "begrunnelse":null
                     },
                     "flyktning":{
                        "status":"VilkårOppfylt",
                        "begrunnelse":null
                     },
                     "lovligOpphold":{
                        "status":"VilkårOppfylt",
                        "begrunnelse":null
                     },
                     "fastOppholdINorge":{
                        "status":"VilkårOppfylt",
                        "begrunnelse":null
                     },
                     "institusjonsopphold":{
                        "status":"VilkårOppfylt",
                        "begrunnelse":null
                     },
                     "oppholdIUtlandet":{
                        "status":"SkalHoldeSegINorge",
                        "begrunnelse":null
                     },
                     "formue":{
                        "status":"VilkårOppfylt",
                        "verdier":{
                           "verdiIkkePrimærbolig":0,
                           "verdiEiendommer":0,
                           "verdiKjøretøy":0,
                           "innskudd":0,
                           "verdipapir":0,
                           "pengerSkyldt":0,
                           "kontanter":0,
                           "depositumskonto":0
                        },
                        "epsVerdier":null,
                        "begrunnelse":null
                     },
                     "personligOppmøte":{
                        "status":"MøttPersonlig",
                        "begrunnelse":null
                     },
                     "bosituasjon":{
                        "ektefelle":{
                        "type":"IngenEktefelle"
                     },
                        "delerBolig":false,
                        "ektemakeEllerSamboerUførFlyktning":null,
                        "begrunnelse":null
                     },
                     "ektefelle":{
                        "type":"IngenEktefelle"
                     }
                  },
                  "behandlingsresultat": {
                    "sats": "HØY",
                    "satsgrunn":"ENSLIG"
                  },
                  "søknad":{
                     "id":"$søknadId",
                     "opprettet":"2021-01-01T01:02:03.456789Z",
                     "sakId":"$sakId",
                     "søknadInnhold":{
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
                           "delerBolig":true,
                           "delerBoligMed":"EKTEMAKE_SAMBOER",
                           "ektefellePartnerSamboer":{
                              "erUførFlyktning":false,
                              "fnr":"01017001337"
                           },
                           "innlagtPåInstitusjon":{
                              "datoForInnleggelse":"2020-01-01",
                              "datoForUtskrivelse":"2020-01-31",
                              "fortsattInnlagt":false
                           },
                           "oppgittAdresse":{
                              "type":"IngenAdresse",
                              "grunn":"HAR_IKKE_FAST_BOSTED"
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
                           "oppholdstillatelseType":"MIDLERTIDIG",
                           "statsborgerskapAndreLand":false,
                           "statsborgerskapAndreLandFritekst":null
                        },
                        "inntektOgPensjon":{
                           "forventetInntekt":2500,
                           "andreYtelserINav":"sosialstønad",
                           "andreYtelserINavBeløp":33,
                           "søktAndreYtelserIkkeBehandletBegrunnelse":"uføre",
                           "sosialstønadBeløp":7000.0,
                           "trygdeytelseIUtlandet":[
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
                           "borIBolig":false,
                           "verdiPåBolig":600000,
                           "boligBrukesTil":"Mine barn bor der",
                           "depositumsBeløp":1000.0,
                           "kontonummer":"12345678912",
                           "verdiPåEiendom":3,
                           "eiendomBrukesTil":"",
                           "kjøretøy":[
                              {
                                 "verdiPåKjøretøy":25000,
                                 "kjøretøyDeEier":"bil"
                              }
                           ],
                           "innskuddsBeløp":25000,
                           "verdipapirBeløp":25000,
                           "skylderNoenMegPengerBeløp":25000,
                           "kontanterBeløp":25000
                        },
                        "forNav":{
                           "type":"DigitalSøknad",
                           "harFullmektigEllerVerge":"VERGE"
                        },
                        "ektefelle":{
                           "formue":{
                              "eierBolig":true,
                              "borIBolig":false,
                              "verdiPåBolig":0,
                              "boligBrukesTil":"",
                              "depositumsBeløp":0,
                              "kontonummer":"11111111111",
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
                              "sosialstønadBeløp":null,
                              "trygdeytelseIUtlandet":null,
                              "pensjon":null
                           }
                        }
                     },
                     "journalpostId":"journalpostId",
                     "oppgaveId":"oppgaveId"
                  },
                  "simulering": {
                      "gjelderId":"12345678910",
                      "gjelderNavn":"gjelderNavn",
                      "datoBeregnet":"2021-01-01",
                      "nettoBeløp":42,
                      "periodeList":[
                          {
                              "fraOgMed":"2021-01-01",
                              "tilOgMed":"2021-01-31",
                              "utbetaling":[
                                  {
                                      "fagSystemId":"fagSystemId",
                                      "utbetalesTilId":"12345678910",
                                      "utbetalesTilNavn":"utbetalesTilNavn",
                                      "forfall":"2021-01-01",
                                      "feilkonto":false,
                                      "detaljer":[
                                          {
                                              "faktiskFraOgMed":"2021-01-01",
                                              "faktiskTilOgMed":"2021-01-31",
                                              "konto":"konto",
                                              "belop":1,
                                              "tilbakeforing":false,
                                              "sats":2,
                                              "typeSats":"typeSats",
                                              "antallSats":3,
                                              "uforegrad":4,
                                              "klassekode":"SUUFORE",
                                              "klassekodeBeskrivelse":"klassekodeBeskrivelse",
                                              "klasseType":"YTEL"
                                          }
                                      ]
                                  }
                              ]
                          }
                      ]
                  }
               },
               "utbetaling": {
                      "id":"${utbetaling.id}",
                      "opprettet":"${utbetaling.opprettet}",
                      "fnr":"12345678910",
                      "utbetalingslinjer":[
                         {
                            "id" : "${utbetaling.utbetalingslinjer[0].id}",
                            "opprettet" :"${utbetaling.utbetalingslinjer[0].opprettet}",
                            "fraOgMed" : "2020-01-01",
                            "tilOgMed" :"2020-12-31",
                            "forrigeUtbetalingslinjeId" : null,
                            "beløp" : 25000,
                            "uføregrad": {
                              "value": 50
                            }
                         }
                      ],
                      "type":"NY",
                      "sakId":"$sakId",
                      "saksnummer":$saksnummer,
                      "behandler":"attestant",
                      "avstemmingsnøkkel":{
                         "opprettet":"${utbetaling.avstemmingsnøkkel.opprettet}",
                         "nøkkel":"${utbetaling.avstemmingsnøkkel}"
                      },
                      "simulering":{
                         "gjelderId":"12345678910",
                         "gjelderNavn":"gjelderNavn",
                         "datoBeregnet":"2021-01-01",
                         "nettoBeløp":100,
                         "periodeList":[]
                      },
                      "utbetalingsrequest":{
                         "value":"<xml></xml>"
                      }
               }
            }
        """.trimIndent()

        val actualJson = objectMapper.writeValueAsString(innvilgelse.toJson())
        JSONAssert.assertEquals(expectedJson, actualJson, true)
    }
}
