package dokument.domain.generer.bygger.maler

import dokument.domain.generer.DokumentMal
import dokument.domain.generer.PdfInnhold
import dokument.domain.generer.PersonaliaPdfInnhold
import dokument.domain.generer.bygger.brev
import dokument.domain.generer.bygger.fellesKlageinfo
import dokument.domain.generer.bygger.fritekst
import dokument.domain.generer.bygger.fødselsnummerAvsnitt
import dokument.domain.generer.bygger.harDuSpørsmål
import dokument.domain.generer.bygger.kroner
import dokument.domain.generer.bygger.linjer
import dokument.domain.generer.bygger.postadresse
import dokument.domain.generer.bygger.punktliste
import dokument.domain.generer.bygger.rettTilInnsyn
import no.nav.brev.brevbaker.markup.Attachment
import no.nav.brev.brevbaker.markup.dsl.attachment
import no.nav.brev.brevbaker.markup.dsl.cell
import no.nav.brev.brevbaker.markup.dsl.column
import no.nav.brev.brevbaker.markup.dsl.header
import no.nav.brev.brevbaker.markup.dsl.paragraph
import no.nav.brev.brevbaker.markup.dsl.row
import no.nav.brev.brevbaker.markup.dsl.table
import no.nav.brev.brevbaker.markup.dsl.title1
import no.nav.brev.brevbaker.markup.dsl.title2
import no.nav.brev.brevbaker.markup.dsl.title3
import no.nav.brev.brevbaker.markup.outline.Block.Table.ColumnAlignment
import no.nav.su.se.bakover.common.domain.sak.Sakstype

data class VedtaksbrevTilbakekrevingsbehandlingPdfInnhold(
    override val sakstype: Sakstype,
    val personalia: PersonaliaPdfInnhold,
    val saksbehandlerNavn: String,
    val attestantNavn: String?,
    val fritekst: String?,
    val dato: String,
    val månedsoversiktMedSum: MånedsoversiktMedSum,
    val skalTilbakekreve: Boolean,
    val dødsbo: Boolean,
    /**
     * Summen av brutto beløp som skal ikke tilbakekreves. Dette vises dersom `skalTilbakekreve` er false.
     */
    val bruttoSkalIkkeTilbakekreveSummert: Int,
) : PdfInnhold {
    override val dokumentMal = DokumentMal.Tilbakekreving.Vedtaksbrev(dødsbo)
}

data class MånedsoversiktMedSum(
    val sorterteMåneder: List<EnkeltMånedsoversikt>,
    val sumBruttoSkalTilbakekreve: Int,
    val sumNettoSkalTilbakekreve: Int,
)

enum class Vurdering {
    SkalTilbakekreve,
    SkalIkkeTilbakekreve,
}

data class EnkeltMånedsoversikt(
    val periode: String,
    val vurdering: Vurdering,
    val bruttoSkalTilbakekreve: Int,
    val nettoSkalTilbakekreve: Int,
)

val vedtaksbrevTilbakekrevingsbehandling = { _: String, pdfInnhold: VedtaksbrevTilbakekrevingsbehandlingPdfInnhold ->
    when (pdfInnhold.dødsbo) {
        false -> vedtaksbrevBruker(pdfInnhold)
        true -> vedtaksbrevDødsbo(pdfInnhold)
    }
}

private val vedtaksbrevBruker = { pdfInnhold: VedtaksbrevTilbakekrevingsbehandlingPdfInnhold ->
    brev(
        tittel = if (pdfInnhold.skalTilbakekreve) {
            "Du må betale tilbake supplerende stønad"
        } else {
            "Du må ikke betale tilbake supplerende stønad"
        },
        personalia = pdfInnhold.personalia,
        saksbehandler = pdfInnhold.saksbehandlerNavn,
        attestant = pdfInnhold.attestantNavn,
        vedlegg = if (pdfInnhold.skalTilbakekreve) {
            listOf(
                informasjonOmKlageOgAnke(),
                vedleggOversiktOverFeilutbetalingen(
                    (pdfInnhold),
                ),
            )
        } else {
            emptyList()
        },
    ) {
        fødselsnummerAvsnitt(pdfInnhold.personalia)
        if (pdfInnhold.skalTilbakekreve) {
            paragraph("Du har fått for mye supplerende stønad som du må betale tilbake.")
            paragraph(
                " Beløpet du skylder før skatt er kr " +
                    "${pdfInnhold.månedsoversiktMedSum.sumBruttoSkalTilbakekreve.kroner(utenDesimaler = true)} kr. " +
                    "Dette er hele det feilutbetalte beløpet.",
            )
            paragraph(
                " Du skal betale kr " +
                    "${pdfInnhold.månedsoversiktMedSum.sumNettoSkalTilbakekreve.kroner(utenDesimaler = true)} kr " +
                    "etter at skatt er trukket fra.",
            )
            title3("Hvordan har vi kommet fram til at du må betale tilbake?")
            fritekst(pdfInnhold.fritekst ?: "")
            paragraph("Vedtaket er gjort etter reglene i lov om supplerende stønad § 13.")
            title3("Skatt og skatteoppgjør")
            paragraph(
                "Skatten som er trukket fra beløpet du skal betale tilbake, " +
                    "er beregnet etter det du har blitt trukket i skatt i gjennomsnitt per måned. " +
                    "Det betyr at beløpet du skal tilbakebetale etter skatt, " +
                    "ikke alltid er likt med det beløpet du fikk inn på kontoen din.",
            )
            paragraph(
                " Nav gir opplysninger til Skatteetaten om skattebeløpet og om beløpet du skal betale tilbake før skatt er trukket i fra. " +
                    "Skatteetaten vil vurdere om det er grunnlag for å endre skatteoppgjør.",
            )
            title3("Hvordan betale tilbake pengene du skylder")
            paragraph("Du vil få faktura fra Skatteetaten på det beløpet du skal betale tilbake. ")
            paragraph("På fakturaen vil det stå informasjon om nøyaktig beløp, kontonummer og forfallsdato. ")
            paragraph("Du finner mer informasjon på www.skatteetaten.no/betale. ")
        } else {
            paragraph("Vi har kommet frem til at du ikke skal betale tilbake for mye utbetalt supplerende stønad på ${pdfInnhold.bruttoSkalIkkeTilbakekreveSummert.kroner(utenDesimaler = true)} kroner, fordi vilkårene for tilbakekreving ikke er oppfylt. ")
            title3("Hvordan har vi kommet fram til at du ikke må betale tilbake?")
            fritekst(pdfInnhold.fritekst ?: "")
        }
        if (pdfInnhold.skalTilbakekreve) {
            fellesKlageinfo(
                sakstype = pdfInnhold.sakstype,
                ekstraVedtaksInfoForKlage =
                "Du må som hovedregel begynne å betale beløpet tilbake når du får fakturaen selv om du klager på dette vedtaket. Dette følger av forvaltningsloven § 42. Hvis du får vedtak om at du ikke trengte å betale tilbake hele eller deler av beløpet du skyldte, betaler vi pengene tilbake til deg. ",
            )
        } else {
            fellesKlageinfo(pdfInnhold.sakstype)
        }
        postadresse()
    }
}

private val vedtaksbrevDødsbo = { pdfInnhold: VedtaksbrevTilbakekrevingsbehandlingPdfInnhold ->
    brev(
        tittel = "Boet må betale tilbake supplerende stønad",
        personalia = pdfInnhold.personalia,
        saksbehandler = pdfInnhold.saksbehandlerNavn,
        attestant = pdfInnhold.attestantNavn,
        vedlegg = listOf(
            vedleggOversiktOverFeilutbetalingen(pdfInnhold),
        ),
    ) {
        fødselsnummerAvsnitt(pdfInnhold.personalia)
        paragraph("Vi har kommet frem til at for mye utbetalt supplerende stønad må betales tilbake.")
        paragraph(
            "Beløpet boet skylder før skatt er " +
                "${pdfInnhold.månedsoversiktMedSum.sumBruttoSkalTilbakekreve.kroner(utenDesimaler = true)} kr. " +
                "Boet skal betale tilbake " +
                "${pdfInnhold.månedsoversiktMedSum.sumNettoSkalTilbakekreve.kroner(utenDesimaler = true)} kr etter at skatten er trukket fra. ",
        )
        paragraph("Beløpet som er trukket i skatt får vi tilbake fra Skatteetaten. ")
        paragraph("Nedenfor følger en oversikt over periodene med feilutbetalinger og feilutbetalt beløp inkludert skatt. ")
        title3("Hvordan har vi kommet fram til at boet må betale tilbake? ")
        fritekst(pdfInnhold.fritekst ?: "")
        title3("Skatt og skatteoppgjør")
        paragraph(
            "Vi gir opplysninger til Skatteetaten om dette vedtaket. " +
                "Skatteetaten fastsetter det endelige skattebeløpet, " +
                "og vil vurdere om det er grunnlag for å endre skatteoppgjøret. ",
        )
        title3("Hvordan betaler boet tilbake?")
        paragraph(
            "Boet får faktura fra Skatteetaten på det beløpet som skal betales tilbake. " +
                "På fakturaen står det informasjon om nøyaktig beløp, kontonummer og forfallsdato. " +
                "Boet trenger ikke å gjøre noe før boet får fakturaen. ",
        )
        paragraph("Boet finner mer informasjon på www.skatteetaten.no/betale. ")
        title3("Boet har rett til å klage ")
        paragraph(
            " Hvis boet mener at vedtaket er feil, kan boet klage innen 6 uker fra den datoen boet mottok vedtaket. " +
                "Skjema og informasjon finnes på www.nav.no/klage.",
        )
        paragraph("Klagen kan sendes digitalt via innlogging på nettsiden vår www.nav.no, eller i posten.")
        paragraph(
            "Skriftlig klage som sendes i posten må inneholde navn, fødselsnummer og adresse, " +
                "og den må være underskrevet av representant for boet. " +
                "Klagen må sendes til Nav Familie- og pensjonsytelser Ålesund sammen med forside fra www.nav.no. ",
        )
        paragraph("Nav kan hjelpe med å skrive ned klagen. ")
        paragraph("Boet må skrive")
        punktliste(
            "hvilket vedtak det klages på",
            "hvilken endring i vedtaket boet ber om",
        )
        paragraph("Boet bør også")
        punktliste(
            "skrive hvorfor boet mener vedtaket er feil ",
            "nevne erklæringer og andre dokumenter som boet legger ved klagen",
        )
        paragraph(
            "Boet må som hovedregel begynne å betale tilbake når boet får fakturaen, selv om boet klager på vedtaket. " +
                "Dette følger av forvaltningsloven § 42 med tilhørende rundskriv. " +
                "Hvis boet får vedtak om at boet ikke trengte å betale tilbake hele eller deler av beløpet boet skyldte, betaler vi pengene tilbake. ",
        )
        paragraph(
            "Nav kan av eget tiltak bestemme at tilbakekrevingen skal utsettes til klagen er behandlet, " +
                "for eksempel hvis vi ser at det er sannsynlig at det påklagede vedtaket blir omgjort. " +
                " Boet kan også søke om utsettelse av tilbakebetaling til klagen er behandlet. " +
                "Vi gjør oppmerksom på at det ikke gis utsettelse bare av økonomiske grunner. ",
        )
        paragraph(
            "Hvis boet får medhold i klagen, kan boet få dekket vesentlige utgifter som har vært nødvendige for å få endret vedtaket. " +
                "Krav om dekning av saksomkostninger må settes frem senest tre uker etter at melding om det nye vedtaket ble mottatt. " +
                "Dette framgår av forvaltningsloven § 36. ",
        )
        paragraph(
            "Boet kan ha krav på fri rettshjelp etter rettshjelploven. " +
                "Informasjon om denne ordningen kan boet få hos Statsforvalteren, advokater eller Nav. ",
        )
        rettTilInnsyn(true)
        harDuSpørsmål(pdfInnhold.sakstype, dødsbo = true)
        postadresse()
    }
}

private fun informasjonOmKlageOgAnke(): Attachment {
    return attachment {
        title1("Vedlegg: Informasjon om klage og anke")
        outline {
            title2("Veiledning fra Nav - forvaltningsloven § 11")
            paragraph(
                "Vi har plikt til å veilede deg om dine rettigheter og plikter i saken din, " +
                    "både før, under og etter saksbehandlingen. Dersom du har spørsmål eller er " +
                    "usikker på noe, vil vi gjøre vårt beste for å hjelpe deg.",
            )
            title2("Hjelp fra andre - forvaltningsloven § 12")
            paragraph(
                "Du kan be om hjelp fra andre under hele saksbehandlingen, " +
                    "for eksempel av advokat, rettshjelper, en organisasjon du er medlem av " +
                    "eller en annen myndig person. Hvis den som hjelper deg ikke er advokat, " +
                    "må du gi denne personen en skriftlig fullmakt. " +
                    "Bruk gjerne skjemaet du finner på www.nav.no/fullmakt.",
            )
            title2("Klage på vedtaket - folketrygdloven § 21-12")
            paragraph(
                "Du kan klage på vedtaket innen seks uker fra du mottok det. Kontoret som har fattet vedtaket vil da vurdere " +
                    "saken din på nytt. Hvis du ikke får gjennomslag for klagen din, blir den sendt videre til Nav klageinstans " +
                    "for ny vurdering og avgjørelse. Dersom du heller ikke får gjennomslag hos klageinstansen, kan du anke saken " +
                    "inn for Trygderetten.",
            )
            title2("Hvordan sende inn klage?")
            paragraph(
                "Klagen må være skriftlig og inneholde ditt navn, fødselsnummer og adresse. Du kan benytte skjemaet som du " +
                    "finner på www.nav.no/klage. Klagen kan sendes via innlogging på nettsiden vår, www.nav.no, eller " +
                    "sendes til oss i posten til",
            )
            paragraph {
                linjer("Nav skanning\nPostboks 1400\n0109 OSLO")
            }
            paragraph(
                "Hvis du velger å sende klagen per post, må du huske å undertegne den. Hvis klagen ikke er undertegnet, vil vi " +
                    "dessverre måtte returnere den til deg.",
            )
            title2("Hva må klagen inneholde?")
            paragraph("Du må skrive")
            punktliste(
                "hvilket vedtak du klager på",
                "hvilken endring i vedtaket du ber om",
            )
            paragraph("Du bør også")
            punktliste(
                "skrive hvorfor du mener vedtaket er feil",
                "nevne erklæringer og andre dokumenter som du legger ved klagen",
            )
            paragraph("Nav kan hjelpe deg med å skrive ned klagen.")
            title2("Tilbakekreving")
            paragraph(
                "Du må som hovedregel begynne å betale tilbake når du får fakturaen, selv om du klager på vedtaket. Dette " +
                    "framgår av forvaltningsloven § 42 med tilhørende rundskriv.",
            )
            paragraph(
                "Nav kan av eget tiltak bestemme at tilbakekrevingen skal utsettes til klagen er behandlet, for eksempel hvis " +
                    "vi ser at det er sannsynlig at det påklagede vedtaket blir omgjort. Du kan også søke om utsettelse av " +
                    "tilbakebetaling til klagen er behandlet. Vi gjør deg oppmerksom på at det ikke gis utsettelse bare av " +
                    "økonomiske grunner.",
            )
            title2("Du kan få dekket utgifter")
            paragraph("Får du medhold, kan du få dekket vesentlige utgifter som har vært nødvendige for å få endret vedtaket.")
            paragraph(
                "Du kan ha krav på fri rettshjelp etter rettshjelploven. Informasjon om denne ordningen kan du få hos " +
                    "statsforvalteren, advokater eller Nav.",
            )
            paragraph("Du kan lese om saksomkostninger i forvaltningsloven § 36.")
        }
    }
}

private fun vedleggOversiktOverFeilutbetalingen(
    pdfInnhold: VedtaksbrevTilbakekrevingsbehandlingPdfInnhold,
): Attachment {
    val månedsoversikt = pdfInnhold.månedsoversiktMedSum
    return attachment {
        title1("Vedlegg: Oversikt over feilutbetalingen")
        outline {
            table {
                header {
                    column("Periode")
                    column("Feilutbetalt beløp", ColumnAlignment.RIGHT)
                    column("Tilbakebetaling")
                    column("Beløp før skatt", alignment = ColumnAlignment.RIGHT)
                    column(
                        "Beløp du skal betale tilbake etter at skatt er trukket fra",
                        alignment = ColumnAlignment.RIGHT,
                    )
                }
                månedsoversikt.sorterteMåneder.forEach { måned ->
                    row {
                        cell(måned.periode)
                        cell("${måned.bruttoSkalTilbakekreve.kroner(utenDesimaler = true)} kroner")
                        cell(
                            if (måned.vurdering == Vurdering.SkalTilbakekreve) {
                                "Hele beløpet"
                            } else {
                                "Ingen tilbakebetaling"
                            },
                        )
                        cell("${måned.bruttoSkalTilbakekreve.kroner(utenDesimaler = true)} kroner")
                        cell("${måned.nettoSkalTilbakekreve.kroner(utenDesimaler = true)} kroner")
                    }
                }
                row {
                    cell("sum")
                    cell("")
                    cell("")
                    cell("${månedsoversikt.sumBruttoSkalTilbakekreve.kroner(utenDesimaler = true)} kroner")
                    cell("${månedsoversikt.sumNettoSkalTilbakekreve.kroner(utenDesimaler = true)} kroner")
                }
            }
        }
    }
}
