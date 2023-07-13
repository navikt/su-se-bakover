package no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.su.se.bakover.domain.beregning.Merknad

internal fun List<Merknad.Beregning>.toJson() = map { it.toJson() }

internal fun Merknad.Beregning.toJson(): MerknadJson.BeregningJson {
    return when (this) {
        is Merknad.Beregning.Avslag.BeløpErNull -> toJson()
        is Merknad.Beregning.Avslag.BeløpMellomNullOgToProsentAvHøySats -> toJson()
        is Merknad.Beregning.SosialstønadFørerTilBeløpLavereEnnToProsentAvHøySats -> toJson()
        is Merknad.Beregning.AvkortingFørerTilBeløpLavereEnnToProsentAvHøySats -> toJson()
    }
}

internal fun Merknad.Beregning.Avslag.BeløpErNull.toJson() = MerknadJson.BeregningJson.BeløpErNullJson
internal fun Merknad.Beregning.Avslag.BeløpMellomNullOgToProsentAvHøySats.toJson() =
    MerknadJson.BeregningJson.BeløpMellomNullOgToProsentAvHøySatsJson

internal fun Merknad.Beregning.SosialstønadFørerTilBeløpLavereEnnToProsentAvHøySats.toJson() =
    MerknadJson.BeregningJson.SosialstønadFørerTilBeløpLavereEnnToProsentAvHøySatsJson

internal fun Merknad.Beregning.AvkortingFørerTilBeløpLavereEnnToProsentAvHøySats.toJson() =
    MerknadJson.BeregningJson.AvkortingFørerTilBeløpLavereEnnToProsentAvHøySatsJson

internal sealed class MerknadJson {

    @JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type",
    )
    @JsonSubTypes(
        JsonSubTypes.Type(
            value = BeregningJson.BeløpErNullJson::class,
            name = "BeløpErNull",
        ),
        JsonSubTypes.Type(
            value = BeregningJson.BeløpMellomNullOgToProsentAvHøySatsJson::class,
            name = "BeløpMellomNullOgToProsentAvHøySats",
        ),
        JsonSubTypes.Type(
            value = BeregningJson.SosialstønadFørerTilBeløpLavereEnnToProsentAvHøySatsJson::class,
            name = "SosialstønadFørerTilBeløpLavereEnnToProsentAvHøySats",
        ),
        JsonSubTypes.Type(
            value = BeregningJson.AvkortingFørerTilBeløpLavereEnnToProsentAvHøySatsJson::class,
            name = "AvkortingFørerTilBeløpLavereEnnToProsentAvHøySats",
        ),
    )
    sealed class BeregningJson {
        data object SosialstønadFørerTilBeløpLavereEnnToProsentAvHøySatsJson : MerknadJson.BeregningJson()
        data object AvkortingFørerTilBeløpLavereEnnToProsentAvHøySatsJson : MerknadJson.BeregningJson()
        data object BeløpMellomNullOgToProsentAvHøySatsJson : MerknadJson.BeregningJson()
        data object BeløpErNullJson : MerknadJson.BeregningJson()
    }
}
