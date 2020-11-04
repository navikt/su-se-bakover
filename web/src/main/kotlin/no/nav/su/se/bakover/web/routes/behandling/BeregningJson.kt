package no.nav.su.se.bakover.web.routes.behandling

import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.beregning.fradrag.UtenlandskInntekt
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

internal fun Beregning.toJson() = BeregningJson(
    id = id().toString(),
    opprettet = opprettet().toString(),
    fraOgMed = periode().fraOgMed().format(DateTimeFormatter.ISO_DATE),
    tilOgMed = periode().tilOgMed().format(DateTimeFormatter.ISO_DATE),
    sats = getSats().name,
    månedsberegninger = getMånedsberegninger().map { it.toJson() }, // TODO show fradrag/month
    fradrag = getFradrag().map {
        FradragJson(
            type = it.getFradragstype().toString(),
            beløp = it.getTotaltFradrag(),
            utenlandskInntekt = it.getUtenlandskInntekt(),
            periode = it.periode().toJson()
        )
    }
)

internal data class FradragJson(
    val periode: PeriodeJson?,
    val type: String,
    val beløp: Double,
    val utenlandskInntekt: UtenlandskInntekt?
) {
    fun toFradrag(periode: Periode): Fradrag = FradragFactory.ny(
        periode = this.periode?.toPeriode() ?: periode,
        type = Fradragstype.valueOf(type),
        beløp = beløp,
        utenlandskInntekt = utenlandskInntekt
    )
}

internal data class PeriodeJson(
    val fraOgMed: String,
    val tilOgMed: String
) {
    fun toPeriode() = Periode(LocalDate.parse(fraOgMed), LocalDate.parse(tilOgMed))
}

internal fun Periode.toJson() =
    PeriodeJson(fraOgMed().format(DateTimeFormatter.ISO_DATE), tilOgMed().format(DateTimeFormatter.ISO_DATE))
