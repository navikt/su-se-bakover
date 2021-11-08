package no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.su.se.bakover.domain.beregning.Merknad
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.UtenlandskInntektJson.Companion.toJson

internal fun List<Merknad.Beregning>.toJson() = map { it.toJson() }

internal fun Merknad.Beregning.toJson(): MerknadJson.BeregningJson {
    return when (this) {
        is Merknad.Beregning.EndringGrunnbeløp -> toJson()
        is Merknad.Beregning.BeløpErNull -> toJson()
        is Merknad.Beregning.BeløpMellomNullOgToProsentAvHøySats -> toJson()
        is Merknad.Beregning.SosialstønadFørerTilBeløpLavereEnnToProsentAvHøySats -> toJson()
    }
}

internal fun Merknad.Beregning.EndringGrunnbeløp.toJson() = MerknadJson.BeregningJson.EndringGrunnbeløpJson(
    gammeltGrunnbeløp = MerknadJson.BeregningJson.EndringGrunnbeløpJson.DetaljJson(
        dato = this.gammeltGrunnbeløp.dato.toString(),
        grunnbeløp = this.gammeltGrunnbeløp.grunnbeløp,
    ),
    nyttGrunnbeløp = MerknadJson.BeregningJson.EndringGrunnbeløpJson.DetaljJson(
        dato = this.nyttGrunnbeløp.dato.toString(),
        grunnbeløp = this.nyttGrunnbeløp.grunnbeløp,
    ),
)

internal fun Merknad.Beregning.BeløpErNull.toJson() = MerknadJson.BeregningJson.BeløpErNullJson
internal fun Merknad.Beregning.BeløpMellomNullOgToProsentAvHøySats.toJson() =
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
