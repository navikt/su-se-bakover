package no.nav.su.se.bakover.web.routes.revurdering

import arrow.core.Either
import io.ktor.application.call
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.routing.Route
import io.ktor.routing.post
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.service.revurdering.RevurderingFeilet
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.deserialize
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.features.suUserContext
import no.nav.su.se.bakover.web.message
import no.nav.su.se.bakover.web.routes.behandling.beregning.NyBeregningForSøknadsbehandlingJson
import no.nav.su.se.bakover.web.routes.behandling.beregning.PeriodeJson
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withRevurderingId
import no.nav.su.se.bakover.web.withSakId
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.util.UUID

internal const val revurderingPath = "$sakPath/{sakId}/revurdering"

@KtorExperimentalAPI
internal fun Route.revurderingRoutes(
    revurderingService: RevurderingService
) {
    val log = LoggerFactory.getLogger(this::class.java)

    authorize(Brukerrolle.Saksbehandler) {
        post("$revurderingPath/opprett") {
            call.withSakId { sakId ->
                val navIdent = call.suUserContext.getNAVIdent()
                Either.catch { deserialize<PeriodeJson>(call) }.fold(
                    ifLeft = {
                        call.svar(BadRequest.message("Ugyldig body"))
                    },
                    ifRight = { periode ->
                        revurderingService.opprettRevurdering(
                            sakId,
                            periode = Periode.create(
                                LocalDate.parse(periode.fraOgMed),
                                LocalDate.parse(periode.tilOgMed)
                            ),
                            saksbehandler = Saksbehandler(navIdent)
                        ).fold(
                            ifLeft = {
                                when (it) {
                                    RevurderingFeilet.FantIkkeSak -> call.svar(NotFound.message("Fant ikke sak"))
                                    RevurderingFeilet.FantIngentingSomKanRevurderes -> call.svar(NotFound.message("Fant ingenting som kan revurderes for perioden $periode"))
                                    RevurderingFeilet.GeneriskFeil -> call.svar(InternalServerError.message("Noe gikk feil ved revurdering"))
                                    else -> call.svar(BadRequest.message("Ukjent feil"))
                                }
                            },
                            ifRight = {
                                call.audit("Opprettet en ny revurdering beregning og simulering på sak med id $sakId")
                                call.svar(Resultat.json(OK, serialize(it.toJson())))
                            },
                        )
                    }
                )
            }
        }
    }

    post("$revurderingPath/beregnOgSimuler") {
        call.withSakId { sakId ->
            Either.catch { deserialize<NyBeregningForSøknadsbehandlingJson>(call) }.fold(
                ifLeft = {
                    log.info("Ugyldig behandling-body: ", it)
                    call.svar(BadRequest.message("Ugyldig body"))
                },
                ifRight = { body ->
                    body.toDomain(UUID.randomUUID(), Saksbehandler(call.suUserContext.getNAVIdent()))
                        .mapLeft { call.svar(it) }
                        .map {
                            revurderingService.beregnOgSimuler(
                                revurderingId = it.behandlingId,
                                saksbehandler = Saksbehandler(call.suUserContext.getNAVIdent()),
                                periode = Periode.create(
                                    LocalDate.parse(body.stønadsperiode.periode.fraOgMed),
                                    LocalDate.parse(body.stønadsperiode.periode.tilOgMed)
                                ),
                                fradrag = it.fradrag
                            ).fold(
                                ifLeft = { revurderingFeilet ->
                                    when (revurderingFeilet) {
                                        RevurderingFeilet.GeneriskFeil -> call.svar(InternalServerError.message("Noe gikk feil ved revurdering"))
                                        RevurderingFeilet.FantIkkeSak -> call.svar(NotFound.message("Fant ikke sak"))
                                        RevurderingFeilet.FantIngentingSomKanRevurderes -> call.svar(
                                            NotFound.message(
                                                "Fant ingenting som kan revurderes for perioden ${
                                                Periode.create(
                                                    LocalDate.parse(body.stønadsperiode.periode.fraOgMed),
                                                    LocalDate.parse(body.stønadsperiode.periode.tilOgMed)
                                                )
                                                }"
                                            )
                                        )
                                        RevurderingFeilet.KunneIkkeFinneAktørId -> call.svar(NotFound.message("Kunne ikke finen aktør id"))
                                        RevurderingFeilet.KunneIkkeOppretteOppgave -> call.svar(
                                            InternalServerError.message(
                                                "Kunne ikke opprette oppgave"
                                            )
                                        )
                                    }
                                },
                                ifRight = { revurdertBeregning ->
                                    call.audit("Opprettet en ny revurdering beregning og simulering på sak med id $sakId")
                                    call.svar(Resultat.json(OK, serialize(revurdertBeregning.toJson())))
                                },
                            )
                        }
                }
            )
        }
    }

    post("$revurderingPath/{revurderingId}/tilAttestering") {
        call.withRevurderingId { revurderingId ->
            revurderingService.sendTilAttestering(
                revurderingId = revurderingId, saksbehandler = Saksbehandler(call.suUserContext.getNAVIdent())
            ).fold(
                ifLeft = {
                    when (it) {
                        RevurderingFeilet.GeneriskFeil -> call.svar(InternalServerError.message("Noe gikk feil ved revurdering"))
                        RevurderingFeilet.FantIkkeSak -> call.svar(NotFound.message("Fant ikke sak"))
                        RevurderingFeilet.FantIngentingSomKanRevurderes -> call.svar(
                            NotFound.message("Fant ingenting som kan revurderes for perioden")
                        )
                        RevurderingFeilet.KunneIkkeFinneAktørId -> call.svar(NotFound.message("Kunne ikke finen aktør id"))
                        RevurderingFeilet.KunneIkkeOppretteOppgave -> call.svar(
                            InternalServerError.message("Kunne ikke opprette oppgave")
                        )
                    }
                },
                ifRight = {
                    call.audit("sendt revurdering til attestering med id $revurderingId")
                    call.svar(Resultat.json(OK, serialize(it.toJson())))
                },
            )
        }
    }
}
