package no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.su.se.bakover.domain.beregning.Merknad
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.UtenlandskInntektJson.Companion.toJson
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

internal fun List<Merknad>.toJson() = map { it.toJson() }

internal fun Merknad.toJson(): MerknadJson {
    return when (this) {
        is Merknad.EndringGrunnbeløp -> this.toJson()
        is Merknad.RedusertYtelse -> this.toJson()
        is Merknad.EndringUnderTiProsent -> this.toJson()
        is Merknad.ØktYtelse -> this.toJson()
        is Merknad.NyYtelse -> this.toJson()
    }
}

internal fun Merknad.EndringGrunnbeløp.toJson() = MerknadJson.EndringGrunnbeløpJson(
    gammeltGrunnbeløp = MerknadJson.EndringGrunnbeløpJson.DetaljJson(
        dato = this.gammeltGrunnbeløp.dato.toString(),
        grunnbeløp = this.gammeltGrunnbeløp.grunnbeløp,
    ),
    nyttGrunnbeløp = MerknadJson.EndringGrunnbeløpJson.DetaljJson(
        dato = this.nyttGrunnbeløp.dato.toString(),
        grunnbeløp = this.nyttGrunnbeløp.grunnbeløp,
    ),
)

internal fun Merknad.RedusertYtelse.toJson() = MerknadJson.RedusertYtelseJson(
    benyttetBeregning = benyttetBeregning.toJson(),
    forkastetBeregning = forkastetBeregning.toJson(),
)

internal fun Merknad.ØktYtelse.toJson() = MerknadJson.ØktYtelseJson(
    benyttetBeregning = benyttetBeregning.toJson(),
    forkastetBeregning = forkastetBeregning.toJson(),
)

internal fun Merknad.EndringUnderTiProsent.toJson() = MerknadJson.EndringUnderTiProsentJson(
    benyttetBeregning = benyttetBeregning.toJson(),
    forkastetBeregning = forkastetBeregning.toJson(),
)

internal fun Merknad.NyYtelse.toJson() = MerknadJson.NyYtelseJson(
    benyttetBeregning = benyttetBeregning.toJson(),
)

internal fun Merknad.MerknadMånedsberegning.toJson() = MerknadJson.MerknadMånedsberegningJson(
    fraOgMed = periode.fraOgMed.format(DateTimeFormatter.ISO_DATE),
    tilOgMed = periode.tilOgMed.format(DateTimeFormatter.ISO_DATE),
    sats = sats.name,
    grunnbeløp = grunnbeløp,
    beløp = beløp,
    fradrag = fradrag.map { it.toJson() },
    satsbeløp = satsbeløp.roundToInt(),
    fribeløpForEps = fribeløpForEps,
)

internal fun Merknad.MerknadFradrag.toJson() = MerknadJson.MerknadFradragJson(
    periode = periode.toJson(),
    type = fradragstype.toString(),
    beløp = månedsbeløp,
    utenlandskInntekt = utenlandskInntekt?.toJson(),
    tilhører = tilhører.toString(),
)

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type",
)
@JsonSubTypes(
    JsonSubTypes.Type(value = MerknadJson.EndringGrunnbeløpJson::class, name = "EndringGrunnbeløp"),
    JsonSubTypes.Type(value = MerknadJson.ØktYtelseJson::class, name = "ØktYtelse"),
    JsonSubTypes.Type(value = MerknadJson.RedusertYtelseJson::class, name = "RedusertYtelse"),
    JsonSubTypes.Type(value = MerknadJson.EndringUnderTiProsentJson::class, name = "EndringUnderTiProsent"),
    JsonSubTypes.Type(value = MerknadJson.NyYtelseJson::class, name = "NyYtelse"),
)
internal sealed class MerknadJson {

    data class EndringGrunnbeløpJson(
        val gammeltGrunnbeløp: DetaljJson,
        val nyttGrunnbeløp: DetaljJson,
    ) : MerknadJson() {

        data class DetaljJson(
            val dato: String,
            val grunnbeløp: Int,
        )
    }

    data class ØktYtelseJson(
        val benyttetBeregning: MerknadMånedsberegningJson,
        val forkastetBeregning: MerknadMånedsberegningJson,
    ) : MerknadJson()

    data class RedusertYtelseJson(
        val benyttetBeregning: MerknadMånedsberegningJson,
        val forkastetBeregning: MerknadMånedsberegningJson,
    ) : MerknadJson()

    data class EndringUnderTiProsentJson(
        val benyttetBeregning: MerknadMånedsberegningJson,
        val forkastetBeregning: MerknadMånedsberegningJson,
    ) : MerknadJson()

    data class NyYtelseJson(
        val benyttetBeregning: MerknadMånedsberegningJson,
    ) : MerknadJson()

    data class MerknadMånedsberegningJson(
        val fraOgMed: String,
        val tilOgMed: String,
        val sats: String,
        val grunnbeløp: Int,
        val beløp: Int,
        val fradrag: List<MerknadFradragJson>,
        val satsbeløp: Int,
        val fribeløpForEps: Double,
    )

    data class MerknadFradragJson(
        val periode: PeriodeJson?,
        val type: String,
        val beløp: Double,
        val utenlandskInntekt: UtenlandskInntektJson?,
        val tilhører: String,
    )
}
