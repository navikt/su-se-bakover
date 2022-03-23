package no.nav.su.se.bakover.domain.brev

import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.Beløp
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.MånedBeløp
import no.nav.su.se.bakover.domain.behandling.Satsgrunn
import no.nav.su.se.bakover.domain.behandling.avslag.Opphørsgrunn
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.brev.beregning.Beregningsperiode
import no.nav.su.se.bakover.domain.brev.beregning.BrevPeriode
import no.nav.su.se.bakover.domain.brev.beregning.Fradrag
import no.nav.su.se.bakover.domain.brev.beregning.Tilbakekreving
import no.nav.su.se.bakover.domain.brev.søknad.lukk.TrukketSøknadBrevInnhold
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.periodeJanuar2021
import no.nav.su.se.bakover.test.person
import no.nav.su.se.bakover.test.saksnummer
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.time.LocalDate
import kotlin.text.Typography.nbsp

internal class BrevInnholdTest {
    private val personalia = BrevInnhold.Personalia(
        dato = "01.01.2020",
        fødselsnummer = Fnr("12345678901"),
        fornavn = "Tore",
        etternavn = "Strømøy",
        saksnummer = 2021,
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
              "etternavn": "Strømøy",
              "saksnummer": 2021
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
                    "etternavn": "Strømøy",
                    "saksnummer": 2021
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
                "fritekst": "",
                "harAvkorting": false
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
                  "etternavn": "Strømøy",
                  "saksnummer": 2021
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
            opphørsgrunner = listOf(Opphørsgrunn.FOR_HØY_INNTEKT),
            avslagsparagrafer = listOf(1),
            satsGjeldendeFraDato = "01.01.2020",
            forventetInntektStørreEnn0 = false,
            halvGrunnbeløp = 50000,
            opphørsdato = "01.01.2020",
            avkortingsBeløp = null,
        )

        //language=JSON
        val expectedJson = """
            {
                "personalia": {
                    "dato": "01.01.2020",
                    "fødselsnummer": "12345678901",
                    "fornavn": "Tore",
                    "etternavn": "Strømøy",
                    "saksnummer": 2021
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
                "opphørsgrunner" : ["FOR_HØY_INNTEKT"],
                "avslagsparagrafer" : [1],
                "forventetInntektStørreEnn0" : false,
                "halvGrunnbeløp": 50000,
                "opphørsdato": "01.01.2020",            
                "avkortingsBeløp": null,
                "harAvkorting": false
            }
        """.trimIndent()

        JSONAssert.assertEquals(expectedJson, objectMapper.writeValueAsString(opphørsvedtak), true)
    }

    @Test
    fun `brev for forhåndsvarsel ingen tilbakekreving`() {
        val forhåndsvarsel = LagBrevRequest.Forhåndsvarsel(
            person = person(),
            saksbehandlerNavn = "saks",
            fritekst = "fri",
            dagensDato = LocalDate.now(fixedClock),
            saksnummer = saksnummer,
        )

        val expected = """
            {
                "personalia": {
                    "dato": "01.01.2021",
                    "fødselsnummer": "$fnr",
                    "fornavn": "Tore",
                    "etternavn": "Strømøy",
                    "saksnummer": 12345676
                },
                "saksbehandlerNavn": "saks",
                "fritekst": "fri",
            }
        """.trimIndent()

        JSONAssert.assertEquals(expected, objectMapper.writeValueAsString(forhåndsvarsel.brevInnhold), true)
    }

    @Test
    fun `brev for forhåndsvarsel med tilbakekreving`() {
        val forhåndsvarsel = LagBrevRequest.ForhåndsvarselTilbakekreving(
            person = person(),
            saksbehandlerNavn = "saks",
            fritekst = "fri",
            dagensDato = LocalDate.now(fixedClock),
            saksnummer = saksnummer,
            bruttoTilbakekreving = 5000000,
            opphør = false,
            tilbakekreving = Tilbakekreving(listOf(MånedBeløp(periodeJanuar2021, Beløp.invoke(1000))))
        )

        val expected = """
            {
                "personalia": {
                    "dato": "01.01.2021",
                    "fødselsnummer": "$fnr",
                    "fornavn": "Tore",
                    "etternavn": "Strømøy",
                    "saksnummer": 12345676
                },
                "saksbehandlerNavn": "saks",
                "fritekst": "fri",
                "bruttoTilbakekreving":"5${nbsp}000${nbsp}000",
                "tilbakekreving": [{"periode": "1. januar 2021 - 31. januar 2021", "beløp":"1${nbsp}000", "tilbakekrevingsgrad": "100%"}],
                "periodeStart": "1. januar 2021",
                "periodeSlutt": "31. januar 2021",
                "dato": "1. januar 2021",
                "opphør": false
            }
        """.trimIndent()

        JSONAssert.assertEquals(expected, objectMapper.writeValueAsString(forhåndsvarsel.brevInnhold), true)
    }
}
