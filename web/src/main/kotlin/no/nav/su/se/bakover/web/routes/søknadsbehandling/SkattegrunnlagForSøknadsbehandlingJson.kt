package no.nav.su.se.bakover.web.routes.søknadsbehandling

import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingMedSkattegrunnlag
import no.nav.su.se.bakover.web.routes.skatt.SkattegrunnlagJSON
import no.nav.su.se.bakover.web.routes.skatt.SkattegrunnlagJSON.Companion.toJSON

internal data class SkattegrunnlagForSøknadsbehandlingJson(
    val skatteoppslagSøker: SkattegrunnlagJSON,
    val skatteoppslagEps: SkattegrunnlagJSON?,
) {
    companion object {
        fun SøknadsbehandlingMedSkattegrunnlag.toJson(): String = SkattegrunnlagForSøknadsbehandlingJson(
            skatteoppslagSøker = this.søker.toJSON(),
            skatteoppslagEps = this.eps?.toJSON(),
        ).let { serialize(it) }
    }
}
