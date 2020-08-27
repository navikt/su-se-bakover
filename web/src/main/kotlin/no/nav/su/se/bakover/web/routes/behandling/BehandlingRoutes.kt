package no.nav.su.se.bakover.web.routes.behandling

import arrow.core.Either
import arrow.core.getOrElse
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Created
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.response.respondBytes
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.patch
import io.ktor.routing.post
import no.nav.su.se.bakover.client.person.PersonOppslag
import no.nav.su.se.bakover.database.ObjectRepo
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Attestant
import no.nav.su.se.bakover.domain.Behandling
import no.nav.su.se.bakover.domain.beregning.Fradragstype
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringClient
import no.nav.su.se.bakover.domain.oppgave.OppgaveClient
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.deserialize
import no.nav.su.se.bakover.web.lesBehandlerId
import no.nav.su.se.bakover.web.lesUUID
import no.nav.su.se.bakover.web.message
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.services.brev.BrevService
import no.nav.su.se.bakover.web.svar
import org.slf4j.LoggerFactory
import java.time.LocalDate

internal const val behandlingPath = "$sakPath/{sakId}/behandlinger"

internal fun Route.behandlingRoutes(
    repo: ObjectRepo,
    brevService: BrevService,
    simuleringClient: SimuleringClient,
    personOppslag: PersonOppslag,
    oppgaveClient: OppgaveClient
) {
    val log = LoggerFactory.getLogger(this::class.java)

    get("$behandlingPath/{behandlingId}") {
        call.withBehandling(repo) {
            call.svar(OK.jsonBody(it))
        }
    }

    data class OpprettBeregningBody(
        val fom: LocalDate,
        val tom: LocalDate,
        val sats: String,
        val fradrag: List<FradragJson>
    ) {
        fun valid() = fom.dayOfMonth == 1 &&
            tom.dayOfMonth == tom.lengthOfMonth() &&
            (sats == Sats.HØY.name || sats == Sats.LAV.name) &&
            fradrag.all { Fradragstype.isValid(it.type) }
    }

    post("$behandlingPath/{behandlingId}/beregn") {
        call.withBehandling(repo) { behandling ->
            Either.catch { deserialize<OpprettBeregningBody>(call) }.fold(
                ifLeft = {
                    log.info("Ugyldig behandling-body: ", it)
                    call.svar(BadRequest.message("Ugyldig body"))
                },
                ifRight = { body ->
                    if (body.valid()) {
                        behandling.opprettBeregning(
                            fom = body.fom,
                            tom = body.tom,
                            sats = Sats.valueOf(body.sats),
                            fradrag = body.fradrag.map { it.toFradrag() }
                        )
                        call.svar(Created.jsonBody(behandling))
                    } else {
                        call.svar(BadRequest.message("Ugyldige input-parametere for: $body"))
                    }
                }
            )
        }
    }

    get("$behandlingPath/{behandlingId}/vedtaksutkast") {
        call.withBehandling(repo) { behandling ->
            brevService.lagUtkastTilBrev(behandling.toDto()).fold(
                ifLeft = { call.svar(InternalServerError.message("Kunne ikke generere pdf")) },
                ifRight = { call.respondBytes(it, ContentType.Application.Pdf) }
            )
        }
    }

    post("$behandlingPath/{behandlingId}/simuler") {
        call.withBehandling(repo) { behandling ->
            behandling.simuler(simuleringClient).fold(
                { call.svar(InternalServerError.message("Kunne ikke gjennomføre simulering")) },
                { call.svar(OK.jsonBody(behandling)) }
            )
        }
    }

    post("$behandlingPath/{behandlingId}/tilAttestering") {
        call.withBehandling(repo) { behandling ->
            val sak = repo.hentSak(behandling.sakId)!!
            val aktørId: AktørId = personOppslag.aktørId(sak.fnr).getOrElse {
                log.error("Fant ikke aktør-id med gitt fødselsnummer")
                throw RuntimeException("Kunne ikke finne aktørid")
            }
            behandling.sendTilAttestering(aktørId, oppgaveClient).fold(
                {
                    call.svar(InternalServerError.message("Kunne ikke opprette oppgave for attestering"))
                },
                {
                    call.audit("Sender behandling med id: ${it.id} til attestering")
                    call.svar(OK.jsonBody(it))
                }
            )
        }
    }

    patch("$behandlingPath/{behandlingId}/attester") {
        call.withBehandling(repo) { behandling ->
            call.audit("Attesterer behandling med id: ${behandling.id}")
            call.svar(OK.jsonBody(behandling.attester(Attestant(call.lesBehandlerId()))))
        }
    }
}

suspend fun ApplicationCall.withBehandling(repo: ObjectRepo, ifRight: suspend (Behandling) -> Unit) {
    this.lesUUID("sakId").fold(
        {
            this.svar(BadRequest.message(it))
        },
        { sakId ->
            this.lesUUID("behandlingId").fold(
                {
                    this.svar(BadRequest.message(it))
                },
                { behandlingId ->
                    repo.hentBehandling(behandlingId)?.let { behandling ->
                        if (behandling.sakId == sakId) {
                            this.audit("Hentet behandling med id: $behandlingId")
                            ifRight(behandling)
                        } else {
                            this.svar(NotFound.message("Ugyldig kombinasjon av sak og behandling"))
                        }
                    } ?: this.svar(NotFound.message("Fant ikke behandling med behandlingId:$behandlingId"))
                }
            )
        }
    )
}
