package no.nav.su.se.bakover.web.routes.tilbakekreving.vedtaksbrev

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.merge
import arrow.core.raise.either
import arrow.core.right
import dokument.domain.brev.Brevvalg
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.domain.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.ident.NavIdentBruker
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
import no.nav.su.se.bakover.web.routes.tilbakekreving.manglerBrukkerroller
import org.slf4j.LoggerFactory
import tilbakekreving.application.service.vurder.BrevTilbakekrevingsbehandlingService
import tilbakekreving.domain.TilbakekrevingsbehandlingId
import tilbakekreving.domain.vedtaksbrev.KunneIkkeOppdatereVedtaksbrev
import tilbakekreving.domain.vedtaksbrev.OppdaterVedtaksbrevCommand
import tilbakekreving.presentation.api.common.TilbakekrevingsbehandlingJson.Companion.toStringifiedJson
import java.util.UUID

data class BrevtekstBody(
    val versjon: Long,
    val brevtekst: String?,
) {
    fun toCommand(
        sakId: UUID,
        behandlingsId: UUID,
        utførtAv: NavIdentBruker.Saksbehandler,
        correlationId: CorrelationId,
        brukerroller: List<Brukerrolle>,
    ): Either<Resultat, OppdaterVedtaksbrevCommand> {
        val validatedBrukerroller = Either.catch { brukerroller.toNonEmptyList() }.getOrElse {
            return manglerBrukkerroller.left()
        }
        val brevvalg = when (brevtekst) {
            null -> Brevvalg.SaksbehandlersValg.SkalIkkeSendeBrev()
            else -> {
                if (brevtekst.isBlank()) {
                    return HttpStatusCode.BadRequest.errorJson(
                        message = "Må fylle ut minst et tegn i fritekstfeltet",
                        code = "mangler_fritekst",
                    ).left()
                }
                Brevvalg.SaksbehandlersValg.SkalSendeBrev.Vedtaksbrev.MedFritekst(
                    fritekst = brevtekst,
                )
            }
        }
        return OppdaterVedtaksbrevCommand(
            sakId = sakId,
            behandlingId = TilbakekrevingsbehandlingId(value = behandlingsId),
            brevvalg = brevvalg,
            utførtAv = utførtAv,
            correlationId = correlationId,
            brukerroller = validatedBrukerroller,
            klientensSisteSaksversjon = Hendelsesversjon(value = versjon),
        ).right()
    }
}

internal fun Route.vedtaksbrevTilbakekrevingsbehandlingRoute(
    brevTilbakekrevingsbehandlingService: BrevTilbakekrevingsbehandlingService,
) {
    val log = LoggerFactory.getLogger(this::class.java)
    post("$TILBAKEKREVING_PATH/{tilbakekrevingsId}/brevtekst") {
        authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
            call.withSakId { sakId ->
                call.withTilbakekrevingId { tilbakekrevingId ->
                    call.withValidertBody<BrevtekstBody>(log) { body ->
                        either {
                            val command = body.toCommand(
                                sakId = sakId,
                                behandlingsId = tilbakekrevingId,
                                utførtAv = call.suUserContext.saksbehandler,
                                correlationId = call.correlationId,
                                brukerroller = call.suUserContext.roller.toNonEmptyList(),
                            ).bind()
                            val behandling = brevTilbakekrevingsbehandlingService.lagreBrevtekst(command)
                                .mapLeft { feil -> feil.tilResultat() }.bind()
                            Resultat.json(HttpStatusCode.Created, behandling.toStringifiedJson())
                        }.merge().let { resultat -> call.svar(resultat) }
                    }
                }
            }
        }
    }
}

internal fun KunneIkkeOppdatereVedtaksbrev.tilResultat(): Resultat = when (this) {
    is KunneIkkeOppdatereVedtaksbrev.IkkeTilgang -> ikkeTilgangTilSak
    KunneIkkeOppdatereVedtaksbrev.UlikVersjon -> Feilresponser.utdatertVersjon
    KunneIkkeOppdatereVedtaksbrev.FantIkkeSak -> Feilresponser.fantIkkeSak
    KunneIkkeOppdatereVedtaksbrev.FantIkkeTilbakekrevingsbehandling -> Feilresponser.fantIkkeTilbakekrevingsbehandling
    KunneIkkeOppdatereVedtaksbrev.ManglerFritekst -> Feilresponser.manglerFritekstTilbakekreving
}
