package no.nav.su.se.bakover.service.brev.maltest

import dokument.domain.generer.PdfInnhold
import dokument.domain.generer.bygger.PdfBestilling
import no.nav.brev.brevbaker.document.DocumentSaksnummer
import no.nav.brev.brevbaker.markup.Markup
import no.nav.brev.brevbaker.markup.Saksinformasjon
import no.nav.brev.brevbaker.markup.outline.Block
import no.nav.brev.brevbaker.markup.outline.Text

// Hjelpefunksjoner for å lese markup-treet som en mal bygger.
// Testene ser på struktur og data, ikke på ordlyd.

internal fun bestillingFor(innhold: PdfInnhold): PdfBestilling = innhold.bestilling()

/** Det klienten sender til pdf-bygger. */
internal fun PdfBestilling.forespørsel(): Any = when (this) {
    is PdfBestilling.Brev -> request
    is PdfBestilling.Dokument -> request
}

/** Blokkene på øverste nivå, inkludert vedleggene i et brev. */
internal fun PdfBestilling.toppblokker(): List<Block> = when (this) {
    is PdfBestilling.Brev -> request.letterMarkup.blocks + request.attachments.flatMap { it.blocks }
    is PdfBestilling.Dokument -> request.document.blocks
}

/** All synlig tekst: titler, avsnitt, punkter, tabellceller og skjemaspørsmål. Linjeskift er utelatt. */
internal fun PdfBestilling.alleTekster(): List<String> {
    val titler = when (this) {
        is PdfBestilling.Brev ->
            (request.letterMarkup.title1 + request.attachments.flatMap { it.title1 }).synligTekst()

        is PdfBestilling.Dokument -> listOf(request.document.tittel)
    }
    return titler + toppblokker().flatMap { teksterI(it) }
}

private fun teksterI(blokk: Block): List<String> {
    val tekster = mutableListOf<Text>()
    if (blokk is Markup.TextContainer) tekster += blokk.content
    if (blokk is Block.ItemList) blokk.items.forEach { tekster += it.content }
    if (blokk is Block.NumberedList) blokk.items.forEach { tekster += it.content }
    if (blokk is Block.Table) blokk.rows.forEach { rad -> rad.cells.forEach { tekster += it.content } }
    if (blokk is Block.FormChoice) tekster += blokk.prompt
    return tekster.synligTekst()
}

private fun List<Text>.synligTekst(): List<String> = filterNot { it is Text.NewLine }.map { it.text }

/**
 * Tomme strukturer og linjeskift som ikke hører hjemme. Én tekstlinje per problem.
 *
 * - Avsnitt, overskrifter og punkter skal ha synlig tekst.
 * - Lister skal ha punkter, og tabeller skal ha rader.
 * - Et linjeskift skal ikke stå først eller sist i et avsnitt, en overskrift eller et punkt.
 *
 * Tomme tabellceller er lov.
 */
internal fun PdfBestilling.blokkproblemer(): List<String> = toppblokker().flatMap { blokkproblemer(it) }

private fun blokkproblemer(blokk: Block): List<String> = buildList {
    val navn = blokk.type.name
    if (blokk is Markup.TextContainer) addAll(tekstproblemer(navn, blokk.content))
    if (blokk is Block.ItemList) {
        if (blokk.items.isEmpty()) add("$navn uten punkter")
        blokk.items.forEach { addAll(tekstproblemer("punkt i $navn", it.content)) }
    }
    if (blokk is Block.NumberedList) {
        if (blokk.items.isEmpty()) add("$navn uten punkter")
        blokk.items.forEach { addAll(tekstproblemer("punkt i $navn", it.content)) }
    }
    if (blokk is Block.Table && blokk.rows.isEmpty()) add("$navn uten rader")
}

private fun tekstproblemer(hva: String, innhold: List<Text>): List<String> {
    val meningsfullt = innhold.filterNot { it !is Text.NewLine && it.text.isBlank() }
    return buildList {
        if (meningsfullt.none { it !is Text.NewLine }) add("$hva uten synlig tekst")
        if (meningsfullt.firstOrNull() is Text.NewLine) add("$hva starter med linjeskift")
        if (meningsfullt.lastOrNull() is Text.NewLine) add("$hva slutter med linjeskift")
    }
}

/** Navn, fødselsnummer og saksnummer slik de havner i topptekst eller saksinformasjon. */
internal data class Saksinfo(
    val navn: String?,
    val fødselsnummer: String?,
    val saksnummer: String?,
)

internal fun PdfBestilling.saksinfo(): Saksinfo? = when (this) {
    is PdfBestilling.Brev -> {
        val saksinformasjon: Any? = request.letterMarkup.saksinformasjon
        (saksinformasjon as? Saksinformasjon)?.let {
            Saksinfo(
                navn = it.gjelderNavn,
                fødselsnummer = verdiAv(it.gjelderPersonidentifikator),
                saksnummer = verdiAv(it.saksnummer),
            )
        }
    }

    is PdfBestilling.Dokument -> {
        val saksnummer: Any? = request.document.saksnummer
        Saksinfo(
            navn = request.document.gjelderNavn,
            fødselsnummer = null,
            saksnummer = (saksnummer as? DocumentSaksnummer)?.let { verdiAv(it.saksnummer) },
        )
    }
}

/** Pakker ut value-klassene i markup-modellen uten å anta om de er nullable. */
private fun verdiAv(verdi: Any?): String? = when (verdi) {
    is Markup.Personidentifikator -> verdi.value
    is Markup.Saksnummer -> verdi.value
    is String -> verdi
    else -> null
}
