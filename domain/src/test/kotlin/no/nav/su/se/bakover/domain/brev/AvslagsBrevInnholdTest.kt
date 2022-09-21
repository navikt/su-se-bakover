package no.nav.su.se.bakover.domain.brev

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Sakstype
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn.Companion.getDistinkteParagrafer
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

class AvslagsBrevInnholdTest {
    private val personalia = BrevInnhold.Personalia(
        dato = "01.01.2020",
        fødselsnummer = Fnr("12345678901"),
        fornavn = "Tore",
        etternavn = "Strømøy",
        saksnummer = 2021,
    )

    private val avslagsvedtak = BrevInnhold.AvslagsBrevInnhold(
        personalia = personalia,
        avslagsgrunner = listOf(Avslagsgrunn.FLYKTNING),
        harEktefelle = false,
        halvGrunnbeløp = 10,
        beregningsperioder = emptyList(),
        saksbehandlerNavn = "Sak Sakesen",
        attestantNavn = "Att Attestantsen",
        fritekst = "Fritekst til brevet",
        forventetInntektStørreEnn0 = false,
        formueVerdier = null,
        satsoversikt = Satsoversikt(
            perioder = listOf(
                Satsoversikt.Satsperiode(
                    fraOgMed = "01.01.2020",
                    tilOgMed = "31.01.2020",
                    sats = "høy",
                    satsBeløp = 1000,
                    satsGrunn = "ENSLIG",
                ),
            ),
        ),
        sakstype = Sakstype.UFØRE,
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
                  "etternavn": "Strømøy",
                  "saksnummer": 2021
              },
              "avslagsgrunner": ["FLYKTNING"],
              "erAldersbrev": false,
              "harFlereAvslagsgrunner": false,
              "halvGrunnbeløp": 10,
              "harEktefelle": false,
              "beregningsperioder": [],
              "avslagsparagrafer": [3],
              "saksbehandlerNavn": "Sak Sakesen",
              "attestantNavn": "Att Attestantsen",
              "fritekst": "Fritekst til brevet",
              "forventetInntektStørreEnn0": false,
              "formueVerdier": null,
              "satsoversikt": {
                  "perioder": [
                    {
                      "fraOgMed": "01.01.2020",
                      "tilOgMed": "31.01.2020",
                      "sats": "høy",
                      "satsBeløp": 1000,
                      "satsGrunn": "ENSLIG"
                    }
                  ]
                }
            }
        """.trimIndent()
        JSONAssert.assertEquals(expectedJson, actualJson, true)
    }

    @Test
    fun `mapper avslagsgrunn til korrekt paragraf`() {
        mapOf(
            Avslagsgrunn.UFØRHET to listOf(1, 2),
            Avslagsgrunn.FLYKTNING to listOf(3),
            Avslagsgrunn.OPPHOLDSTILLATELSE to listOf(1, 2),
            Avslagsgrunn.PERSONLIG_OPPMØTE to listOf(17),
            Avslagsgrunn.FORMUE to listOf(8),
            Avslagsgrunn.BOR_OG_OPPHOLDER_SEG_I_NORGE to listOf(1, 2, 3, 4),
            Avslagsgrunn.FOR_HØY_INNTEKT to listOf(5, 6, 7),
            Avslagsgrunn.SU_UNDER_MINSTEGRENSE to listOf(5, 6, 9),
            Avslagsgrunn.UTENLANDSOPPHOLD_OVER_90_DAGER to listOf(1, 2, 4),
            Avslagsgrunn.INNLAGT_PÅ_INSTITUSJON to listOf(12),
            Avslagsgrunn.MANGLENDE_DOKUMENTASJON to listOf(18),
        ).forEach { (avslagsgrunn, paragrafer) -> avslagsgrunn.getParagrafer() shouldBe paragrafer }
    }

    @Test
    fun `plukker ut distinkte paragrafer for tilfeller hvor flere grunner benytter samme paragraf`() {
        listOf(Avslagsgrunn.UFØRHET, Avslagsgrunn.FLYKTNING).getDistinkteParagrafer() shouldBe listOf(1, 2, 3)
        listOf(
            Avslagsgrunn.UFØRHET,
            Avslagsgrunn.BOR_OG_OPPHOLDER_SEG_I_NORGE,
        ).getDistinkteParagrafer() shouldBe listOf(1, 2, 3, 4)

        listOf(
            Avslagsgrunn.INNLAGT_PÅ_INSTITUSJON,
            Avslagsgrunn.FORMUE,
            Avslagsgrunn.UTENLANDSOPPHOLD_OVER_90_DAGER,
        ).getDistinkteParagrafer() shouldBe listOf(1, 2, 4, 8, 12)
    }

    @Test
    fun `jsonformat for avslagsvedtak pga formue stemmer med pdfgen`() {
        val annetVedtak = avslagsvedtak.copy(
            avslagsgrunner = listOf(Avslagsgrunn.FORMUE),
            formueVerdier = FormueForBrev(
                søkersFormue = FormueVerdierForBrev(
                    verdiSekundærBoliger = 1,
                    verdiSekundærKjøretøyer = 2,
                    pengerIBanken = 3,
                    depositumskonto = 4,
                    pengerIKontanter = 5,
                    aksjerOgVerdiPapir = 6,
                    pengerSøkerSkyldes = 7,
                ),
                epsFormue = null,
                totalt = 8,
            ),
        )

        val actualJson = objectMapper.writeValueAsString(annetVedtak)
        //language=json
        val expectedJson = """
            {
              "personalia": {
                  "dato":"01.01.2020",
                  "fødselsnummer": "12345678901",
                  "fornavn": "Tore",
                  "etternavn": "Strømøy",
                  "saksnummer": 2021
              },
              "avslagsgrunner": ["FORMUE"],
              "erAldersbrev": false,
              "harFlereAvslagsgrunner": false,
              "halvGrunnbeløp": 10,
              "harEktefelle": false,
              "beregningsperioder": [],
              "avslagsparagrafer": [8],
              "saksbehandlerNavn": "Sak Sakesen",
              "attestantNavn": "Att Attestantsen",
              "fritekst": "Fritekst til brevet",
              "forventetInntektStørreEnn0": false,
              "formueVerdier": {
                "søkersFormue": {
                "verdiSekundærBoliger": 1,
                "verdiSekundærKjøretøyer": 2,
                "pengerIBanken": 3,
                "depositumskonto": 4,
                "pengerIKontanter": 5,
                "aksjerOgVerdiPapir": 6,
                "pengerSøkerSkyldes": 7
                },
                "epsFormue": null,
                "totalt": 8
              },
              "satsoversikt": {
                  "perioder": [
                    {
                      "fraOgMed": "01.01.2020",
                      "tilOgMed": "31.01.2020",
                      "sats": "høy",
                      "satsBeløp": 1000,
                      "satsGrunn": "ENSLIG"
                    }
                  ]
                }
            }
        """.trimIndent()
        JSONAssert.assertEquals(expectedJson, actualJson, true)
    }

    @Test
    fun `jsonformat for avslagsvedtak pga formue med EPS stemmer med pdfgen`() {
        val annetVedtak = avslagsvedtak.copy(
            avslagsgrunner = listOf(Avslagsgrunn.FORMUE),
            formueVerdier = FormueForBrev(
                søkersFormue = FormueVerdierForBrev(
                    verdiSekundærBoliger = 1,
                    verdiSekundærKjøretøyer = 1,
                    pengerIBanken = 1,
                    depositumskonto = 0,
                    pengerIKontanter = 1,
                    aksjerOgVerdiPapir = 1,
                    pengerSøkerSkyldes = 1,
                ),
                epsFormue = FormueVerdierForBrev(
                    verdiSekundærBoliger = 1,
                    verdiSekundærKjøretøyer = 1,
                    pengerIBanken = 1,
                    depositumskonto = 0,
                    pengerIKontanter = 1,
                    aksjerOgVerdiPapir = 1,
                    pengerSøkerSkyldes = 1,
                ),
                totalt = 12,
            ),
        )

        val actualJson = objectMapper.writeValueAsString(annetVedtak)
        //language=json
        val expectedJson = """
            {
              "personalia": {
                  "dato":"01.01.2020",
                  "fødselsnummer": "12345678901",
                  "fornavn": "Tore",
                  "etternavn": "Strømøy",
                  "saksnummer": 2021
              },
              "avslagsgrunner": ["FORMUE"],
              "erAldersbrev": false,
              "harFlereAvslagsgrunner": false,
              "halvGrunnbeløp": 10,
              "harEktefelle": false,
              "beregningsperioder": [],
              "avslagsparagrafer": [8],
              "saksbehandlerNavn": "Sak Sakesen",
              "attestantNavn": "Att Attestantsen",
              "fritekst": "Fritekst til brevet",
              "forventetInntektStørreEnn0": false,
              "formueVerdier": {
                "søkersFormue": {
                    "verdiSekundærBoliger": 1,
                    "verdiSekundærKjøretøyer": 1,
                    "pengerIBanken": 1,
                    "depositumskonto": 0,
                    "pengerIKontanter": 1,
                    "aksjerOgVerdiPapir": 1,
                    "pengerSøkerSkyldes": 1
                },
                "epsFormue": {
                    "verdiSekundærBoliger": 1,
                    "verdiSekundærKjøretøyer": 1,
                    "pengerIBanken": 1,
                    "depositumskonto": 0,
                    "pengerIKontanter": 1,
                    "aksjerOgVerdiPapir": 1,
                    "pengerSøkerSkyldes": 1
                },
                "totalt": 12
              },
              "satsoversikt": {
                  "perioder": [
                    {
                      "fraOgMed": "01.01.2020",
                      "tilOgMed": "31.01.2020",
                      "sats": "høy",
                      "satsBeløp": 1000,
                      "satsGrunn": "ENSLIG"
                    }
                  ]
                }
            }

        """.trimIndent()
        JSONAssert.assertEquals(expectedJson, actualJson, true)
    }
}
