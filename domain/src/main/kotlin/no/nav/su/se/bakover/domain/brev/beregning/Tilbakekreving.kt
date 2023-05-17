package no.nav.su.se.bakover.domain.brev.beregning

import no.nav.su.se.bakover.common.MånedBeløp
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.sorterPåPeriode
import no.nav.su.se.bakover.common.toBrevformat

data class BrevTilbakekrevingInfo(
    val periode: String,
    val beløp: String,
    val tilbakekrevingsgrad: String,
)

data class Tilbakekreving(
    private val månedBeløp: List<MånedBeløp>,
) {
    val tilbakekrevingavdrag = månedBeløp.map {
        BrevTilbakekrevingInfo(
            periode = it.periode.toBrevPeriode(),
            beløp = it.beløp.tusenseparert(),
            tilbakekrevingsgrad = "100%",
        )
    }
    val periodeStart = månedBeløp.sorterPåPeriode().first().periode.fraOgMed.toBrevformat()
    val periodeSlutt = månedBeløp.sorterPåPeriode().last().periode.tilOgMed.toBrevformat()
}

private fun Periode.toBrevPeriode(): String = this.fraOgMed.toBrevformat() + " - " + this.tilOgMed.toBrevformat()
