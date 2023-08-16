package no.nav.su.se.bakover.domain.brev.beregning

import no.nav.su.se.bakover.common.MånedBeløp
import no.nav.su.se.bakover.common.extensions.toBrevformat
import no.nav.su.se.bakover.common.sorterPåPeriode
import no.nav.su.se.bakover.common.tid.periode.Periode

data class BrevTilbakekrevingInfo(
    val periode: String,
    val beløp: String,
    val tilbakekrevingsgrad: String,
)

data class Tilbakekreving(
    // TODO jah: Dersom man bruker periodeStart/periodeSlutt får man en NoSuchElementException elements exceptions
    //  Kan vi bytte til NEL?
    private val månedBeløp: List<MånedBeløp>,
) {
    val tilbakekrevingavdrag = månedBeløp.map {
        BrevTilbakekrevingInfo(
            periode = it.periode.toBrevPeriode(),
            beløp = it.beløp.tusenseparert(),
            tilbakekrevingsgrad = "100%",
        )
    }

    // TODO jah: Flytt formateringen nærmere PdfInnhold? Kanskje pdfgen har denne funksjonaliteten? ref. https://github.com/navikt/pdfgen/blob/master/src/main/kotlin/no/nav/pdfgen/template/Helpers.kt
    val periodeStart = månedBeløp.sorterPåPeriode().first().periode.fraOgMed.toBrevformat()
    val periodeSlutt = månedBeløp.sorterPåPeriode().last().periode.tilOgMed.toBrevformat()
}

// TODO jah: Flytt formateringen nærmere PdfInnhold? ref. https://github.com/navikt/pdfgen/blob/master/src/main/kotlin/no/nav/pdfgen/template/Helpers.kt
private fun Periode.toBrevPeriode(): String = this.fraOgMed.toBrevformat() + " - " + this.tilOgMed.toBrevformat()
