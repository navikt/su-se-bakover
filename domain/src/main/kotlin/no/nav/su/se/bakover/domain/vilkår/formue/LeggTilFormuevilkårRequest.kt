package no.nav.su.se.bakover.domain.vilkår.formue

import arrow.core.Either
import arrow.core.Nel
import arrow.core.getOrElse
import arrow.core.left
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.grunnlag.Formuegrunnlag
import no.nav.su.se.bakover.domain.grunnlag.Konsistensproblem
import no.nav.su.se.bakover.domain.grunnlag.KunneIkkeLageFormueGrunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingsHandling
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandlingshendelse
import no.nav.su.se.bakover.domain.vilkår.FormueVilkår
import no.nav.su.se.bakover.domain.vilkår.FormuegrenserFactory
import java.util.UUID

data class LeggTilFormuevilkårRequest(
    val behandlingId: UUID,
    val formuegrunnlag: Nel<Grunnlag>,
    val saksbehandler: NavIdentBruker.Saksbehandler,
    val tidspunkt: Tidspunkt,
) {
    fun toDomain(
        bosituasjon: List<no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Bosituasjon>,
        behandlingsperiode: Periode,
        formuegrenserFactory: FormuegrenserFactory,
    ): Either<KunneIkkeMappeTilDomenet, FormueVilkår.Vurdert> {
        return FormueVilkår.Vurdert.tryCreateFromGrunnlag(
            grunnlag = formuegrunnlag.map { element ->
                element.måInnhenteMerInformasjon to Formuegrunnlag.tryCreate(
                    opprettet = tidspunkt,
                    periode = element.periode,
                    epsFormue = element.epsFormue,
                    søkersFormue = element.søkersFormue,
                    bosituasjon = bosituasjon,
                    behandlingsPeriode = behandlingsperiode,
                ).getOrElse {
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
                FormueVilkår.Vurdert.UgyldigFormuevilkår.OverlappendeVurderingsperioder -> return KunneIkkeMappeTilDomenet.IkkeLovMedOverlappendePerioder.left()
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
        formuegrenserFactory: FormuegrenserFactory,
    ): Either<KunneIkkeMappeTilDomenet, FormueVilkår.Vurdert> {
        return FormueVilkår.Vurdert.tryCreateFromGrunnlag(
            grunnlag = formuegrunnlag.map { element ->
                element.måInnhenteMerInformasjon to Formuegrunnlag.tryCreate(
                    opprettet = tidspunkt,
                    periode = element.periode,
                    epsFormue = element.epsFormue,
                    søkersFormue = element.søkersFormue,
                    bosituasjon = bosituasjon,
                    behandlingsPeriode = behandlingsperiode,
                ).getOrElse {
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
                FormueVilkår.Vurdert.UgyldigFormuevilkår.OverlappendeVurderingsperioder -> return KunneIkkeMappeTilDomenet.IkkeLovMedOverlappendePerioder.left()
            }
        }
    }

    fun handling(tidspunkt: Tidspunkt): Søknadsbehandlingshendelse {
        return Søknadsbehandlingshendelse(
            tidspunkt = tidspunkt,
            saksbehandler = saksbehandler,
            handling = SøknadsbehandlingsHandling.OppdatertFormue,
        )
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
