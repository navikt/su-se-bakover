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

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type",
)
@JsonSubTypes(
    JsonSubTypes.Type(value = PersistertMerknad.EndringGrunnbeløp::class, name = "EndringGrunnbeløp"),
    JsonSubTypes.Type(value = PersistertMerknad.ØktYtelse::class, name = "ØktYtelse"),
    JsonSubTypes.Type(value = PersistertMerknad.RedusertYtelse::class, name = "RedusertYtelse"),
    JsonSubTypes.Type(value = PersistertMerknad.EndringUnderTiProsent::class, name = "EndringUnderTiProsent"),
    JsonSubTypes.Type(value = PersistertMerknad.NyYtelse::class, name = "NyYtelse"),
)
internal sealed class PersistertMerknad {

    data class EndringGrunnbeløp(
        val gammeltGrunnbeløp: Detalj,
        val nyttGrunnbeløp: Detalj,
    ) : PersistertMerknad() {

        data class Detalj(
            val dato: LocalDate,
            val grunnbeløp: Int,
        )
    }

    data class NyYtelse(
        val benyttetBeregning: MerknadMånedsberegning,
    ) : PersistertMerknad()

    data class ØktYtelse(
        val benyttetBeregning: MerknadMånedsberegning,
        val forkastetBeregning: MerknadMånedsberegning,
    ) : PersistertMerknad()

    data class RedusertYtelse(
        val benyttetBeregning: MerknadMånedsberegning,
        val forkastetBeregning: MerknadMånedsberegning,
    ) : PersistertMerknad()

    data class EndringUnderTiProsent(
        val benyttetBeregning: MerknadMånedsberegning,
        val forkastetBeregning: MerknadMånedsberegning,
    ) : PersistertMerknad()

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

internal fun List<Merknad>.toSnapshot(): List<PersistertMerknad> {
    return map { it.toSnapshot() }
}

internal fun List<PersistertMerknad>.toDomain(): List<Merknad> {
    return map { it.toDomain() }
}

internal fun Merknad.toSnapshot(): PersistertMerknad {
    return when (this) {
        is Merknad.EndringGrunnbeløp -> toSnapshot()
        is Merknad.EndringUnderTiProsent -> toSnapshot()
        is Merknad.NyYtelse -> toSnapshot()
        is Merknad.RedusertYtelse -> toSnapshot()
        is Merknad.ØktYtelse -> toSnapshot()
    }
}

internal fun PersistertMerknad.toDomain(): Merknad {
    return when (this) {
        is PersistertMerknad.EndringGrunnbeløp -> toDomain()
        is PersistertMerknad.EndringUnderTiProsent -> toDomain()
        is PersistertMerknad.NyYtelse -> toDomain()
        is PersistertMerknad.RedusertYtelse -> toDomain()
        is PersistertMerknad.ØktYtelse -> toDomain()
    }
}

internal fun Merknad.EndringGrunnbeløp.toSnapshot(): PersistertMerknad.EndringGrunnbeløp {
    return PersistertMerknad.EndringGrunnbeløp(
        gammeltGrunnbeløp = gammeltGrunnbeløp.toSnapshot(),
        nyttGrunnbeløp = nyttGrunnbeløp.toSnapshot(),
    )
}

internal fun PersistertMerknad.EndringGrunnbeløp.toDomain(): Merknad.EndringGrunnbeløp {
    return Merknad.EndringGrunnbeløp(
        gammeltGrunnbeløp = gammeltGrunnbeløp.toDomain(),
        nyttGrunnbeløp = nyttGrunnbeløp.toDomain(),
    )
}

internal fun Merknad.EndringGrunnbeløp.Detalj.toSnapshot(): PersistertMerknad.EndringGrunnbeløp.Detalj {
    return PersistertMerknad.EndringGrunnbeløp.Detalj(
        dato = dato,
        grunnbeløp = grunnbeløp,
    )
}

internal fun PersistertMerknad.EndringGrunnbeløp.Detalj.toDomain(): Merknad.EndringGrunnbeløp.Detalj {
    return Merknad.EndringGrunnbeløp.Detalj(
        dato = dato,
        grunnbeløp = grunnbeløp,
    )
}

internal fun Merknad.EndringUnderTiProsent.toSnapshot(): PersistertMerknad.EndringUnderTiProsent {
    return PersistertMerknad.EndringUnderTiProsent(
        benyttetBeregning = benyttetBeregning.toSnapshot(),
        forkastetBeregning = forkastetBeregning.toSnapshot(),
    )
}

internal fun PersistertMerknad.EndringUnderTiProsent.toDomain(): Merknad.EndringUnderTiProsent {
    return Merknad.EndringUnderTiProsent(
        benyttetBeregning = benyttetBeregning.toDomain(),
        forkastetBeregning = forkastetBeregning.toDomain(),
    )
}

internal fun Merknad.NyYtelse.toSnapshot(): PersistertMerknad.NyYtelse {
    return PersistertMerknad.NyYtelse(
        benyttetBeregning = benyttetBeregning.toSnapshot(),
    )
}

internal fun PersistertMerknad.NyYtelse.toDomain(): Merknad.NyYtelse {
    return Merknad.NyYtelse(
        benyttetBeregning = benyttetBeregning.toDomain(),
    )
}

internal fun Merknad.RedusertYtelse.toSnapshot(): PersistertMerknad.RedusertYtelse {
    return PersistertMerknad.RedusertYtelse(
        benyttetBeregning = benyttetBeregning.toSnapshot(),
        forkastetBeregning = forkastetBeregning.toSnapshot(),
    )
}

internal fun PersistertMerknad.RedusertYtelse.toDomain(): Merknad.RedusertYtelse {
    return Merknad.RedusertYtelse(
        benyttetBeregning = benyttetBeregning.toDomain(),
        forkastetBeregning = forkastetBeregning.toDomain(),
    )
}

internal fun Merknad.ØktYtelse.toSnapshot(): PersistertMerknad.ØktYtelse {
    return PersistertMerknad.ØktYtelse(
        benyttetBeregning = benyttetBeregning.toSnapshot(),
        forkastetBeregning = forkastetBeregning.toSnapshot(),
    )
}

internal fun PersistertMerknad.ØktYtelse.toDomain(): Merknad.ØktYtelse {
    return Merknad.ØktYtelse(
        benyttetBeregning = benyttetBeregning.toDomain(),
        forkastetBeregning = forkastetBeregning.toDomain(),
    )
}

internal fun Merknad.MerknadMånedsberegning.toSnapshot(): PersistertMerknad.MerknadMånedsberegning {
    return PersistertMerknad.MerknadMånedsberegning(
        periode = periode,
        sats = sats,
        grunnbeløp = grunnbeløp,
        beløp = beløp,
        fradrag = fradrag.map { it.toSnapshot() },
        satsbeløp = satsbeløp,
        fribeløpForEps = fribeløpForEps,
    )
}

internal fun PersistertMerknad.MerknadMånedsberegning.toDomain(): Merknad.MerknadMånedsberegning {
    return Merknad.MerknadMånedsberegning(
        periode = periode,
        sats = sats,
        grunnbeløp = grunnbeløp,
        beløp = beløp,
        fradrag = fradrag.map { it.toDomain() },
        satsbeløp = satsbeløp,
        fribeløpForEps = fribeløpForEps,
    )
}

internal fun Merknad.MerknadFradrag.toSnapshot(): PersistertMerknad.MerknadFradrag {
    return PersistertMerknad.MerknadFradrag(
        periode = periode,
        fradragstype = fradragstype,
        månedsbeløp = månedsbeløp,
        utenlandskInntekt = utenlandskInntekt,
        tilhører = tilhører,
    )
}

internal fun PersistertMerknad.MerknadFradrag.toDomain(): Merknad.MerknadFradrag {
    return Merknad.MerknadFradrag(
        periode = periode,
        fradragstype = fradragstype,
        månedsbeløp = månedsbeløp,
        utenlandskInntekt = utenlandskInntekt,
        tilhører = tilhører,
    )
}
