package no.nav.su.se.bakover.service.revurdering

import arrow.core.Either
import arrow.core.Nel
import arrow.core.left
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.grunnlag.Formuegrunnlag
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import java.util.UUID

data class LeggTilFormuegrunnlagRequest(
    val revurderingId: UUID,
    val elementer: Nel<Element>,
) {
    fun toDomain(): Either<KunneIkkeLeggeTilFormuegrunnlag, Vilkår.Formue.Vurdert> {
        return Vilkår.Formue.Vurdert.tryCreate(
            grunnlag = elementer.map {
                Formuegrunnlag.tryCreate(
                    periode = it.periode,
                    epsFormue = it.epsFormue,
                    søkersFormue = it.søkersFormue,
                    begrunnelse = it.begrunnelse,
                )
            },
        ).mapLeft {
            when (it) {
                Vilkår.Formue.Vurdert.UgyldigFormuevilkår.OverlappendeVurderingsperioder -> return KunneIkkeLeggeTilFormuegrunnlag.IkkeLovMedOverlappendePerioder.left()
            }
        }
    }

    data class Element(
        val periode: Periode,
        val epsFormue: Formuegrunnlag.Verdier?,
        val søkersFormue: Formuegrunnlag.Verdier,
        val begrunnelse: String?,
    )
}
