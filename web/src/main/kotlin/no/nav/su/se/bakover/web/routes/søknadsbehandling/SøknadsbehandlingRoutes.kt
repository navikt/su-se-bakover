package no.nav.su.se.bakover.web.routes.søknadsbehandling

import arrow.core.Either
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
import no.nav.su.se.bakover.common.audit.AuditLogEvent
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.domain.attestering.Attestering
import no.nav.su.se.bakover.common.extensions.enumContains
import no.nav.su.se.bakover.common.ident.NavIdentBruker.Attestant
import no.nav.su.se.bakover.common.ident.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.common.infrastructure.metrics.SuMetrics
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.attestantOgSaksbehandlerKanIkkeVæreSammePerson
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.fantIkkeBehandling
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.ugyldigTilstand
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.audit
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.deserialize
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.sikkerlogg
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.toUUID
import no.nav.su.se.bakover.common.infrastructure.web.withBehandlingId
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withSakId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.attestering.UnderkjennAttesteringsgrunnBehandling
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService.BeregnRequest
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService.HentRequest
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService.KunneIkkeBeregne
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService.OpprettRequest
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService.SendTilAttesteringRequest
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService.SimulerRequest
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService.UnderkjennRequest
import no.nav.su.se.bakover.domain.søknadsbehandling.brev.utkast.BrevutkastForSøknadsbehandlingCommand
import no.nav.su.se.bakover.domain.søknadsbehandling.brev.utkast.KunneIkkeGenerereBrevutkastForSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Aldersvurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.MaskinellAldersvurderingMedGrunnlagsdata
import no.nav.su.se.bakover.domain.søknadsbehandling.underkjenn.KunneIkkeUnderkjenneSøknadsbehandling
import no.nav.su.se.bakover.web.routes.dokument.tilResultat
import no.nav.su.se.bakover.web.routes.sak.SAK_PATH
import no.nav.su.se.bakover.web.routes.søknadsbehandling.attester.tilResultat
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.OppdaterStønadsperiodeRequest
import no.nav.su.se.bakover.web.routes.søknadsbehandling.opprett.tilResultat
import no.nav.su.se.bakover.web.routes.søknadsbehandling.vilkårOgGrunnlag.tilResultat
import no.nav.su.se.bakover.web.routes.tilResultat
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.UUID

internal const val SØKNADSBEHANDLING_PATH = "$SAK_PATH/{sakId}/behandlinger"

internal fun Route.søknadsbehandlingRoutes(
    søknadsbehandlingService: SøknadsbehandlingService,
    clock: Clock,
    satsFactory: SatsFactory,
) {
    val log = LoggerFactory.getLogger(this::class.java)

    data class OpprettBehandlingBody(val soknadId: String)
    data class WithFritekstBody(val fritekst: String)

    post("$SAK_PATH/{sakId}/behandlinger") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withSakId { sakId ->
                call.withBody<OpprettBehandlingBody> { body ->
                    body.soknadId.toUUID().mapLeft {
                        return@authorize call.svar(
                            BadRequest.errorJson(
                                "soknadId er ikke en gyldig uuid",
                                "ikke_gyldig_uuid",
                            ),
                        )
                    }.map { søknadId ->
                        søknadsbehandlingService.opprett(
                            OpprettRequest(
                                søknadId = søknadId,
                                sakId = sakId,
                                saksbehandler = call.suUserContext.saksbehandler,
                            ),
                        ).fold(
                            { call.svar(it.tilResultat()) },
                            {
                                call.sikkerlogg("Opprettet behandling på sak: $sakId og søknadId: $søknadId")
                                call.audit(it.second.fnr, AuditLogEvent.Action.CREATE, it.second.id)
                                SuMetrics.behandlingStartet(SuMetrics.Behandlingstype.SØKNAD)
                                call.svar(Created.jsonBody(it.second, satsFactory))
                            },
                        )
                    }
                }
            }
        }
    }

    post("$SØKNADSBEHANDLING_PATH/{behandlingId}/stønadsperiode") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withSakId { sakId ->
                call.withBehandlingId { behandlingId ->
                    call.withBody<OppdaterStønadsperiodeRequest> { body ->
                        body.toDomain(clock).onLeft {
                            return@authorize call.svar(it)
                        }.onRight { partialOppdaterRequest ->
                            søknadsbehandlingService.oppdaterStønadsperiode(
                                SøknadsbehandlingService.OppdaterStønadsperiodeRequest(
                                    behandlingId = behandlingId,
                                    stønadsperiode = partialOppdaterRequest.stønadsperiode,
                                    sakId = sakId,
                                    saksbehandler = call.suUserContext.saksbehandler,
                                    saksbehandlersAvgjørelse = partialOppdaterRequest.saksbehandlersAvgjørelse,
                                ),
                            ).fold(
                                { return@authorize call.svar(it.tilResultat()) },
                                {
                                    call.audit(it.fnr, AuditLogEvent.Action.UPDATE, it.id)
                                    return@authorize call.svar(
                                        Resultat.json(
                                            Created,
                                            serialize(
                                                it.toJson(satsFactory),
                                            ),
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    get("$SØKNADSBEHANDLING_PATH/{behandlingId}") {
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

    post("$SØKNADSBEHANDLING_PATH/{behandlingId}/beregn") {
        authorize(Brukerrolle.Saksbehandler) {
            data class Body(
                val begrunnelse: String?,
            ) {
                fun toDomain(behandlingId: UUID, saksbehandler: Saksbehandler): Either<Resultat, BeregnRequest> {
                    return BeregnRequest(
                        behandlingId = behandlingId,
                        begrunnelse = begrunnelse,
                        saksbehandler = saksbehandler,
                    ).right()
                }
            }

            call.withBehandlingId { behandlingId ->
                call.withBody<Body> { body ->
                    body.toDomain(behandlingId, call.suUserContext.saksbehandler)
                        .mapLeft { return@authorize call.svar(it) }
                        .map { serviceCommand ->
                            søknadsbehandlingService.beregn(serviceCommand)
                                .mapLeft { kunneIkkeBeregne ->
                                    val resultat = when (kunneIkkeBeregne) {
                                        KunneIkkeBeregne.FantIkkeBehandling -> {
                                            fantIkkeBehandling
                                        }

                                        is KunneIkkeBeregne.UgyldigTilstand -> {
                                            ugyldigTilstand(fra = kunneIkkeBeregne.fra, til = kunneIkkeBeregne.til)
                                        }

                                        is KunneIkkeBeregne.UgyldigTilstandForEndringAvFradrag -> {
                                            kunneIkkeBeregne.feil.tilResultat()
                                        }
                                    }
                                    return@authorize call.svar(resultat)
                                }.map { behandling ->
                                    call.sikkerlogg("Beregner på søknadsbehandling med id $behandlingId")
                                    call.audit(behandling.fnr, AuditLogEvent.Action.UPDATE, behandling.id)
                                    return@authorize call.svar(Created.jsonBody(behandling, satsFactory))
                                }
                        }
                }
            }
        }
    }

    suspend fun lagBrevutkast(call: ApplicationCall, req: BrevutkastForSøknadsbehandlingCommand) =
        søknadsbehandlingService.genererBrevutkast(req).fold(
            {
                call.svar(
                    when (it) {
                        is KunneIkkeGenerereBrevutkastForSøknadsbehandling.UgyldigTilstand -> ugyldigTilstand(
                            fra = it.fra,
                            til = it.til,
                        )

                        is KunneIkkeGenerereBrevutkastForSøknadsbehandling.UnderliggendeFeil -> it.underliggende.tilResultat()
                    },
                )
            },
            {
                call.sikkerlogg("Hentet brev for behandling med id ${req.søknadsbehandlingId}")
                call.audit(it.second, AuditLogEvent.Action.ACCESS, req.søknadsbehandlingId)
                call.respondBytes(it.first.getContent(), ContentType.Application.Pdf)
            },
        )

    // Brukes av saksbehandler før hen sen sender til attestering.
    post("$SØKNADSBEHANDLING_PATH/{behandlingId}/vedtaksutkast") {
        authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
            call.withBehandlingId { behandlingId ->
                call.withBody<WithFritekstBody> { body ->
                    lagBrevutkast(
                        call,
                        BrevutkastForSøknadsbehandlingCommand.ForSaksbehandler(
                            søknadsbehandlingId = behandlingId,
                            utførtAv = Saksbehandler(call.suUserContext.navIdent),
                            fritekst = body.fritekst,
                        ),
                    )
                }
            }
        }
    }
    // Brukes av attestant når hen skal se på et vedtaksutkast.
    get("$SØKNADSBEHANDLING_PATH/{behandlingId}/vedtaksutkast") {
        authorize(Brukerrolle.Attestant) {
            call.withBehandlingId { behandlingId ->
                lagBrevutkast(
                    call,
                    BrevutkastForSøknadsbehandlingCommand.ForAttestant(
                        søknadsbehandlingId = behandlingId,
                        utførtAv = Attestant(call.suUserContext.navIdent),
                    ),
                )
            }
        }
    }

    post("$SØKNADSBEHANDLING_PATH/{behandlingId}/simuler") {
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

    post("$SØKNADSBEHANDLING_PATH/{behandlingId}/tilAttestering") {
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
                                call.svar(it.tilResultat())
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

    data class UnderkjennBody(
        val grunn: String,
        val kommentar: String,
    ) {
        fun valid() = enumContains<UnderkjennAttesteringsgrunnBehandling>(grunn) && kommentar.isNotBlank()
    }

    patch("$SØKNADSBEHANDLING_PATH/{behandlingId}/underkjenn") {
        authorize(Brukerrolle.Attestant) {
            val navIdent = call.suUserContext.navIdent

            call.withBehandlingId { behandlingId ->
                Either.catch { deserialize<UnderkjennBody>(call) }.fold(
                    ifLeft = {
                        log.info("Ugyldig behandling-body: ", it)
                        return@authorize call.svar(Feilresponser.ugyldigBody)
                    },
                    ifRight = { body ->
                        if (body.valid()) {
                            søknadsbehandlingService.underkjenn(
                                UnderkjennRequest(
                                    behandlingId = behandlingId,
                                    attestering = Attestering.Underkjent(
                                        attestant = Attestant(navIdent),
                                        grunn = UnderkjennAttesteringsgrunnBehandling.valueOf(body.grunn),
                                        kommentar = body.kommentar,
                                        opprettet = Tidspunkt.now(clock),
                                    ),
                                ),
                            ).fold(
                                ifLeft = {
                                    val resultat = when (it) {
                                        KunneIkkeUnderkjenneSøknadsbehandling.FantIkkeBehandling -> fantIkkeBehandling
                                        KunneIkkeUnderkjenneSøknadsbehandling.AttestantOgSaksbehandlerKanIkkeVæreSammePerson -> attestantOgSaksbehandlerKanIkkeVæreSammePerson
                                        KunneIkkeUnderkjenneSøknadsbehandling.KunneIkkeOppretteOppgave -> Feilresponser.kunneIkkeOppretteOppgave
                                        KunneIkkeUnderkjenneSøknadsbehandling.FantIkkeAktørId -> Feilresponser.fantIkkeAktørId
                                        is KunneIkkeUnderkjenneSøknadsbehandling.UgyldigTilstand -> ugyldigTilstand(
                                            it.fra,
                                            it.til,
                                        )
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
                            return@authorize call.svar(
                                BadRequest.errorJson(
                                    "Må angi en begrunnelse",
                                    "mangler_begrunnelse",
                                ),
                            )
                        }
                    },
                )
            }
        }
    }
}

internal fun Sak.KunneIkkeOppdatereStønadsperiode.tilResultat(): Resultat {
    return when (this) {
        is Sak.KunneIkkeOppdatereStønadsperiode.FantIkkeBehandling -> fantIkkeBehandling
        is Sak.KunneIkkeOppdatereStønadsperiode.KunneIkkeOppdatereGrunnlagsdata -> {
            InternalServerError.errorJson(
                "Feil ved oppdatering av stønadsperiode",
                "oppdatering_av_stønadsperiode",
            )
        }

        is Sak.KunneIkkeOppdatereStønadsperiode.OverlappendeStønadsperiode -> this.feil.tilResultat()

        is Sak.KunneIkkeOppdatereStønadsperiode.AldersvurderingGirIkkeRettPåUføre -> {
            // OppdaterStønadsperiode vil fra dette tidspunktet alltid gi ut en vurdert vurdering
            val maskinellVurdering = when ((this.vurdering as Aldersvurdering.Vurdert).maskinellVurdering) {
                is MaskinellAldersvurderingMedGrunnlagsdata.IkkeRettPåUføre.MedFødselsdato -> "Ikke rett på uføre med fødselsdato"
                is MaskinellAldersvurderingMedGrunnlagsdata.IkkeRettPåUføre.MedFødselsår -> "Ikke rett på uføre med fødselsår"
                is MaskinellAldersvurderingMedGrunnlagsdata.RettPåUføre.MedFødselsdato -> "rett på uføre med fødselsdato"
                is MaskinellAldersvurderingMedGrunnlagsdata.RettPåUføre.MedFødselsår -> "rett på uføre med fødselsår"
                is MaskinellAldersvurderingMedGrunnlagsdata.Ukjent.MedFødselsår -> "Ukjent med fødselsår"
                is MaskinellAldersvurderingMedGrunnlagsdata.Ukjent.UtenFødselsår -> "ukjent uten fødselsår"
            }

            BadRequest.errorJson(
                "Aldersvurdering gir ikke rett på uføre. Stønadsperioden må justeres, eller overstyres. vurdering - $maskinellVurdering",
                "aldersvurdering_gir_ikke_rett_på_uføre",
            )
        }

        else -> TODO()
    }
}
