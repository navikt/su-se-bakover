package no.nav.su.se.bakover.web.routes.behandling

import no.nav.su.se.bakover.domain.beregning.Beregning
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
    fradrag = fradrag.map { FradragJson(it.type.toString(), it.beløp, it.beskrivelse) },
    forventetInntekt = forventetInntekt
)

internal data class FradragJson(
    val type: String,
    val beløp: Int,
    val beskrivelse: String?
) {
    fun toFradrag(): Fradrag = Fradrag(type = Fradragstype.valueOf(type), beløp = beløp, beskrivelse = beskrivelse)
}
