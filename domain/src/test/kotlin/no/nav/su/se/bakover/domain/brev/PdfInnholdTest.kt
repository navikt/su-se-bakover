package no.nav.su.se.bakover.domain.brev

import arrow.core.right
import behandling.revurdering.domain.Opphørsgrunn
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.brev.beregning.Beregningsperiode
import no.nav.su.se.bakover.domain.brev.beregning.BrevPeriode
import no.nav.su.se.bakover.domain.brev.beregning.FradragForBrev
import no.nav.su.se.bakover.domain.brev.command.ForhåndsvarselDokumentCommand
import no.nav.su.se.bakover.domain.brev.jsonRequest.InnvilgetSøknadsbehandlingPdfInnhold
import no.nav.su.se.bakover.domain.brev.jsonRequest.OpphørsvedtakPdfInnhold
import no.nav.su.se.bakover.domain.brev.jsonRequest.tilPdfInnhold
import no.nav.su.se.bakover.test.brev.pdfInnholdPersonalia
import no.nav.su.se.bakover.test.brev.pdfInnholdTrukketSøknad
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.person
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.saksnummer
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

internal class PdfInnholdTest {

    @Test
    fun `jsonformat for personalia stemmer overens med det som forventes av pdfgenerator`() {
        val actualJson = serialize(pdfInnholdPersonalia())
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
        val innvilgetVedtak = InnvilgetSøknadsbehandlingPdfInnhold(
            personalia = pdfInnholdPersonalia(),
            fradato = "01.01.2020",
            tildato = "01.01.2020",
            forventetInntektStørreEnn0 = true,
            harEktefelle = true,
            beregningsperioder = listOf(
                Beregningsperiode(
                    periode = BrevPeriode("januar 2021", "desember 2021"),
                    ytelsePerMåned = 100,
                    satsbeløpPerMåned = 100,
                    epsFribeløp = 100,
                    fradrag = FradragForBrev(emptyList(), FradragForBrev.Eps(emptyList(), false)),
                    sats = "høy",
                ),
            ),
            saksbehandlerNavn = "Hei",
            attestantNavn = "Hopp",
            fritekst = "",
            satsoversikt = Satsoversikt(
                perioder = listOf(
                    Satsoversikt.Satsperiode(
                        fraOgMed = "01.01.2020",
                        tilOgMed = "31.01.2020",
                        sats = "høy",
                        satsBeløp = 1000,
                        satsGrunn = "ENSLIG",
                    ),
                    Satsoversikt.Satsperiode(
                        fraOgMed = "01.02.2020",
                        tilOgMed = "31.12.2020",
                        sats = "ordinær",
                        satsBeløp = 5000,
                        satsGrunn = "DELER_BOLIG_MED_VOKSNE_BARN_ELLER_ANNEN_VOKSEN",
                    ),
                ),
            ),
            sakstype = Sakstype.UFØRE,
            erAldersbrev = false,
        )

        val actualJson = serialize(innvilgetVedtak)
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
                "sakstype": "UFØRE",
                "fradato": "01.01.2020",
                "tildato": "01.01.2020",
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
                    },
                    "sats": "høy"
                }],
                "saksbehandlerNavn": "Hei",
                "attestantNavn": "Hopp",
                "fritekst": "",
                "erAldersbrev": false,
                "satsoversikt": {
                  "perioder": [
                    {
                      "fraOgMed": "01.01.2020",
                      "tilOgMed": "31.01.2020",
                      "sats": "høy",
                      "satsBeløp": 1000,
                      "satsGrunn": "ENSLIG"
                    },
                    {
                      "fraOgMed": "01.02.2020",
                      "tilOgMed": "31.12.2020",
                      "sats": "ordinær",
                      "satsBeløp": 5000,
                      "satsGrunn": "DELER_BOLIG_MED_VOKSNE_BARN_ELLER_ANNEN_VOKSEN"
                    }
                  ]
                }
            }
            """.trimIndent()
        JSONAssert.assertEquals(expectedJson, actualJson, true)
    }

    @Test
    fun `jsonformat for trukket søknad stemmer overens med det som forventes av pdfgenerator`() {
        val actualJson = serialize(pdfInnholdTrukketSøknad())
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
              "sakstype": "UFØRE",
              "erAldersbrev": false,
              "datoSøknadOpprettet": "01.01.2020",
              "trukketDato": "01.02.2020",
              "saksbehandlerNavn": "saksbehandler"
            }
        """.trimIndent()
        JSONAssert.assertEquals(expectedJson, actualJson, true)
    }

    @Test
    fun `jsonformat for opphørsvedtak stemmer overens med det som forventes av pdfgenerator`() {
        val opphørsvedtak = OpphørsvedtakPdfInnhold(
            personalia = pdfInnholdPersonalia(),
            opphørsgrunner = listOf(Opphørsgrunn.FOR_HØY_INNTEKT),
            avslagsparagrafer = listOf(1),
            harEktefelle = true,
            beregningsperioder = listOf(
                Beregningsperiode(
                    periode = BrevPeriode("januar 2021", "desember 2021"),
                    ytelsePerMåned = 100,
                    satsbeløpPerMåned = 100,
                    epsFribeløp = 100,
                    fradrag = FradragForBrev(emptyList(), FradragForBrev.Eps(emptyList(), false)),
                    sats = "høy",
                ),
            ),
            saksbehandlerNavn = "Hei",
            attestantNavn = "Hopp",
            fritekst = "",
            forventetInntektStørreEnn0 = false,
            halvGrunnbeløp = 50000,
            opphørsperiode = BrevPeriode(
                fraOgMed = "01.01.2020",
                tilOgMed = "31.12.2020",
            ),
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
                "sakstype": "UFØRE",
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
                    },
                    "sats": "høy"
                }],
                "saksbehandlerNavn": "Hei",
                "attestantNavn": "Hopp",
                "fritekst": "",
                "erAldersbrev": false,
                "opphørsgrunner" : ["FOR_HØY_INNTEKT"],
                "avslagsparagrafer" : [1],
                "forventetInntektStørreEnn0" : false,
                "halvGrunnbeløp": 50000,
                "opphørsperiode": {
                  "fraOgMed": "01.01.2020",
                  "tilOgMed": "31.12.2020"
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

        JSONAssert.assertEquals(expectedJson, serialize(opphørsvedtak), true)
    }

    @Test
    fun `brev for forhåndsvarsel ingen tilbakekreving`() {
        val forhåndsvarsel = ForhåndsvarselDokumentCommand(
            fritekst = "fri",
            saksnummer = saksnummer,
            fødselsnummer = fnr,
            saksbehandler = saksbehandler,
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
                "sakstype": "UFØRE",
                "erAldersbrev": false,
                "saksbehandlerNavn": "saks",
                "fritekst": "fri",
            }
        """.trimIndent()

        val actual: String = serialize(
            forhåndsvarsel.tilPdfInnhold(
                clock = fixedClock,
                hentPerson = { person().right() },
                hentNavnForIdent = { "saks".right() },
            ).getOrFail(),
        )
        JSONAssert.assertEquals(
            expected,
            actual,
            true,
        )
    }
}
