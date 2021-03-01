package no.nav.su.se.bakover.web.routes.revurdering

import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Forbidden
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.response.respondBytes
import io.ktor.routing.Route
import io.ktor.routing.post
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.NavIdentBruker.Attestant
import no.nav.su.se.bakover.service.revurdering.KunneIkkeIverksetteRevurdering
import no.nav.su.se.bakover.service.revurdering.KunneIkkeRevurdere
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.features.suUserContext
import no.nav.su.se.bakover.web.routes.revurdering.GenerelleRevurderingsfeilresponser.fantIkkeRevurdering
import no.nav.su.se.bakover.web.routes.revurdering.GenerelleRevurderingsfeilresponser.fantIkkeSak
import no.nav.su.se.bakover.web.routes.revurdering.GenerelleRevurderingsfeilresponser.ugyldigTilstand
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withBody
import no.nav.su.se.bakover.web.withRevurderingId

internal const val revurderingPath = "$sakPath/{sakId}/revurderinger"

@KtorExperimentalAPI
internal fun Route.revurderingRoutes(
    revurderingService: RevurderingService
) {
    opprettRevurderingRoute(revurderingService)

    oppdaterRevurderingsperiodeRoute(revurderingService)

    beregnOgSimulerRevurdering(revurderingService)

    sendRevurderingTilAttestering(revurderingService)

    post("$revurderingPath/{revurderingId}/iverksett") {
        call.withRevurderingId { revurderingId ->
            revurderingService.iverksett(
                revurderingId = revurderingId, attestant = Attestant(call.suUserContext.navIdent)
            ).fold(
                ifLeft = {
                    val message = when (it) {
                        KunneIkkeIverksetteRevurdering.AttestantOgSaksbehandlerKanIkkeVæreSammePerson -> Forbidden.errorJson(
                            "Attestant og saksbehandler kan ikke være samme person",
                            "attestant_og_saksbehandler_kan_ikke_være_samme_person",
                        )
                        KunneIkkeIverksetteRevurdering.FantIkkeRevurdering -> fantIkkeRevurdering
                        is KunneIkkeIverksetteRevurdering.UgyldigTilstand -> ugyldigTilstand(it.fra, it.til)
                        KunneIkkeIverksetteRevurdering.KunneIkkeJournalføreBrev -> InternalServerError.errorJson(
                            "Feil ved journalføring av vedtaksbrev",
                            "kunne_ikke_journalføre_brev",
                        )
                        KunneIkkeIverksetteRevurdering.KunneIkkeKontrollsimulere -> InternalServerError.errorJson(
                            "Kunne ikke utføre kontrollsimulering",
                            "kunne_ikke_kontrollsimulere",
                        )
                        KunneIkkeIverksetteRevurdering.KunneIkkeUtbetale -> InternalServerError.errorJson(
                            "Kunne ikke utføre utbetaling",
                            "kunne_ikke_utbetale",
                        )
                        KunneIkkeIverksetteRevurdering.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte -> InternalServerError.errorJson(
                            "Oppdaget inkonsistens mellom tidligere utført simulering og kontrollsimulering. Ny simulering må utføres og kontrolleres før iverksetting kan gjennomføres",
                            "simulering_har_blitt_endret_siden_saksbehandler_simulerte",
                        )
                    }
                    call.svar(message)
                },
                ifRight = {
                    call.audit("Iverksatt revurdering med id $revurderingId")
                    call.svar(Resultat.json(OK, serialize(it.toJson())))
                },
            )
        }
    }

    data class BrevutkastMedFritekst(val fritekst: String?)
    post("$revurderingPath/{revurderingId}/brevutkast") {
        call.withRevurderingId { revurderingId ->
            call.withBody<BrevutkastMedFritekst> { it ->
                revurderingService.lagBrevutkast(revurderingId, it.fritekst).fold(
                    ifLeft = { call.svar(it.tilFeilMelding()) },
                    ifRight = {
                        call.audit("Lagd brevutkast for revurdering med id $revurderingId")
                        call.respondBytes(it, ContentType.Application.Pdf)
                    },
                )
            }
        }
    }
}

internal fun KunneIkkeRevurdere.tilFeilMelding(): Resultat {
    return when (this) {
        KunneIkkeRevurdere.FantIkkeSak -> fantIkkeSak
        KunneIkkeRevurdere.FantIngentingSomKanRevurderes -> NotFound.errorJson(
            "Ingen behandlinger som kan revurderes for angitt periode",
            "ingenting_å_revurdere_i_perioden",
        )
        KunneIkkeRevurdere.FantIkkeAktørid -> NotFound.errorJson(
            "Fant ikke aktør id",
            "fant_ikke_aktør_id",
        )
        KunneIkkeRevurdere.KunneIkkeOppretteOppgave -> InternalServerError.errorJson(
            "Kunne ikke opprette oppgave",
            "kunne_ikke_opprette_oppgave",
        )
        KunneIkkeRevurdere.FantIkkePerson -> InternalServerError.errorJson(
            "Fant ikke person",
            "fant_ikke_person",
        )
        KunneIkkeRevurdere.FantIkkeRevurdering -> fantIkkeRevurdering
        KunneIkkeRevurdere.KunneIkkeLageBrevutkast -> InternalServerError.errorJson(
            "Kunne ikke lage brev",
            "kunne_ikke_lage_brev",
        )
        KunneIkkeRevurdere.KanIkkeRevurdereInneværendeMånedEllerTidligere -> BadRequest.errorJson(
            // TODO jah: På sikt vil vi kunne revurdere tilbake i tid også.
            "Revurdering kan kun gjøres fra og med neste kalendermåned",
            "tidligest_neste_måned",
        )
        KunneIkkeRevurdere.KanIkkeVelgeSisteMånedVedNedgangIStønaden -> BadRequest.errorJson(
            "Kan ikke velge siste måned av stønadsperioden ved nedgang i stønaden",
            "siste_måned_ved_nedgang_i_stønaden",
        )
        KunneIkkeRevurdere.SimuleringFeilet -> InternalServerError.errorJson(
            "Simulering feilet",
            "simulering_feilet",
        )

        KunneIkkeRevurdere.KanIkkeRevurderePerioderMedFlereAktiveStønadsperioder -> InternalServerError.errorJson(
            // TODO AI 03-02-2020: Midlertidig løsning. På sikt vil vi støtte flere aktive stønadsperioder og denne feilmeldingen forsvinner.
            "Revurderingsperioden kan ikke overlappe flere aktive stønadsperioder",
            "flere_aktive_stønadsperioder",
        )
        KunneIkkeRevurdere.KanIkkeRevurdereEnPeriodeMedEksisterendeRevurdering -> InternalServerError.errorJson(
            // TODO AI: Midlertidig løsning. På sikt vil vi støtte å revurdere en revurdering.
            "Kan ikke revurdere en behandling som allerede har en eksisterende revurdering",
            "finnes_en_eksisterende_revurdering",
        )
        is KunneIkkeRevurdere.UgyldigTilstand -> BadRequest.errorJson(
            "Kan ikke gå fra tilstanden ${this.fra.simpleName} til tilstanden ${this.til.simpleName}",
            "ugyldig_tilstandsovergang",
        )
        is KunneIkkeRevurdere.UgyldigPeriode -> BadRequest.errorJson(
            this.subError.toString(),
            "ugyldig_periode",
        )
    }
}
