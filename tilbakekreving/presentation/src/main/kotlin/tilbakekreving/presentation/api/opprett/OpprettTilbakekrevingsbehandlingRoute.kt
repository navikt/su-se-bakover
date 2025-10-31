package tilbakekreving.presentation.api.opprett

import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.domain.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.correlationId
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withSakId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import person.domain.KunneIkkeHentePerson
import tilbakekreving.application.service.opprett.OpprettTilbakekrevingsbehandlingService
import tilbakekreving.domain.opprettelse.KunneIkkeOppretteTilbakekrevingsbehandling
import tilbakekreving.domain.opprettelse.OpprettTilbakekrevingsbehandlingCommand
import tilbakekreving.presentation.api.TILBAKEKREVING_PATH
import tilbakekreving.presentation.api.common.TilbakekrevingsbehandlingJson.Companion.toStringifiedJson
import tilbakekreving.presentation.api.common.ikkeTilgangTilSak
import tilbakekreving.presentation.api.common.ingenUteståendeKravgrunnlag

private data class Body(
    val versjon: Long,
)

internal fun Route.opprettTilbakekrevingsbehandlingRoute(
    opprettTilbakekrevingsbehandlingService: OpprettTilbakekrevingsbehandlingService,
) {
    post("$TILBAKEKREVING_PATH/ny") {
        authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
            call.withSakId { sakId ->
                call.withBody<Body> { body ->
                    opprettTilbakekrevingsbehandlingService.opprett(
                        command = OpprettTilbakekrevingsbehandlingCommand(
                            sakId = sakId,
                            opprettetAv = call.suUserContext.saksbehandler,
                            correlationId = call.correlationId,
                            brukerroller = call.suUserContext.roller.toNonEmptyList(),
                            klientensSisteSaksversjon = Hendelsesversjon(body.versjon),
                        ),
                    ).fold(
                        ifLeft = { call.svar(it.tilResultat()) },
                        ifRight = { call.svar(Resultat.json(HttpStatusCode.Created, it.toStringifiedJson())) },
                    )
                }
            }
        }
    }
}

private fun KunneIkkeOppretteTilbakekrevingsbehandling.tilResultat(): Resultat = when (this) {
    is KunneIkkeOppretteTilbakekrevingsbehandling.IngenUteståendeKravgrunnlag -> ingenUteståendeKravgrunnlag
    is KunneIkkeOppretteTilbakekrevingsbehandling.IkkeTilgang -> ikkeTilgangTilSak
    is KunneIkkeOppretteTilbakekrevingsbehandling.FinnesAlleredeEnÅpenBehandling -> Feilresponser.harAlleredeÅpenBehandling
    is KunneIkkeOppretteTilbakekrevingsbehandling.FeilVedHentingAvPerson -> this.feil.tilResultat()
    is KunneIkkeOppretteTilbakekrevingsbehandling.FeilVedOpprettelseAvOppgave -> Feilresponser.kunneIkkeOppretteOppgave
    is KunneIkkeOppretteTilbakekrevingsbehandling.UlikVersjon -> Feilresponser.utdatertVersjon
}

// dobbel-impl av person routes
internal fun KunneIkkeHentePerson.tilResultat(): Resultat = when (this) {
    KunneIkkeHentePerson.FantIkkePerson -> Feilresponser.fantIkkePerson
    KunneIkkeHentePerson.IkkeTilgangTilPerson -> Feilresponser.ikkeTilgangTilPerson
    KunneIkkeHentePerson.Ukjent -> Feilresponser.feilVedOppslagPåPerson
}
