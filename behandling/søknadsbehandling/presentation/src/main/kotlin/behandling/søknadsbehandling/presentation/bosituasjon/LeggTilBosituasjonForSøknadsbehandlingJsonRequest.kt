package behandling.søknadsbehandling.presentation.bosituasjon

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import behandling.søknadsbehandling.domain.bosituasjon.KunneIkkeLeggeTilBosituasjongrunnlag
import behandling.søknadsbehandling.domain.bosituasjon.LeggTilBosituasjonCommand
import behandling.søknadsbehandling.domain.bosituasjon.LeggTilBosituasjonerCommand
import common.presentation.periode.toPeriodeOrResultat
import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.domain.BehandlingsId
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.errorJson

data class LeggTilBosituasjonForSøknadsbehandlingJsonRequest(
    val bosituasjoner: List<JsonBody>,
) {
    fun toService(behandlingId: BehandlingsId): Either<Resultat, LeggTilBosituasjonerCommand> {
        return either {
            LeggTilBosituasjonerCommand(
                behandlingId = behandlingId,
                bosituasjoner = bosituasjoner.map { it.toService().bind() },
            )
        }
    }

    data class JsonBody(
        val periode: PeriodeJson,
        val epsFnr: String?,
        val delerBolig: Boolean?,
        val erEPSUførFlyktning: Boolean?,
    ) {
        fun toService(): Either<Resultat, LeggTilBosituasjonCommand> {
            val periode = periode.toPeriodeOrResultat()
                .getOrElse { return it.left() }

            return LeggTilBosituasjonCommand(
                periode = periode,
                epsFnr = epsFnr,
                delerBolig = delerBolig,
                ektemakeEllerSamboerUførFlyktning = erEPSUførFlyktning,
            ).right()
        }
    }
}

fun KunneIkkeLeggeTilBosituasjongrunnlag.tilResultat() = when (this) {
    KunneIkkeLeggeTilBosituasjongrunnlag.FantIkkeBehandling -> Feilresponser.fantIkkeBehandling
    KunneIkkeLeggeTilBosituasjongrunnlag.EpsAlderErNull -> HttpStatusCode.InternalServerError.errorJson(
        "eps alder er null",
        "eps_alder_er_null",
    )

    KunneIkkeLeggeTilBosituasjongrunnlag.KunneIkkeSlåOppEPS -> HttpStatusCode.InternalServerError.errorJson(
        "kunne ikke slå opp EPS",
        "kunne_ikke_slå_opp_eps",
    )

    KunneIkkeLeggeTilBosituasjongrunnlag.UgyldigData -> Feilresponser.ugyldigBody
    KunneIkkeLeggeTilBosituasjongrunnlag.GrunnlagetMåVæreInnenforBehandlingsperioden -> Feilresponser.utenforBehandlingsperioden
    is KunneIkkeLeggeTilBosituasjongrunnlag.UgyldigTilstand -> Feilresponser.ugyldigTilstand(fra, til)
}
