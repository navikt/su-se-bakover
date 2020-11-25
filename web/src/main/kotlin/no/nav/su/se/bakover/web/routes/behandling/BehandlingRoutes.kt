package no.nav.su.se.bakover.web.routes.behandling

import arrow.core.Either
import arrow.core.flatMap
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
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.service.behandling.BehandlingService
import no.nav.su.se.bakover.service.behandling.IverksattBehandling
import no.nav.su.se.bakover.service.behandling.KunneIkkeBeregne
import no.nav.su.se.bakover.service.behandling.KunneIkkeIverksetteBehandling
import no.nav.su.se.bakover.service.behandling.KunneIkkeLageBrevutkast
import no.nav.su.se.bakover.service.behandling.KunneIkkeOppretteSøknadsbehandling
import no.nav.su.se.bakover.service.behandling.KunneIkkeSendeTilAttestering
import no.nav.su.se.bakover.service.behandling.KunneIkkeSimulereBehandling
import no.nav.su.se.bakover.service.behandling.KunneIkkeUnderkjenneBehandling
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.deserialize
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.features.suUserContext
import no.nav.su.se.bakover.web.lesUUID
import no.nav.su.se.bakover.web.message
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.toUUID
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

    data class OpprettBehandlingBody(val soknadId: String)

    authorize(Brukerrolle.Saksbehandler) {
        post("$sakPath/{sakId}/behandlinger") {
            call.withSakId { sakId ->
                Either.catch { deserialize<OpprettBehandlingBody>(call) }
                    .flatMap { it.soknadId.toUUID() }
                    .fold(
                        ifLeft = { call.svar(BadRequest.message("Ugyldig body")) },
                        ifRight = { søknadId ->
                            behandlingService.opprettSøknadsbehandling(søknadId)
                                .fold(
                                    {
                                        val error: Resultat = when (it) {
                                            is KunneIkkeOppretteSøknadsbehandling.FantIkkeSøknad -> {
                                                log.info("Fant ikke søknad med id:$søknadId")
                                                NotFound.message(
                                                    "Fant ikke søknad med id:$søknadId"
                                                )
                                            }
                                            is KunneIkkeOppretteSøknadsbehandling.SøknadManglerOppgave -> {
                                                log.info("Søknad med id $søknadId mangler oppgave")
                                                InternalServerError.message(
                                                    "Søknad med id $søknadId mangler oppgave"
                                                )
                                            }
                                            is KunneIkkeOppretteSøknadsbehandling.SøknadHarAlleredeBehandling -> {
                                                log.info("Søknad med id $søknadId har allerede en behandling")
                                                BadRequest.message(
                                                    "Søknad med id $søknadId har allerede en behandling"
                                                )
                                            }
                                            is KunneIkkeOppretteSøknadsbehandling.SøknadErLukket -> {
                                                log.info("Søknad med id $søknadId er lukket")
                                                BadRequest.message(
                                                    "Søknad med id $søknadId er lukket"
                                                )
                                            }
                                        }
                                        call.svar(error)
                                    },
                                    {
                                        call.audit("Opprettet behandling på sak: $sakId og søknadId: $søknadId")
                                        call.svar(Created.jsonBody(it))
                                    }
                                )
                        }
                    )
            }
        }
    }

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
                Fradragstype.isValid(it.type) &&
                    enumContains<FradragTilhører>(it.tilhører) &&
                    it.utenlandskInntekt?.isValid() ?: true
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
                            behandlingService.opprettBeregning(
                                saksbehandler = Saksbehandler(call.suUserContext.getNAVIdent()),
                                behandlingId = behandling.id,
                                fraOgMed = body.fraOgMed,
                                tilOgMed = body.tilOgMed,
                                fradrag = body.fradrag.map {
                                    it.toFradrag(
                                        Periode(
                                            body.fraOgMed,
                                            body.tilOgMed
                                        )
                                    )
                                }
                            ).mapLeft {
                                val resultat = when (it) {
                                    KunneIkkeBeregne.FantIkkeBehandling -> NotFound.message("Fant ikke behandling")
                                    KunneIkkeBeregne.AttestantOgSaksbehandlerKanIkkeVæreSammePerson -> BadRequest.message("Attestant og saksbehandler kan ikke være like")
                                }
                                call.svar(resultat)
                            }.map {
                                call.svar(
                                    Created.jsonBody(
                                        it
                                    )
                                )
                            }
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
                        val resultat = when (it) {
                            KunneIkkeSimulereBehandling.KunneIkkeSimulere -> InternalServerError.message("Kunne ikke gjennomføre simulering")
                            KunneIkkeSimulereBehandling.AttestantOgSaksbehandlerKanIkkeVæreSammePerson -> BadRequest.message("Attestant og saksbehandler kan ikke være samme person")
                            KunneIkkeSimulereBehandling.FantIkkeBehandling -> NotFound.message("Kunne ikke finne behandling")
                        }
                        call.svar(resultat)
                    },
                    { call.svar(OK.jsonBody(it)) }
                )
            }
        }
    }

    authorize(Brukerrolle.Saksbehandler) {
        post("$behandlingPath/{behandlingId}/tilAttestering") {
            call.withBehandlingId { behandlingId ->
                call.withSakId {
                    val saksBehandler = Saksbehandler(call.suUserContext.getNAVIdent())
                    behandlingService.sendTilAttestering(behandlingId, saksBehandler).fold(
                        {
                            val resultat = when (it) {
                                KunneIkkeSendeTilAttestering.KunneIkkeOppretteOppgave -> InternalServerError.message("Kunne ikke opprette oppgave for attestering")
                                KunneIkkeSendeTilAttestering.KunneIkkeFinneAktørId -> InternalServerError.message("Kunne ikke finne person")
                                KunneIkkeSendeTilAttestering.AttestantOgSaksbehandlerKanIkkeVæreSammePerson -> BadRequest.message("Attestant og saksbehandler kan ikke være samme person")
                                KunneIkkeSendeTilAttestering.FantIkkeBehandling -> NotFound.message("Kunne ikke finne behandling")
                            }
                            call.svar(resultat)
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

        fun kunneIkkeIverksetteMelding(value: KunneIkkeIverksetteBehandling): Resultat {
            return when (value) {
                is KunneIkkeIverksetteBehandling.AttestantOgSaksbehandlerKanIkkeVæreSammePerson -> Forbidden.message("Attestant og saksbehandler kan ikke være samme person")
                is KunneIkkeIverksetteBehandling.KunneIkkeUtbetale -> InternalServerError.message("Kunne ikke utføre utbetaling")
                is KunneIkkeIverksetteBehandling.KunneIkkeKontrollsimulere -> InternalServerError.message("Kunne ikke utføre kontrollsimulering")
                is KunneIkkeIverksetteBehandling.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte -> InternalServerError.message(
                    "Oppdaget inkonsistens mellom tidligere utført simulering og kontrollsimulering. Ny simulering må utføres og kontrolleres før iverksetting kan gjennomføres"
                )
                is KunneIkkeIverksetteBehandling.KunneIkkeJournalføreBrev -> InternalServerError.message("Feil ved journalføring av vedtaksbrev")
                is KunneIkkeIverksetteBehandling.FantIkkeBehandling -> NotFound.message("Fant ikke behandling")
            }
        }

        fun iverksattMelding(value: IverksattBehandling): Resultat {
            return when (value) {
                // TODO jah: Vurdere om vi skal legge på manglene i json-responsen. Vurdere Multi-respons.
                is IverksattBehandling.UtenMangler -> OK.jsonBody(value.behandling)
                is IverksattBehandling.MedMangler.KunneIkkeLukkeOppgave -> OK.jsonBody(value.behandling)
                is IverksattBehandling.MedMangler.KunneIkkeDistribuereBrev -> OK.jsonBody(value.behandling)
                is IverksattBehandling.MedMangler.KunneIkkeJournalføreBrev -> OK.jsonBody(value.behandling)
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
                    { call.svar(iverksattMelding(it)) }
                )
            }
        }
    }

    authorize(Brukerrolle.Attestant) {
        patch("$behandlingPath/{behandlingId}/underkjenn") {
            val navIdent = call.suUserContext.getNAVIdent()

            call.withBehandlingId { behandlingId ->
                call.audit("behandling med id: $behandlingId godkjennes ikke")
                // TODO jah: Ignorerer resultatet her inntil videre og attesterer uansett.

                Either.catch { deserialize<UnderkjennBody>(call) }.fold(
                    ifLeft = {
                        log.info("Ugyldig behandling-body: ", it)
                        call.svar(BadRequest.message("Ugyldig body"))
                    },
                    ifRight = { body ->
                        if (body.valid()) {
                            behandlingService.underkjenn(
                                behandlingId = behandlingId,
                                attestant = Attestant(navIdent),
                                begrunnelse = body.begrunnelse
                            ).fold(
                                ifLeft = {
                                    fun kunneIkkeUnderkjenneFeilmelding(feil: KunneIkkeUnderkjenneBehandling): Resultat {
                                        return when (feil) {
                                            KunneIkkeUnderkjenneBehandling.FantIkkeBehandling -> NotFound.message("Fant ikke behandling")
                                            KunneIkkeUnderkjenneBehandling.AttestantOgSaksbehandlerKanIkkeVæreSammePerson -> Forbidden.message(
                                                "Attestant og saksbehandler kan ikke vare samme person."
                                            )
                                            KunneIkkeUnderkjenneBehandling.KunneIkkeOppretteOppgave -> InternalServerError.message(
                                                "Oppgaven er lukket, men vi kunne ikke opprette oppgave. Prøv igjen senere."
                                            )
                                            KunneIkkeUnderkjenneBehandling.FantIkkeAktørId -> InternalServerError.message(
                                                "Fant ikke aktørid som er knyttet til tokenet"
                                            )
                                        }
                                    }
                                    call.svar(kunneIkkeUnderkjenneFeilmelding(it))
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
