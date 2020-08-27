package no.nav.su.se.bakover.web.routes.behandling

import arrow.core.Either
import arrow.core.flatMap
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
import no.nav.su.se.bakover.web.toUUID
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

    data class OpprettBehandlingBody(val soknadId: String)

    post(behandlingPath) {
        call.lesUUID("sakId").fold(
            ifLeft = { call.svar(BadRequest.message(it)) },
            ifRight = { sakId ->
                Either.catch { deserialize<OpprettBehandlingBody>(call) }
                    .flatMap { it.soknadId.toUUID() }
                    .fold(
                        ifLeft = { call.svar(BadRequest.message("Ugyldig body")) },
                        ifRight = { søknadId ->
                            when (val sak = repo.hentSak(sakId)) {
                                null -> call.svar(NotFound.message("Fant ikke sak med id:$sakId"))
                                else -> {
                                    call.audit("Oppretter behandling på sak: $sakId og søknadId: $søknadId")
                                    val behandling = sak.opprettSøknadsbehandling(søknadId)
                                    call.svar(Created.jsonBody(behandling))
                                }
                            }
                        }
                    )
            }
        )
    }

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
        call.lesUUID("sakId").fold(
            ifLeft = { call.svar(BadRequest.message(it)) },
            ifRight = { sakId ->
                when (val sak = repo.hentSak(sakId)) {
                    null -> call.svar(NotFound.message("Fant ikke sak med sakId:$sakId"))
                    else -> {
                        call.lesUUID("behandlingId").fold(
                            ifLeft = { call.svar(BadRequest.message(it)) },
                            ifRight = { behandlingId ->
                                sak.fullførBehandling(behandlingId, simuleringClient).fold(
                                    {
                                        call.svar(InternalServerError.message(it.name))
                                    },
                                    {
                                        call.svar(OK.jsonBody(it))
                                    }
                                )
                            }
                        )
                    }
                }
            }
        )
    }

    post("$behandlingPath/{behandlingId}/tilAttestering") {
        call.lesUUID("sakId").fold(
            ifLeft = { call.svar(BadRequest.message(it)) },
            ifRight = { sakId ->
                when (val sak = repo.hentSak(sakId)) {
                    null -> call.svar(NotFound.message("Fant ikke sak med sakId:$sakId"))
                    else -> {
                        call.lesUUID("behandlingId").fold(
                            ifLeft = { call.svar(BadRequest.message(it)) },
                            ifRight = { behandlingId ->
                                val aktørId: AktørId = personOppslag.aktørId(sak.fnr).getOrElse {
                                    log.error("Fant ikke aktør-id med gitt fødselsnummer")
                                    throw RuntimeException("Kunne ikke finne aktørid")
                                }
                                sak.sendTilAttestering(behandlingId, aktørId, oppgaveClient).fold(
                                    {
                                        call.svar(InternalServerError.message("$it"))
                                    },
                                    {
                                        call.svar(OK.message("Opprettet oppgave med id : $it"))
                                    }
                                )
                            }
                        )
                    }
                }
            }
        )
    }

    patch("$behandlingPath/{behandlingId}/attester") {
        // TODO authorize by group
        call.lesUUID("sakId").fold(
            ifLeft = { call.svar(BadRequest.message(it)) },
            ifRight = {
                call.lesUUID("behandlingId").fold(
                    ifLeft = { call.svar(BadRequest.message(it)) },
                    ifRight = { behandlingId ->
                        call.audit("Attesterer behandling med id: $behandlingId")
                        when (val behandling = repo.hentBehandling(behandlingId)) {
                            null -> call.svar(NotFound.message("Fant ikke behandling med id:$behandlingId"))
                            else -> {
                                call.svar(OK.jsonBody(behandling.attester(Attestant(call.lesBehandlerId()))))
                            }
                        }
                    }
                )
            }
        )
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
