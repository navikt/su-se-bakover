package no.nav.su.se.bakover.domain.vilkår.formue

import arrow.core.Either
import arrow.core.Nel
import arrow.core.getOrElse
import arrow.core.left
import no.nav.su.se.bakover.common.domain.BehandlingsId
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingsHandling
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandlingshendelse
import vilkår.formue.domain.FormueVilkår
import vilkår.formue.domain.FormuegrenserFactory
import vilkår.formue.domain.Formuegrunnlag
import vilkår.formue.domain.KunneIkkeLageFormueGrunnlag
import vilkår.formue.domain.Verdier
import vilkår.vurderinger.domain.Konsistensproblem

data class LeggTilFormuevilkårRequest(
    val behandlingId: BehandlingsId,
    val formuegrunnlag: Nel<Grunnlag>,
    val saksbehandler: NavIdentBruker.Saksbehandler,
    val tidspunkt: Tidspunkt,
) {
    fun toDomain(
        behandlingsperiode: Periode,
        formuegrenserFactory: FormuegrenserFactory,
    ): Either<KunneIkkeMappeTilDomenet, FormueVilkår.Vurdert> {
        return FormueVilkår.Vurdert.tryCreateFromGrunnlag(
            grunnlag = formuegrunnlag.map { element ->
                // TODO: Denne bør sende commanden til grunnlagsdataOgVilkårsvurderinger som utfører bosituasjonsverifiseringen.
                element.måInnhenteMerInformasjon to Formuegrunnlag.tryCreate(
                    opprettet = tidspunkt,
                    periode = element.periode,
                    epsFormue = element.epsFormue,
                    søkersFormue = element.søkersFormue,
                    behandlingsPeriode = behandlingsperiode,
                ).getOrElse {
                    return when (it) {
                        KunneIkkeLageFormueGrunnlag.FormuePeriodeErUtenforBehandlingsperioden -> {
                            KunneIkkeMappeTilDomenet.FormuePeriodeErUtenforBehandlingsperioden
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
        data object FormuePeriodeErUtenforBehandlingsperioden : KunneIkkeMappeTilDomenet
        data class Konsistenssjekk(val feil: Konsistensproblem.BosituasjonOgFormue) : KunneIkkeMappeTilDomenet
        data object IkkeLovMedOverlappendePerioder : KunneIkkeMappeTilDomenet
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
        val epsFormue: Verdier?
        val søkersFormue: Verdier
        val begrunnelse: String?
        val måInnhenteMerInformasjon: Boolean

        data class Søknadsbehandling(
            override val periode: Periode,
            override val epsFormue: Verdier?,
            override val søkersFormue: Verdier,
            override val begrunnelse: String?,
            override val måInnhenteMerInformasjon: Boolean,
        ) : Grunnlag

        data class Revurdering(
            override val periode: Periode,
            override val epsFormue: Verdier?,
            override val søkersFormue: Verdier,
            override val begrunnelse: String?,

        ) : Grunnlag {
            override val måInnhenteMerInformasjon = false
        }
    }
}
