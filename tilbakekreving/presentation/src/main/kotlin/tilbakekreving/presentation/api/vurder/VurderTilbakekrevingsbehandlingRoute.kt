package tilbakekreving.presentation.api.vurder

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
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.correlationId
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withSakId
import no.nav.su.se.bakover.common.infrastructure.web.withTilbakekrevingId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import tilbakekreving.application.service.vurder.MånedsvurderingerTilbakekrevingsbehandlingService
import tilbakekreving.domain.TilbakekrevingsbehandlingId
import tilbakekreving.domain.vurdert.KunneIkkeVurdereTilbakekrevingsbehandling
import tilbakekreving.domain.vurdert.VurderCommand
import tilbakekreving.domain.vurdert.Vurdering
import tilbakekreving.domain.vurdert.Vurderinger
import tilbakekreving.presentation.api.TILBAKEKREVING_PATH
import tilbakekreving.presentation.api.common.TilbakekrevingsbehandlingJson.Companion.toStringifiedJson
import tilbakekreving.presentation.api.common.ikkeTilgangTilSak
import tilbakekreving.presentation.api.common.kravgrunnlagetHarEndretSeg
import tilbakekreving.presentation.api.common.manglerBrukkerroller
import tilbakekreving.presentation.api.common.periodeneIKravgrunnlagetSamsvarerIkkeMedVurderingene
import java.util.UUID

private data class Body(
    val versjon: Long,
    val perioder: List<ForPeriode>,
) {
    data class ForPeriode(
        val periode: PeriodeJson,
        val vurdering: String,
    )
}

private fun Body.toCommand(
    sakId: UUID,
    behandlingsId: UUID,
    utførtAv: NavIdentBruker.Saksbehandler,
    correlationId: CorrelationId,
    brukerroller: List<Brukerrolle>,
): Either<Resultat, VurderCommand> {
    return this.perioder.map { forPeriode ->
        Vurderinger.Periodevurdering(
            periode = forPeriode.periode.toDatoIntervall(),
            vurdering = when (forPeriode.vurdering) {
                "SkalIkkeTilbakekreve" -> Vurdering.SkalIkkeTilbakekreve
                "SkalTilbakekreve" -> Vurdering.SkalTilbakekreve
                else -> return HttpStatusCode.BadRequest.errorJson(
                    message = "Ukjent vurdering, må være en av SkalTilbakekreve/SkalIkkeTilbakekreve, men var: ${forPeriode.vurdering}",
                    code = "ukjent_vurdering",
                ).left()
            },
        )
    }.let {
        val validatedBrukerroller = Either.catch { brukerroller.toNonEmptyList() }.getOrElse {
            return manglerBrukkerroller.left()
        }

        val validatedMånedsvurderinger = Either.catch { it.toNonEmptyList() }.getOrElse {
            return HttpStatusCode.BadRequest.errorJson(
                message = "Ingen månedsvurderinger ble sendt inn",
                code = "månedsvurderinger_ble_ikke_sendt_inn",
            ).left()
        }

        VurderCommand(
            vurderinger = Vurderinger(validatedMånedsvurderinger),
            sakId = sakId,
            behandlingsId = TilbakekrevingsbehandlingId(behandlingsId),
            utførtAv = utførtAv,
            correlationId = correlationId,
            brukerroller = validatedBrukerroller,
            klientensSisteSaksversjon = Hendelsesversjon(versjon),
        ).right()
    }
}

internal fun Route.vurderTilbakekrevingsbehandlingRoute(
    månedsvurderingerTilbakekrevingsbehandlingService: MånedsvurderingerTilbakekrevingsbehandlingService,
) {
    post("$TILBAKEKREVING_PATH/{tilbakekrevingsId}/vurder") {
        authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
            call.withBody<Body> { body ->
                call.withSakId { sakId ->
                    call.withTilbakekrevingId { tilbakekrevingId ->
                        body.toCommand(
                            sakId = sakId,
                            behandlingsId = tilbakekrevingId,
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
                                    ifRight = {
                                        call.svar(
                                            Resultat.json(
                                                HttpStatusCode.Created,
                                                it.toStringifiedJson(),
                                            ),
                                        )
                                    },
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun KunneIkkeVurdereTilbakekrevingsbehandling.tilResultat(): Resultat = when (this) {
    is KunneIkkeVurdereTilbakekrevingsbehandling.IkkeTilgang -> ikkeTilgangTilSak
    is KunneIkkeVurdereTilbakekrevingsbehandling.UlikVersjon -> Feilresponser.utdatertVersjon
    KunneIkkeVurdereTilbakekrevingsbehandling.KravgrunnlagetHarEndretSeg -> kravgrunnlagetHarEndretSeg
    KunneIkkeVurdereTilbakekrevingsbehandling.VurderingeneStemmerIkkeOverensMedKravgrunnlaget -> periodeneIKravgrunnlagetSamsvarerIkkeMedVurderingene
}
