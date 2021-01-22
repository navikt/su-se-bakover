package no.nav.su.se.bakover.web.routes.behandling

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrHandle
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
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.NavIdentBruker.Attestant
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.service.behandling.BehandlingService
import no.nav.su.se.bakover.service.behandling.IverksattBehandling
import no.nav.su.se.bakover.service.behandling.KunneIkkeBeregne
import no.nav.su.se.bakover.service.behandling.KunneIkkeIverksetteBehandling
import no.nav.su.se.bakover.service.behandling.KunneIkkeLageBrevutkast
import no.nav.su.se.bakover.service.behandling.KunneIkkeOppdatereBehandlingsinformasjon
import no.nav.su.se.bakover.service.behandling.KunneIkkeOppretteSøknadsbehandling
import no.nav.su.se.bakover.service.behandling.KunneIkkeSendeTilAttestering
import no.nav.su.se.bakover.service.behandling.KunneIkkeSimulereBehandling
import no.nav.su.se.bakover.service.behandling.KunneIkkeUnderkjenneBehandling
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.deserialize
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.features.suUserContext
import no.nav.su.se.bakover.web.message
import no.nav.su.se.bakover.web.routes.behandling.UgyldigFradrag.Companion.toResultat
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
                                        val resultat: Resultat = when (it) {
                                            is KunneIkkeOppretteSøknadsbehandling.FantIkkeSøknad -> NotFound.message("Fant ikke søknad med id $søknadId")
                                            is KunneIkkeOppretteSøknadsbehandling.SøknadManglerOppgave -> InternalServerError.message(
                                                "Søknad med id $søknadId mangler oppgave"
                                            )
                                            is KunneIkkeOppretteSøknadsbehandling.SøknadHarAlleredeBehandling -> BadRequest.message(
                                                "Søknad med id $søknadId har allerede en behandling"
                                            )
                                            is KunneIkkeOppretteSøknadsbehandling.SøknadErLukket -> BadRequest.message("Søknad med id $søknadId er lukket")
                                        }
                                        call.svar(resultat)
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
            call.withBehandlingId { behandlingId ->
                behandlingService.hentBehandling(behandlingId).mapLeft {
                    call.svar(NotFound.message("Fant ikke behandling med id $behandlingId"))
                }.map {
                    call.audit("Hentet behandling med id $behandlingId")
                    call.svar(OK.jsonBody(it))
                }
            }
        }
    }

    authorize(Brukerrolle.Saksbehandler) {
        patch("$behandlingPath/{behandlingId}/informasjon") {
            call.withBehandlingId { behandlingId ->
                Either.catch { deserialize<BehandlingsinformasjonJson>(call) }.fold(
                    ifLeft = {
                        log.info("Ugylding behandlingsinformasjon-body", it)
                        call.svar(BadRequest.message("Klarte ikke deserialisere body"))
                    },
                    ifRight = { body ->
                        behandlingService.oppdaterBehandlingsinformasjon(
                            behandlingId = behandlingId,
                            saksbehandler = Saksbehandler(call.suUserContext.getNAVIdent()),
                            behandlingsinformasjon = behandlingsinformasjonFromJson(body)
                        ).mapLeft {
                            val resultat = when (it) {
                                KunneIkkeOppdatereBehandlingsinformasjon.AttestantOgSaksbehandlerKanIkkeVæreSammePerson -> BadRequest.message(
                                    "Attestant og saksbehandler kan ikke være samme person"
                                )
                                KunneIkkeOppdatereBehandlingsinformasjon.FantIkkeBehandling -> NotFound.message("Fant ikke behandling")
                            }
                            call.svar(resultat)
                        }.map {
                            call.audit("Oppdaterte behandlingsinformasjon med behandlingsid $behandlingId")
                            call.svar(OK.jsonBody(it))
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
    )

    authorize(Brukerrolle.Saksbehandler) {
        post("$behandlingPath/{behandlingId}/beregn") {
            call.withBehandlingId { behandlingId ->
                Either.catch { deserialize<OpprettBeregningBody>(call) }.fold(
                    ifLeft = {
                        log.info("Ugyldig behandling-body: ", it)
                        call.svar(BadRequest.message("Ugyldig body"))
                    },
                    ifRight = { body ->
                        val beregningsperiode = Periode.tryCreate(body.fraOgMed, body.tilOgMed).getOrHandle {
                            call.svar(it.toResultat().resultat)
                            return@withBehandlingId
                        }
                        // TODO en hack for å stoppe perioder før 2021, da Periode() ikke kan endres enda
                        if (beregningsperiode.getFraOgMed().isBefore(LocalDate.of(2021, 1, 1))) {
                            call.svar(BadRequest.message("En stønadsperiode kan ikke starte før 2021"))
                            return@withBehandlingId
                        }
                        val fradrag: List<Fradrag> = body.fradrag.map {
                            it.toFradrag(beregningsperiode = beregningsperiode).getOrHandle { ugyldigFradrag ->
                                call.svar(ugyldigFradrag.resultat)
                                return@withBehandlingId
                            }
                        }
                        behandlingService.opprettBeregning(
                            behandlingId = behandlingId,
                            saksbehandler = Saksbehandler(call.suUserContext.getNAVIdent()),
                            periode = beregningsperiode,
                            fradrag = fradrag
                        ).mapLeft {
                            val resultat = when (it) {
                                KunneIkkeBeregne.FantIkkeBehandling -> NotFound.message("Fant ikke behandling")
                                KunneIkkeBeregne.AttestantOgSaksbehandlerKanIkkeVæreSammePerson -> BadRequest.message(
                                    "Attestant og saksbehandler kan ikke være samme person"
                                )
                            }
                            call.svar(resultat)
                        }.map {
                            call.audit("Opprettet en ny beregning på behandling med id $behandlingId")
                            call.svar(Created.jsonBody(it))
                        }

                    }
                )
            }
        }
    }

    authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
        get("$behandlingPath/{behandlingId}/utledetSatsInfo") {
            call.withBehandlingId { behandlingId ->
                behandlingService.hentBehandling(behandlingId).mapLeft {
                    call.svar(NotFound.message("Fant ikke behandling"))
                }.map {
                    call.audit("Hentet utledet sats informasjon for behandling med id $behandlingId")
                    call.svar(Resultat.json(OK, serialize(it.toUtledetSatsInfoJson())))
                }
            }
        }
    }

    authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
        get("$behandlingPath/{behandlingId}/vedtaksutkast") {
            call.withBehandlingId { behandlingId ->
                behandlingService.lagBrevutkast(behandlingId).fold(
                    {
                        val resultat = when (it) {
                            is KunneIkkeLageBrevutkast.FantIkkeBehandling -> NotFound.message("Fant ikke behandling")
                            is KunneIkkeLageBrevutkast.KunneIkkeLageBrev -> InternalServerError.message("Kunne ikke lage brev")
                            is KunneIkkeLageBrevutkast.KanIkkeLageBrevutkastForStatus -> BadRequest.message("Kunne ikke lage brev for behandlingstatus: ${it.status}")
                            is KunneIkkeLageBrevutkast.FantIkkePerson -> NotFound.message("Fant ikke person")
                            is KunneIkkeLageBrevutkast.FikkIkkeHentetSaksbehandlerEllerAttestant -> InternalServerError.message(
                                "Klarte ikke hente informasjon om saksbehandler og/eller attestant"
                            )
                        }
                        call.svar(resultat)
                    },
                    {
                        call.audit("Hentet behandling med id $behandlingId")
                        call.respondBytes(it, ContentType.Application.Pdf)
                    }
                )
            }
        }
    }

    authorize(Brukerrolle.Saksbehandler) {
        post("$behandlingPath/{behandlingId}/simuler") {
            call.withBehandlingId { behandlingId ->
                behandlingService.simuler(behandlingId, Saksbehandler(call.suUserContext.getNAVIdent())).fold(
                    {
                        val resultat = when (it) {
                            KunneIkkeSimulereBehandling.KunneIkkeSimulere -> InternalServerError.message("Kunne ikke gjennomføre simulering")
                            KunneIkkeSimulereBehandling.AttestantOgSaksbehandlerKanIkkeVæreSammePerson -> BadRequest.message(
                                "Attestant og saksbehandler kan ikke være samme person"
                            )
                            KunneIkkeSimulereBehandling.FantIkkeBehandling -> NotFound.message("Kunne ikke finne behandling")
                        }
                        call.svar(resultat)
                    },
                    {
                        call.audit("Oppdatert simulering for behandling med id $behandlingId")
                        call.svar(OK.jsonBody(it))
                    }
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
                                KunneIkkeSendeTilAttestering.AttestantOgSaksbehandlerKanIkkeVæreSammePerson -> BadRequest.message(
                                    "Attestant og saksbehandler kan ikke være samme person"
                                )
                                KunneIkkeSendeTilAttestering.FantIkkeBehandling -> NotFound.message("Kunne ikke finne behandling")
                            }
                            call.svar(resultat)
                        },
                        {
                            call.audit("Sendte behandling med id ${it.id} til attestering")
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
                is KunneIkkeIverksetteBehandling.FantIkkePerson -> NotFound.message("Fant ikke person")
                is KunneIkkeIverksetteBehandling.FikkIkkeHentetSaksbehandlerEllerAttestant -> InternalServerError.message(
                    "Klarte ikke hente informasjon om saksbehandler og/eller attestant"
                )
            }
        }

        fun iverksattMelding(value: IverksattBehandling): Resultat {
            return when (value) {
                // TODO jah: Vurdere om vi skal legge på manglene i json-responsen. Vurdere Multi-respons.
                is IverksattBehandling.UtenMangler -> OK.jsonBody(value.behandling)
                is IverksattBehandling.MedMangler.KunneIkkeLukkeOppgave -> OK.jsonBody(value.behandling)
                is IverksattBehandling.MedMangler.KunneIkkeDistribuereBrev -> OK.jsonBody(value.behandling)
            }
        }

        patch("$behandlingPath/{behandlingId}/iverksett") {
            call.withBehandlingId { behandlingId ->

                val navIdent = call.suUserContext.getNAVIdent()

                behandlingService.iverksett(
                    behandlingId = behandlingId,
                    attestant = Attestant(navIdent)
                ).fold(
                    {
                        call.svar(kunneIkkeIverksetteMelding(it))
                    },
                    {
                        call.audit("Iverksatte behandling med id: $behandlingId")
                        call.svar(iverksattMelding(it))
                    }
                )
            }
        }
    }
    data class UnderkjennBody(
        val grunn: String,
        val kommentar: String
    ) {
        fun valid() = enumContains<Attestering.Underkjent.Grunn>(grunn) && kommentar.isNotBlank()
    }

    authorize(Brukerrolle.Attestant) {
        patch("$behandlingPath/{behandlingId}/underkjenn") {
            val navIdent = call.suUserContext.getNAVIdent()

            call.withBehandlingId { behandlingId ->
                Either.catch { deserialize<UnderkjennBody>(call) }.fold(
                    ifLeft = {
                        log.info("Ugyldig behandling-body: ", it)
                        call.svar(BadRequest.message("Ugyldig body"))
                    },
                    ifRight = { body ->
                        if (body.valid()) {
                            behandlingService.underkjenn(
                                behandlingId = behandlingId,
                                attestering = Attestering.Underkjent(
                                    attestant = Attestant(navIdent),
                                    grunn = Attestering.Underkjent.Grunn.valueOf(body.grunn),
                                    kommentar = body.kommentar
                                )
                            ).fold(
                                ifLeft = {
                                    val resultat = when (it) {
                                        KunneIkkeUnderkjenneBehandling.FantIkkeBehandling -> NotFound.message("Fant ikke behandling")
                                        KunneIkkeUnderkjenneBehandling.AttestantOgSaksbehandlerKanIkkeVæreSammePerson -> Forbidden.message(
                                            "Attestant og saksbehandler kan ikke være samme person"
                                        )
                                        KunneIkkeUnderkjenneBehandling.KunneIkkeOppretteOppgave -> InternalServerError.message(
                                            "Oppgaven er lukket, men vi kunne ikke opprette oppgave. Prøv igjen senere."
                                        )
                                        KunneIkkeUnderkjenneBehandling.FantIkkeAktørId -> InternalServerError.message(
                                            "Fant ikke aktørid som er knyttet til tokenet"
                                        )
                                    }
                                    call.svar(resultat)
                                },
                                ifRight = {
                                    call.audit("Underkjente behandling med id: $behandlingId")
                                    call.svar(OK.jsonBody(it))
                                }
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
