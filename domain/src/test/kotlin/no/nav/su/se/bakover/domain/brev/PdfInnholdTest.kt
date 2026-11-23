package no.nav.su.se.bakover.domain.brev

import arrow.core.left
import arrow.core.right
import dokument.domain.generer.bygger.BeregningsperiodeBrev
import dokument.domain.generer.bygger.BrevPeriode
import dokument.domain.generer.bygger.FradragForBrev
import dokument.domain.generer.bygger.SatsoversiktBrev
import dokument.domain.generer.bygger.harFradrag
import dokument.domain.generer.bygger.maler.ForhåndsvarselPdfInnhold
import dokument.domain.generer.bygger.maler.InnvilgetSøknadsbehandlingPdfInnhold
import dokument.domain.generer.bygger.maler.OpphørsgrunnBrev
import dokument.domain.generer.bygger.maler.OpphørsvedtakPdfInnhold
import dokument.domain.generer.bygger.maler.RevurderingAvInntektPdfInnhold
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import no.nav.su.se.bakover.common.SU_SE_BAKOVER_CONSUMER_ID
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.brev.command.ForhåndsvarselDokumentCommand
import no.nav.su.se.bakover.domain.brev.jsonRequest.FeilVedHentingAvInformasjon
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
import person.domain.KunneIkkeHenteNavnForNavIdent

internal class PdfInnholdTest {

    @Test
    fun `jsonformat for inntektsrevurdering stemmer overens med det som forventes av pdfgenerator`() {
        // TODO PdfBygger ryddejobb

        val satsbeløp = 20637
        val sats = "høy"
        val fraOgMed = "01.01.2020"
        val tilOgMed = "31.01.2020"
        val beregningsperiode = "januar 2020"
        val innhold = RevurderingAvInntektPdfInnhold(
            sakstype = Sakstype.UFØRE,
            personalia = pdfInnholdPersonalia(),
            saksbehandlerNavn = "Saksbehandler",
            attestantNavn = "Attestant",
            beregningsperioder = listOf(
                BeregningsperiodeBrev(
                    ytelsePerMåned = satsbeløp,
                    satsbeløpPerMåned = satsbeløp,
                    epsFribeløp = 0,
                    fradrag = FradragForBrev(emptyList(), FradragForBrev.Eps(emptyList(), false)),
                    periode = BrevPeriode(beregningsperiode, beregningsperiode),
                    sats = sats,
                ),
            ),
            fritekst = null,
            harEktefelle = false,
            forventetInntektStørreEnn0 = false,
            satsoversikt = SatsoversiktBrev(
                perioder = listOf(
                    SatsoversiktBrev.Satsperiode(
                        fraOgMed = fraOgMed,
                        tilOgMed = tilOgMed,
                        sats = sats,
                        satsBeløp = satsbeløp,
                        satsGrunn = "ENSLIG",
                    ),
                ),
            ),
            harFradrag = false,
        )

        //language=json
        val expectedJson = """
            {
                "sakstype": "UFØRE",
                "personalia": {
                    "dato": "01.01.2020",
                    "fødselsnummer": "12345678901",
                    "fornavn": "Tore",
                    "etternavn": "Strømøy",
                    "saksnummer": 2021
                },
                "saksbehandlerNavn": "Saksbehandler",
                "attestantNavn": "Attestant",
                "beregningsperioder": [{
                    "ytelsePerMåned": 20637,
                    "satsbeløpPerMåned": 20637,
                    "epsFribeløp": 0,
                    "fradrag": {
                        "bruker": [],
                        "eps": {
                            "fradrag": [],
                            "harFradragMedSumSomErLavereEnnFribeløp": false
                        }
                    },
                    "periode": {
                        "fraOgMed": "januar 2020",
                        "tilOgMed": "januar 2020"
                    },
                    "sats": "høy"
                }],
                "fritekst": null,
                "harEktefelle": false,
                "forventetInntektStørreEnn0": false,
                "satsoversikt": {
                    "perioder": [{
                        "fraOgMed": "01.01.2020",
                        "tilOgMed": "31.01.2020",
                        "sats": "høy",
                        "satsBeløp": 20637,
                        "satsGrunn": "ENSLIG"
                    }]
                },
                "harFradrag": false
            }
        """.trimIndent()

        JSONAssert.assertEquals(expectedJson, innhold.toJson(), true)
    }

    @Test
    fun `systembruker får tomt navn i brev uten navneoppslag`() {
        val command = ForhåndsvarselDokumentCommand(
            fritekst = "",
            saksnummer = saksnummer,
            sakstype = Sakstype.UFØRE,
            fødselsnummer = fnr,
            saksbehandler = NavIdentBruker.Saksbehandler.systembruker(),
        )

        val innhold = command.tilPdfInnhold(
            clock = fixedClock,
            hentPerson = { person().right() },
            hentNavnForIdent = { error("Systembruker skal ikke utløse navneoppslag") },
        ).getOrFail().shouldBeInstanceOf<ForhåndsvarselPdfInnhold>()

        innhold.saksbehandlerNavn shouldBe SU_SE_BAKOVER_CONSUMER_ID
    }

    @Test
    fun `vanlig saksbehandler får navn fra navneoppslag`() {
        val navn = "Saksbehandler"
        val command = ForhåndsvarselDokumentCommand(
            fritekst = "",
            saksnummer = saksnummer,
            sakstype = Sakstype.UFØRE,
            fødselsnummer = fnr,
            saksbehandler = saksbehandler,
        )

        val innhold = command.tilPdfInnhold(
            clock = fixedClock,
            hentPerson = { person().right() },
            hentNavnForIdent = {
                it shouldBe saksbehandler
                navn.right()
            },
        ).getOrFail().shouldBeInstanceOf<ForhåndsvarselPdfInnhold>()

        innhold.saksbehandlerNavn shouldBe navn
    }

    @Test
    fun `feil i navneoppslag for vanlig saksbehandler stopper brevgenerering`() {
        val feil = KunneIkkeHenteNavnForNavIdent.FantIkkeBrukerForNavIdent
        val command = ForhåndsvarselDokumentCommand(
            fritekst = "",
            saksnummer = saksnummer,
            sakstype = Sakstype.UFØRE,
            fødselsnummer = fnr,
            saksbehandler = saksbehandler,
        )

        command.tilPdfInnhold(
            clock = fixedClock,
            hentPerson = { person().right() },
            hentNavnForIdent = { feil.left() },
        ) shouldBe FeilVedHentingAvInformasjon.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant(feil).left()
    }

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
        val beregningsperioder = listOf(
            BeregningsperiodeBrev(
                periode = BrevPeriode("januar 2021", "desember 2021"),
                ytelsePerMåned = 100,
                satsbeløpPerMåned = 100,
                epsFribeløp = 100,
                fradrag = FradragForBrev(emptyList(), FradragForBrev.Eps(emptyList(), false)),
                sats = "høy",
            ),
        )
        val innvilgetVedtak = InnvilgetSøknadsbehandlingPdfInnhold(
            personalia = pdfInnholdPersonalia(),
            fradato = "01.01.2020",
            tildato = "01.01.2020",
            søkedato = "01.12.2019",
            forventetInntektStørreEnn0 = true,
            harEktefelle = true,
            beregningsperioder = beregningsperioder,
            saksbehandlerNavn = "Hei",
            attestantNavn = "Hopp",
            fritekst = "",
            satsoversikt = SatsoversiktBrev(
                perioder = listOf(
                    SatsoversiktBrev.Satsperiode(
                        fraOgMed = "01.01.2020",
                        tilOgMed = "31.01.2020",
                        sats = "høy",
                        satsBeløp = 1000,
                        satsGrunn = "ENSLIG",
                    ),
                    SatsoversiktBrev.Satsperiode(
                        fraOgMed = "01.02.2020",
                        tilOgMed = "31.12.2020",
                        sats = "ordinær",
                        satsBeløp = 5000,
                        satsGrunn = "DELER_BOLIG_MED_VOKSNE_BARN_ELLER_ANNEN_VOKSEN",
                    ),
                ),
            ),
            sakstype = Sakstype.UFØRE,
            harFradrag = beregningsperioder.harFradrag(),
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
                "søkedato": "01.12.2019",
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
            opphørsgrunner = listOf(OpphørsgrunnBrev.FOR_HØY_INNTEKT),
            avslagsparagrafer = listOf(1),
            harEktefelle = true,
            harFradrag = false,
            beregningsperioder = listOf(
                BeregningsperiodeBrev(
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
            satsoversikt = SatsoversiktBrev(
                perioder = listOf(
                    SatsoversiktBrev.Satsperiode(
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
            sakstype = Sakstype.UFØRE,
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
