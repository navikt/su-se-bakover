package no.nav.su.se.bakover.web.routes.behandling

import arrow.core.Either
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
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Attestant
import no.nav.su.se.bakover.domain.Behandling
import no.nav.su.se.bakover.domain.Behandling.IverksettFeil.AttestantOgSaksbehandlerErLik
import no.nav.su.se.bakover.domain.Saksbehandler
import no.nav.su.se.bakover.domain.beregning.Fradragstype
import no.nav.su.se.bakover.service.behandling.BehandlingService
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.deserialize
import no.nav.su.se.bakover.web.erAttestant
import no.nav.su.se.bakover.web.features.suUserContext
import no.nav.su.se.bakover.web.lesUUID
import no.nav.su.se.bakover.web.message
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withBehandlingId
import no.nav.su.se.bakover.web.withSakId
import org.slf4j.LoggerFactory
import java.time.LocalDate

internal const val behandlingPath = "$sakPath/{sakId}/behandlinger"

internal fun Route.behandlingRoutes(
    brevService: BrevService,
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
                        call.svar(
                            OK.jsonBody(
                                behandlingService.oppdaterBehandlingsinformasjon(
                                    behandlingId = behandling.id,
                                    behandlingsinformasjon = behandlingsinformasjonFromJson(body)
                                )
                            )
                        )
                    } else {
                        call.svar(BadRequest.message("Data i behandlingsinformasjon er ugyldig"))
                    }
                }
            )
        }
    }

    data class OpprettBeregningBody(
        val fraOgMed: LocalDate,
        val tilOgMed: LocalDate,
        val fradrag: List<FradragJson>
    ) {
        fun valid() = fraOgMed.dayOfMonth == 1 &&
            tilOgMed.dayOfMonth == tilOgMed.lengthOfMonth() &&
            fradrag.all {
                Fradragstype.isValid(it.type) &&
                    it.utenlandskInntekt?.isValid() ?: true &&
                    it.inntektDelerAvPeriode?.isValid() ?: true
            }
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
                        call.svar(
                            Created.jsonBody(
                                behandlingService.opprettBeregning(
                                    behandlingId = behandling.id,
                                    fraOgMed = body.fraOgMed,
                                    tilOgMed = body.tilOgMed,
                                    fradrag = body.fradrag.map { it.toFradrag() }
                                )
                            )
                        )
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
                { call.svar(OK.jsonBody(it)) }
            )
        }
    }

    post("$behandlingPath/{behandlingId}/tilAttestering") {
        call.withBehandlingId { behandlingId ->
            call.withSakId { sakId ->
                val saksBehandler = Saksbehandler(call.suUserContext.getNAVIdent())
                behandlingService.sendTilAttestering(sakId, behandlingId, saksBehandler).fold(
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
            val navIdent = call.suUserContext.getNAVIdent()

            sakService.hentSak(behandling.sakId)
                .mapLeft { throw RuntimeException("Sak id finnes ikke") }
                .map { sak ->
                    // TODO jah: Ignorerer resultatet her inntil videre og attesterer uansett.
                    brevService.journalførVedtakOgSendBrev(sak, behandling).fold(
                        ifLeft = { call.svar(InternalServerError.message("Feilet ved attestering")) },
                        ifRight = {
                            behandlingService.iverksett(
                                behandlingId = behandling.id,
                                attestant = Attestant(navIdent)
                            ).fold(
                                {
                                    when (it) {
                                        is AttestantOgSaksbehandlerErLik -> call.svar(Forbidden.message(it.msg))
                                        is Behandling.IverksettFeil.Utbetaling -> call.svar(InternalServerError.message(it.msg))
                                        is Behandling.IverksettFeil.KunneIkkeSimulere -> call.svar(InternalServerError.message(it.msg))
                                        is Behandling.IverksettFeil.InkonsistentSimuleringsResultat -> call.svar(InternalServerError.message(it.msg))
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
        val navIdent = call.suUserContext.getNAVIdent()

        call.withBehandling(behandlingService) { behandling ->
            call.audit("behandling med id: ${behandling.id} godkjennes ikke")
            // TODO jah: Ignorerer resultatet her inntil videre og attesterer uansett.

            Either.catch { deserialize<UnderkjennBody>(call) }.fold(
                ifLeft = {
                    log.info("Ugyldig behandling-body: ", it)
                    call.svar(BadRequest.message("Ugyldig body"))
                },
                ifRight = { body ->
                    if (body.valid()) {
                        behandlingService.underkjenn(
                            begrunnelse = body.begrunnelse,
                            attestant = Attestant(navIdent),
                            behandling = behandling
                        ).fold(
                            ifLeft = {
                                call.svar(Forbidden.message(it.msg))
                            },
                            ifRight = { call.svar(OK.jsonBody(it)) }
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
