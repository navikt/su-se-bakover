package no.nav.su.se.bakover.web.routes.grunnlag

import no.nav.su.se.bakover.domain.grunnlag.EksterneGrunnlag
import no.nav.su.se.bakover.domain.grunnlag.EksterneGrunnlagSkatt
import no.nav.su.se.bakover.domain.grunnlag.SkattegrunnlagMedId
import no.nav.su.se.bakover.web.routes.grunnlag.EksterneGrunnlagSkattJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.skatt.SkattegrunnlagJSON
import no.nav.su.se.bakover.web.routes.skatt.SkattegrunnlagJSON.Companion.toJSON
import java.util.UUID

internal data class EksterneGrunnlagJson(
    val skatt: EksterneGrunnlagSkattJson?,
) {
    companion object {
        fun EksterneGrunnlag.toJson(): EksterneGrunnlagJson = EksterneGrunnlagJson(
            skatt = this.skatt.toJson(),
        )
    }
}

internal data class EksterneGrunnlagSkattJson(
    val søkers: SkattegrunnlagJSON,
    val eps: SkattegrunnlagJSON?,
) {
    companion object {
        fun EksterneGrunnlagSkatt.toJson(): EksterneGrunnlagSkattJson? = when (this) {
            is EksterneGrunnlagSkatt.Hentet -> EksterneGrunnlagSkattJson(
                søkers = this.søkers.skattegrunnlag.toJSON(),
                eps = this.eps?.skattegrunnlag?.toJSON(),
            )

            EksterneGrunnlagSkatt.IkkeHentet -> null
        }
    }
}

internal data class SkattegrunnlagMedIdJson(
    val id: UUID,
    val skattegrunnlag: SkattegrunnlagJSON,
) {
    companion object {
        fun SkattegrunnlagMedId.toJson(): SkattegrunnlagMedIdJson =
            SkattegrunnlagMedIdJson(id = this.id, skattegrunnlag = this.skattegrunnlag.toJSON())
    }
}
