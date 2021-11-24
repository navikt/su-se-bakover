package no.nav.su.se.bakover.web.routes.klage

import arrow.core.getOrHandle
import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.response.respond
import io.ktor.response.respondBytes
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.post
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.klage.KunneIkkeVilkårsvurdereKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeVurdereKlage
import no.nav.su.se.bakover.service.klage.KlageService
import no.nav.su.se.bakover.service.klage.KlageVurderingerRequest
import no.nav.su.se.bakover.service.klage.KunneIkkeLageBrevutkast
import no.nav.su.se.bakover.service.klage.KunneIkkeOppretteKlage
import no.nav.su.se.bakover.service.klage.NyKlageRequest
import no.nav.su.se.bakover.service.klage.VurderKlagevilkårRequest
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.features.suUserContext
import no.nav.su.se.bakover.web.routes.Feilresponser.Brev.kunneIkkeGenerereBrev
import no.nav.su.se.bakover.web.routes.Feilresponser.fantIkkeKlage
import no.nav.su.se.bakover.web.routes.Feilresponser.fantIkkePerson
import no.nav.su.se.bakover.web.routes.Feilresponser.fantIkkeSak
import no.nav.su.se.bakover.web.routes.Feilresponser.fantIkkeSaksbehandlerEllerAttestant
import no.nav.su.se.bakover.web.routes.Feilresponser.fantIkkeVedtak
import no.nav.su.se.bakover.web.routes.Feilresponser.ugyldigTilstand
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withBody
import no.nav.su.se.bakover.web.withKlageId
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
                    ).map {
                        Resultat.json(HttpStatusCode.Created, serialize(it.toJson()))
                    }.getOrHandle {
                        when (it) {
                            KunneIkkeOppretteKlage.FantIkkeSak -> fantIkkeSak
                            KunneIkkeOppretteKlage.FinnesAlleredeEnÅpenKlage -> BadRequest.errorJson(
                                "Det finnes allerede en åpen klage",
                                "finnes_allerede_en_åpen_klage",
                            )
                        }
                    }
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
                    ).map {
                        Resultat.json(OK, serialize(it.toJson()))
                    }.getOrHandle {
                        when (it) {
                            KunneIkkeVilkårsvurdereKlage.FantIkkeKlage -> fantIkkeKlage
                            KunneIkkeVilkårsvurdereKlage.FantIkkeVedtak -> fantIkkeVedtak
                        }
                    }
                    call.svar(resultat)
                }
            }
        }
    }

    get("$klagePath/{klageId}/brevutkast") {
        call.withSakId { sakId ->
            call.withKlageId { klageId ->
                klageService.brevutkast(sakId, klageId, NavIdentBruker.Saksbehandler(call.suUserContext.navIdent)).fold(
                    ifLeft = {
                        val resultat = when (it) {
                            KunneIkkeLageBrevutkast.FantIkkePerson -> fantIkkePerson
                            KunneIkkeLageBrevutkast.FantIkkeSak -> fantIkkeSak
                            KunneIkkeLageBrevutkast.FantIkkeSaksbehandler -> fantIkkeSaksbehandlerEllerAttestant
                            KunneIkkeLageBrevutkast.GenereringAvBrevFeilet -> kunneIkkeGenerereBrev
                        }
                        call.respond(resultat)
                    },
                    ifRight = {
                        call.respondBytes(it, ContentType.Application.Pdf)
                    },
                )
            }
        }
    }

    authorize(Brukerrolle.Saksbehandler) {
        post("$klagePath/{id}/vurderinger") {
            data class Omgjør(val årsak: String, val utfall: String)
            data class Oppretthold(val hjemler: List<String> = emptyList())

            data class Body(
                val fritekstTilBrev: String?,
                val omgjør: Omgjør?,
                val oppretthold: Oppretthold?,
            )

            call.withStringParam("id") { klageId ->
                call.withBody<Body> { body ->
                    val resultat: Resultat = klageService.vurder(
                        request = KlageVurderingerRequest(
                            klageId = klageId,
                            fritekstTilBrev = body.fritekstTilBrev,
                            omgjør = body.omgjør?.let { o ->
                                KlageVurderingerRequest.Omgjør(
                                    årsak = o.årsak,
                                    utfall = o.utfall,
                                )
                            },
                            oppretthold = body.oppretthold?.let { o ->
                                KlageVurderingerRequest.Oppretthold(hjemler = o.hjemler)
                            },
                            navIdent = call.suUserContext.navIdent,
                        ),
                    ).map { vurdertKlage ->
                        Resultat.json(OK, serialize(vurdertKlage.toJson()))
                    }.getOrHandle { error ->
                        when (error) {
                            KunneIkkeVurdereKlage.FantIkkeKlage -> fantIkkeKlage
                            KunneIkkeVurdereKlage.KanIkkeVelgeBådeOmgjørOgOppretthold -> BadRequest.errorJson(
                                "Kan ikke velge både omgjør og oppretthold.",
                                "kan_ikke_velge_både_omgjør_og_oppretthold",
                            )
                            KunneIkkeVurdereKlage.UgyldigOmgjøringsutfall -> BadRequest.errorJson(
                                "Ugyldig omgjøringsutfall",
                                "ugyldig_omgjøringsutfall",
                            )
                            KunneIkkeVurdereKlage.UgyldigOmgjøringsårsak -> BadRequest.errorJson(
                                "Ugyldig omgjøringsårsak",
                                "ugyldig_omgjøringsårsak",
                            )
                            KunneIkkeVurdereKlage.UgyldigOpprettholdelseshjemler -> BadRequest.errorJson(
                                "Ugyldig opprettholdeseshjemler",
                                "ugyldig_opprettholdeseshjemler",
                            )
                            is KunneIkkeVurdereKlage.UgyldigTilstand -> ugyldigTilstand(error.fra, error.til)
                        }
                    }
                    call.svar(resultat)
                }
            }
        }
    }

    authorize(Brukerrolle.Saksbehandler) {
        post("$klagePath/{id}/brevutkast") {
            call.svar(Resultat.json(OK, "{}"))
        }
    }

    authorize(Brukerrolle.Saksbehandler) {
        post("$klagePath/{id}/tilAttestering") {
            call.svar(Resultat.json(OK, "{}"))
        }
    }

    authorize(Brukerrolle.Attestant) {
        data class Body(val årsak: String, val begrunnelse: String)
        post("$klagePath/{id}/underkjenn") {
            call.svar(Resultat.json(OK, "{}"))
        }
    }

    authorize(Brukerrolle.Attestant) {
        post("$klagePath/{id}/iverksett") {
            call.svar(Resultat.json(OK, "{}"))
        }
    }
}
