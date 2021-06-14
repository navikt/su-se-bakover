package no.nav.su.se.bakover.service.revurdering

import arrow.core.Either
import arrow.core.Nel
import arrow.core.getOrHandle
import arrow.core.left
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.grunnlag.Formuegrunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.KunneIkkeLageFormueGrunnlag
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import java.util.UUID

data class LeggTilFormuegrunnlagRequest(
    val revurderingId: UUID,
    val elementer: Nel<Element>,
) {
    fun toDomain(bosituasjon: Grunnlag.Bosituasjon.Fullstendig, behandlingsperiode: Periode): Either<KunneIkkeLeggeTilFormuegrunnlag, Vilkår.Formue.Vurdert> {
        return Vilkår.Formue.Vurdert.tryCreate(
            grunnlag = elementer.map { element ->
                Formuegrunnlag.tryCreate(
                    periode = element.periode,
                    epsFormue = element.epsFormue,
                    søkersFormue = element.søkersFormue,
                    begrunnelse = element.begrunnelse,
                    bosituasjon = bosituasjon,
                    behandlingsPeriode = behandlingsperiode,
                ).getOrHandle {
                    return when (it) {
                        KunneIkkeLageFormueGrunnlag.EpsFormueperiodeErUtenforBosituasjonPeriode -> KunneIkkeLeggeTilFormuegrunnlag.EpsFormueperiodeErUtenforBosituasjonPeriode.left()
                        KunneIkkeLageFormueGrunnlag.MåHaEpsHvisManHarSattEpsFormue -> KunneIkkeLeggeTilFormuegrunnlag.MåHaEpsHvisManHarSattEpsFormue.left()
                        KunneIkkeLageFormueGrunnlag.FormuePeriodeErUtenforBehandlingsperioden -> KunneIkkeLeggeTilFormuegrunnlag.FormuePeriodeErUtenforBehandlingsperioden.left()
                    }
                }
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
