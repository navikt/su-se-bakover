package no.nav.su.se.bakover.domain.brev

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

class AvslagsBrevInnholdTest {
    private val personalia = BrevInnhold.Personalia(
        dato = "01.01.2020",
        fødselsnummer = Fnr("12345678901"),
        fornavn = "Tore",
        etternavn = "Strømøy",
    )

    private val avslagsvedtak = BrevInnhold.AvslagsBrevInnhold(
        personalia = personalia,
        avslagsgrunner = listOf(Avslagsgrunn.FLYKTNING),
        harEktefelle = false,
        halvGrunnbeløp = 10,
        beregningsperioder = emptyList(),
        saksbehandlerNavn = "Sak Sakesen",
        attestantNavn = "Att Attestantsen",
        sats = "lav",
        fritekst = "Fritekst til brevet",
        satsGjeldendeFraDato = "01.01.2020",
    )

    @Test
    fun `jsonformat for avslagsvedtak stemmer overens med det som forventes av pdfgenerator`() {
        val actualJson = objectMapper.writeValueAsString(avslagsvedtak)
        //language=json
        val expectedJson = """
            {
              "personalia": {
                  "dato":"01.01.2020",
                  "fødselsnummer": "12345678901",
                  "fornavn": "Tore",
                  "etternavn": "Strømøy"
              },
              "avslagsgrunner": ["FLYKTNING"],
              "harFlereAvslagsgrunner": false,
              "halvGrunnbeløp": 10,
              "harEktefelle": false,
              "beregningsperioder": [],
              "avslagsparagrafer": [1,2],
              "saksbehandlerNavn": "Sak Sakesen",
              "attestantNavn": "Att Attestantsen",
              "sats": "lav",
              "satsBeløp": null,
              "satsGjeldendeFraDato": "01.01.2020",
              "fritekst": "Fritekst til brevet"
            }
        """.trimIndent()
        JSONAssert.assertEquals(expectedJson, actualJson, true)
    }

    @Test
    fun `mapper avslagsgrunn til korrekt paragraf`() {
        mapOf(
            Avslagsgrunn.UFØRHET to listOf(1, 2),
            Avslagsgrunn.FLYKTNING to listOf(1, 2),
            Avslagsgrunn.OPPHOLDSTILLATELSE to listOf(1, 2),
            Avslagsgrunn.PERSONLIG_OPPMØTE to listOf(17),
            Avslagsgrunn.FORMUE to listOf(8),
            Avslagsgrunn.BOR_OG_OPPHOLDER_SEG_I_NORGE to listOf(1, 2, 3, 4),
            Avslagsgrunn.FOR_HØY_INNTEKT to listOf(5, 6, 7),
            Avslagsgrunn.SU_UNDER_MINSTEGRENSE to listOf(5, 6, 9),
            Avslagsgrunn.UTENLANDSOPPHOLD_OVER_90_DAGER to listOf(1, 2, 4),
            Avslagsgrunn.INNLAGT_PÅ_INSTITUSJON to listOf(12)
        ).forEach { (avslagsgrunn, paragrafer) -> avslagsgrunn.getParagrafer() shouldBe paragrafer }
    }

    @Test
    fun `plukker ut distinkte paragrafer for tilfeller hvor flere grunner benytter samme paragraf`() {
        listOf(Avslagsgrunn.UFØRHET, Avslagsgrunn.FLYKTNING).getDistinkteParagrafer() shouldBe listOf(1, 2)
        listOf(
            Avslagsgrunn.UFØRHET,
            Avslagsgrunn.BOR_OG_OPPHOLDER_SEG_I_NORGE
        ).getDistinkteParagrafer() shouldBe listOf(1, 2, 3, 4)

        listOf(
            Avslagsgrunn.INNLAGT_PÅ_INSTITUSJON,
            Avslagsgrunn.FORMUE,
            Avslagsgrunn.UTENLANDSOPPHOLD_OVER_90_DAGER
        ).getDistinkteParagrafer() shouldBe listOf(1, 2, 4, 8, 12)
    }
}
