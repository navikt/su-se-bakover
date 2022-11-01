package no.nav.su.se.bakover.web.routes.vilkår.opplysningsplikt

import arrow.core.Either
import arrow.core.getOrHandle
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.audit.AuditLogEvent
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.audit
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeLeggeTilVilkår
import no.nav.su.se.bakover.domain.vilkår.KunneIkkeLageOpplysningspliktVilkår
import no.nav.su.se.bakover.service.revurdering.KunneIkkeLeggeTilOpplysningsplikt
import no.nav.su.se.bakover.service.revurdering.LeggTilOpplysningspliktRequest
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.revurdering.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.toJson
import java.util.UUID

internal fun Route.opplysningspliktRoutes(
    søknadsbehandlingService: SøknadsbehandlingService,
    revurderingService: RevurderingService,
    satsFactory: SatsFactory,
) {
    post("/vilkar/opplysningsplikt") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withBody<LeggTilOpplysningspliktVilkårBody> { body ->

                val request = body.toDomain().getOrHandle { return@authorize call.svar(it.tilResultat()) }

                call.svar(
                    when (request) {
                        is LeggTilOpplysningspliktRequest.Revurdering -> {
                            revurderingService.leggTilOpplysningspliktVilkår(request)
                                .fold(
                                    { it.tilResultat() },
                                    {
                                        call.audit(it.revurdering.fnr, AuditLogEvent.Action.UPDATE, it.revurdering.id)
                                        Resultat.json(HttpStatusCode.Created, serialize(it.toJson(satsFactory)))
                                    },
                                )
                        }

                        is LeggTilOpplysningspliktRequest.Søknadsbehandling -> {
                            søknadsbehandlingService.leggTilOpplysningspliktVilkår(request)
                                .fold(
                                    { it.tilResultat() },
                                    {
                                        call.audit(it.fnr, AuditLogEvent.Action.UPDATE, it.id)
                                        Resultat.json(HttpStatusCode.Created, serialize(it.toJson(satsFactory)))
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
            when (this.feil) {
                KunneIkkeLageOpplysningspliktVilkår.OverlappendeVurderingsperioder -> {
                    Feilresponser.overlappendeVurderingsperioder
                }

                KunneIkkeLageOpplysningspliktVilkår.Vurderingsperiode.PeriodeForGrunnlagOgVurderingErForskjellig -> {
                    Feilresponser.periodeForGrunnlagOgVurderingErForskjellig
                }
            }
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
                KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilOpplysningsplikt.HeleBehandlingsperiodenErIkkeVurdert -> {
                    Feilresponser.vilkårMåVurderesForHeleBehandlingsperioden
                }

                is KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilOpplysningsplikt.UgyldigTilstand -> {
                    Feilresponser.ugyldigTilstand(feil.fra, feil.til)
                }
            }
        }
    }
}

private class LeggTilOpplysningspliktVilkårBody private constructor(
    val id: UUID,
    val type: Behandlingstype,
    val data: List<VurderingsperiodeOpplysningspliktVilkårJson>,
) {
    fun toDomain(): Either<KunneIkkeLeggeTilOpplysningsplikt, LeggTilOpplysningspliktRequest> {
        return data.toDomain()
            .map {
                when (type) {
                    Behandlingstype.SØKNADSBEHANDLING -> {
                        LeggTilOpplysningspliktRequest.Søknadsbehandling(id, it)
                    }

                    Behandlingstype.REVURDERING -> {
                        LeggTilOpplysningspliktRequest.Revurdering(id, it)
                    }
                }
            }
    }
}

private enum class Behandlingstype {
    SØKNADSBEHANDLING,
    REVURDERING,
}
