package no.nav.su.se.bakover.web.routes.vilkår.opplysningsplikt

import arrow.core.Either
import arrow.core.getOrHandle
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.vilkår.KunneIkkeLageOpplysningspliktVilkår
import no.nav.su.se.bakover.service.revurdering.KunneIkkeLeggeTilOpplysningsplikt
import no.nav.su.se.bakover.service.revurdering.LeggTilOpplysningspliktRequest
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.Feilresponser
import no.nav.su.se.bakover.web.routes.revurdering.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.toJson
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withBody
import java.util.UUID

internal fun Route.opplysningspliktRoutes(
    søknadsbehandlingService: SøknadsbehandlingService,
    revurderingService: RevurderingService,
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
                                    {
                                        it.tilResultat()
                                    },
                                    {
                                        Resultat.json(
                                            HttpStatusCode.Created,
                                            serialize(it.toJson()),
                                        )
                                    },
                                )
                        }
                        is LeggTilOpplysningspliktRequest.Søknadsbehandling -> {
                            søknadsbehandlingService.leggTilOpplysningspliktVilkår(request)
                                .fold(
                                    {
                                        it.tilResultat()
                                    },
                                    {
                                        Resultat.json(
                                            HttpStatusCode.Created,
                                            serialize(it.toJson()),
                                        )
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
        KunneIkkeLeggeTilOpplysningsplikt.FantIkkeBehandling -> {
            Feilresponser.fantIkkeBehandling
        }
        KunneIkkeLeggeTilOpplysningsplikt.HeleBehandlingsperiodenMåVurderes -> {
            Feilresponser.vilkårMåVurderesForHeleBehandlingsperioden
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
        is KunneIkkeLeggeTilOpplysningsplikt.UgyldigTilstand -> {
            Feilresponser.ugyldigTilstand(fra, til)
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
    REVURDERING
}
