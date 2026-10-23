package no.nav.su.se.bakover.service.brev.migrerTilPdfBygger

import no.nav.brev.brevbaker.document.Document
import no.nav.brev.brevbaker.markup.Attachment
import no.nav.brev.brevbaker.markup.LetterMarkup
import no.nav.brev.brevbaker.markup.outline.Block
import no.nav.brev.brevbaker.markup.outline.Text
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.NodeTraversor
import org.jsoup.select.NodeVisitor

data class GammeltInnhold(val ord: List<String>, val header: List<String>?, val hilsen: List<String>?)

private val bryterTagger = setOf("br", "td", "th", "li", "tr")

object HtmlTekst {
    fun uttrekk(html: String, hilsenErRamme: Boolean): GammeltInnhold {
        val dokument = Jsoup.parse(html)
        dokument.select("head, style, script").remove()
        val body = dokument.body()
        val header = body.selectFirst("div.header")?.let { ord(it) }
        body.select("div.header").remove()
        var hilsen: List<String>? = null
        if (hilsenErRamme) {
            val hilsenElement = body.selectFirst("div.hilsen")
            if (hilsenElement != null) {
                val alle = ord(hilsenElement)
                val postadresse = alle.indexOf("Postadresse:")
                hilsen = if (postadresse >= 0) alle.subList(0, postadresse) else alle
                val beholdt = if (postadresse >= 0) alle.subList(postadresse, alle.size) else emptyList()
                hilsenElement.empty()
                hilsenElement.appendText(beholdt.joinToString(" "))
            }
        }
        return GammeltInnhold(ord(body), header, hilsen)
    }

    fun ord(element: Element): List<String> {
        val sb = StringBuilder()
        NodeTraversor.traverse(
            object : NodeVisitor {
                override fun head(node: Node, depth: Int) {
                    when (node) {
                        is TextNode -> sb.append(node.wholeText)
                        is Element -> if (node.isBlock || node.normalName() in bryterTagger) sb.append(' ')
                    }
                }

                override fun tail(node: Node, depth: Int) {
                    if (node is Element && (node.isBlock || node.normalName() in bryterTagger)) sb.append(' ')
                }
            },
            element,
        )
        return tilOrd(sb.toString())
    }
}

fun tilOrd(tekst: String): List<String> =
    tekst.replace("\u200B", "").replace('\u00A0', ' ').split(Regex("\\s+")).filter { it.isNotEmpty() }

object MarkupTekst {
    fun ord(markup: LetterMarkup, vedlegg: List<Attachment>): List<String> =
        tilOrd(
            buildString {
                append(tekst(markup.title1)).append(' ')
                markup.blocks.forEach { append(blokk(it)).append(' ') }
                vedlegg.forEach { v ->
                    append(tekst(v.title1)).append(' ')
                    v.blocks.forEach { append(blokk(it)).append(' ') }
                }
            },
        )

    fun ord(dokument: Document): List<String> =
        tilOrd(
            buildString {
                if (dokument.visTittel) append(dokument.tittel).append(' ')
                dokument.blocks.forEach { append(blokk(it)).append(' ') }
            },
        )

    private fun blokk(blokk: Block): String = when (blokk) {
        is Block.Title2 -> tekst(blokk.content)
        is Block.Title3 -> tekst(blokk.content)
        is Block.Title4 -> tekst(blokk.content)
        is Block.Paragraph -> tekst(blokk.content)
        is Block.ItemList -> blokk.items.joinToString(" ") { tekst(it.content) }
        is Block.NumberedList -> blokk.items.joinToString(" ") { tekst(it.content) }
        is Block.Table ->
            (
                listOf(blokk.header.colSpec.joinToString(" ") { tekst(it.content) }) +
                    blokk.rows.map { r -> r.cells.joinToString(" ") { tekst(it.content) } }
                ).joinToString(" ")
        is Block.FormText -> tekst(blokk.content)
        is Block.FormChoice -> error("FormChoice brukes ikke")
        // else -> error("Ukjent blokk $blokk")
    }

    private fun tekst(tekster: List<Text>): String = tekster.joinToString("") {
        when (it) {
            is Text.NewLine -> " "
            else -> it.text
        }
    }
}
