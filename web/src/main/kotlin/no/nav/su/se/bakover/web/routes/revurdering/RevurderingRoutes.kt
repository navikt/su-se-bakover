package no.nav.su.se.bakover.web.routes.revurdering

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode.Companion.BadRequest
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
import no.nav.su.se.bakover.service.revurdering.RevurderingFeilet
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.deserialize
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.features.suUserContext
import no.nav.su.se.bakover.web.message
import no.nav.su.se.bakover.web.routes.behandling.beregning.FradragJson
import no.nav.su.se.bakover.web.routes.behandling.beregning.FradragJson.Companion.toFradrag
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
                            ifLeft = { call.svar(hentFeilResultat(it)) },
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

    data class BeregningForRevurderingsBody(
        val revurderingId: UUID,
        val periode: PeriodeJson,
        val fradrag: List<FradragJson>,
    ) {
        fun toDomain(): Either<Resultat, Pair<Periode, List<Fradrag>>> {
            val periode = periode.toPeriode().getOrHandle { return it.left() }
            val fradrag = fradrag.toFradrag(periode).getOrHandle { return it.left() }

            return Pair(periode, fradrag).right()
        }
    }

    post("$revurderingPath/beregnOgSimuler") {
        call.withSakId { sakId ->
            Either.catch { deserialize<BeregningForRevurderingsBody>(call) }.fold(
                ifLeft = {
                    log.info("Ugyldig behandling-body: ", it)
                    call.svar(BadRequest.message("Ugyldig body"))
                },
                ifRight = { body ->
                    body.toDomain()
                        .fold(
                            ifLeft = { call.svar(it) },
                            ifRight = {
                                val (periode, fradrag) = it

                                revurderingService.beregnOgSimuler(
                                    revurderingId = body.revurderingId,
                                    saksbehandler = Saksbehandler(call.suUserContext.getNAVIdent()),
                                    periode = periode,
                                    fradrag = fradrag
                                ).fold(
                                    ifLeft = { revurderingFeilet -> call.svar(hentFeilResultat(revurderingFeilet)) },
                                    ifRight = { simulertRevurdering ->
                                        call.audit("Opprettet en ny revurdering beregning og simulering på sak med id $sakId")
                                        call.svar(Resultat.json(OK, serialize(simulertRevurdering.toJson())))
                                    },
                                )
                            }
                        )
                }
            )
        }
    }

    post("$revurderingPath/{revurderingId}/tilAttestering") {
        call.withRevurderingId { revurderingId ->
            revurderingService.sendTilAttestering(
                revurderingId = revurderingId, saksbehandler = Saksbehandler(call.suUserContext.getNAVIdent())
            ).fold(
                ifLeft = { call.svar(hentFeilResultat(it)) },
                ifRight = {
                    call.audit("sendt revurdering til attestering med id $revurderingId")
                    call.svar(Resultat.json(OK, serialize(it.toJson())))
                },
            )
        }
    }

    data class BrevutkastMedFritekst(val fritekst: String?)
    post("$revurderingPath/{revurderingId}/brevutkast") {
        call.withRevurderingId { revurderingId ->
            Either.catch { deserialize<BrevutkastMedFritekst>(call) }.fold(
                ifLeft = { call.svar(BadRequest.message("Ugyldig body")) },
                ifRight = { it ->
                    revurderingService.lagBrevutkast(revurderingId, it.fritekst).fold(
                        ifLeft = { call.svar(hentFeilResultat(it)) },
                        ifRight = {
                            call.audit("sendt revurdering til attestering med id $revurderingId")
                            call.respondBytes(it, ContentType.Application.Pdf)
                        },
                    )
                }
            )
        }
    }
}

internal fun hentFeilResultat(feil: RevurderingFeilet): Resultat {
    return when (feil) {
        RevurderingFeilet.GeneriskFeil -> InternalServerError.message("Noe gikk feil ved revurdering")
        RevurderingFeilet.FantIkkeSak -> NotFound.message("Fant ikke sak")
        RevurderingFeilet.FantIngentingSomKanRevurderes -> NotFound.message("Fant ingenting som kan revurderes for perioden")
        RevurderingFeilet.KunneIkkeFinneAktørId -> NotFound.message("Kunne ikke finen aktør id")
        RevurderingFeilet.KunneIkkeOppretteOppgave -> InternalServerError.message("Kunne ikke opprette oppgave")
        RevurderingFeilet.FantIkkePerson -> InternalServerError.message("Kunne ikke opprette oppgave")
        RevurderingFeilet.FantIkkeRevurdering -> NotFound.message("Fant ikke revurdering")
        RevurderingFeilet.KunneIkkeLageBrevutkast -> InternalServerError.message("Kunne ikke lage brev")
        RevurderingFeilet.MicrosoftApiGraphFeil -> InternalServerError.message("Kunne ikke slå opp saksbehandler")
    }
}
