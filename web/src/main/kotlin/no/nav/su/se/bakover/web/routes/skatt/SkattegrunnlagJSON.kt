package no.nav.su.se.bakover.web.routes.skatt

import arrow.core.NonEmptyList
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.database.common.YearRangeJson
import no.nav.su.se.bakover.database.common.YearRangeJson.Companion.toYearRangeJson
import no.nav.su.se.bakover.domain.skatt.Skattegrunnlag
import no.nav.su.se.bakover.web.routes.skatt.StadieJson.Companion.toJson

internal data class SkattegrunnlagJSON(
    val fnr: String,
    val hentetTidspunkt: String,
    val årsgrunnlag: NonEmptyList<StadieJson>,
    val saksbehandler: String,
    val årSpurtFor: YearRangeJson,
) {
    companion object {
        internal fun Skattegrunnlag.toStringifiedJson(): String = serialize(this.toJSON())

        internal fun Skattegrunnlag.toJSON() = SkattegrunnlagJSON(
            fnr = fnr.toString(),
            hentetTidspunkt = hentetTidspunkt.toString(),
            årsgrunnlag = årsgrunnlag.toJson(),
            saksbehandler = saksbehandler.toString(),
            årSpurtFor = årSpurtFor.toYearRangeJson(),
        )
    }
}
