package no.nav.su.se.bakover.web.routes.behandling

import arrow.core.Either
import arrow.core.getOrElse
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Created
import io.ktor.http.HttpStatusCode.Companion.Forbidden
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.response.respondBytes
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.patch
import io.ktor.routing.post
import no.nav.su.se.bakover.client.person.PersonOppslag
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Attestant
import no.nav.su.se.bakover.domain.Behandling
import no.nav.su.se.bakover.domain.Behandling.IverksettFeil.AttestantOgSaksbehandlerErLik
import no.nav.su.se.bakover.domain.Behandling.IverksettFeil.Utbetaling
import no.nav.su.se.bakover.domain.Saksbehandler
import no.nav.su.se.bakover.domain.beregning.Fradragstype
import no.nav.su.se.bakover.service.behandling.BehandlingService
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.deserialize
import no.nav.su.se.bakover.web.erAttestant
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
    brevService: BrevService,
    personOppslag: PersonOppslag,
    behandlingService: BehandlingService,
    sakService: SakService,
) {
    val log = LoggerFactory.getLogger(this::class.java)

    get("$behandlingPath/{behandlingId}") {
        call.withBehandling(behandlingService) {
            call.svar(OK.jsonBody(it))
        }
    }

    patch("$behandlingPath/{behandlingId}/informasjon") {
        call.withBehandling(behandlingService) { behandling ->
            Either.catch { deserialize<BehandlingsinformasjonJson>(call) }.fold(
                ifLeft = {
                    log.info("Ugylding behandlingsinformasjon-body", it)
                    call.svar(BadRequest.message("Klarte ikke deserialisere body"))
                },
                ifRight = { body ->
                    call.audit("Oppdater behandlingsinformasjon for id: ${behandling.id}")
                    if (body.isValid()) {
                        val oppdatert = behandlingService.oppdaterBehandlingsinformasjon(
                            behandlingId = behandling.id,
                            behandlingsinformasjon = behandlingsinformasjonFromJson(body)
                        )
                        call.svar(OK.jsonBody(oppdatert))
                    } else {
                        call.svar(BadRequest.message("Data i behandlingsinformasjon er ugyldig"))
                    }
                }
            )
        }
    }

    data class OpprettBeregningBody(
        val fom: LocalDate,
        val tom: LocalDate,
        val fradrag: List<FradragJson>
    ) {
        fun valid() = fom.dayOfMonth == 1 &&
            tom.dayOfMonth == tom.lengthOfMonth() &&
            fradrag.all { Fradragstype.isValid(it.type) }
    }

    data class UnderkjennBody(
        val begrunnelse: String
    ) {
        fun valid() = begrunnelse.isNotBlank()
    }

    post("$behandlingPath/{behandlingId}/beregn") {
        call.withBehandling(behandlingService) { behandling ->
            Either.catch { deserialize<OpprettBeregningBody>(call) }.fold(
                ifLeft = {
                    log.info("Ugyldig behandling-body: ", it)
                    call.svar(BadRequest.message("Ugyldig body"))
                },
                ifRight = { body ->
                    if (body.valid()) {
                        val oppdatert = behandlingService.opprettBeregning(
                            behandlingId = behandling.id,
                            fom = body.fom,
                            tom = body.tom,
                            fradrag = body.fradrag.map { it.toFradrag() }
                        )
                        call.svar(Created.jsonBody(oppdatert))
                    } else {
                        call.svar(BadRequest.message("Ugyldige input-parametere for: $body"))
                    }
                }
            )
        }
    }

    get("$behandlingPath/{behandlingId}/utledetSatsInfo") {
        call.withBehandling(behandlingService) { behandling ->
            call.svar(Resultat.json(OK, serialize(behandling.toUtledetSatsInfoJson())))
        }
    }

    get("$behandlingPath/{behandlingId}/vedtaksutkast") {
        call.withBehandling(behandlingService) { behandling ->
            brevService.lagUtkastTilBrev(behandling).fold(
                ifLeft = { call.svar(InternalServerError.message("Kunne ikke generere vedtaksbrevutkast")) },
                ifRight = { call.respondBytes(it, ContentType.Application.Pdf) }
            )
        }
    }

    post("$behandlingPath/{behandlingId}/simuler") {
        call.withBehandling(behandlingService) { behandling ->
            behandlingService.simuler(behandling.id).fold(
                {
                    log.info("Feil ved simulering: ", it)
                    call.svar(InternalServerError.message("Kunne ikke gjennomføre simulering"))
                },
                { call.svar(OK.jsonBody(behandling)) }
            )
        }
    }

    post("$behandlingPath/{behandlingId}/tilAttestering") {
        // TODO: Short circuit
        call.withBehandling(behandlingService) { behandling ->
            sakService.hentSak(behandling.sakId)
                .mapLeft { throw RuntimeException("Sak id finnes ikke") }
                .map {
                    val aktørId: AktørId = personOppslag.aktørId(it.fnr).getOrElse {
                        log.error("Fant ikke aktør-id med gitt fødselsnummer")
                        throw RuntimeException("Kunne ikke finne aktørid")
                    }
                    val saksBehandler = Saksbehandler(call.lesBehandlerId())

                    behandlingService.sendTilAttestering(behandling.id, aktørId, saksBehandler).fold(
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
    }

    patch("$behandlingPath/{behandlingId}/iverksett") {
        if (!call.erAttestant()) {
            return@patch call.svar(Forbidden.message("Du har ikke tillgang."))
        }

        call.withBehandling(behandlingService) { behandling ->
            call.audit("Iverksetter behandling med id: ${behandling.id}")
            sakService.hentSak(behandling.sakId)
                .mapLeft { throw RuntimeException("Sak id finnes ikke") }
                .map {
                    // TODO jah: Ignorerer resultatet her inntil videre og attesterer uansett.
                    // TODO jah: lesBehandlerId() henter bare oid fra JWT som er en UUID. Her er det nok heller ønskelig med 7-tegns ident
                    brevService.journalførVedtakOgSendBrev(it, behandling).fold(
                        ifLeft = { call.svar(InternalServerError.message("Feilet ved attestering")) },
                        ifRight = {
                            behandlingService.iverksett(
                                behandlingId = behandling.id,
                                attestant = Attestant(id = call.lesBehandlerId())
                            ).fold(
                                {
                                    when (it) {
                                        is AttestantOgSaksbehandlerErLik -> call.svar(Forbidden.message(it.msg))
                                        is Utbetaling -> call.svar(InternalServerError.message(it.msg))
                                    }
                                },
                                { call.svar(OK.jsonBody(it)) }
                            )
                        }
                    )
                }
        }
    }

    patch("$behandlingPath/{behandlingId}/underkjenn") {
        if (!call.erAttestant()) {
            return@patch call.svar(Forbidden.message("Du har ikke tillgang."))
        }

        call.withBehandling(behandlingService) { behandling ->
            call.audit("behandling med id: ${behandling.id} godkjennes ikke")
            // TODO jah: Ignorerer resultatet her inntil videre og attesterer uansett.
            // TODO jah: lesBehandlerId() henter bare oid fra JWT som er en UUID. Her er det nok heller ønskelig med 7-tegns ident

            Either.catch { deserialize<UnderkjennBody>(call) }.fold(
                ifLeft = {
                    log.info("Ugyldig behandling-body: ", it)
                    call.svar(BadRequest.message("Ugyldig body"))
                },
                ifRight = { body ->
                    if (body.valid()) {
                        behandlingService.underkjenn(
                            begrunnelse = body.begrunnelse,
                            attestant = Attestant(call.lesBehandlerId()),
                            behandling = behandling
                        ).fold(
                            ifLeft = {
                                call.svar(Forbidden.message(it.msg))
                            },
                            ifRight = { call.svar(OK.jsonBody(behandling)) }
                        )
                    } else {
                        call.svar(BadRequest.message("Må anngi en begrunnelse"))
                    }
                }
            )
        }
    }
}

suspend fun ApplicationCall.withBehandling(
    behandlingService: BehandlingService,
    ifRight: suspend (Behandling) -> Unit
) {
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
                    behandlingService.hentBehandling(behandlingId)
                        .mapLeft { this.svar(NotFound.message("Fant ikke behandling med behandlingId:$behandlingId")) }
                        .map {
                            if (it.sakId == sakId) {
                                this.audit("Hentet behandling med id: $behandlingId")
                                ifRight(it)
                            } else {
                                this.svar(NotFound.message("Ugyldig kombinasjon av sak og behandling"))
                            }
                        }
                }
            )
        }
    )
}
