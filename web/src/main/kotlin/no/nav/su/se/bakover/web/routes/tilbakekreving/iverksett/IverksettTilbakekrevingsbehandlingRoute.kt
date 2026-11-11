package no.nav.su.se.bakover.web.routes.tilbakekreving.iverksett

import arrow.core.merge
import arrow.core.raise.either
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.domain.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.correlationId
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withSakId
import no.nav.su.se.bakover.common.infrastructure.web.withTilbakekrevingId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.web.inputvalidation.withValidertBody
import no.nav.su.se.bakover.web.routes.tilbakekreving.TILBAKEKREVING_PATH
import no.nav.su.se.bakover.web.routes.tilbakekreving.ikkeTilgangTilSak
import no.nav.su.se.bakover.web.routes.tilbakekreving.kravgrunnlagetHarEndretSeg
import org.slf4j.LoggerFactory
import tilbakekreving.application.service.iverksett.IverksettTilbakekrevingService
import tilbakekreving.domain.TilbakekrevingsbehandlingId
import tilbakekreving.domain.iverksettelse.IverksettTilbakekrevingsbehandlingCommand
import tilbakekreving.domain.iverksettelse.KunneIkkeIverksette
import tilbakekreving.presentation.api.common.TilbakekrevingsbehandlingJson.Companion.toStringifiedJson

data class iverksettTkRequest(
    val versjon: Long,
)

internal fun Route.iverksettTilbakekrevingsbehandlingRoute(
    service: IverksettTilbakekrevingService,
) {
    val log = LoggerFactory.getLogger(this::class.java)
    post("$TILBAKEKREVING_PATH/{tilbakekrevingsId}/iverksett") {
        authorize(Brukerrolle.Attestant) {
            call.withSakId { sakId ->
                call.withTilbakekrevingId { id ->
                    call.withValidertBody<iverksettTkRequest>(log) { body ->
                        either {
                            val behandling = service.iverksett(
                                command = IverksettTilbakekrevingsbehandlingCommand(
                                    sakId = sakId,
                                    tilbakekrevingsbehandlingId = TilbakekrevingsbehandlingId(id),
                                    utførtAv = call.suUserContext.attestant,
                                    correlationId = call.correlationId,
                                    brukerroller = call.suUserContext.roller.toNonEmptyList(),
                                    klientensSisteSaksversjon = Hendelsesversjon(body.versjon),
                                ),
                            ).mapLeft { feil -> feil.tilResultat() }.bind()
                            Resultat.json(HttpStatusCode.Created, behandling.toStringifiedJson())
                        }.merge().let { resultat -> call.svar(resultat) }
                    }
                }
            }
        }
    }
}

private fun KunneIkkeIverksette.tilResultat(): Resultat = when (this) {
    is KunneIkkeIverksette.IkkeTilgang -> ikkeTilgangTilSak
    is KunneIkkeIverksette.KravgrunnlagetHarEndretSeg -> kravgrunnlagetHarEndretSeg
    KunneIkkeIverksette.UlikVersjon -> Feilresponser.utdatertVersjon
    KunneIkkeIverksette.SaksbehandlerOgAttestantKanIkkeVæreSammePerson -> Feilresponser.attestantOgSaksbehandlerKanIkkeVæreSammePerson
    KunneIkkeIverksette.KunneIkkeSendeTilbakekrevingsvedtak -> HttpStatusCode.InternalServerError.errorJson(
        "Kunne ikke sende tilbakekrevingsvedtaket til oppdrag. Utenfor OS/UR sin åpningstid? Kan kravgrunnlaget eller statusene ha endret seg? F.eks. ved at nye linjer har blitt sendt inn for denne saken. Meld fra til utviklere, da det også kan være en programmeringsfeil/tolkningsfeil.",
        "kunne_ikke_sende_tilbakekrevingsvedtaket_til_oppdrag",
    )
    KunneIkkeIverksette.KunneIkkeAvgjøreOmDød -> Feilresponser.KunneIkkeAvgjøreOmDød
    KunneIkkeIverksette.MåLeggeTilMottakerDødsboForDødBruker -> Feilresponser.MåLeggeTilMottakerDødsboForDødBruker
    KunneIkkeIverksette.BrukerManglerAdresse -> Feilresponser.fantIkkeAdresse
}
