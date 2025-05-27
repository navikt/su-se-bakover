package no.nav.su.se.bakover.web.routes.vilkår.opplysningsplikt

import arrow.core.Either
import arrow.core.getOrElse
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.audit.AuditLogEvent
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.audit
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingId
import no.nav.su.se.bakover.domain.revurdering.service.RevurderingService
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingId
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.domain.søknadsbehandling.vilkår.KunneIkkeLeggeTilVilkår
import no.nav.su.se.bakover.domain.vilkår.opplysningsplikt.KunneIkkeLeggeTilOpplysningsplikt
import no.nav.su.se.bakover.domain.vilkår.opplysningsplikt.LeggTilOpplysningspliktRequest
import no.nav.su.se.bakover.web.routes.revurdering.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.vilkårOgGrunnlag.tilResultat
import vilkår.formue.domain.FormuegrenserFactory
import vilkår.opplysningsplikt.domain.KunneIkkeLageOpplysningspliktVilkår
import java.time.Clock
import java.util.UUID

internal fun Route.opplysningspliktRoutes(
    søknadsbehandlingService: SøknadsbehandlingService,
    revurderingService: RevurderingService,
    formuegrenserFactory: FormuegrenserFactory,
    clock: Clock,
) {
    post("/vilkar/opplysningsplikt") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withBody<LeggTilOpplysningspliktVilkårBody> { body ->

                val request = body.toDomain(saksbehandler = call.suUserContext.saksbehandler, clock = clock)
                    .getOrElse { return@authorize call.svar(it.tilResultat()) }

                call.svar(
                    when (request) {
                        is LeggTilOpplysningspliktRequest.Revurdering -> {
                            revurderingService.leggTilOpplysningspliktVilkår(request)
                                .fold(
                                    { it.tilResultat() },
                                    {
                                        call.audit(it.revurdering.fnr, AuditLogEvent.Action.UPDATE, it.revurdering.id.value)
                                        Resultat.json(HttpStatusCode.Created, serialize(it.toJson(formuegrenserFactory)))
                                    },
                                )
                        }

                        is LeggTilOpplysningspliktRequest.Søknadsbehandling -> {
                            søknadsbehandlingService.leggTilOpplysningspliktVilkår(request)
                                .fold(
                                    { it.tilResultat() },
                                    {
                                        call.audit(it.fnr, AuditLogEvent.Action.UPDATE, it.id.value)
                                        Resultat.json(HttpStatusCode.Created, serialize(it.toJson(formuegrenserFactory)))
                                    },
                                )
                        }
                    },
                )
            }
        }
    }
}

internal fun KunneIkkeLeggeTilOpplysningsplikt.tilResultat(): Resultat {
    return when (this) {
        is KunneIkkeLeggeTilOpplysningsplikt.FantIkkeBehandling -> {
            Feilresponser.fantIkkeBehandling
        }

        is KunneIkkeLeggeTilOpplysningsplikt.UgyldigOpplysningspliktVilkår -> {
            this.feil.tilResultat()
        }

        is KunneIkkeLeggeTilOpplysningsplikt.Revurdering -> {
            when (val feil = this.feil) {
                Revurdering.KunneIkkeLeggeTilOpplysningsplikt.HeleBehandlingsperiodenErIkkeVurdert -> {
                    Feilresponser.vilkårMåVurderesForHeleBehandlingsperioden
                }

                is Revurdering.KunneIkkeLeggeTilOpplysningsplikt.UgyldigTilstand -> {
                    Feilresponser.ugyldigTilstand(feil.fra, feil.til)
                }
            }
        }

        is KunneIkkeLeggeTilOpplysningsplikt.Søknadsbehandling -> {
            when (val feil = this.feil) {
                is KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilOpplysningsplikt.Vilkårsfeil -> feil.underliggende.tilResultat()
            }
        }
    }
}

internal fun KunneIkkeLageOpplysningspliktVilkår.tilResultat(): Resultat {
    return when (this) {
        KunneIkkeLageOpplysningspliktVilkår.OverlappendeVurderingsperioder -> {
            Feilresponser.overlappendeVurderingsperioder
        }

        KunneIkkeLageOpplysningspliktVilkår.Vurderingsperiode.PeriodeForGrunnlagOgVurderingErForskjellig -> {
            Feilresponser.periodeForGrunnlagOgVurderingErForskjellig
        }
    }
}

private class LeggTilOpplysningspliktVilkårBody private constructor(
    val id: UUID,
    val type: Behandlingstype,
    val data: List<VurderingsperiodeOpplysningspliktVilkårJson>,
) {
    fun toDomain(
        saksbehandler: NavIdentBruker.Saksbehandler,
        clock: Clock,
    ): Either<KunneIkkeLeggeTilOpplysningsplikt, LeggTilOpplysningspliktRequest> {
        return data.toDomain(clock)
            .map {
                when (type) {
                    Behandlingstype.SØKNADSBEHANDLING -> {
                        LeggTilOpplysningspliktRequest.Søknadsbehandling(SøknadsbehandlingId(id), it, saksbehandler)
                    }

                    Behandlingstype.REVURDERING -> {
                        LeggTilOpplysningspliktRequest.Revurdering(RevurderingId(id), it)
                    }
                }
            }
    }
}

private enum class Behandlingstype {
    SØKNADSBEHANDLING,
    REVURDERING,
}
