package no.nav.su.se.bakover.web.routes.behandling

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.domain.Behandlingsperiode
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.routes.behandling.beregning.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.behandling.beregning.StønadsperiodeJson

internal data class BehandlingsperiodeJson(
    val periode: StønadsperiodeJson,
    val begrunnelse: String,
) {
    fun toDomain(): Either<Resultat, Behandlingsperiode> {
        periode.toStønadsperiode().getOrHandle {
            return it.left()
        }.let {
            return Behandlingsperiode(it.periode, begrunnelse).right()
        }
    }
}

internal fun Behandlingsperiode.toJson() = BehandlingsperiodeJson(
    periode = StønadsperiodeJson(periode.toJson()), begrunnelse = begrunnelse,
)
