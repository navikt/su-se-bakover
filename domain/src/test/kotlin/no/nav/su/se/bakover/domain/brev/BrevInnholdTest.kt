package no.nav.su.se.bakover.domain.brev

import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.behandling.Satsgrunn
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.beregning.fradrag.UtenlandskInntekt
import no.nav.su.se.bakover.domain.brev.søknad.lukk.TrukketSøknadBrevInnhold
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

internal class BrevInnholdTest {
    private val personalia = BrevInnhold.Personalia(
        dato = "01.01.2020",
        fødselsnummer = Fnr("12345678901"),
        fornavn = "Tore",
        etternavn = "Strømøy",
    )

    private val trukketSøknad = TrukketSøknadBrevInnhold(
        personalia, 1.januar(2020), 1.februar(2020)
    )

    @Test
    fun `jsonformat for personalia stemmer overens med det som forventes av pdfgenerator`() {
        val actualJson = objectMapper.writeValueAsString(personalia)
        //language=json
        val expectedJson = """
            {
              "dato":"01.01.2020",
              "fødselsnummer": "12345678901",
              "fornavn": "Tore",
              "etternavn": "Strømøy"
            }
        """.trimIndent()
        JSONAssert.assertEquals(expectedJson, actualJson, true)
    }

    @Test
    fun `jsonformat for innvilget vedtak stemmer overens med det som forventes av pdfgenerator`() {
        val innvilgetVedtak = BrevInnhold.InnvilgetVedtak(
            personalia = personalia,
            fradato = "01.01.2020",
            tildato = "01.01.2020",
            sats = Sats.HØY.toString(),
            satsGrunn = Satsgrunn.DELER_BOLIG_MED_VOKSNE_BARN_ELLER_ANNEN_VOKSEN,
            harEktefelle = true,
            beregning = BrevInnhold.Beregning(
                ytelsePerMåned = 100,
                satsbeløpPerMåned = 100,
                epsFribeløp = 100.0,
                fradrag = BrevInnhold.Beregning.Fradrag(
                    bruker = BrevInnhold.Beregning.FradragForBruker(
                        fradrag = listOf(
                            BrevInnhold.Månedsfradrag(
                                type = Fradragstype.Arbeidsinntekt.toReadableTypeName(utenlandsk = false),
                                beløp = 10.0,
                                utenlandskInntekt = null
                            ),
                            BrevInnhold.Månedsfradrag(
                                type = Fradragstype.OffentligPensjon.toReadableTypeName(utenlandsk = false),
                                beløp = 35.0,
                                utenlandskInntekt = null
                            ),
                            BrevInnhold.Månedsfradrag(
                                type = Fradragstype.OffentligPensjon.toReadableTypeName(utenlandsk = true),
                                beløp = 70.0,
                                utenlandskInntekt = UtenlandskInntekt(
                                    beløpIUtenlandskValuta = 140,
                                    valuta = "SEK",
                                    kurs = 0.5
                                )
                            )
                        ),
                        sum = 45.0,
                        harBruktForventetInntektIStedetForArbeidsinntekt = false
                    ),
                    eps = BrevInnhold.Beregning.FradragForEps(
                        fradrag = listOf(
                            BrevInnhold.Månedsfradrag(
                                type = Fradragstype.Arbeidsinntekt.toReadableTypeName(utenlandsk = false),
                                beløp = 20.0,
                                utenlandskInntekt = null
                            ),
                            BrevInnhold.Månedsfradrag(
                                type = Fradragstype.OffentligPensjon.toReadableTypeName(utenlandsk = false),
                                beløp = 70.0,
                                utenlandskInntekt = null
                            )
                        ),
                        sum = 0.0
                    )
                ),
            ),
            saksbehandlerNavn = "Hei",
            attestantNavn = "Hopp"
        )

        val actualJson = objectMapper.writeValueAsString(innvilgetVedtak)
        //language=json
        val expectedJson =
            """
            {
                "personalia": {
                    "dato": "01.01.2020",
                    "fødselsnummer": "12345678901",
                    "fornavn": "Tore",
                    "etternavn": "Strømøy"
                },
                "fradato": "01.01.2020",
                "tildato": "01.01.2020",
                "sats": "HØY",
                "satsGrunn": "DELER_BOLIG_MED_VOKSNE_BARN_ELLER_ANNEN_VOKSEN",
                "harEktefelle": true,
                "beregning": {
                    "ytelsePerMåned": 100,
                    "satsbeløpPerMåned": 100,
                    "epsFribeløp": 100.0,
                    "fradrag": {
                        "bruker": {
                            "fradrag": [
                                {
                                    "type": "Arbeidsinntekt",
                                    "beløp": 10.0,
                                    "utenlandskInntekt": null
                                },
                                {
                                    "type": "Offentlig pensjon",
                                    "beløp": 35.0,
                                    "utenlandskInntekt": null
                                },
                                {
                                    "type": "Offentlig pensjon — fra utlandet",
                                    "beløp": 70.0,
                                    "utenlandskInntekt": {
                                        "beløpIUtenlandskValuta": 140,
                                        "valuta": "SEK",
                                        "kurs": 0.5
                                    }
                                }
                            ],
                            "sum": 45.0,
                            "harBruktForventetInntektIStedetForArbeidsinntekt": false
                        },
                        "eps": {
                            "fradrag": [
                                {
                                    "type": "Arbeidsinntekt",
                                    "beløp": 20.0,
                                    "utenlandskInntekt": null
                                },
                                {
                                    "type": "Offentlig pensjon",
                                    "beløp": 70.0,
                                    "utenlandskInntekt": null
                                }
                            ],
                            "sum": 0.0
                        }
                    }
                },
                "saksbehandlerNavn": "Hei",
                "attestantNavn": "Hopp"
            }
            """.trimIndent()
        JSONAssert.assertEquals(expectedJson, actualJson, true)
    }

    @Test
    fun `jsonformat for trukket søknad stemmer overens med det som forventes av pdfgenerator`() {
        val actualJson = objectMapper.writeValueAsString(trukketSøknad)
        //language=json
        val expectedJson = """
            {
              "personalia": {
                  "dato":"01.01.2020",
                  "fødselsnummer": "12345678901",
                  "fornavn": "Tore",
                  "etternavn": "Strømøy"
              },
              "datoSøknadOpprettet": "01.01.2020",
              "trukketDato": "01.02.2020"
            }
        """.trimIndent()
        JSONAssert.assertEquals(expectedJson, actualJson, true)
    }
}
