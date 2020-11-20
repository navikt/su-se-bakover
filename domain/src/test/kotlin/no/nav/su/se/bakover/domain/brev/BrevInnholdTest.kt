package no.nav.su.se.bakover.domain.brev

import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.Fnr
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

    private val avslagsvedtak = BrevInnhold.AvslagsVedtak(
        personalia = personalia,
        satsbeløp = 31,
        fradragSum = 566,
        avslagsgrunner = listOf(Avslagsgrunn.FLYKTNING),
        halvGrunnbeløp = 10,
        fradrag = emptyList(),
        harFlereAvslagsgrunner = false,
        harEktefelle = false

    )

    private val innvilgetVedtak = BrevInnhold.InnvilgetVedtak(
        personalia = personalia,
        månedsbeløp = 100,
        fradato = "01.01.2020",
        tildato = "01.01.2020",
        sats = "100",
        satsbeløp = 100,
        satsGrunn = Satsgrunn.DELER_BOLIG_MED_VOKSNE_BARN_ELLER_ANNEN_VOKSEN,
        redusertStønadStatus = true,
        harEktefelle = true,
        fradrag = emptyList(),
        fradragSum = 0,
    )

    private val trukketSøknad = TrukketSøknadBrevInnhold(
        personalia, 1.januar(2020), 1.februar(2020)
    )

    @Test
    fun `jsonformat for personalia stemmer overens med det som forventes av pdfgenerator`() {
        val serialized = objectMapper.writeValueAsString(personalia)
        //language=json
        val expectedJson = """
            {
              "dato":"01.01.2020",
              "fødselsnummer": "12345678901",
              "fornavn": "Tore",
              "etternavn": "Strømøy"
            }
        """.trimIndent()
        JSONAssert.assertEquals(serialized, expectedJson, true)
    }

    @Test
    fun `jsonformat for innvilget vedtak stemmer overens med det som forventes av pdfgenerator`() {
        val serialized = objectMapper.writeValueAsString(innvilgetVedtak)
        //language=json
        val expectedJson = """
            {
              "personalia": {
              "dato":"01.01.2020",
              "fødselsnummer": "12345678901",
              "fornavn": "Tore",
              "etternavn": "Strømøy"
              },
              "månedsbeløp": 100,
              "fradato": "01.01.2020",
              "tildato": "01.01.2020",
              "sats": "100",
              "satsbeløp": 100,
              "satsGrunn": "DELER_BOLIG_MED_VOKSNE_BARN_ELLER_ANNEN_VOKSEN",
              "redusertStønadStatus": true,
              "harEktefelle": true,
              "fradrag": [],
              "fradragSum": 0
            }
        """.trimIndent()
        JSONAssert.assertEquals(serialized, expectedJson, true)
    }

    @Test
    fun `jsonformat for avslagsvedtak stemmer overens med det som forventes av pdfgenerator`() {
        val serialized = objectMapper.writeValueAsString(avslagsvedtak)
        //language=json
        val expectedJson = """
            {
              "personalia": {
              "dato":"01.01.2020",
              "fødselsnummer": "12345678901",
              "fornavn": "Tore",
              "etternavn": "Strømøy"
              },
              "satsbeløp": 31,
              "fradragSum": 566,
              "avslagsgrunner":["FLYKTNING"],
              "harFlereAvslagsgrunner": false,
              "halvGrunnbeløp": 10,
              "harEktefelle": false,
              "fradrag": []
            }
        """.trimIndent()
        JSONAssert.assertEquals(serialized, expectedJson, true)
    }

    @Test
    fun `jsonformat for trukket søknad stemmer overens med det som forventes av pdfgenerator`() {
        val serialized = objectMapper.writeValueAsString(trukketSøknad)
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
        JSONAssert.assertEquals(serialized, expectedJson, true)
    }
}
