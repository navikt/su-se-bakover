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
import io.ktor.response.respond
import io.ktor.response.respondBytes
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.patch
import io.ktor.routing.post
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.NavIdentBruker.Attestant
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.Behandling.KunneIkkeIverksetteBehandling.AttestantOgSaksbehandlerKanIkkeVæreLik
import no.nav.su.se.bakover.domain.behandling.Behandling.KunneIkkeIverksetteBehandling.FantIkkeAktørId
import no.nav.su.se.bakover.domain.behandling.Behandling.KunneIkkeIverksetteBehandling.FantIkkeBehandling
import no.nav.su.se.bakover.domain.behandling.Behandling.KunneIkkeIverksetteBehandling.KunneIkkeDistribuereBrev
import no.nav.su.se.bakover.domain.behandling.Behandling.KunneIkkeIverksetteBehandling.KunneIkkeJournalføreBrev
import no.nav.su.se.bakover.domain.behandling.Behandling.KunneIkkeIverksetteBehandling.KunneIkkeKontrollsimulere
import no.nav.su.se.bakover.domain.behandling.Behandling.KunneIkkeIverksetteBehandling.KunneIkkeUtbetale
import no.nav.su.se.bakover.domain.behandling.Behandling.KunneIkkeIverksetteBehandling.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.service.behandling.BehandlingService
import no.nav.su.se.bakover.service.behandling.KunneIkkeLageBrevutkast
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.deserialize
import no.nav.su.se.bakover.web.features.authorize
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

@KtorExperimentalAPI
internal fun Route.behandlingRoutes(
    behandlingService: BehandlingService
) {
    val log = LoggerFactory.getLogger(this::class.java)

    authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
        get("$behandlingPath/{behandlingId}") {
            call.withBehandling(behandlingService) {
                call.svar(OK.jsonBody(it))
            }
        }
    }

    authorize(Brukerrolle.Saksbehandler) {
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
                            // TODO (CHM): Her burde vi prøve å logge ut hvilken del av body som ikke er gyldig
                            call.svar(BadRequest.message("Data i behandlingsinformasjon er ugyldig"))
                        }
                    }
                )
            }
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
                Fradragstype.isValid(it.type) && it.utenlandskInntekt?.isValid() ?: true
            }
    }

    data class UnderkjennBody(
        val begrunnelse: String
    ) {
        fun valid() = begrunnelse.isNotBlank()
    }

    authorize(Brukerrolle.Saksbehandler) {
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
                                        fradrag = body.fradrag.map { it.toFradrag(Periode(body.fraOgMed, body.tilOgMed)) }
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
    }

    authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
        get("$behandlingPath/{behandlingId}/utledetSatsInfo") {
            call.withBehandling(behandlingService) { behandling ->
                call.svar(Resultat.json(OK, serialize(behandling.toUtledetSatsInfoJson())))
            }
        }
    }

    authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
        get("$behandlingPath/{behandlingId}/vedtaksutkast") {
            call.withBehandlingId { behandlingId ->
                behandlingService.lagBrevutkast(behandlingId).fold(
                    {
                        when (it) {
                            KunneIkkeLageBrevutkast.FantIkkeBehandling -> call.respond(InternalServerError.message("Fant ikke behandling"))
                            KunneIkkeLageBrevutkast.KunneIkkeLageBrev -> call.respond(InternalServerError.message("Kunne ikke lage brev"))
                        }
                    },
                    { call.respondBytes(it, ContentType.Application.Pdf) }
                )
            }
        }
    }

    authorize(Brukerrolle.Saksbehandler) {
        post("$behandlingPath/{behandlingId}/simuler") {
            call.withBehandling(behandlingService) { behandling ->
                behandlingService.simuler(behandling.id, Saksbehandler(call.suUserContext.getNAVIdent())).fold(
                    {
                        log.info("Feil ved simulering: ", it)
                        call.svar(InternalServerError.message("Kunne ikke gjennomføre simulering"))
                    },
                    { call.svar(OK.jsonBody(it)) }
                )
            }
        }
    }

    authorize(Brukerrolle.Saksbehandler) {
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
    }

    authorize(Brukerrolle.Attestant) {

        fun kunneIkkeIverksetteMelding(feil: Behandling.KunneIkkeIverksetteBehandling): Resultat {
            // funksjon + return: Triks for å få exhaustive when
            return when (feil) {
                is AttestantOgSaksbehandlerKanIkkeVæreLik -> Forbidden.message("Attestant og saksbehandler kan ikke være samme person")
                is KunneIkkeUtbetale -> InternalServerError.message("Kunne ikke utføre utbetaling")
                is KunneIkkeKontrollsimulere -> InternalServerError.message("Kunne ikke utføre kontrollsimulering")
                is SimuleringHarBlittEndretSidenSaksbehandlerSimulerte -> InternalServerError.message("Oppdaget inkonsistens mellom tidligere utført simulering og kontrollsimulering. Ny simulering må utføres og kontrolleres før iverksetting kan gjennomføres")
                is KunneIkkeJournalføreBrev -> InternalServerError.message("Feil ved journalføring av vedtaksbrev")
                is KunneIkkeDistribuereBrev -> InternalServerError.message("Feil ved bestilling av distribusjon for vedtaksbrev")
                is FantIkkeAktørId -> InternalServerError.message("Fant ikke aktør-id")
                is FantIkkeBehandling -> NotFound.message("Fant ikke behandling")
            }
        }

        patch("$behandlingPath/{behandlingId}/iverksett") {
            call.withBehandlingId { behandlingId ->
                call.audit("Iverksetter behandling med id: $behandlingId")
                val navIdent = call.suUserContext.getNAVIdent()

                behandlingService.iverksett(
                    behandlingId = behandlingId,
                    attestant = Attestant(navIdent)
                ).fold(
                    { call.svar(kunneIkkeIverksetteMelding(it)) },
                    { call.svar(OK.jsonBody(it)) }
                )
            }
        }
    }

    authorize(Brukerrolle.Attestant) {
        patch("$behandlingPath/{behandlingId}/underkjenn") {
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
                            call.svar(BadRequest.message("Må angi en begrunnelse"))
                        }
                    }
                )
            }
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
