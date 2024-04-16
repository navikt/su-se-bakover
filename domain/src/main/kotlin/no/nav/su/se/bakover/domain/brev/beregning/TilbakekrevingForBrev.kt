package no.nav.su.se.bakover.domain.brev.beregning

import no.nav.su.se.bakover.common.Månedsbeløp
import no.nav.su.se.bakover.common.domain.tid.toBrevformat
import no.nav.su.se.bakover.common.tid.periode.Periode

data class BrevTilbakekrevingInfo(
    val periode: String,
    val beløp: String,
    val tilbakekrevingsgrad: String,
)

data class Tilbakekreving(
    private val månedBeløp: Månedsbeløp,
) {
    init {
        require(månedBeløp.isNotEmpty()) {
            "Kan ikke lage et tilbakekrevingsbrev uten måneder"
        }
    }
    val tilbakekrevingavdrag = månedBeløp.map {
        BrevTilbakekrevingInfo(
            periode = it.periode.toBrevPeriode(),
            beløp = it.beløp.tusenseparert(),
            tilbakekrevingsgrad = "100%",
        )
    }

    // TODO jah: Flytt formateringen nærmere PdfInnhold? Kanskje pdfgen har denne funksjonaliteten? ref. https://github.com/navikt/pdfgen/blob/master/src/main/kotlin/no/nav/pdfgen/template/Helpers.kt
    val periodeStart = månedBeløp.fraOgMed!!.toBrevformat()
    val periodeSlutt = månedBeløp.tilOgMed!!.toBrevformat()
}

// TODO jah: Flytt formateringen nærmere PdfInnhold? ref. https://github.com/navikt/pdfgen/blob/master/src/main/kotlin/no/nav/pdfgen/template/Helpers.kt
private fun Periode.toBrevPeriode(): String = this.fraOgMed.toBrevformat() + " - " + this.tilOgMed.toBrevformat()
