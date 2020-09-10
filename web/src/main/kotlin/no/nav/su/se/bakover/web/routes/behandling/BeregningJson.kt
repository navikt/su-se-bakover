package no.nav.su.se.bakover.web.routes.behandling

import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Fradrag
import no.nav.su.se.bakover.domain.beregning.Fradragstype
import java.time.format.DateTimeFormatter

internal data class BeregningJson(
    val id: String,
    val opprettet: String,
    val fom: String,
    val tom: String,
    val sats: String,
    val månedsberegninger: List<MånedsberegningJson> = emptyList(),
    val fradrag: List<FradragJson> = emptyList()
)

internal fun Beregning.toJson() = BeregningJson(
    id = id.toString(),
    opprettet = opprettet.toString(),
    fom = fom.format(DateTimeFormatter.ISO_DATE),
    tom = tom.format(DateTimeFormatter.ISO_DATE),
    sats = sats.name,
    månedsberegninger = månedsberegninger.map { it.toJson() },
    fradrag = fradrag.map { FradragJson(it.type.toString(), it.beløp, it.beskrivelse) }
)

internal data class FradragJson(
    val type: String,
    val beløp: Int,
    val beskrivelse: String?
) {
    fun toFradrag(): Fradrag = Fradrag(type = Fradragstype.valueOf(type), beløp = beløp, beskrivelse = beskrivelse)
}

// internal fun HttpStatusCode.jsonBody(dtoConvertable: DtoConvertable<Beregning>) =
//     Resultat.json(this, serialize(dtoConvertable.toDto().toJson()))
