package tilbakekreving.presentation.api.vedtaksbrev

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import dokument.domain.brev.Brevvalg
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.correlationId
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withSakId
import no.nav.su.se.bakover.common.infrastructure.web.withTilbakekrevingId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import tilbakekreving.application.service.vurder.BrevTilbakekrevingsbehandlingService
import tilbakekreving.domain.TilbakekrevingsbehandlingId
import tilbakekreving.domain.vurdert.KunneIkkeLagreBrevtekst
import tilbakekreving.domain.vurdert.OppdaterBrevtekstCommand
import tilbakekreving.presentation.api.TILBAKEKREVING_PATH
import tilbakekreving.presentation.api.common.TilbakekrevingsbehandlingJson.Companion.toStringifiedJson
import tilbakekreving.presentation.api.common.ikkeTilgangTilSak
import tilbakekreving.presentation.api.common.manglerBrukkerroller
import java.util.UUID

// TODO jah: Vi må ta stilling til om vi skal sende brev eller ikke.
private data class BrevtekstBody(
    val versjon: Long,
    val brevtekst: String?,
) {
    fun toCommand(
        sakId: UUID,
        behandlingsId: UUID,
        utførtAv: NavIdentBruker.Saksbehandler,
        correlationId: CorrelationId,
        brukerroller: List<Brukerrolle>,
    ): Either<Resultat, OppdaterBrevtekstCommand> {
        val validatedBrukerroller = Either.catch { brukerroller.toNonEmptyList() }.getOrElse {
            return manglerBrukkerroller.left()
        }

        return OppdaterBrevtekstCommand(
            sakId = sakId,
            behandlingId = TilbakekrevingsbehandlingId(value = behandlingsId),
            brevvalg = when (brevtekst) {
                null -> Brevvalg.SaksbehandlersValg.SkalIkkeSendeBrev()
                else -> Brevvalg.SaksbehandlersValg.SkalSendeBrev.Vedtaksbrev.MedFritekst(
                    fritekst = brevtekst,
                )
            },
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
    post("$TILBAKEKREVING_PATH/{tilbakekrevingsId}/brevtekst") {
        authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
            call.withSakId { sakId ->
                call.withTilbakekrevingId { tilbakekrevingId ->
                    call.withBody<BrevtekstBody> { body ->
                        body.toCommand(
                            sakId = sakId,
                            behandlingsId = tilbakekrevingId,
                            utførtAv = call.suUserContext.saksbehandler,
                            correlationId = call.correlationId,
                            brukerroller = call.suUserContext.roller.toNonEmptyList(),
                        ).fold(
                            ifLeft = { call.svar(it) },
                            ifRight = {
                                brevTilbakekrevingsbehandlingService.lagreBrevtekst(it)
                                    .fold(
                                        { call.svar(it.tilResultat()) },
                                        { call.svar(Resultat.json(HttpStatusCode.Created, it.toStringifiedJson())) },
                                    )
                            },
                        )
                    }
                }
            }
        }
    }
}

internal fun KunneIkkeLagreBrevtekst.tilResultat(): Resultat = when (this) {
    is KunneIkkeLagreBrevtekst.IkkeTilgang -> ikkeTilgangTilSak
    KunneIkkeLagreBrevtekst.UlikVersjon -> Feilresponser.utdatertVersjon
}
