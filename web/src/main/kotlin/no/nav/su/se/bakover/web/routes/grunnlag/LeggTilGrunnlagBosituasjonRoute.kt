package no.nav.su.se.bakover.web.routes.grunnlag

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import arrow.core.sequence
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.Brukerrolle
import no.nav.su.se.bakover.common.audit.application.AuditLogEvent
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.audit
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.periode.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.web.sikkerlogg
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBehandlingId
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withRevurderingId
import no.nav.su.se.bakover.common.infrastructure.web.withSakId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.revurdering.service.RevurderingService
import no.nav.su.se.bakover.domain.revurdering.vilkår.bosituasjon.KunneIkkeLeggeTilBosituasjongrunnlag
import no.nav.su.se.bakover.domain.revurdering.vilkår.bosituasjon.LeggTilBosituasjonRequest
import no.nav.su.se.bakover.domain.revurdering.vilkår.bosituasjon.LeggTilBosituasjonerRequest
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeLeggeTilGrunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.domain.søknadsbehandling.VilkårsvurdertSøknadsbehandling
import no.nav.su.se.bakover.domain.vilkår.bosituasjon.KunneIkkeLeggeTilBosituasjon
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.periode.toPeriodeOrResultat
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser
import no.nav.su.se.bakover.web.routes.revurdering.revurderingPath
import no.nav.su.se.bakover.web.routes.revurdering.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.behandlingPath
import no.nav.su.se.bakover.web.routes.søknadsbehandling.tilResultat
import no.nav.su.se.bakover.web.routes.søknadsbehandling.toJson
import java.util.UUID

private data class JsonRequest(
    val bosituasjoner: List<JsonBody>,
) {
    fun toService(behandlingId: UUID): Either<Resultat, LeggTilBosituasjonerRequest> {
        return bosituasjoner.map { it.toService() }.sequence()
            .mapLeft { it }
            .map { LeggTilBosituasjonerRequest(behandlingId = behandlingId, bosituasjoner = it) }
    }

    data class JsonBody(
        val periode: PeriodeJson,
        val epsFnr: String?,
        val delerBolig: Boolean?,
        val erEPSUførFlyktning: Boolean?,
    ) {
        fun toService(): Either<Resultat, LeggTilBosituasjonRequest> {
            val periode = periode.toPeriodeOrResultat()
                .getOrElse { return it.left() }

            return LeggTilBosituasjonRequest(
                periode = periode,
                epsFnr = epsFnr,
                delerBolig = delerBolig,
                ektemakeEllerSamboerUførFlyktning = erEPSUførFlyktning,
            ).right()
        }
    }
}

internal fun Route.leggTilGrunnlagBosituasjonRoutes(
    søknadsbehandlingService: SøknadsbehandlingService,
    revurderingService: RevurderingService,
    satsFactory: SatsFactory,
) {
    post("$behandlingPath/{behandlingId}/grunnlag/bosituasjon") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withSakId { sakId ->
                call.withBehandlingId { behandlingId ->
                    call.withBody<JsonRequest> { json ->
                        call.svar(
                            json.toService(behandlingId)
                                .mapLeft { it }
                                .flatMap {
                                    søknadsbehandlingService.leggTilBosituasjongrunnlag(
                                        it,
                                        call.suUserContext.saksbehandler,
                                    )
                                        .mapLeft { feil -> feil.tilResultat() }
                                        .map { respons ->
                                            call.audit(respons.fnr, AuditLogEvent.Action.UPDATE, respons.id)
                                            call.sikkerlogg("Lagret bosituasjon for søknadsbehandling $behandlingId på $sakId")
                                            Resultat.json(HttpStatusCode.OK, serialize(respons.toJson(satsFactory)))
                                        }
                                }.getOrElse { it },
                        )
                    }
                }
            }
        }
    }

    post("$revurderingPath/{revurderingId}/bosituasjongrunnlag") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withSakId { sakId ->
                call.withRevurderingId { revurderingId ->
                    call.withBody<JsonRequest> { json ->
                        call.svar(
                            json.toService(revurderingId)
                                .mapLeft { it }
                                .flatMap {
                                    revurderingService.leggTilBosituasjongrunnlag(it)
                                        .mapLeft { feil -> feil.tilResultat() }
                                        .map { respons ->
                                            call.audit(
                                                respons.revurdering.fnr,
                                                AuditLogEvent.Action.UPDATE,
                                                respons.revurdering.id,
                                            )
                                            call.sikkerlogg("Lagret bosituasjon for revudering $revurderingId på $sakId")
                                            Resultat.json(HttpStatusCode.OK, serialize(respons.toJson(satsFactory)))
                                        }
                                }.getOrElse { it },
                        )
                    }
                }
            }
        }
    }
}

internal fun SøknadsbehandlingService.KunneIkkeVilkårsvurdere.tilResultat(): Resultat {
    return when (this) {
        SøknadsbehandlingService.KunneIkkeVilkårsvurdere.FantIkkeBehandling -> Feilresponser.fantIkkeBehandling
    }
}

internal fun KunneIkkeLeggeTilGrunnlag.KunneIkkeOppdatereBosituasjon.tilResultat(): Resultat {
    return when (this) {
        is KunneIkkeLeggeTilGrunnlag.KunneIkkeOppdatereBosituasjon.UgyldigTilstand -> Feilresponser.ugyldigTilstand(
            this.fra,
            this.til,
        )
    }
}

internal fun KunneIkkeLeggeTilBosituasjongrunnlag.tilResultat() = when (this) {
    KunneIkkeLeggeTilBosituasjongrunnlag.FantIkkeBehandling -> {
        Revurderingsfeilresponser.fantIkkeRevurdering
    }

    KunneIkkeLeggeTilBosituasjongrunnlag.EpsAlderErNull -> {
        HttpStatusCode.InternalServerError.errorJson(
            "eps alder er null",
            "eps_alder_er_null",
        )
    }

    KunneIkkeLeggeTilBosituasjongrunnlag.KunneIkkeSlåOppEPS -> {
        HttpStatusCode.InternalServerError.errorJson(
            "kunne ikke slå opp EPS",
            "kunne_ikke_slå_opp_eps",
        )
    }

    KunneIkkeLeggeTilBosituasjongrunnlag.UgyldigData -> {
        Feilresponser.ugyldigBody
    }

    is KunneIkkeLeggeTilBosituasjongrunnlag.KunneIkkeLeggeTilBosituasjon -> {
        when (val inner = this.feil) {
            is KunneIkkeLeggeTilBosituasjon.Konsistenssjekk -> {
                inner.feil.tilResultat()
            }

            is KunneIkkeLeggeTilBosituasjon.KunneIkkeOppdatereFormue -> {
                when (val innerInner = inner.feil) {
                    is Revurdering.KunneIkkeLeggeTilFormue.Konsistenssjekk -> {
                        innerInner.feil.tilResultat()
                    }

                    is Revurdering.KunneIkkeLeggeTilFormue.UgyldigTilstand -> {
                        Feilresponser.ugyldigTilstand(innerInner.fra, innerInner.til)
                    }
                }
            }

            KunneIkkeLeggeTilBosituasjon.PerioderMangler -> {
                HttpStatusCode.BadRequest.errorJson(
                    message = "Bosituasjon mangler for hele eller deler av behandlingsperioden",
                    code = "bosituasjon_mangler_for_perioder",
                )
            }

            is KunneIkkeLeggeTilBosituasjon.UgyldigTilstand -> {
                Feilresponser.ugyldigTilstand(inner.fra, inner.til)
            }

            is KunneIkkeLeggeTilBosituasjon.Valideringsfeil -> {
                inner.feil.tilResultat()
            }
        }
    }

    is KunneIkkeLeggeTilBosituasjongrunnlag.Konsistenssjekk -> {
        this.feil.tilResultat()
    }

    is KunneIkkeLeggeTilBosituasjongrunnlag.KunneIkkeLeggeTilGrunnlag -> this.feil.tilResultat()
}

internal fun KunneIkkeLeggeTilGrunnlag.tilResultat(): Resultat = when (this) {
    KunneIkkeLeggeTilGrunnlag.KunneIkkeLeggeTilFradragsgrunnlag.GrunnlagetMåVæreInnenforBehandlingsperioden -> Feilresponser.utenforBehandlingsperioden
    is KunneIkkeLeggeTilGrunnlag.KunneIkkeLeggeTilFradragsgrunnlag.IkkeLovÅLeggeTilFradragIDenneStatusen -> SøknadsbehandlingService.KunneIkkeLeggeTilFradragsgrunnlag.UgyldigTilstand(
        this.status,
        VilkårsvurdertSøknadsbehandling::class,
    ).tilResultat()

    is KunneIkkeLeggeTilGrunnlag.KunneIkkeLeggeTilFradragsgrunnlag.KunneIkkeEndreFradragsgrunnlag -> this.feil.tilResultat()
    is KunneIkkeLeggeTilGrunnlag.KunneIkkeOppdatereBosituasjon.UgyldigTilstand -> KunneIkkeLeggeTilGrunnlag.KunneIkkeOppdatereBosituasjon.UgyldigTilstand(
        this.fra,
        this.til,
    ).tilResultat()
}
