package no.nav.su.se.bakover.web.routes.behandling

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrHandle
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
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.NavIdentBruker.Attestant
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.beregning.Stønadsperiode
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.BrevRequest
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.HentRequest
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.IverksettRequest
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.KunneIkkeBeregne
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.KunneIkkeIverksette
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.KunneIkkeLageBrev
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.KunneIkkeOpprette
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.KunneIkkeSendeTilAttestering
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.KunneIkkeSimulereBehandling
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.KunneIkkeUnderkjenne
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.KunneIkkeVilkårsvurdere
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.OpprettRequest
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.SendTilAttesteringRequest
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.SimulerRequest
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.UnderkjennRequest
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.VilkårsvurderRequest
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.deserialize
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.features.suUserContext
import no.nav.su.se.bakover.web.message
import no.nav.su.se.bakover.web.routes.behandling.beregning.NyBeregningForSøknadsbehandlingJson
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.toUUID
import no.nav.su.se.bakover.web.withBehandlingId
import no.nav.su.se.bakover.web.withBody
import no.nav.su.se.bakover.web.withSakId
import org.slf4j.LoggerFactory

internal const val behandlingPath = "$sakPath/{sakId}/behandlinger"

@KtorExperimentalAPI
internal fun Route.behandlingRoutes(
    søknadsbehandlingService: SøknadsbehandlingService,
) {
    val log = LoggerFactory.getLogger(this::class.java)

    data class OpprettBehandlingBody(val soknadId: String)
    data class WithFritekstBody(val fritekst: String)

    authorize(Brukerrolle.Saksbehandler) {
        post("$sakPath/{sakId}/behandlinger") {
            call.withSakId { sakId ->
                call.withBody<OpprettBehandlingBody> { body ->
                    body.soknadId.toUUID().mapLeft {
                        call.svar(BadRequest.message("soknadId er ikke en gyldig uuid"))
                    }.map { søknadId ->
                        søknadsbehandlingService.opprett(OpprettRequest(søknadId))
                            .fold(
                                {
                                    call.svar(
                                        when (it) {
                                            is KunneIkkeOpprette.FantIkkeSøknad -> {
                                                NotFound.message("Fant ikke søknad med id $søknadId")
                                            }
                                            is KunneIkkeOpprette.SøknadManglerOppgave -> {
                                                InternalServerError.message("Søknad med id $søknadId mangler oppgave")
                                            }
                                            is KunneIkkeOpprette.SøknadHarAlleredeBehandling -> {
                                                BadRequest.message("Søknad med id $søknadId har allerede en behandling")
                                            }
                                            is KunneIkkeOpprette.SøknadErLukket -> {
                                                BadRequest.message("Søknad med id $søknadId er lukket")
                                            }
                                        },
                                    )
                                },
                                {
                                    call.audit("Opprettet behandling på sak: $sakId og søknadId: $søknadId")
                                    call.svar(Created.jsonBody(it))
                                },
                            )
                    }
                }
            }
        }
    }

    authorize(Brukerrolle.Saksbehandler) {
        post("$behandlingPath/{behandlingId}/stønadsperiode") {
            call.withBehandlingId { behandlingId ->
                call.withBody<ValgtStønadsperiodeJson> { body ->
                    body.toDomain()
                        .mapLeft {
                            call.svar(it)
                        }
                        .flatMap { stønadsperiode ->
                            søknadsbehandlingService.oppdaterStønadsperiode(SøknadsbehandlingService.OppdaterStønadsperiodeRequest(behandlingId, stønadsperiode))
                                .mapLeft { error ->
                                    call.svar(
                                        when (error) {
                                            SøknadsbehandlingService.KunneIkkeOppdatereStønadsperiode.FantIkkeBehandling -> {
                                                NotFound.message("Fant ikke behandling")
                                            }
                                            SøknadsbehandlingService.KunneIkkeOppdatereStønadsperiode.FraOgMedDatoKanIkkeVæreFør2021 -> {
                                                BadRequest.message("En stønadsperiode kan ikke starte før 2021")
                                            }
                                            SøknadsbehandlingService.KunneIkkeOppdatereStønadsperiode.PeriodeKanIkkeVæreLengreEnn12Måneder -> {
                                                BadRequest.message("En stønadsperiode kan være maks 12 måneder")
                                            }
                                        },
                                    )
                                }
                                .map {
                                    call.svar(Created.jsonBody(it))
                                }
                        }
                }
            }
        }
    }

    authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
        get("$behandlingPath/{behandlingId}") {
            call.withBehandlingId { behandlingId ->
                søknadsbehandlingService.hent(HentRequest(behandlingId)).mapLeft {
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
                call.withBody<BehandlingsinformasjonJson> { body ->
                    søknadsbehandlingService.vilkårsvurder(
                        VilkårsvurderRequest(
                            behandlingId = behandlingId,
                            saksbehandler = Saksbehandler(call.suUserContext.navIdent),
                            behandlingsinformasjon = behandlingsinformasjonFromJson(body),
                        ),
                    ).mapLeft {
                        call.svar(
                            when (it) {
                                KunneIkkeVilkårsvurdere.FantIkkeBehandling -> {
                                    NotFound.message("Fant ikke behandling")
                                }
                            },
                        )
                    }.map {
                        call.audit("Oppdaterte behandlingsinformasjon med behandlingsid $behandlingId")
                        call.svar(OK.jsonBody(it))
                    }
                }
            }
        }
    }

    authorize(Brukerrolle.Saksbehandler) {
        post("$behandlingPath/{behandlingId}/beregn") {
            call.withBehandlingId { behandlingId ->
                val behandling = søknadsbehandlingService.hent(HentRequest(behandlingId)).getOrHandle {
                    return@withBehandlingId call.svar(NotFound.message("Fant ikke behandling"))
                }
                call.withBody<NyBeregningForSøknadsbehandlingJson> { body ->
                    body.toDomain(behandlingId, Saksbehandler(call.suUserContext.navIdent), Stønadsperiode.create(behandling.periode))
                        .mapLeft { call.svar(it) }
                        .map {
                            søknadsbehandlingService.beregn(
                                SøknadsbehandlingService.BeregnRequest(
                                    behandlingId = it.behandlingId,
                                    fradrag = it.fradrag,
                                    begrunnelse = it.begrunnelse,
                                ),
                            ).mapLeft { kunneIkkeBeregne ->
                                val resultat = when (kunneIkkeBeregne) {
                                    KunneIkkeBeregne.FantIkkeBehandling -> {
                                        NotFound.message("Fant ikke behandling")
                                    }
                                }
                                call.svar(resultat)
                            }.map { behandling ->
                                call.audit("Opprettet en ny beregning på søknadsbehandling med id $behandlingId")
                                call.svar(Created.jsonBody(behandling))
                            }
                        }
                }
            }
        }
    }

    authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
        suspend fun handleBrevRequest(call: ApplicationCall, req: BrevRequest) =
            søknadsbehandlingService.brev(req)
                .fold(
                    {
                        val resultat = when (it) {
                            is KunneIkkeLageBrev.FantIkkeBehandling -> {
                                NotFound.message("Fant ikke behandling")
                            }
                            is KunneIkkeLageBrev.KunneIkkeLagePDF -> {
                                InternalServerError.message("Kunne ikke lage brev")
                            }
                            is KunneIkkeLageBrev.KanIkkeLageBrevutkastForStatus -> {
                                BadRequest.message("Kunne ikke lage brev for behandlingstatus: ${it.status}")
                            }
                            is KunneIkkeLageBrev.FantIkkePerson -> {
                                NotFound.message("Fant ikke person")
                            }
                            is KunneIkkeLageBrev.FikkIkkeHentetSaksbehandlerEllerAttestant -> {
                                InternalServerError.message(
                                    "Klarte ikke hente informasjon om saksbehandler og/eller attestant",
                                )
                            }
                        }
                        call.svar(resultat)
                    },
                    {
                        call.audit("Hentet behandling med id ${req.behandlingId}")
                        call.respondBytes(it, ContentType.Application.Pdf)
                    },
                )

        post("$behandlingPath/{behandlingId}/vedtaksutkast") {
            call.withBehandlingId { behandlingId ->
                call.withBody<WithFritekstBody> { body ->
                    handleBrevRequest(call, BrevRequest.MedFritekst(behandlingId, body.fritekst))
                }
            }
        }
        get("$behandlingPath/{behandlingId}/vedtaksutkast") {
            call.withBehandlingId { behandlingId ->
                handleBrevRequest(call, BrevRequest.UtenFritekst(behandlingId))
            }
        }
    }

    authorize(Brukerrolle.Saksbehandler) {
        post("$behandlingPath/{behandlingId}/simuler") {
            call.withBehandlingId { behandlingId ->
                søknadsbehandlingService.simuler(
                    SimulerRequest(
                        behandlingId = behandlingId,
                        saksbehandler = Saksbehandler(call.suUserContext.navIdent),
                    ),
                ).fold(
                    {
                        val resultat = when (it) {
                            KunneIkkeSimulereBehandling.KunneIkkeSimulere -> {
                                InternalServerError.message("Kunne ikke gjennomføre simulering")
                            }
                            KunneIkkeSimulereBehandling.FantIkkeBehandling -> {
                                NotFound.message("Kunne ikke finne behandling")
                            }
                        }
                        call.svar(resultat)
                    },
                    {
                        call.audit("Oppdatert simulering for behandling med id $behandlingId")
                        call.svar(OK.jsonBody(it))
                    },
                )
            }
        }
    }

    authorize(Brukerrolle.Saksbehandler) {
        post("$behandlingPath/{behandlingId}/tilAttestering") {
            call.withBehandlingId { behandlingId ->
                call.withSakId {
                    call.withBody<WithFritekstBody> { body ->
                        val saksBehandler = Saksbehandler(call.suUserContext.navIdent)
                        søknadsbehandlingService.sendTilAttestering(
                            SendTilAttesteringRequest(
                                behandlingId = behandlingId,
                                saksbehandler = saksBehandler,
                                fritekstTilBrev = body.fritekst,
                            ),
                        ).fold(
                            {
                                val resultat = when (it) {
                                    KunneIkkeSendeTilAttestering.KunneIkkeOppretteOppgave -> {
                                        InternalServerError.message("Kunne ikke opprette oppgave for attestering")
                                    }
                                    KunneIkkeSendeTilAttestering.KunneIkkeFinneAktørId -> {
                                        InternalServerError.message("Kunne ikke finne person")
                                    }
                                    KunneIkkeSendeTilAttestering.FantIkkeBehandling -> {
                                        NotFound.message("Kunne ikke finne behandling")
                                    }
                                }
                                call.svar(resultat)
                            },
                            {
                                call.audit("Sendte behandling med id $behandlingId til attestering")
                                call.svar(OK.jsonBody(it))
                            },
                        )
                    }
                }
            }
        }
    }

    authorize(Brukerrolle.Attestant) {

        fun kunneIkkeIverksetteMelding(value: KunneIkkeIverksette): Resultat {
            return when (value) {
                is KunneIkkeIverksette.AttestantOgSaksbehandlerKanIkkeVæreSammePerson -> {
                    Forbidden.message("Attestant og saksbehandler kan ikke være samme person")
                }
                is KunneIkkeIverksette.KunneIkkeUtbetale -> {
                    InternalServerError.message("Kunne ikke utføre utbetaling")
                }
                is KunneIkkeIverksette.KunneIkkeKontrollsimulere -> {
                    InternalServerError.message("Kunne ikke utføre kontrollsimulering")
                }
                is KunneIkkeIverksette.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte -> {
                    InternalServerError.message(
                        "Oppdaget inkonsistens mellom tidligere utført simulering og kontrollsimulering. Ny simulering må utføres og kontrolleres før iverksetting kan gjennomføres",
                    )
                }
                is KunneIkkeIverksette.KunneIkkeJournalføreBrev -> {
                    InternalServerError.message("Feil ved journalføring av vedtaksbrev")
                }
                is KunneIkkeIverksette.FantIkkeBehandling -> {
                    NotFound.message("Fant ikke behandling")
                }
                is KunneIkkeIverksette.FantIkkePerson -> {
                    NotFound.message("Fant ikke person")
                }
                is KunneIkkeIverksette.FikkIkkeHentetSaksbehandlerEllerAttestant -> {
                    InternalServerError.message(
                        "Klarte ikke hente informasjon om saksbehandler og/eller attestant",
                    )
                }
            }
        }

        patch("$behandlingPath/{behandlingId}/iverksett") {
            call.withBehandlingId { behandlingId ->

                val navIdent = call.suUserContext.navIdent

                søknadsbehandlingService.iverksett(
                    IverksettRequest(
                        behandlingId = behandlingId,
                        attestering = Attestering.Iverksatt(Attestant(navIdent)),
                    ),
                ).fold(
                    {
                        call.svar(kunneIkkeIverksetteMelding(it))
                    },
                    {
                        call.audit("Iverksatte behandling med id: $behandlingId")
                        call.svar(OK.jsonBody(it))
                    },
                )
            }
        }
    }

    data class UnderkjennBody(
        val grunn: String,
        val kommentar: String,
    ) {
        fun valid() = enumContains<Attestering.Underkjent.Grunn>(grunn) && kommentar.isNotBlank()
    }

    authorize(Brukerrolle.Attestant) {
        patch("$behandlingPath/{behandlingId}/underkjenn") {
            val navIdent = call.suUserContext.navIdent

            call.withBehandlingId { behandlingId ->
                Either.catch { deserialize<UnderkjennBody>(call) }.fold(
                    ifLeft = {
                        log.info("Ugyldig behandling-body: ", it)
                        call.svar(BadRequest.message("Ugyldig body"))
                    },
                    ifRight = { body ->
                        if (body.valid()) {
                            søknadsbehandlingService.underkjenn(
                                UnderkjennRequest(
                                    behandlingId = behandlingId,
                                    attestering = Attestering.Underkjent(
                                        attestant = Attestant(navIdent),
                                        grunn = Attestering.Underkjent.Grunn.valueOf(body.grunn),
                                        kommentar = body.kommentar,
                                    ),
                                ),
                            ).fold(
                                ifLeft = {
                                    val resultat = when (it) {
                                        KunneIkkeUnderkjenne.FantIkkeBehandling -> {
                                            NotFound.message("Fant ikke behandling")
                                        }
                                        KunneIkkeUnderkjenne.AttestantOgSaksbehandlerKanIkkeVæreSammePerson -> {
                                            Forbidden.message("Attestant og saksbehandler kan ikke være samme person")
                                        }
                                        KunneIkkeUnderkjenne.KunneIkkeOppretteOppgave -> {
                                            InternalServerError.message("Oppgaven er lukket, men vi kunne ikke opprette oppgave. Prøv igjen senere.")
                                        }
                                        KunneIkkeUnderkjenne.FantIkkeAktørId -> {
                                            InternalServerError.message("Fant ikke aktørid som er knyttet til tokenet")
                                        }
                                    }
                                    call.svar(resultat)
                                },
                                ifRight = {
                                    call.audit("Underkjente behandling med id: $behandlingId")
                                    call.svar(OK.jsonBody(it))
                                },
                            )
                        } else {
                            call.svar(BadRequest.message("Må angi en begrunnelse"))
                        }
                    },
                )
            }
        }
    }
}
