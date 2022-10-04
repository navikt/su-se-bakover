package no.nav.su.se.bakover.web.routes.søknadsbehandling

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.right
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Created
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.NavIdentBruker.Attestant
import no.nav.su.se.bakover.common.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.enumContains
import no.nav.su.se.bakover.common.metrics.SuMetrics
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeIverksette
import no.nav.su.se.bakover.service.brev.KunneIkkeLageDokument
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.BeregnRequest
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.BrevRequest
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.HentRequest
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.IverksettRequest
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.KunneIkkeBeregne
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.KunneIkkeOpprette
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.KunneIkkeSendeTilAttestering
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.KunneIkkeUnderkjenne
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.OpprettRequest
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.SendTilAttesteringRequest
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.SimulerRequest
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.UnderkjennRequest
import no.nav.su.se.bakover.web.AuditLogEvent
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.deserialize
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.features.suUserContext
import no.nav.su.se.bakover.web.routes.Feilresponser
import no.nav.su.se.bakover.web.routes.Feilresponser.Brev.kanIkkeSendeBrevIDenneTilstanden
import no.nav.su.se.bakover.web.routes.Feilresponser.Brev.kunneIkkeGenerereBrev
import no.nav.su.se.bakover.web.routes.Feilresponser.attestantOgSaksbehandlerKanIkkeVæreSammePerson
import no.nav.su.se.bakover.web.routes.Feilresponser.avkortingErAlleredeAnnullert
import no.nav.su.se.bakover.web.routes.Feilresponser.avkortingErAlleredeAvkortet
import no.nav.su.se.bakover.web.routes.Feilresponser.avkortingErUfullstendig
import no.nav.su.se.bakover.web.routes.Feilresponser.fantIkkeBehandling
import no.nav.su.se.bakover.web.routes.Feilresponser.fantIkkeGjeldendeUtbetaling
import no.nav.su.se.bakover.web.routes.Feilresponser.fantIkkePerson
import no.nav.su.se.bakover.web.routes.Feilresponser.fantIkkeSak
import no.nav.su.se.bakover.web.routes.Feilresponser.fantIkkeSaksbehandlerEllerAttestant
import no.nav.su.se.bakover.web.routes.Feilresponser.feilVedGenereringAvDokument
import no.nav.su.se.bakover.web.routes.Feilresponser.kunneIkkeSimulere
import no.nav.su.se.bakover.web.routes.Feilresponser.lagringFeilet
import no.nav.su.se.bakover.web.routes.Feilresponser.sakAvventerKravgrunnlagForTilbakekreving
import no.nav.su.se.bakover.web.routes.Feilresponser.tilResultat
import no.nav.su.se.bakover.web.routes.Feilresponser.ugyldigTilstand
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.routes.søknad.tilResultat
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.StønadsperiodeJson
import no.nav.su.se.bakover.web.sikkerlogg
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.toUUID
import no.nav.su.se.bakover.web.withBehandlingId
import no.nav.su.se.bakover.web.withBody
import no.nav.su.se.bakover.web.withSakId
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.UUID

internal const val behandlingPath = "$sakPath/{sakId}/behandlinger"

internal fun Route.søknadsbehandlingRoutes(
    søknadsbehandlingService: SøknadsbehandlingService,
    clock: Clock,
    satsFactory: SatsFactory,
) {
    val log = LoggerFactory.getLogger(this::class.java)

    data class OpprettBehandlingBody(val soknadId: String)
    data class WithFritekstBody(val fritekst: String)

    post("$sakPath/{sakId}/behandlinger") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withSakId { sakId ->
                call.withBody<OpprettBehandlingBody> { body ->
                    body.soknadId.toUUID().mapLeft {
                        call.svar(BadRequest.errorJson("soknadId er ikke en gyldig uuid", "ikke_gyldig_uuid"))
                    }.map { søknadId ->
                        søknadsbehandlingService.opprett(
                            OpprettRequest(
                                søknadId = søknadId,
                                sakId = sakId,
                                saksbehandler = call.suUserContext.saksbehandler,
                            ),
                        ).fold(
                            {
                                call.svar(
                                    when (it) {
                                        is KunneIkkeOpprette.KunneIkkeOppretteSøknadsbehandling -> it.feil.tilResultat()
                                        KunneIkkeOpprette.FantIkkeSak -> Feilresponser.fantIkkeSak
                                    },
                                )
                            },
                            {
                                call.sikkerlogg("Opprettet behandling på sak: $sakId og søknadId: $søknadId")
                                call.audit(it.fnr, AuditLogEvent.Action.CREATE, it.id)
                                SuMetrics.behandlingStartet(SuMetrics.Behandlingstype.SØKNAD)
                                call.svar(Created.jsonBody(it, satsFactory))
                            },
                        )
                    }
                }
            }
        }
    }

    post("$behandlingPath/{behandlingId}/stønadsperiode") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withSakId { sakId ->
                call.withBehandlingId { behandlingId ->
                    call.withBody<StønadsperiodeJson> { body ->
                        body.toStønadsperiode().mapLeft {
                            call.svar(it)
                        }.flatMap { stønadsperiode ->
                            søknadsbehandlingService.oppdaterStønadsperiode(
                                SøknadsbehandlingService.OppdaterStønadsperiodeRequest(
                                    behandlingId = behandlingId,
                                    stønadsperiode = stønadsperiode,
                                    sakId = sakId,
                                ),
                            ).mapLeft { error ->
                                call.svar(
                                    when (error) {
                                        SøknadsbehandlingService.KunneIkkeOppdatereStønadsperiode.FantIkkeBehandling -> {
                                            fantIkkeBehandling
                                        }

                                        SøknadsbehandlingService.KunneIkkeOppdatereStønadsperiode.FantIkkeSak -> {
                                            fantIkkeSak
                                        }

                                        is SøknadsbehandlingService.KunneIkkeOppdatereStønadsperiode.KunneIkkeOppdatereStønadsperiode -> {
                                            when (val feil = error.feil) {
                                                Sak.KunneIkkeOppdatereStønadsperiode.FantIkkeBehandling -> {
                                                    fantIkkeBehandling
                                                }

                                                is Sak.KunneIkkeOppdatereStønadsperiode.KunneIkkeOppdatereGrunnlagsdata -> {
                                                    log.error("Feil ved oppdatering av stønadsperiode: ${feil.feil}")
                                                    InternalServerError.errorJson(
                                                        "Feil ved oppdatering av stønadsperiode",
                                                        "oppdatering_av_stønadsperiode",
                                                    )
                                                }

                                                Sak.KunneIkkeOppdatereStønadsperiode.StønadsperiodeForSenerePeriodeEksisterer -> {
                                                    BadRequest.errorJson(
                                                        "Kan ikke legge til ny stønadsperiode forut for eksisterende stønadsperioder",
                                                        "senere_stønadsperioder_eksisterer",
                                                    )
                                                }

                                                Sak.KunneIkkeOppdatereStønadsperiode.StønadsperiodeOverlapperMedLøpendeStønadsperiode -> {
                                                    BadRequest.errorJson(
                                                        "Stønadsperioden overlapper med eksisterende stønadsperiode",
                                                        "stønadsperioden_overlapper_med_eksisterende_søknadsbehandling",
                                                    )
                                                }

                                                is Sak.KunneIkkeOppdatereStønadsperiode.KunneIkkeHenteGjeldendeVedtaksdata -> {
                                                    InternalServerError.errorJson(
                                                        "Kunne ikke hente gjeldende vedtaksdata",
                                                        "kunne_ikke_hente_gjeldende_vedtaksdata",
                                                    )
                                                }

                                                Sak.KunneIkkeOppdatereStønadsperiode.StønadsperiodeInneholderAvkortingPgaUtenlandsopphold -> {
                                                    BadRequest.errorJson(
                                                        "Stønadsperioden inneholder utbetalinger som skal avkortes pga utenlandsopphold. Dette støttes ikke.",
                                                        "stønadsperiode_inneholder_avkorting_utenlandsopphold",
                                                    )
                                                }
                                            }
                                        }
                                    },
                                )
                            }.map {
                                call.svar(Created.jsonBody(it, satsFactory))
                            }
                        }
                    }
                }
            }
        }
    }

    get("$behandlingPath/{behandlingId}") {
        authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
            call.withBehandlingId { behandlingId ->
                søknadsbehandlingService.hent(HentRequest(behandlingId)).mapLeft {
                    call.svar(
                        NotFound.errorJson(
                            "Fant ikke behandling med id $behandlingId",
                            "fant_ikke_behandling",
                        ),
                    )
                }.map {
                    call.sikkerlogg("Hentet behandling med id $behandlingId")
                    call.audit(it.fnr, AuditLogEvent.Action.ACCESS, it.id)
                    call.svar(OK.jsonBody(it, satsFactory))
                }
            }
        }
    }

    post("$behandlingPath/{behandlingId}/beregn") {
        authorize(Brukerrolle.Saksbehandler) {
            data class Body(
                val begrunnelse: String?,
            ) {
                fun toDomain(behandlingId: UUID): Either<Resultat, BeregnRequest> {
                    return BeregnRequest(
                        behandlingId = behandlingId,
                        begrunnelse = begrunnelse,
                    ).right()
                }
            }

            call.withBehandlingId { behandlingId ->
                call.withBody<Body> { body ->
                    body.toDomain(behandlingId)
                        .mapLeft { call.svar(it) }
                        .map { serviceCommand ->
                            søknadsbehandlingService.beregn(serviceCommand)
                                .mapLeft { kunneIkkeBeregne ->
                                    val resultat = when (kunneIkkeBeregne) {
                                        KunneIkkeBeregne.FantIkkeBehandling -> {
                                            fantIkkeBehandling
                                        }

                                        KunneIkkeBeregne.KunneIkkeSimulereUtbetaling -> {
                                            kunneIkkeSimulere
                                        }

                                        is KunneIkkeBeregne.UgyldigTilstand -> {
                                            ugyldigTilstand(fra = kunneIkkeBeregne.fra, til = kunneIkkeBeregne.til)
                                        }

                                        is KunneIkkeBeregne.UgyldigTilstandForEndringAvFradrag -> {
                                            kunneIkkeBeregne.feil.tilResultat()
                                        }

                                        KunneIkkeBeregne.AvkortingErUfullstendig -> {
                                            avkortingErUfullstendig
                                        }
                                    }
                                    call.svar(resultat)
                                }.map { behandling ->
                                    call.sikkerlogg("Beregner på søknadsbehandling med id $behandlingId")
                                    call.audit(behandling.fnr, AuditLogEvent.Action.UPDATE, behandling.id)
                                    call.svar(Created.jsonBody(behandling, satsFactory))
                                }
                        }
                }
            }
        }
    }

    suspend fun lagBrevutkast(call: ApplicationCall, req: BrevRequest) = søknadsbehandlingService.brev(req).fold(
        {
            call.svar(
                when (it) {
                    KunneIkkeLageDokument.DetSkalIkkeSendesBrev -> kanIkkeSendeBrevIDenneTilstanden
                    KunneIkkeLageDokument.KunneIkkeFinneGjeldendeUtbetaling -> fantIkkeGjeldendeUtbetaling
                    KunneIkkeLageDokument.KunneIkkeGenererePDF -> feilVedGenereringAvDokument
                    KunneIkkeLageDokument.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant -> fantIkkeSaksbehandlerEllerAttestant
                    KunneIkkeLageDokument.KunneIkkeHentePerson -> fantIkkePerson
                },
            )
        },
        {
            call.sikkerlogg("Hentet brev for behandling med id ${req.behandling.id}")
            call.audit(req.behandling.fnr, AuditLogEvent.Action.ACCESS, req.behandling.id)
            call.respondBytes(it, ContentType.Application.Pdf)
        },
    )

    post("$behandlingPath/{behandlingId}/vedtaksutkast") {
        authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
            call.withBehandlingId { behandlingId ->
                call.withBody<WithFritekstBody> { body ->
                    søknadsbehandlingService.hent(HentRequest(behandlingId)).fold(
                        { call.svar(fantIkkeBehandling) },
                        { lagBrevutkast(call, BrevRequest.MedFritekst(it, body.fritekst)) },
                    )
                }
            }
        }
    }

    get("$behandlingPath/{behandlingId}/vedtaksutkast") {
        authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
            call.withBehandlingId { behandlingId ->
                søknadsbehandlingService.hent(HentRequest(behandlingId)).fold(
                    { call.svar(fantIkkeBehandling) },
                    { lagBrevutkast(call, BrevRequest.UtenFritekst(it)) },
                )
            }
        }
    }

    post("$behandlingPath/{behandlingId}/simuler") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withBehandlingId { behandlingId ->
                søknadsbehandlingService.simuler(
                    SimulerRequest(
                        behandlingId = behandlingId,
                        saksbehandler = Saksbehandler(call.suUserContext.navIdent),
                    ),
                ).fold(
                    {
                        call.svar(it.tilResultat())
                    },
                    {
                        call.sikkerlogg("Oppdatert simulering for behandling med id $behandlingId")
                        call.audit(it.fnr, AuditLogEvent.Action.UPDATE, behandlingId)
                        call.svar(OK.jsonBody(it, satsFactory))
                    },
                )
            }
        }
    }

    post("$behandlingPath/{behandlingId}/tilAttestering") {
        authorize(Brukerrolle.Saksbehandler) {
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
                                        Feilresponser.kunneIkkeOppretteOppgave
                                    }

                                    KunneIkkeSendeTilAttestering.KunneIkkeFinneAktørId -> {
                                        Feilresponser.fantIkkeAktørId
                                    }

                                    KunneIkkeSendeTilAttestering.FantIkkeBehandling -> {
                                        fantIkkeBehandling
                                    }

                                    KunneIkkeSendeTilAttestering.SakHarRevurderingerMedÅpentKravgrunnlagForTilbakekreving -> {
                                        Feilresponser.sakAvventerKravgrunnlagForTilbakekreving
                                    }
                                }
                                call.svar(resultat)
                            },
                            {
                                call.sikkerlogg("Sendte behandling med id $behandlingId til attestering")
                                call.audit(it.fnr, AuditLogEvent.Action.UPDATE, it.id)
                                call.svar(OK.jsonBody(it, satsFactory))
                            },
                        )
                    }
                }
            }
        }
    }

    fun kunneIkkeIverksetteMelding(value: KunneIkkeIverksette): Resultat {
        return when (value) {
            is KunneIkkeIverksette.AttestantOgSaksbehandlerKanIkkeVæreSammePerson -> attestantOgSaksbehandlerKanIkkeVæreSammePerson
            is KunneIkkeIverksette.KunneIkkeUtbetale -> value.utbetalingFeilet.tilResultat()
            is KunneIkkeIverksette.KunneIkkeGenerereVedtaksbrev -> kunneIkkeGenerereBrev
            is KunneIkkeIverksette.FantIkkeBehandling -> fantIkkeBehandling
            KunneIkkeIverksette.AvkortingErUfullstendig -> avkortingErUfullstendig
            KunneIkkeIverksette.HarAlleredeBlittAvkortetAvEnAnnen -> avkortingErAlleredeAvkortet
            KunneIkkeIverksette.HarBlittAnnullertAvEnAnnen -> avkortingErAlleredeAnnullert
            KunneIkkeIverksette.KunneIkkeOpprettePlanlagtKontrollsamtale -> InternalServerError.errorJson(
                "Kunne ikke opprette kontrollsamtale",
                "kunne_ikke_opprette_kontrollsamtale",
            )

            KunneIkkeIverksette.LagringFeilet -> lagringFeilet
            KunneIkkeIverksette.SakHarRevurderingerMedÅpentKravgrunnlagForTilbakekreving -> sakAvventerKravgrunnlagForTilbakekreving
        }
    }

    patch("$behandlingPath/{behandlingId}/iverksett") {
        authorize(Brukerrolle.Attestant) {
            call.withBehandlingId { behandlingId ->

                val navIdent = call.suUserContext.navIdent

                søknadsbehandlingService.iverksett(
                    IverksettRequest(
                        behandlingId = behandlingId,
                        attestering = Attestering.Iverksatt(Attestant(navIdent), Tidspunkt.now(clock)),
                    ),
                ).fold(
                    {
                        call.svar(kunneIkkeIverksetteMelding(it))
                    },
                    {
                        call.sikkerlogg("Iverksatte behandling med id: $behandlingId")
                        call.audit(it.fnr, AuditLogEvent.Action.UPDATE, it.id)
                        SuMetrics.vedtakIverksatt(SuMetrics.Behandlingstype.SØKNAD)
                        call.svar(OK.jsonBody(it, satsFactory))
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

    patch("$behandlingPath/{behandlingId}/underkjenn") {
        authorize(Brukerrolle.Attestant) {
            val navIdent = call.suUserContext.navIdent

            call.withBehandlingId { behandlingId ->
                Either.catch { deserialize<UnderkjennBody>(call) }.fold(
                    ifLeft = {
                        log.info("Ugyldig behandling-body: ", it)
                        call.svar(Feilresponser.ugyldigBody)
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
                                        opprettet = Tidspunkt.now(clock),
                                    ),
                                ),
                            ).fold(
                                ifLeft = {
                                    val resultat = when (it) {
                                        KunneIkkeUnderkjenne.FantIkkeBehandling -> fantIkkeBehandling
                                        KunneIkkeUnderkjenne.AttestantOgSaksbehandlerKanIkkeVæreSammePerson -> attestantOgSaksbehandlerKanIkkeVæreSammePerson
                                        KunneIkkeUnderkjenne.KunneIkkeOppretteOppgave -> Feilresponser.kunneIkkeOppretteOppgave
                                        KunneIkkeUnderkjenne.FantIkkeAktørId -> Feilresponser.fantIkkeAktørId
                                    }
                                    call.svar(resultat)
                                },
                                ifRight = {
                                    call.sikkerlogg("Underkjente behandling med id: $behandlingId")
                                    call.audit(it.fnr, AuditLogEvent.Action.UPDATE, it.id)
                                    call.svar(OK.jsonBody(it, satsFactory))
                                },
                            )
                        } else {
                            call.svar(BadRequest.errorJson("Må angi en begrunnelse", "mangler_begrunnelse"))
                        }
                    },
                )
            }
        }
    }
}
