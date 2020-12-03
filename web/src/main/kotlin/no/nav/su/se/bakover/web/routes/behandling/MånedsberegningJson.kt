package no.nav.su.se.bakover.web.routes.behandling

import no.nav.su.se.bakover.domain.beregning.Månedsberegning
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import java.time.format.DateTimeFormatter

internal data class MånedsberegningJson(
    val fraOgMed: String,
    val tilOgMed: String,
    val sats: String,
    val grunnbeløp: Int,
    val beløp: Int,
    val fradrag: List<FradragJson>,
    val satsbeløp: Double,
    val epsFribeløp: Double,
    val epsInputFradrag: List<FradragJson>
)
internal fun Månedsberegning.toJson(epsFribeløp: Double, epsInputFradrag: List<Fradrag>) = MånedsberegningJson(
    fraOgMed = getPeriode().getFraOgMed().format(DateTimeFormatter.ISO_DATE),
    tilOgMed = getPeriode().getTilOgMed().format(DateTimeFormatter.ISO_DATE),
    sats = getSats().name,
    grunnbeløp = getBenyttetGrunnbeløp(),
    beløp = getSumYtelse(),
    fradrag = getFradrag().map { it.toJson() },
    satsbeløp = getSatsbeløp(),
    epsFribeløp = epsFribeløp,
    epsInputFradrag = epsInputFradrag.map { it.toJson() }
)
