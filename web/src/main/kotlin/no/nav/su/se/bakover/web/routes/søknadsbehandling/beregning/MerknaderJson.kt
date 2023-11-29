package no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning

import beregning.domain.Merknad
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

internal fun List<Merknad.Beregning>.toJson() = map { it.toJson() }

internal fun Merknad.Beregning.toJson(): MerknadJson.BeregningJson {
    return when (this) {
        is Merknad.Beregning.Avslag.BeløpErNull -> toJson()
        is Merknad.Beregning.Avslag.BeløpMellomNullOgToProsentAvHøySats -> toJson()
        is Merknad.Beregning.SosialstønadFørerTilBeløpLavereEnnToProsentAvHøySats -> toJson()
    }
}

internal fun Merknad.Beregning.Avslag.BeløpErNull.toJson() = MerknadJson.BeregningJson.BeløpErNullJson
internal fun Merknad.Beregning.Avslag.BeløpMellomNullOgToProsentAvHøySats.toJson() =
    MerknadJson.BeregningJson.BeløpMellomNullOgToProsentAvHøySatsJson

internal fun Merknad.Beregning.SosialstønadFørerTilBeløpLavereEnnToProsentAvHøySats.toJson() =
    MerknadJson.BeregningJson.SosialstønadFørerTilBeløpLavereEnnToProsentAvHøySatsJson

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
    )
    sealed interface BeregningJson {
        data object SosialstønadFørerTilBeløpLavereEnnToProsentAvHøySatsJson : BeregningJson
        data object BeløpMellomNullOgToProsentAvHøySatsJson : BeregningJson
        data object BeløpErNullJson : BeregningJson
    }
}
