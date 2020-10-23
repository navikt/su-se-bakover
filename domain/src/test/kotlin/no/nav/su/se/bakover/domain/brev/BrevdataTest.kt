package no.nav.su.se.bakover.domain.brev

import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.Fnr
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

internal class BrevdataTest {
    val personalia = Brevdata.Personalia(
        dato = "01.01.2020",
        fødselsnummer = Fnr("12345678901"),
        fornavn = "Tore",
        etternavn = "Strømøy",
        adresse = "en Adresse",
        husnummer = "4C",
        bruksenhet = "H102",
        postnummer = "0186",
        poststed = "Oslo"
    )

    val avslagsvedtak = Brevdata.AvslagsVedtak(
        personalia = personalia,
        satsbeløp = 31,
        fradragSum = 566,
        avslagsgrunn = Avslagsgrunn.FLYKTNING,
        halvGrunnbeløp = 10
    )

    val innvilgetVedtak = Brevdata.InnvilgetVedtak(
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

    @Test
    fun `jsonformat for personalia stemmer overens med det som forventes av pdfgenerator`() {
        val serialized = objectMapper.writeValueAsString(personalia)
        //language=json
        val expectedJson = """
            {
              "dato":"01.01.2020",
              "fødselsnummer": "12345678901",
              "fornavn": "Tore",
              "etternavn": "Strømøy",
              "adresse": "en Adresse",
              "husnummer": "4C",
              "bruksenhet": "H102",
              "postnummer": "0186",
              "poststed": "Oslo"
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
              "etternavn": "Strømøy",
              "adresse": "en Adresse",
              "husnummer": "4C",
              "bruksenhet": "H102",
              "postnummer": "0186",
              "poststed": "Oslo"
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
              "etternavn": "Strømøy",
              "adresse": "en Adresse",
              "husnummer": "4C",
              "bruksenhet": "H102",
              "postnummer": "0186",
              "poststed": "Oslo"
              },
              "satsbeløp": 31,
              "fradragSum": 566,
              "avslagsgrunn": "FLYKTNING",
              "halvGrunnbeløp": 10
            }
        """.trimIndent()
        JSONAssert.assertEquals(serialized, expectedJson, true)
    }
}
