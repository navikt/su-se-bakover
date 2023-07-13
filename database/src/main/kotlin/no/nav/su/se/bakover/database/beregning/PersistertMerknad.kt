package no.nav.su.se.bakover.database.beregning

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.su.se.bakover.domain.beregning.Merknad

internal sealed class PersistertMerknad {

    @JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type",
    )
    @JsonSubTypes(
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
        JsonSubTypes.Type(
            value = Beregning.AvkortingFørerTilBeløpLavereEnnToProsentAvHøySats::class,
            name = "AvkortingFørerTilBeløpLavereEnnToProsentAvHøySats",
        ),
    )
    sealed class Beregning : PersistertMerknad() {
        data object AvkortingFørerTilBeløpLavereEnnToProsentAvHøySats : Beregning()
        data object SosialstønadFørerTilBeløpLavereEnnToProsentAvHøySats : Beregning()
        data object BeløpMellomNullOgToProsentAvHøySats : Beregning()
        data object BeløpErNull : Beregning()
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
        is Merknad.Beregning.Avslag.BeløpErNull -> toSnapshot()
        is Merknad.Beregning.Avslag.BeløpMellomNullOgToProsentAvHøySats -> toSnapshot()
        is Merknad.Beregning.SosialstønadFørerTilBeløpLavereEnnToProsentAvHøySats -> toSnapshot()
        is Merknad.Beregning.AvkortingFørerTilBeløpLavereEnnToProsentAvHøySats -> toSnapshot()
    }
}

internal fun PersistertMerknad.Beregning.toDomain(): Merknad.Beregning {
    return when (this) {
        is PersistertMerknad.Beregning.BeløpErNull -> toDomain()
        is PersistertMerknad.Beregning.BeløpMellomNullOgToProsentAvHøySats -> toDomain()
        is PersistertMerknad.Beregning.SosialstønadFørerTilBeløpLavereEnnToProsentAvHøySats -> toDomain()
        is PersistertMerknad.Beregning.AvkortingFørerTilBeløpLavereEnnToProsentAvHøySats -> toDomain()
    }
}

@Suppress("unused")
internal fun Merknad.Beregning.Avslag.BeløpErNull.toSnapshot(): PersistertMerknad.Beregning.BeløpErNull {
    return PersistertMerknad.Beregning.BeløpErNull
}

@Suppress("unused")
internal fun Merknad.Beregning.Avslag.BeløpMellomNullOgToProsentAvHøySats.toSnapshot(): PersistertMerknad.Beregning.BeløpMellomNullOgToProsentAvHøySats {
    return PersistertMerknad.Beregning.BeløpMellomNullOgToProsentAvHøySats
}

@Suppress("unused")
internal fun Merknad.Beregning.SosialstønadFørerTilBeløpLavereEnnToProsentAvHøySats.toSnapshot(): PersistertMerknad.Beregning.SosialstønadFørerTilBeløpLavereEnnToProsentAvHøySats {
    return PersistertMerknad.Beregning.SosialstønadFørerTilBeløpLavereEnnToProsentAvHøySats
}

@Suppress("unused")
internal fun Merknad.Beregning.AvkortingFørerTilBeløpLavereEnnToProsentAvHøySats.toSnapshot(): PersistertMerknad.Beregning.AvkortingFørerTilBeløpLavereEnnToProsentAvHøySats {
    return PersistertMerknad.Beregning.AvkortingFørerTilBeløpLavereEnnToProsentAvHøySats
}

@Suppress("unused")
internal fun PersistertMerknad.Beregning.BeløpErNull.toDomain(): Merknad.Beregning.Avslag.BeløpErNull {
    return Merknad.Beregning.Avslag.BeløpErNull
}

@Suppress("unused")
internal fun PersistertMerknad.Beregning.BeløpMellomNullOgToProsentAvHøySats.toDomain(): Merknad.Beregning.Avslag.BeløpMellomNullOgToProsentAvHøySats {
    return Merknad.Beregning.Avslag.BeløpMellomNullOgToProsentAvHøySats
}

@Suppress("unused")
internal fun PersistertMerknad.Beregning.SosialstønadFørerTilBeløpLavereEnnToProsentAvHøySats.toDomain(): Merknad.Beregning.SosialstønadFørerTilBeløpLavereEnnToProsentAvHøySats {
    return Merknad.Beregning.SosialstønadFørerTilBeløpLavereEnnToProsentAvHøySats
}

@Suppress("unused")
internal fun PersistertMerknad.Beregning.AvkortingFørerTilBeløpLavereEnnToProsentAvHøySats.toDomain(): Merknad.Beregning.AvkortingFørerTilBeløpLavereEnnToProsentAvHøySats {
    return Merknad.Beregning.AvkortingFørerTilBeløpLavereEnnToProsentAvHøySats
}
