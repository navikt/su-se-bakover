package no.nav.su.se.bakover.domain.brev

import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.behandling.Satsgrunn
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.brev.beregning.Beregningsperiode
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
            beregningsperioder = listOf(
                Beregningsperiode(
                    periode = Periode(fraOgMed = 1.januar(2021), tilOgMed = 31.desember(2021)),
                    ytelsePerMåned = 100,
                    satsbeløpPerMåned = 100,
                    epsFribeløp = 100.0,
                    fradrag = null
                )
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
                "beregningsperioder": [{
                    "periode": {
                      "fraOgMed": "2021-01-01",
                      "tilOgMed": "2021-12-31"
                    },
                    "ytelsePerMåned": 100,
                    "satsbeløpPerMåned": 100,
                    "epsFribeløp": 100.0,
                    "fradrag": null
                }],
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
