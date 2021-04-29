package no.nav.su.se.bakover.web.routes.søknadsbehandling

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.domain.ValgtStønadsperiode
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.StønadsperiodeJson

internal data class ValgtStønadsperiodeJson(
    val periode: StønadsperiodeJson,
    val begrunnelse: String,
) {
    fun toDomain(): Either<Resultat, ValgtStønadsperiode> {
        periode.toStønadsperiode().getOrHandle {
            return it.left()
        }.let {
            return ValgtStønadsperiode(it.periode, begrunnelse).right()
        }
    }
}

internal fun ValgtStønadsperiode.toJson() = ValgtStønadsperiodeJson(
    periode = StønadsperiodeJson(periode.toJson()), begrunnelse = begrunnelse,
)
