package no.nav.su.se.bakover.web.routes.revurdering

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.routing.Route
import io.ktor.routing.post
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.service.revurdering.KunneIkkeOppretteRevurdering
import no.nav.su.se.bakover.service.revurdering.KunneIkkeOppretteRevurdering.FantIkkeAktørId
import no.nav.su.se.bakover.service.revurdering.KunneIkkeOppretteRevurdering.FantIkkeSak
import no.nav.su.se.bakover.service.revurdering.KunneIkkeOppretteRevurdering.FantIngentingSomKanRevurderes
import no.nav.su.se.bakover.service.revurdering.KunneIkkeOppretteRevurdering.KunneIkkeOppretteOppgave
import no.nav.su.se.bakover.service.revurdering.KunneIkkeOppretteRevurdering.UgyldigBegrunnelse
import no.nav.su.se.bakover.service.revurdering.KunneIkkeOppretteRevurdering.UgyldigPeriode
import no.nav.su.se.bakover.service.revurdering.KunneIkkeOppretteRevurdering.UgyldigÅrsak
import no.nav.su.se.bakover.service.revurdering.OpprettRevurderingRequest
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.web.AuditLogEvent
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.features.suUserContext
import no.nav.su.se.bakover.web.routes.revurdering.GenerelleRevurderingsfeilresponser.fantIkkeAktørId
import no.nav.su.se.bakover.web.routes.revurdering.GenerelleRevurderingsfeilresponser.fantIkkeSak
import no.nav.su.se.bakover.web.routes.revurdering.GenerelleRevurderingsfeilresponser.kunneIkkeOppretteOppgave
import no.nav.su.se.bakover.web.routes.revurdering.GenerelleRevurderingsfeilresponser.ugyldigPeriode
import no.nav.su.se.bakover.web.sikkerlogg
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withBody
import no.nav.su.se.bakover.web.withSakId
import java.time.LocalDate

@KtorExperimentalAPI
internal fun Route.opprettRevurderingRoute(
    revurderingService: RevurderingService,
) {
    data class Body(
        val fraOgMed: LocalDate,
        val årsak: String,
        val begrunnelse: String,
    )
    authorize(Brukerrolle.Saksbehandler) {
        post(revurderingPath) {
            call.withSakId { sakId ->
                call.withBody<Body> { body ->
                    val navIdent = call.suUserContext.navIdent

                    revurderingService.opprettRevurdering(
                        OpprettRevurderingRequest(
                            sakId = sakId,
                            fraOgMed = body.fraOgMed,
                            årsak = body.årsak,
                            begrunnelse = body.begrunnelse,
                            saksbehandler = NavIdentBruker.Saksbehandler(navIdent),
                        ),
                    ).fold(
                        ifLeft = { call.svar(it.tilResultat()) },
                        ifRight = {
                            call.sikkerlogg("Opprettet en ny revurdering på sak med id $sakId")
                            call.audit(it.fnr, AuditLogEvent.Action.CREATE, it.id)
                            call.svar(Resultat.json(HttpStatusCode.Created, serialize(it.toJson())))
                        },
                    )
                }
            }
        }
    }
}

private fun KunneIkkeOppretteRevurdering.tilResultat(): Resultat {
    return when (this) {
        is FantIkkeSak -> fantIkkeSak
        is FantIkkeAktørId -> fantIkkeAktørId
        is KunneIkkeOppretteOppgave -> kunneIkkeOppretteOppgave
        is UgyldigPeriode -> ugyldigPeriode(this.subError)
        is FantIngentingSomKanRevurderes -> NotFound.errorJson(
            "Ingen behandlinger som kan revurderes for angitt periode",
            "ingenting_å_revurdere_i_perioden",
        )
        is UgyldigBegrunnelse -> BadRequest.errorJson(
            "Begrunnelse kan ikke være tom",
            "begrunnelse_kan_ikke_være_tom",
        )
        is UgyldigÅrsak -> BadRequest.errorJson(
            "Ugyldig årsak, må være en av: ${Revurderingsårsak.Årsak.values()}",
            "ugyldig_årsak",
        )
        KunneIkkeOppretteRevurdering.PeriodeOgÅrsakKombinasjonErUgyldig -> BadRequest.errorJson(
            "periode og årsak kombinasjon er ugyldig",
            "periode_og_årsak_kombinasjon_er_ugyldig",
        )
    }
}
