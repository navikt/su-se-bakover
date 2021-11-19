package no.nav.su.se.bakover.web.routes.klage

import arrow.core.getOrHandle
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.klage.KunneIkkeVilkårsvurdereKlage
import no.nav.su.se.bakover.service.klage.KlageService
import no.nav.su.se.bakover.service.klage.KunneIkkeOppretteKlage
import no.nav.su.se.bakover.service.klage.NyKlageRequest
import no.nav.su.se.bakover.service.klage.VurderKlagevilkårRequest
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.features.suUserContext
import no.nav.su.se.bakover.web.routes.Feilresponser.fantIkkeSak
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withBody
import no.nav.su.se.bakover.web.withSakId
import no.nav.su.se.bakover.web.withStringParam

internal const val klagePath = "$sakPath/{sakId}/klager"

internal fun Route.klageRoutes(
    klageService: KlageService,
) {
    authorize(Brukerrolle.Saksbehandler) {
        post(klagePath) {
            data class Body(val journalpostId: String)
            call.withSakId { sakId ->
                call.withBody<Body> { body ->
                    val resultat = klageService.opprett(
                        NyKlageRequest(
                            sakId = sakId,
                            navIdent = body.journalpostId,
                            journalpostId = call.suUserContext.navIdent,
                        ),
                    ).mapLeft {
                        when (it) {
                            KunneIkkeOppretteKlage.FantIkkeSak -> fantIkkeSak
                            KunneIkkeOppretteKlage.FinnesAlleredeEnÅpenKlage -> BadRequest.errorJson(
                                "Det finnes allerede en åpen klage",
                                "finnes_allerede_en_åpen_klage",
                            )
                        }
                    }.map {
                        Resultat.json(HttpStatusCode.Created, serialize(it.toJson()))
                    }.getOrHandle { it }
                    call.svar(resultat)
                }
            }
        }
    }

    authorize(Brukerrolle.Saksbehandler) {
        post("$klagePath/{id}/vilkår/vurderinger") {
            data class Body(
                val vedtakId: String?,
                val innenforFristen: Boolean?,
                val klagesDetPåKonkreteElementerIVedtaket: Boolean?,
                val erUnderskrevet: Boolean?,
                val begrunnelse: String?,
            )
            call.withStringParam("id") { klageId ->
                call.withBody<Body> { body ->
                    val resultat = klageService.vilkårsvurder(
                        VurderKlagevilkårRequest(
                            klageId = klageId,
                            navIdent = call.suUserContext.navIdent,
                            vedtakId = body.vedtakId,
                            innenforFristen = body.innenforFristen,
                            klagesDetPåKonkreteElementerIVedtaket = body.klagesDetPåKonkreteElementerIVedtaket,
                            erUnderskrevet = body.erUnderskrevet,
                            begrunnelse = body.begrunnelse,
                        ),
                    ).mapLeft {
                        when (it) {
                            KunneIkkeVilkårsvurdereKlage.FantIkkeKlage -> HttpStatusCode.NotFound.errorJson(
                                "Fant ikke klage",
                                "fant_ikke_klage",
                            )
                            KunneIkkeVilkårsvurdereKlage.FantIkkeVedtak -> HttpStatusCode.NotFound.errorJson(
                                "Fant ikke vedtak",
                                "fant_ikke_vedtak",
                            )
                        }
                    }.map {
                        Resultat.json(HttpStatusCode.OK, serialize(it.toJson()))
                    }.getOrHandle { it }
                    call.svar(resultat)
                }
            }
        }
    }

    authorize(Brukerrolle.Saksbehandler) {
        post("$klagePath/{id}/vurderinger") {
            data class Omgjør(val årsak: String, val utfall: String)
            data class Oppretthold(val hjemmel: String)

            data class Body(
                val fritekstTilBrev: String?,
                val vurdering: String?,
                val omgjør: Omgjør?,
                val oppretthold: Oppretthold?,
            )

            call.withStringParam("id") { klageId ->
                call.withBody<Body> {
                    call.svar(Resultat.json(HttpStatusCode.OK, "{}"))
                }
            }
        }
    }

    authorize(Brukerrolle.Saksbehandler) {
        post("$klagePath/{id}/brevutkast") {
            call.svar(Resultat.json(HttpStatusCode.OK, "{}"))
        }
    }

    authorize(Brukerrolle.Saksbehandler) {
        post("$klagePath/{id}/tilAttestering") {
            call.svar(Resultat.json(HttpStatusCode.OK, "{}"))
        }
    }

    authorize(Brukerrolle.Attestant) {
        data class Body(val årsak: String, val begrunnelse: String)
        post("$klagePath/{id}/underkjenn") {
            call.svar(Resultat.json(HttpStatusCode.OK, "{}"))
        }
    }

    authorize(Brukerrolle.Attestant) {
        post("$klagePath/{id}/iverksett") {
            call.svar(Resultat.json(HttpStatusCode.OK, "{}"))
        }
    }
}
