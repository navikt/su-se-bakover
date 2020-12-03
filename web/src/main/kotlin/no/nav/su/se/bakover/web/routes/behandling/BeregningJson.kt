package no.nav.su.se.bakover.web.routes.behandling

import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.beregning.fradrag.UtenlandskInntekt
import no.nav.su.se.bakover.domain.beregning.fradrag.getEpsFribeløp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal data class BeregningJson(
    val id: String,
    val opprettet: String,
    val fraOgMed: String,
    val tilOgMed: String,
    val sats: String,
    val månedsberegninger: List<MånedsberegningJson> = emptyList(),
    val fradrag: List<FradragJson> = emptyList()
)

internal fun Beregning.toJson(): BeregningJson {
    val epsInputFradragMap = getFradrag()
        .filter { it.getTilhører() == FradragTilhører.EPS }
        .flatMap { FradragFactory.periodiser(it) }
        .groupBy { it.getPeriode() }

    return BeregningJson(
        id = getId().toString(),
        opprettet = getOpprettet().toString(),
        fraOgMed = getPeriode().getFraOgMed().format(DateTimeFormatter.ISO_DATE),
        tilOgMed = getPeriode().getTilOgMed().format(DateTimeFormatter.ISO_DATE),
        sats = getSats().name,
        månedsberegninger = getMånedsberegninger().map {
            it.toJson(
                getEpsFribeløp(getFradragStrategyName(), it.getPeriode()),
                epsInputFradrag = epsInputFradragMap[it.getPeriode()] ?: emptyList()
            )
        }, // TODO show fradrag/month
        fradrag = getFradrag().map { it.toJson() }
    )
}

internal fun Fradrag.toJson() =
    FradragJson(
        type = getFradragstype().toString(),
        beløp = getTotaltFradrag(),
        utenlandskInntekt = getUtenlandskInntekt(),
        periode = getPeriode().toJson(),
        tilhører = getTilhører().toString()
    )

internal data class FradragJson(
    val periode: PeriodeJson?,
    val type: String,
    val beløp: Double,
    val utenlandskInntekt: UtenlandskInntekt?,
    val tilhører: String
) {
    fun toFradrag(periode: Periode): Fradrag = FradragFactory.ny(
        type = Fradragstype.valueOf(type),
        beløp = beløp,
        periode = this.periode?.toPeriode() ?: periode,
        utenlandskInntekt = utenlandskInntekt,
        tilhører = FradragTilhører.valueOf(tilhører)
    )
}

internal data class PeriodeJson(
    val fraOgMed: String,
    val tilOgMed: String
) {
    fun toPeriode() = Periode(LocalDate.parse(fraOgMed), LocalDate.parse(tilOgMed))
}

internal fun Periode.toJson() =
    PeriodeJson(getFraOgMed().format(DateTimeFormatter.ISO_DATE), getTilOgMed().format(DateTimeFormatter.ISO_DATE))
