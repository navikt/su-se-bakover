package no.nav.su.se.bakover.database.beregning

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.Merknad
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.beregning.fradrag.UtenlandskInntekt
import java.time.LocalDate

internal sealed class PersistertMerknad {

    @JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type",
    )
    @JsonSubTypes(
        JsonSubTypes.Type(
            value = Beregning.EndringGrunnbeløp::class,
            name = "EndringGrunnbeløp",
        ),
        JsonSubTypes.Type(
            value = Beregning.BeløpErNull::class,
            name = "BeløpErNull",
        ),
        JsonSubTypes.Type(
            value = Beregning.BeløpMellomNullOgToProsentAvHøySats::class,
            name = "BeløpMellomNullOgToProsentAvHøySats",
        ),
        JsonSubTypes.Type(
            value = Beregning.SosialstønadFørerTilBeløpLavereEnnToProsentAvHøySats::class,
            name = "SosialstønadFørerTilBeløpLavereEnnToProsentAvHøySats",
        ),
    )
    sealed class Beregning {

        data class EndringGrunnbeløp(
            val gammeltGrunnbeløp: Detalj,
            val nyttGrunnbeløp: Detalj,
        ) : PersistertMerknad.Beregning() {

            data class Detalj(
                val dato: LocalDate,
                val grunnbeløp: Int,
            )
        }

        object SosialstønadFørerTilBeløpLavereEnnToProsentAvHøySats : PersistertMerknad.Beregning()
        object BeløpMellomNullOgToProsentAvHøySats : PersistertMerknad.Beregning()
        object BeløpErNull : PersistertMerknad.Beregning()

        data class MerknadMånedsberegning(
            val periode: Periode,
            val sats: Sats,
            val grunnbeløp: Int,
            val beløp: Int,
            val fradrag: List<MerknadFradrag>,
            val satsbeløp: Double,
            val fribeløpForEps: Double,
        )

        data class MerknadFradrag(
            val periode: Periode,
            val fradragstype: Fradragstype,
            val månedsbeløp: Double,
            val utenlandskInntekt: UtenlandskInntekt?,
            val tilhører: FradragTilhører,
        )
    }
}

internal fun List<Merknad.Beregning>.toSnapshot(): List<PersistertMerknad.Beregning> {
    return map { it.toSnapshot() }
}

internal fun List<PersistertMerknad.Beregning>.toDomain(): List<Merknad.Beregning> {
    return map { it.toDomain() }
}

internal fun Merknad.Beregning.toSnapshot(): PersistertMerknad.Beregning {
    return when (this) {
        is Merknad.Beregning.EndringGrunnbeløp -> toSnapshot()
        is Merknad.Beregning.BeløpErNull -> toSnapshot()
        is Merknad.Beregning.BeløpMellomNullOgToProsentAvHøySats -> toSnapshot()
        is Merknad.Beregning.SosialstønadFørerTilBeløpLavereEnnToProsentAvHøySats -> toSnapshot()
    }
}

internal fun PersistertMerknad.Beregning.toDomain(): Merknad.Beregning {
    return when (this) {
        is PersistertMerknad.Beregning.EndringGrunnbeløp -> toDomain()
        is PersistertMerknad.Beregning.BeløpErNull -> toDomain()
        is PersistertMerknad.Beregning.BeløpMellomNullOgToProsentAvHøySats -> toDomain()
        is PersistertMerknad.Beregning.SosialstønadFørerTilBeløpLavereEnnToProsentAvHøySats -> toDomain()
    }
}

internal fun Merknad.Beregning.EndringGrunnbeløp.toSnapshot(): PersistertMerknad.Beregning.EndringGrunnbeløp {
    return PersistertMerknad.Beregning.EndringGrunnbeløp(
        gammeltGrunnbeløp = gammeltGrunnbeløp.toSnapshot(),
        nyttGrunnbeløp = nyttGrunnbeløp.toSnapshot(),
    )
}

internal fun PersistertMerknad.Beregning.EndringGrunnbeløp.toDomain(): Merknad.Beregning.EndringGrunnbeløp {
    return Merknad.Beregning.EndringGrunnbeløp(
        gammeltGrunnbeløp = gammeltGrunnbeløp.toDomain(),
        nyttGrunnbeløp = nyttGrunnbeløp.toDomain(),
    )
}

internal fun Merknad.Beregning.BeløpErNull.toSnapshot(): PersistertMerknad.Beregning.BeløpErNull {
    return PersistertMerknad.Beregning.BeløpErNull
}

internal fun Merknad.Beregning.BeløpMellomNullOgToProsentAvHøySats.toSnapshot(): PersistertMerknad.Beregning.BeløpMellomNullOgToProsentAvHøySats {
    return PersistertMerknad.Beregning.BeløpMellomNullOgToProsentAvHøySats
}

internal fun Merknad.Beregning.SosialstønadFørerTilBeløpLavereEnnToProsentAvHøySats.toSnapshot(): PersistertMerknad.Beregning.SosialstønadFørerTilBeløpLavereEnnToProsentAvHøySats {
    return PersistertMerknad.Beregning.SosialstønadFørerTilBeløpLavereEnnToProsentAvHøySats
}

internal fun PersistertMerknad.Beregning.BeløpErNull.toDomain(): Merknad.Beregning.BeløpErNull {
    return Merknad.Beregning.BeløpErNull
}

internal fun PersistertMerknad.Beregning.BeløpMellomNullOgToProsentAvHøySats.toDomain(): Merknad.Beregning.BeløpMellomNullOgToProsentAvHøySats {
    return Merknad.Beregning.BeløpMellomNullOgToProsentAvHøySats
}

internal fun PersistertMerknad.Beregning.SosialstønadFørerTilBeløpLavereEnnToProsentAvHøySats.toDomain(): Merknad.Beregning.SosialstønadFørerTilBeløpLavereEnnToProsentAvHøySats {
    return Merknad.Beregning.SosialstønadFørerTilBeløpLavereEnnToProsentAvHøySats
}

internal fun Merknad.Beregning.EndringGrunnbeløp.Detalj.toSnapshot(): PersistertMerknad.Beregning.EndringGrunnbeløp.Detalj {
    return PersistertMerknad.Beregning.EndringGrunnbeløp.Detalj(
        dato = dato,
        grunnbeløp = grunnbeløp,
    )
}

internal fun PersistertMerknad.Beregning.EndringGrunnbeløp.Detalj.toDomain(): Merknad.Beregning.EndringGrunnbeløp.Detalj {
    return Merknad.Beregning.EndringGrunnbeløp.Detalj(
        dato = dato,
        grunnbeløp = grunnbeløp,
    )
}

internal fun Merknad.Beregning.MerknadMånedsberegning.toSnapshot(): PersistertMerknad.Beregning.MerknadMånedsberegning {
    return PersistertMerknad.Beregning.MerknadMånedsberegning(
        periode = periode,
        sats = sats,
        grunnbeløp = grunnbeløp,
        beløp = beløp,
        fradrag = fradrag.map { it.toSnapshot() },
        satsbeløp = satsbeløp,
        fribeløpForEps = fribeløpForEps,
    )
}

internal fun PersistertMerknad.Beregning.MerknadMånedsberegning.toDomain(): Merknad.Beregning.MerknadMånedsberegning {
    return Merknad.Beregning.MerknadMånedsberegning(
        periode = periode,
        sats = sats,
        grunnbeløp = grunnbeløp,
        beløp = beløp,
        fradrag = fradrag.map { it.toDomain() },
        satsbeløp = satsbeløp,
        fribeløpForEps = fribeløpForEps,
    )
}

internal fun Merknad.Beregning.MerknadFradrag.toSnapshot(): PersistertMerknad.Beregning.MerknadFradrag {
    return PersistertMerknad.Beregning.MerknadFradrag(
        periode = periode,
        fradragstype = fradragstype,
        månedsbeløp = månedsbeløp,
        utenlandskInntekt = utenlandskInntekt,
        tilhører = tilhører,
    )
}

internal fun PersistertMerknad.Beregning.MerknadFradrag.toDomain(): Merknad.Beregning.MerknadFradrag {
    return Merknad.Beregning.MerknadFradrag(
        periode = periode,
        fradragstype = fradragstype,
        månedsbeløp = månedsbeløp,
        utenlandskInntekt = utenlandskInntekt,
        tilhører = tilhører,
    )
}
