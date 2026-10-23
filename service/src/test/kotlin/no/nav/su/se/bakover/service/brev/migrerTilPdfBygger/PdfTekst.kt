package no.nav.su.se.bakover.service.brev.migrerTilPdfBygger

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper

private val ordmønster = Regex("[\\p{L}\\p{N}]+")

data class PdfOrd(val ord: String, val sistPåLinja: Boolean)

private fun vask(tekst: String) = tekst.replace("\u200B", "").replace('\u00A0', ' ').replace('\u2011', '-')

object PdfTekst {
    fun tekst(pdf: ByteArray): String = Loader.loadPDF(pdf).use { PDFTextStripper().getText(it) }

    fun ord(pdf: ByteArray): List<PdfOrd> = ord(tekst(pdf))

    fun ord(tekst: String): List<PdfOrd> =
        vask(tekst).lines().flatMap { linje ->
            val treff = ordmønster.findAll(linje).toList()
            treff.mapIndexed { indeks, t -> PdfOrd(t.value, indeks == treff.lastIndex) }
        }

    fun innholdsord(tekst: String): List<String> = ordmønster.findAll(vask(tekst)).map { it.value }.toList()
}

data class Ordresultat(val mangler: Map<String, Int>, val ekstra: List<String>)

fun sammenlignOrd(forventet: List<String>, pdf: List<PdfOrd>): Ordresultat {
    val behov = forventet.groupingBy { it }.eachCount().toMutableMap()
    val ekstra = mutableListOf<String>()
    fun trenger(ord: String) = (behov[ord] ?: 0) > 0
    fun bruk(ord: String) = behov.merge(ord, -1, Int::plus)

    var i = 0
    while (i < pdf.size) {
        val ord = pdf[i]
        if (ord.sistPåLinja && !trenger(ord.ord)) {
            var sammensatt = ord.ord
            var j = i
            var funnet = -1
            while (j + 1 < pdf.size && j - i < 5) {
                j++
                sammensatt += pdf[j].ord
                if (trenger(sammensatt)) {
                    funnet = j
                    break
                }
                if (!pdf[j].sistPåLinja) break
            }
            if (funnet >= 0) {
                bruk(sammensatt)
                i = funnet + 1
                continue
            }
        }
        if (trenger(ord.ord)) bruk(ord.ord) else ekstra += ord.ord
        i++
    }
    return Ordresultat(mangler = behov.filterValues { it > 0 }, ekstra = ekstra)
}
