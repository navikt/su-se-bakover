package no.nav.su.se.bakover.domain.brev.beregning

import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.toBrevformat
import no.nav.su.se.bakover.domain.MånedBeløp

data class BrevTilbakekrevingInfo(
    val periode: String,
    val beløp: Int,
    val tilbakekrevingsgrad: String,
)

data class Tilbakekreving(
    private val månedBeløp: List<MånedBeløp>
) {
    val tilbakekrevingavdrag = månedBeløp.map {
        BrevTilbakekrevingInfo(
            periode = it.periode.toBrevPeriode(),
            beløp = it.beløp.sum(),
            tilbakekrevingsgrad = "100%",
        )
    }
    val periodeStart = månedBeløp.first().periode.fraOgMed.toBrevformat()
    val periodeSlutt = månedBeløp.last().periode.tilOgMed.toBrevformat()
}

private fun Periode.toBrevPeriode(): String = this.fraOgMed.toBrevformat() + " - " + this.tilOgMed.toBrevformat()
