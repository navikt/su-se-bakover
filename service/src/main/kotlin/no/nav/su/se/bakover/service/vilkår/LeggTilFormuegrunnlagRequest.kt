package no.nav.su.se.bakover.service.vilkår

import arrow.core.Either
import arrow.core.Nel
import arrow.core.getOrHandle
import arrow.core.left
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.grunnlag.Formuegrunnlag
import no.nav.su.se.bakover.domain.grunnlag.Konsistensproblem
import no.nav.su.se.bakover.domain.grunnlag.KunneIkkeLageFormueGrunnlag
import no.nav.su.se.bakover.domain.vilkår.FormuegrenserFactory
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import java.time.Clock
import java.util.UUID

data class LeggTilFormuegrunnlagRequest(
    val behandlingId: UUID,
    val formuegrunnlag: Nel<Grunnlag>,
) {
    fun toDomain(
        bosituasjon: List<no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Bosituasjon>,
        behandlingsperiode: Periode,
        clock: Clock,
        formuegrenserFactory: FormuegrenserFactory,
    ): Either<KunneIkkeMappeTilDomenet, Vilkår.Formue.Vurdert> {
        return Vilkår.Formue.Vurdert.tryCreateFromGrunnlag(
            grunnlag = formuegrunnlag.map { element ->
                Formuegrunnlag.tryCreate(
                    opprettet = Tidspunkt.now(clock),
                    periode = element.periode,
                    epsFormue = element.epsFormue,
                    søkersFormue = element.søkersFormue,
                    bosituasjon = bosituasjon,
                    behandlingsPeriode = behandlingsperiode,
                ).getOrHandle {
                    return when (it) {
                        KunneIkkeLageFormueGrunnlag.FormuePeriodeErUtenforBehandlingsperioden -> {
                            KunneIkkeMappeTilDomenet.FormuePeriodeErUtenforBehandlingsperioden
                        }
                        is KunneIkkeLageFormueGrunnlag.Konsistenssjekk -> {
                            // TODO jah: Vi må akseptere at bosituasjonen er ufullstendig på dette tidspunktet.
                            KunneIkkeMappeTilDomenet.Konsistenssjekk(it.feil)
                        }
                    }.left()
                }
            },
            formuegrenserFactory = formuegrenserFactory,
        ).mapLeft {
            when (it) {
                Vilkår.Formue.Vurdert.UgyldigFormuevilkår.OverlappendeVurderingsperioder -> return KunneIkkeMappeTilDomenet.IkkeLovMedOverlappendePerioder.left()
            }
        }
    }

    sealed interface KunneIkkeMappeTilDomenet {
        object FormuePeriodeErUtenforBehandlingsperioden : KunneIkkeMappeTilDomenet
        data class Konsistenssjekk(val feil: Konsistensproblem.BosituasjonOgFormue) : KunneIkkeMappeTilDomenet
        object IkkeLovMedOverlappendePerioder : KunneIkkeMappeTilDomenet
    }

    @JvmName("toDomainFullstendig")
    fun toDomain(
        bosituasjon: List<no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Bosituasjon.Fullstendig>,
        behandlingsperiode: Periode,
        clock: Clock,
        formuegrenserFactory: FormuegrenserFactory,
    ): Either<KunneIkkeMappeTilDomenet, Vilkår.Formue.Vurdert> {
        return Vilkår.Formue.Vurdert.tryCreateFromGrunnlag(
            grunnlag = formuegrunnlag.map { element ->
                Formuegrunnlag.tryCreate(
                    opprettet = Tidspunkt.now(clock),
                    periode = element.periode,
                    epsFormue = element.epsFormue,
                    søkersFormue = element.søkersFormue,
                    bosituasjon = bosituasjon,
                    behandlingsPeriode = behandlingsperiode,
                ).getOrHandle {
                    return when (it) {
                        KunneIkkeLageFormueGrunnlag.FormuePeriodeErUtenforBehandlingsperioden -> {
                            KunneIkkeMappeTilDomenet.FormuePeriodeErUtenforBehandlingsperioden
                        }
                        is KunneIkkeLageFormueGrunnlag.Konsistenssjekk -> {
                            KunneIkkeMappeTilDomenet.Konsistenssjekk(it.feil)
                        }
                    }.left()
                }
            },
            formuegrenserFactory = formuegrenserFactory,
        ).mapLeft {
            when (it) {
                Vilkår.Formue.Vurdert.UgyldigFormuevilkår.OverlappendeVurderingsperioder -> return KunneIkkeMappeTilDomenet.IkkeLovMedOverlappendePerioder.left()
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
