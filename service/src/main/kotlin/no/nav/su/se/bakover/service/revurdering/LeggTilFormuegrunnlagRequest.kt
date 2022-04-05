package no.nav.su.se.bakover.service.revurdering

import arrow.core.Either
import arrow.core.Nel
import arrow.core.getOrHandle
import arrow.core.left
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.grunnlag.Formuegrunnlag
import no.nav.su.se.bakover.domain.grunnlag.Konsistensproblem
import no.nav.su.se.bakover.domain.grunnlag.KunneIkkeLageFormueGrunnlag
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import java.time.Clock
import java.util.UUID

data class LeggTilFormuegrunnlagRequest(
    val revurderingId: UUID,
    val formuegrunnlag: Nel<Grunnlag>,
) {
    fun toDomain(
        bosituasjon: List<no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Bosituasjon.Fullstendig>,
        behandlingsperiode: Periode,
        clock: Clock,
    ): Either<KunneIkkeLeggeTilFormuegrunnlag, Vilkår.Formue.Vurdert> {
        return Vilkår.Formue.Vurdert.tryCreateFromGrunnlag(
            grunnlag = formuegrunnlag.map { element ->
                Formuegrunnlag.tryCreate(
                    opprettet = Tidspunkt.now(clock),
                    periode = element.periode,
                    epsFormue = element.epsFormue,
                    søkersFormue = element.søkersFormue,
                    begrunnelse = element.begrunnelse,
                    bosituasjon = bosituasjon,
                    behandlingsPeriode = behandlingsperiode,
                ).getOrHandle {
                    return when (it) {
                        KunneIkkeLageFormueGrunnlag.FormuePeriodeErUtenforBehandlingsperioden -> {
                            KunneIkkeLeggeTilFormuegrunnlag.FormuePeriodeErUtenforBehandlingsperioden
                        }
                        is KunneIkkeLageFormueGrunnlag.Konsistenssjekk -> {
                            when (it.feil) {
                                Konsistensproblem.BosituasjonOgFormue.PerioderForBosituasjonEPSOgFormueEPSSamsvarerIkke -> {
                                    KunneIkkeLeggeTilFormuegrunnlag.EpsFormueperiodeErUtenforBosituasjonPeriode
                                }
                                Konsistensproblem.BosituasjonOgFormue.PerioderForFormueErUtenforPerioderMedBostiuasjon -> {
                                    KunneIkkeLeggeTilFormuegrunnlag.FormuePeriodeErUtenforBehandlingsperioden
                                }
                                is Konsistensproblem.BosituasjonOgFormue.UgyldigBosituasjon -> {
                                    throw IllegalStateException(it.toString())
                                }
                            }
                        }
                    }.left()
                }
            },
        ).mapLeft {
            when (it) {
                Vilkår.Formue.Vurdert.UgyldigFormuevilkår.OverlappendeVurderingsperioder -> return KunneIkkeLeggeTilFormuegrunnlag.IkkeLovMedOverlappendePerioder.left()
            }
        }
    }

    data class Grunnlag(
        val periode: Periode,
        val epsFormue: Formuegrunnlag.Verdier?,
        val søkersFormue: Formuegrunnlag.Verdier,
        val begrunnelse: String?,
    )
}
