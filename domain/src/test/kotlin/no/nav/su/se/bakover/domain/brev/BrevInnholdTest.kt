package no.nav.su.se.bakover.domain.brev

import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.behandling.Satsgrunn
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.brev.beregning.Beregningsperiode
import no.nav.su.se.bakover.domain.brev.beregning.BrevPeriode
import no.nav.su.se.bakover.domain.brev.beregning.Fradrag
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
        personalia, 1.januar(2020), 1.februar(2020), "saksbehandler",
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
            satsBeløp = 100,
            forventetInntektStørreEnn0 = true,
            harEktefelle = true,
            beregningsperioder = listOf(
                Beregningsperiode(
                    periode = BrevPeriode("januar 2021", "desember 2021"),
                    ytelsePerMåned = 100,
                    satsbeløpPerMåned = 100,
                    epsFribeløp = 100,
                    fradrag = Fradrag(emptyList(), Fradrag.Eps(emptyList(), false)),
                ),
            ),
            saksbehandlerNavn = "Hei",
            attestantNavn = "Hopp",
            fritekst = "",
            satsGjeldendeFraDato = "01.01.2020",
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
                "satsBeløp": 100,
                "satsGjeldendeFraDato": "01.01.2020",
                "forventetInntektStørreEnn0": true,
                "harEktefelle": true,
                "harFradrag": false,
                "beregningsperioder": [{
                    "periode": {
                      "fraOgMed": "januar 2021",
                      "tilOgMed": "desember 2021"
                    },
                    "ytelsePerMåned": 100,
                    "satsbeløpPerMåned": 100,
                    "epsFribeløp": 100.0,
                    "fradrag": {
                      "bruker" : [],
                      "eps": {
                      "fradrag": [],
                      "harFradragMedSumSomErLavereEnnFribeløp": false
                      }
                    }
                }],
                "saksbehandlerNavn": "Hei",
                "attestantNavn": "Hopp",
                "fritekst": ""
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
              "trukketDato": "01.02.2020",
              "saksbehandlerNavn": "saksbehandler"
            }
        """.trimIndent()
        JSONAssert.assertEquals(expectedJson, actualJson, true)
    }

    @Test
    fun `jsonformat for opphørsvedtak stemmer overens med det som forventes av pdfgenerator`() {
        val opphørsvedtak = BrevInnhold.Opphørsvedtak(
            personalia = personalia,
            sats = Sats.HØY.toString(),
            satsBeløp = 100,
            harEktefelle = true,
            beregningsperioder = listOf(
                Beregningsperiode(
                    periode = BrevPeriode("januar 2021", "desember 2021"),
                    ytelsePerMåned = 100,
                    satsbeløpPerMåned = 100,
                    epsFribeløp = 100,
                    fradrag = Fradrag(emptyList(), Fradrag.Eps(emptyList(), false)),
                ),
            ),
            saksbehandlerNavn = "Hei",
            attestantNavn = "Hopp",
            fritekst = "",
            avslagsgrunner = listOf(Avslagsgrunn.FOR_HØY_INNTEKT),
            avslagsparagrafer = listOf(1),
            satsGjeldendeFraDato = "01.01.2020",
            forventetInntektStørreEnn0 = false,
        )

        //language=JSON
        val expectedJson = """
            {
                "personalia": {
                    "dato": "01.01.2020",
                    "fødselsnummer": "12345678901",
                    "fornavn": "Tore",
                    "etternavn": "Strømøy"
                },
                "sats": "HØY",
                "satsBeløp": 100,
                "satsGjeldendeFraDato": "01.01.2020",
                "harEktefelle": true,
                "harFradrag": false,
                "beregningsperioder": [{
                    "periode": {
                      "fraOgMed": "januar 2021",
                      "tilOgMed": "desember 2021"
                    },
                    "ytelsePerMåned": 100,
                    "satsbeløpPerMåned": 100,
                    "epsFribeløp": 100.0,
                    "fradrag": {
                      "bruker" : [],
                      "eps": {
                      "fradrag": [],
                      "harFradragMedSumSomErLavereEnnFribeløp": false
                      }
                    }
                }],
                "saksbehandlerNavn": "Hei",
                "attestantNavn": "Hopp",
                "fritekst": "",
                "avslagsgrunner" : ["FOR_HØY_INNTEKT"],
                "avslagsparagrafer" : [1],
                "forventetInntektStørreEnn0" : false
            }
        """.trimIndent()

        JSONAssert.assertEquals(expectedJson, objectMapper.writeValueAsString(opphørsvedtak), true)
    }
}
