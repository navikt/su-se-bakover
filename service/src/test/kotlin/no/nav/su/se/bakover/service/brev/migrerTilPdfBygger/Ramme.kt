package no.nav.su.se.bakover.service.brev.migrerTilPdfBygger

import dokument.domain.pdf.bygger.AVSENDERENHET
import dokument.domain.pdf.bygger.PdfBestilling
import no.nav.brev.brevbaker.markup.outline.Block
import no.nav.brev.brevbaker.markup.outline.Text
import java.time.format.DateTimeFormatter
import java.util.Locale

object Ramme {
    private val dato = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    private val langDato = DateTimeFormatter.ofPattern("d. MMMM yyyy", Locale.of("nb", "NO"))

    private val faste = PdfTekst.innholdsord(
        "Side side av Dato Navn Saksnummer Fødselsnummer Med vennlig hilsen hos $AVSENDERENHET Nav Vedlegg " +
            "Tabellen fortsetter på neste side Fortsettelse fra forrige side",
    ).toSet()

    private fun tekst(innhold: List<Text>) = innhold.joinToString(" ") { it.text }

    private fun tabelloverskrifter(blokker: List<Block>): List<String> =
        blokker.filterIsInstance<Block.Table>().flatMap { tabell -> tabell.header.colSpec.map { tekst(it.content) } }

    fun tillatteOrd(bestilling: PdfBestilling): Set<String> {
        val variable = when (bestilling) {
            is PdfBestilling.Brev -> {
                val markup = bestilling.request.letterMarkup
                val info = markup.saksinformasjon
                val signatur = markup.signatur.saksbehandlerSignatur
                val fnr = info.gjelderPersonidentifikator.value
                listOfNotNull(
                    info.gjelderNavn,
                    fnr,
                    fnr.take(6),
                    fnr.drop(6),
                    info.saksnummer.value,
                    info.dokumentDato.format(dato),
                    info.dokumentDato.format(langDato),
                    signatur?.saksbehandlerNavn,
                    signatur?.attesterendeSaksbehandlerNavn,
                ) + bestilling.request.attachments.map { tekst(it.title1) } +
                    tabelloverskrifter(markup.blocks + bestilling.request.attachments.flatMap { it.blocks })
            }
            is PdfBestilling.Dokument -> {
                val dokument = bestilling.request.document
                listOfNotNull(
                    dokument.gjelderNavn,
                    dokument.saksnummer?.saksnummer?.value,
                    dokument.dokumentDato?.format(dato),
                    dokument.dokumentDato?.format(langDato),
                ) + tabelloverskrifter(dokument.blocks)
            }
        }
        return faste + variable.flatMap { PdfTekst.innholdsord(it) } + (1..99).map { it.toString() }
    }
}
