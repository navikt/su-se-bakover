package no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.su.se.bakover.domain.beregning.Merknad

internal fun List<Merknad.Beregning>.toJson() = map { it.toJson() }

internal fun Merknad.Beregning.toJson(): MerknadJson.BeregningJson {
    return when (this) {
        is Merknad.Beregning.BeløpErNull -> toJson()
        is Merknad.Beregning.BeløpMellomNullOgToProsentAvHøySats -> toJson()
        is Merknad.Beregning.SosialstønadOgAvkortingFørerTilBeløpLavereEnnToProsentAvHøySats -> toJson()
    }
}

internal fun Merknad.Beregning.BeløpErNull.toJson() = MerknadJson.BeregningJson.BeløpErNullJson
internal fun Merknad.Beregning.BeløpMellomNullOgToProsentAvHøySats.toJson() =
    MerknadJson.BeregningJson.BeløpMellomNullOgToProsentAvHøySatsJson

internal fun Merknad.Beregning.SosialstønadOgAvkortingFørerTilBeløpLavereEnnToProsentAvHøySats.toJson() =
    MerknadJson.BeregningJson.SosialstønadFørerTilBeløpLavereEnnToProsentAvHøySatsJson

internal sealed class MerknadJson {

    @JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type",
    )
    @JsonSubTypes(
        JsonSubTypes.Type(
            value = BeregningJson.EndringGrunnbeløpJson::class,
            name = "EndringGrunnbeløp",
        ),
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
    sealed class BeregningJson {

        data class EndringGrunnbeløpJson(
            val gammeltGrunnbeløp: DetaljJson,
            val nyttGrunnbeløp: DetaljJson,
        ) : MerknadJson.BeregningJson() {

            data class DetaljJson(
                val dato: String,
                val grunnbeløp: Int,
            )
        }

        object SosialstønadFørerTilBeløpLavereEnnToProsentAvHøySatsJson : MerknadJson.BeregningJson()
        object BeløpMellomNullOgToProsentAvHøySatsJson : MerknadJson.BeregningJson()
        object BeløpErNullJson : MerknadJson.BeregningJson()
    }
}
