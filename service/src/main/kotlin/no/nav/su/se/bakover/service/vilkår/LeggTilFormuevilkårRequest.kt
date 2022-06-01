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

data class LeggTilFormuevilkårRequest(
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
                element.måInnhenteMerInformasjon to Formuegrunnlag.tryCreate(
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
                element.måInnhenteMerInformasjon to Formuegrunnlag.tryCreate(
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

    sealed interface Grunnlag {
        val periode: Periode
        val epsFormue: Formuegrunnlag.Verdier?
        val søkersFormue: Formuegrunnlag.Verdier
        val begrunnelse: String?
        val måInnhenteMerInformasjon: Boolean

        data class Søknadsbehandling(
            override val periode: Periode,
            override val epsFormue: Formuegrunnlag.Verdier?,
            override val søkersFormue: Formuegrunnlag.Verdier,
            override val begrunnelse: String?,
            override val måInnhenteMerInformasjon: Boolean,
        ) : Grunnlag

        data class Revurdering(
            override val periode: Periode,
            override val epsFormue: Formuegrunnlag.Verdier?,
            override val søkersFormue: Formuegrunnlag.Verdier,
            override val begrunnelse: String?,

        ) : Grunnlag {
            override val måInnhenteMerInformasjon = false
        }
    }
}
