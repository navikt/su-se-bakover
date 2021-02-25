package no.nav.su.se.bakover.web.routes.revurdering

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import io.ktor.routing.post
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.service.revurdering.KunneIkkeOppretteRevurdering
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.features.suUserContext
import no.nav.su.se.bakover.web.routes.revurdering.GenerelleRevurderingsfeilresponser.fantIkkeAktørId
import no.nav.su.se.bakover.web.routes.revurdering.GenerelleRevurderingsfeilresponser.fantIkkeSak
import no.nav.su.se.bakover.web.routes.revurdering.GenerelleRevurderingsfeilresponser.kunneIkkeOppretteOppgave
import no.nav.su.se.bakover.web.routes.revurdering.GenerelleRevurderingsfeilresponser.ugyldigPeriode
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withBody
import no.nav.su.se.bakover.web.withSakId
import java.time.LocalDate

@KtorExperimentalAPI
internal fun Route.opprettRevurderingRoute(
    revurderingService: RevurderingService
) {
    authorize(Brukerrolle.Saksbehandler) {
        post("$revurderingPath/opprett") {
            call.withSakId { sakId ->
                call.withBody<Body> { request ->
                    val navIdent = call.suUserContext.navIdent

                    revurderingService.opprettRevurdering(
                        sakId,
                        fraOgMed = request.fraOgMed,
                        saksbehandler = NavIdentBruker.Saksbehandler(navIdent)
                    ).fold(
                        ifLeft = { call.svar(it.tilResultat()) },
                        ifRight = {
                            call.audit("Opprettet en ny revurdering på sak med id $sakId")
                            call.svar(Resultat.json(HttpStatusCode.Created, serialize(it.toJson())))
                        },
                    )
                }
            }
        }
    }
}

private class Body(val fraOgMed: LocalDate)

private fun KunneIkkeOppretteRevurdering.tilResultat(): Resultat {
    return when (this) {
        KunneIkkeOppretteRevurdering.FantIkkeSak -> fantIkkeSak
        KunneIkkeOppretteRevurdering.FantIkkeAktørid -> fantIkkeAktørId
        KunneIkkeOppretteRevurdering.KunneIkkeOppretteOppgave -> kunneIkkeOppretteOppgave
        is KunneIkkeOppretteRevurdering.UgyldigPeriode -> ugyldigPeriode(this)
        KunneIkkeOppretteRevurdering.FantIngentingSomKanRevurderes -> HttpStatusCode.NotFound.errorJson(
            "Ingen behandlinger som kan revurderes for angitt periode",
            "ingenting_å_revurdere_i_perioden",
        )
        KunneIkkeOppretteRevurdering.KanIkkeRevurdereInneværendeMånedEllerTidligere -> HttpStatusCode.BadRequest.errorJson(
            // TODO jah: På sikt vil vi kunne revurdere tilbake i tid også.
            "Revurdering kan kun gjøres fra og med neste kalendermåned",
            "tidligest_neste_måned",
        )
        KunneIkkeOppretteRevurdering.KanIkkeRevurderePerioderMedFlereAktiveStønadsperioder -> HttpStatusCode.BadRequest.errorJson(
            // TODO AI 03-02-2020: Midlertidig løsning. På sikt vil vi støtte flere aktive stønadsperioder og denne feilmeldingen forsvinner.
            "Revurderingsperioden kan ikke overlappe flere aktive stønadsperioder",
            "flere_aktive_stønadsperioder",
        )
        KunneIkkeOppretteRevurdering.KanIkkeRevurdereEnPeriodeMedEksisterendeRevurdering -> HttpStatusCode.BadRequest.errorJson(
            // TODO AI: Midlertidig løsning. På sikt vil vi støtte å revurdere en revurdering.
            "Kan ikke revurdere en behandling som allerede har en eksisterende revurdering",
            "finnes_en_eksisterende_revurdering",
        )
    }
}
