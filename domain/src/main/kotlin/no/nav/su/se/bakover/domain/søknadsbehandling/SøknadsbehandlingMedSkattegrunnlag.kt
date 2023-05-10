package no.nav.su.se.bakover.domain.søknadsbehandling

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.YearRange
import no.nav.su.se.bakover.common.krympTilØvreGrense
import no.nav.su.se.bakover.common.toRange
import no.nav.su.se.bakover.common.toYearRange
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Bosituasjon.Companion.harEPS
import no.nav.su.se.bakover.domain.grunnlag.singleFullstendigEpsOrNull
import no.nav.su.se.bakover.domain.skatt.Skattegrunnlag
import java.time.Clock
import java.time.Year
import java.util.UUID

/**
 * TODO: private constructor - create/tryCreate - søknadsbehandling skal kun være Vilkårsvurdert
 */
data class SøknadsbehandlingMedSkattegrunnlag(
    val søknadsbehandling: Søknadsbehandling,
    val opprettet: Tidspunkt,
    val søker: Skattegrunnlag,
    val eps: Skattegrunnlag?,
) {
    val sakId: UUID = søknadsbehandling.sakId
    val søkersSkatteId: UUID = søknadsbehandling.grunnlagsdata.skattereferanser!!.søkers
    val epsSkatteId: UUID? = søknadsbehandling.grunnlagsdata.skattereferanser?.eps

    fun hentNySkattedata(
        hentSkattegrunnlag: (Fnr, YearRange) -> Skattegrunnlag,
    ): Either<KunneIkkeHenteNySkattedata, SøknadsbehandlingMedSkattegrunnlag> {
        return when (this.søknadsbehandling) {
            is SøknadsbehandlingTilAttestering,
            is IverksattSøknadsbehandling,
            is LukketSøknadsbehandling,
            -> KunneIkkeHenteNySkattedata.UgyldigTilstand.left()

            is VilkårsvurdertSøknadsbehandling,
            is BeregnetSøknadsbehandling,
            is SimulertSøknadsbehandling,
            is UnderkjentSøknadsbehandling,
            -> this.copy(
                søker = hentSkattegrunnlag(søknadsbehandling.fnr, søknadsbehandling.stønadsperiode!!.toYearRange()),
                eps = if (søknadsbehandling.grunnlagsdataOgVilkårsvurderinger.grunnlagsdata.bosituasjon.harEPS()) {
                    hentSkattegrunnlag(
                        søknadsbehandling.grunnlagsdataOgVilkårsvurderinger.grunnlagsdata.bosituasjon.singleFullstendigEpsOrNull()!!.fnr,
                        søknadsbehandling.stønadsperiode!!.toYearRange(),
                    )
                } else {
                    null
                },
            ).right()
        }
    }

    init {
        søknadsbehandling.grunnlagsdata.skattereferanser.let {
            require(it?.søkers != null) {
                "Søknadsbehandling ${søknadsbehandling.id} må ha satt skattereferanse for søker (var null)"
            }
            it!!.eps?.let {
                require(eps != null) {
                    "Søknadsbehandling ${søknadsbehandling.id} har satt eps skattereferanse i grunnlagene, men mangler skattegrunnlag for eps."
                }
            }
        }

        eps?.let {
            require(søknadsbehandling.grunnlagsdata.skattereferanser?.eps != null) {
                "Vi har skattemelding for EPS, men søknadsbehandlingen har ikke eps sin skatte-referanse"
            }
        }
    }

    companion object {
        fun Søknadsbehandling.getYearRangeForSkatt(clock: Clock): YearRange {
            return Year.now(clock).minusYears(1).let {
                stønadsperiode?.toYearRange()?.krympTilØvreGrense(it) ?: it.toRange()
            }
        }
    }
}

sealed interface KunneIkkeHenteNySkattedata {
    object UgyldigTilstand : KunneIkkeHenteNySkattedata
}
