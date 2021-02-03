package no.nav.su.se.bakover.web.routes.revurdering

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Created
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.response.respondBytes
import io.ktor.routing.Route
import io.ktor.routing.post
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.service.revurdering.KunneIkkeRevurdere
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.features.suUserContext
import no.nav.su.se.bakover.web.message
import no.nav.su.se.bakover.web.routes.behandling.beregning.FradragJson
import no.nav.su.se.bakover.web.routes.behandling.beregning.FradragJson.Companion.toFradrag
import no.nav.su.se.bakover.web.routes.behandling.beregning.PeriodeJson
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withBody
import no.nav.su.se.bakover.web.withRevurderingId
import no.nav.su.se.bakover.web.withSakId
import java.time.LocalDate
import java.util.UUID

internal const val revurderingPath = "$sakPath/{sakId}/revurdering"

@KtorExperimentalAPI
internal fun Route.revurderingRoutes(
    revurderingService: RevurderingService
) {
    authorize(Brukerrolle.Saksbehandler) {
        post("$revurderingPath/opprett") {
            call.withSakId { sakId ->
                call.withBody<PeriodeJson> { periode ->
                    val navIdent = call.suUserContext.getNAVIdent()
                    revurderingService.opprettRevurdering(
                        sakId,
                        periode = Periode.create(
                            LocalDate.parse(periode.fraOgMed),
                            LocalDate.parse(periode.tilOgMed)
                        ),
                        saksbehandler = Saksbehandler(navIdent)
                    ).fold(
                        ifLeft = { call.svar(hentFeilResultat(it)) },
                        ifRight = {
                            call.audit("Opprettet en ny revurdering på sak med id $sakId")
                            call.svar(Resultat.json(Created, serialize(it.toJson())))
                        },
                    )
                }
            }
        }
    }

    data class BeregningForRevurderingBody(
        val revurderingId: UUID,
        val periode: PeriodeJson,
        val fradrag: List<FradragJson>,
    ) {
        fun toDomain(): Either<Resultat, List<Fradrag>> {
            val periode = periode.toPeriode().getOrHandle { return it.left() }
            val fradrag = fradrag.toFradrag(periode).getOrHandle { return it.left() }

            return fradrag.right()
        }
    }

    post("$revurderingPath/beregnOgSimuler") {
        call.withSakId { sakId ->
            call.withBody<BeregningForRevurderingBody> { body ->
                body.toDomain()
                    .fold(
                        ifLeft = { call.svar(it) },
                        ifRight = {
                            revurderingService.beregnOgSimuler(
                                revurderingId = body.revurderingId,
                                saksbehandler = Saksbehandler(call.suUserContext.getNAVIdent()),
                                fradrag = it
                            ).fold(
                                ifLeft = { revurderingFeilet -> call.svar(hentFeilResultat(revurderingFeilet)) },
                                ifRight = { simulertRevurdering ->
                                    call.audit("Opprettet en ny revurdering beregning og simulering på sak med id $sakId")
                                    call.svar(Resultat.json(Created, serialize(simulertRevurdering.toJson())))
                                },
                            )
                        }
                    )
            }
        }
    }

    post("$revurderingPath/{revurderingId}/tilAttestering") {
        call.withRevurderingId { revurderingId ->
            revurderingService.sendTilAttestering(
                revurderingId = revurderingId, saksbehandler = Saksbehandler(call.suUserContext.getNAVIdent())
            ).fold(
                ifLeft = { call.svar(hentFeilResultat(it)) },
                ifRight = {
                    call.audit("Sendt revurdering til attestering med id $revurderingId")
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
                    ifLeft = { call.svar(hentFeilResultat(it)) },
                    ifRight = {
                        call.audit("Lagd brevutkast for revurdering med id $revurderingId")
                        call.respondBytes(it, ContentType.Application.Pdf)
                    },
                )
            }
        }
    }
}

internal fun hentFeilResultat(feil: KunneIkkeRevurdere): Resultat {
    return when (feil) {
        KunneIkkeRevurdere.FantIkkeSak -> NotFound.message("Fant ikke sak")
        KunneIkkeRevurdere.FantIngentingSomKanRevurderes -> NotFound.message("Fant ingenting som kan revurderes for perioden")
        KunneIkkeRevurdere.KunneIkkeFinneAktørId -> NotFound.message("Kunne ikke finen aktør id")
        KunneIkkeRevurdere.KunneIkkeOppretteOppgave -> InternalServerError.message("Kunne ikke opprette oppgave")
        KunneIkkeRevurdere.FantIkkePerson -> InternalServerError.message("Kunne ikke opprette oppgave")
        KunneIkkeRevurdere.FantIkkeRevurdering -> NotFound.message("Fant ikke revurdering")
        KunneIkkeRevurdere.KunneIkkeLageBrevutkast -> InternalServerError.message("Kunne ikke lage brev")
        KunneIkkeRevurdere.MicrosoftApiGraphFeil -> InternalServerError.message("Kunne ikke slå opp saksbehandler")
        KunneIkkeRevurdere.KanIkkeRevurdereInneværendeMånedEllerTidligere -> BadRequest.message("Perioden er ikke i måneden etter")
        KunneIkkeRevurdere.SimuleringFeilet -> InternalServerError.message("Simulering feilet")
        KunneIkkeRevurdere.KanIkkeRevurderePerioderMedFlereAktiveStønadsperioder -> InternalServerError.message("Revurderingsperioden kan ikke overlappe flere aktive stønadsperioder.")
    }
}
