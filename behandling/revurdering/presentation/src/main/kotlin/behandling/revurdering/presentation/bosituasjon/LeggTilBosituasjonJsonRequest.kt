package behandling.revurdering.presentation.bosituasjon

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import behandling.revurdering.domain.bosituasjon.KunneIkkeLeggeTilBosituasjonForRevurdering
import behandling.revurdering.domain.bosituasjon.KunneIkkeLeggeTilBosituasjongrunnlagForRevurdering
import behandling.revurdering.domain.bosituasjon.LeggTilBosituasjonForRevurderingCommand
import behandling.revurdering.domain.bosituasjon.LeggTilBosituasjonerForRevurderingCommand
import common.presentation.periode.toPeriodeOrResultat
import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.domain.BehandlingsId
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import vilkår.vurderinger.tilResultat

/**
 * Søknadsbehandling har fått sin egen type, så denne eies nå alene av revurdering og kan refaktoreres deretter.
 */
data class LeggTilBosituasjonJsonRequest(
    val bosituasjoner: List<JsonBody>,
) {
    fun toService(behandlingId: BehandlingsId): Either<Resultat, LeggTilBosituasjonerForRevurderingCommand> {
        return either {
            LeggTilBosituasjonerForRevurderingCommand(
                behandlingId = behandlingId,
                bosituasjoner = bosituasjoner.map { it.toService().bind() },
            )
        }
    }

    data class JsonBody(
        val periode: PeriodeJson,
        val epsFnr: String?,
        val delerBolig: Boolean?,
        val erEpsFylt67: Boolean?,
        val erEPSUførFlyktning: Boolean?,
    ) {
        fun toService(): Either<Resultat, LeggTilBosituasjonForRevurderingCommand> {
            val periode = periode.toPeriodeOrResultat()
                .getOrElse { return it.left() }

            return LeggTilBosituasjonForRevurderingCommand(
                periode = periode,
                epsFnr = epsFnr,
                delerBolig = delerBolig,
                ektemakeEllerSamboerUførFlyktning = erEPSUførFlyktning,
                epsFylt67 = erEpsFylt67,
            ).right()
        }
    }
}

fun KunneIkkeLeggeTilBosituasjongrunnlagForRevurdering.tilResultat() = when (this) {
    KunneIkkeLeggeTilBosituasjongrunnlagForRevurdering.FantIkkeBehandling -> Feilresponser.fantIkkeBehandling

    KunneIkkeLeggeTilBosituasjongrunnlagForRevurdering.EpsAlderErNull -> HttpStatusCode.InternalServerError.errorJson(
        "eps alder er null",
        "eps_alder_er_null",
    )

    KunneIkkeLeggeTilBosituasjongrunnlagForRevurdering.KunneIkkeSlåOppEPS -> HttpStatusCode.InternalServerError.errorJson(
        "kunne ikke slå opp EPS",
        "kunne_ikke_slå_opp_eps",
    )

    KunneIkkeLeggeTilBosituasjongrunnlagForRevurdering.UgyldigData -> Feilresponser.ugyldigBody

    is KunneIkkeLeggeTilBosituasjongrunnlagForRevurdering.KunneIkkeLeggeTilBosituasjon -> when (val inner = this.feil) {
        is KunneIkkeLeggeTilBosituasjonForRevurdering.Konsistenssjekk -> inner.feil.tilResultat()
        is KunneIkkeLeggeTilBosituasjonForRevurdering.KunneIkkeOppdatereFormue -> inner.feil.tilResultat()
        KunneIkkeLeggeTilBosituasjonForRevurdering.PerioderMangler -> HttpStatusCode.BadRequest.errorJson(
            message = "Bosituasjon mangler for hele eller deler av behandlingsperioden",
            code = "bosituasjon_mangler_for_perioder",
        )

        is KunneIkkeLeggeTilBosituasjonForRevurdering.UgyldigTilstand -> Feilresponser.ugyldigTilstand(
            inner.fra,
            inner.til,
        )
    }
}
