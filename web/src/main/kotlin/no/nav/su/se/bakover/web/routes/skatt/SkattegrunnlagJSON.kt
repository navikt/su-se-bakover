package no.nav.su.se.bakover.web.routes.skatt

import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.database.common.YearRangeJson
import no.nav.su.se.bakover.database.common.YearRangeJson.Companion.toYearRangeJson
import no.nav.su.se.bakover.web.routes.skatt.StadieJson.Companion.toJson
import vilkår.skatt.domain.Skattegrunnlag

internal data class SkattegrunnlagJSON(
    val fnr: String,
    val hentetTidspunkt: String,
    val årsgrunnlag: List<StadieJson>,
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
