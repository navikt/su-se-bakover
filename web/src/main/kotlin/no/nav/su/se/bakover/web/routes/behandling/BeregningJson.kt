package no.nav.su.se.bakover.web.routes.behandling

import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.DelerAvPeriode
import no.nav.su.se.bakover.domain.beregning.FraUtlandInntekt
import no.nav.su.se.bakover.domain.beregning.Fradrag
import no.nav.su.se.bakover.domain.beregning.Fradragstype
import java.time.format.DateTimeFormatter

internal data class BeregningJson(
    val id: String,
    val opprettet: String,
    val fom: String,
    val fraOgMed: String,
    val tom: String,
    val tilOgMed: String,
    val sats: String,
    val månedsberegninger: List<MånedsberegningJson> = emptyList(),
    val fradrag: List<FradragJson> = emptyList(),
    val forventetInntekt: Int,
)

internal fun Beregning.toJson() = BeregningJson(
    id = id.toString(),
    opprettet = opprettet.toString(),
    fom = fraOgMed.format(DateTimeFormatter.ISO_DATE),
    fraOgMed = fraOgMed.format(DateTimeFormatter.ISO_DATE),
    tom = tilOgMed.format(DateTimeFormatter.ISO_DATE),
    tilOgMed = tilOgMed.format(DateTimeFormatter.ISO_DATE),
    sats = sats.name,
    månedsberegninger = månedsberegninger.map { it.toJson() },
    fradrag = fradrag.map {
        FradragJson(
            type = it.type.toString(),
            beløp = it.beløp,
            fraUtlandInntekt = it.fraUtlandInntekt,
            delerAvPeriode = delerAvPeriodeToIso(it.delerAvPeriode)
        )
    },
    forventetInntekt = forventetInntekt
)

internal fun delerAvPeriodeToIso(delerAvPeriode: DelerAvPeriode?): DelerAvPeriode? {
    if (delerAvPeriode == null) {
        return null
    }
    return DelerAvPeriode(
        fraOgMed = delerAvPeriode.fraOgMed.format(DateTimeFormatter.ISO_DATE),
        tilOgMed = delerAvPeriode.tilOgMed.format(DateTimeFormatter.ISO_DATE),
    )
}

internal data class FradragJson(
    val type: String,
    val beløp: Int,
    val fraUtlandInntekt: FraUtlandInntekt?,
    val delerAvPeriode: DelerAvPeriode?
) {
    fun toFradrag(): Fradrag = Fradrag(
        type = Fradragstype.valueOf(type),
        beløp = beløp,
        fraUtlandInntekt = fraUtlandInntekt,
        delerAvPeriode = delerAvPeriode,
    )
}
