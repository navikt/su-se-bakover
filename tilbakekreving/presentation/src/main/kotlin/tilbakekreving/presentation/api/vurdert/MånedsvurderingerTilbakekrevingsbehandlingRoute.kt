package tilbakekreving.presentation.api.vurdert

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.correlationId
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withSakId
import no.nav.su.se.bakover.common.tid.periode.Måned
import tilbakekreving.application.service.MånedsvurderingerTilbakekrevingsbehandlingService
import tilbakekreving.domain.vurdert.KunneIkkeVurdereTilbakekrevingsbehandling
import tilbakekreving.domain.vurdert.LeggTilVurderingerCommand
import tilbakekreving.domain.vurdert.Månedsvurdering
import tilbakekreving.domain.vurdert.Månedsvurderinger
import tilbakekreving.domain.vurdert.Vurdering
import tilbakekreving.presentation.api.common.TilbakekrevingsbehandlingJson.Companion.toStringifiedJson
import tilbakekreving.presentation.api.common.ikkeTilgangTilSak
import tilbakekreving.presentation.api.tilbakekrevingPath
import java.time.YearMonth
import java.util.UUID

private data class Body(
    val måned: String,
    val vurdering: String,
)

private fun List<Body>.toCommand(
    sakId: UUID,
    utførtAv: NavIdentBruker.Saksbehandler,
    correlationId: CorrelationId,
    brukerroller: List<Brukerrolle>,
): Either<Resultat, LeggTilVurderingerCommand> {
    return this.map {
        Månedsvurdering(
            måned = Måned.fra(YearMonth.parse(it.måned)),
            vurdering = when (it.vurdering) {
                "SkalIkkeTilbakekreve" -> Vurdering.SkalIkkeTilbakekreve
                "SkalTilbakekreve" -> Vurdering.SkalTilbakekreve
                else -> return HttpStatusCode.BadRequest.errorJson(
                    message = "Ukjent vurdering, må være en av SkalTilbakekreve/SkalIkkeTilbakekreve, men var: ${it.vurdering}",
                    code = "ukjent_vurdering",
                ).left()
            },
        )
    }.let {
        val validatedBrukerroller = Either.catch { brukerroller.toNonEmptyList() }.getOrElse {
            return HttpStatusCode.InternalServerError.errorJson(
                message = "teknisk feil: Brukeren mangler brukerroller",
                code = "mangler_brukerroller",
            ).left()
        }

        val validatedMånedsvurderinger = Either.catch { it.toNonEmptyList() }.getOrElse {
            return HttpStatusCode.BadRequest.errorJson(
                message = "Ingen månedsvurderinger ble sendt inn",
                code = "månedsvurderinger_ble_ikke_sendt_inn",
            ).left()
        }

        LeggTilVurderingerCommand(
            vurderinger = Månedsvurderinger(validatedMånedsvurderinger),
            sakId = sakId,
            utførtAv = utførtAv,
            correlationId = correlationId,
            brukerroller = validatedBrukerroller,
        ).right()
    }
}

internal fun Route.månedsvurderingerTilbakekrevingsbehandlingRoute(
    månedsvurderingerTilbakekrevingsbehandlingService: MånedsvurderingerTilbakekrevingsbehandlingService,
) {
    post("$tilbakekrevingPath/manedsvurder") {
        authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
            call.withBody<List<Body>> { body ->
                call.withSakId { sakId ->

                    body.toCommand(
                        sakId = sakId,
                        utførtAv = call.suUserContext.saksbehandler,
                        correlationId = call.correlationId,
                        brukerroller = call.suUserContext.roller.toNonEmptyList(),
                    ).fold(
                        { call.svar(it) },
                        {
                            månedsvurderingerTilbakekrevingsbehandlingService.vurder(
                                command = it,
                            ).fold(
                                ifLeft = { call.svar(it.tilResultat()) },
                                ifRight = { call.svar(Resultat.json(HttpStatusCode.Created, it.toStringifiedJson())) },
                            )
                        },
                    )
                }
            }
        }
    }
}

private fun KunneIkkeVurdereTilbakekrevingsbehandling.tilResultat(): Resultat = when (this) {
    is KunneIkkeVurdereTilbakekrevingsbehandling.IkkeTilgang -> ikkeTilgangTilSak
}
