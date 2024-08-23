package no.nav.su.se.bakover.web.routes.sak

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.merge
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Created
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.server.application.call
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.audit.AuditLogEvent
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.audit
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.isMultipartFormDataRequest
import no.nav.su.se.bakover.common.infrastructure.web.parameter
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withSakId
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.sak.KunneIkkeHenteGjeldendeVedtaksdata
import no.nav.su.se.bakover.domain.sak.KunneIkkeOppretteDokument
import no.nav.su.se.bakover.domain.sak.OpprettDokumentRequest
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.presentation.web.toJson
import no.nav.su.se.bakover.web.routes.dokument.tilResultat
import no.nav.su.se.bakover.web.routes.grunnlag.GrunnlagsdataOgVilkårsvurderingerJson
import no.nav.su.se.bakover.web.routes.grunnlag.toJson
import no.nav.su.se.bakover.web.routes.journalpost.JournalpostJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.journalpost.tilResultat
import no.nav.su.se.bakover.web.routes.sak.BehandlingsoversiktJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.sak.SakJson.Companion.toJson
import person.domain.KunneIkkeHenteNavnForNavIdent
import vilkår.formue.domain.FormuegrenserFactory
import java.time.Clock
import java.time.LocalDate

internal const val SAK_PATH = "/saker"

internal fun Route.sakRoutes(
    sakService: SakService,
    clock: Clock,
    formuegrenserFactory: FormuegrenserFactory,
) {
    oppdaterFødselsnummerPåSakRoute(
        sakService = sakService,
        clock = clock,
        formuegrenserFactory = formuegrenserFactory,
    )

    post("$SAK_PATH/søk") {
        authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
            data class Body(
                val fnr: String?,
                val type: String?,
                val saksnummer: String?,
            )
            call.withBody<Body> { body ->
                when {
                    body.fnr != null -> {
                        Either.catch { Fnr(body.fnr) to Sakstype.from(body.type!!) }.fold(
                            ifLeft = {
                                if (Sakstype.entries.none { it.value == body.type }) {
                                    return@authorize call.svar(Feilresponser.ugyldigTypeSak)
                                } else {
                                    return@authorize call.svar(
                                        Feilresponser.ugyldigFødselsnummer,
                                    )
                                }
                            },
                            ifRight = { (fnr, type) ->
                                sakService.hentSak(fnr, type)
                                    .mapLeft {
                                        call.audit(fnr, AuditLogEvent.Action.SEARCH, null)
                                        return@authorize call.svar(
                                            NotFound.errorJson(
                                                "Fant ikke noen sak av typen ${body.type} for person: ${body.fnr}",
                                                "fant_ikke_sak_for_person",
                                            ),
                                        )
                                    }
                                    .map {
                                        call.audit(fnr, AuditLogEvent.Action.ACCESS, null)
                                        return@authorize call.svar(
                                            Resultat.json(
                                                OK,
                                                serialize(it.toJson(clock, formuegrenserFactory)),
                                            ),
                                        )
                                    }
                            },
                        )
                    }

                    body.saksnummer != null -> {
                        return@authorize call.svar(
                            Saksnummer.tryParse(body.saksnummer).fold(
                                ifLeft = {
                                    BadRequest.errorJson(
                                        "${body.saksnummer} er ikke et gyldig saksnummer",
                                        "saksnummer_ikke_gyldig",
                                    )
                                },
                                ifRight = { saksnummer ->

                                    sakService.hentSak(saksnummer).fold(
                                        {
                                            NotFound.errorJson(
                                                "Fant ikke sak med saksnummer: ${body.saksnummer}",
                                                "fant_ikke_sak",
                                            )
                                        },
                                        {
                                            call.audit(it.fnr, AuditLogEvent.Action.ACCESS, null)
                                            Resultat.json(OK, serialize((it.toJson(clock, formuegrenserFactory))))
                                        },
                                    )
                                },
                            ),
                        )
                    }

                    else -> return@authorize call.svar(
                        BadRequest.errorJson(
                            "Må oppgi enten saksnummer eller fødselsnummer",
                            "mangler_saksnummer_fødselsnummer",
                        ),
                    )
                }
            }
        }
    }

    get("$SAK_PATH/info/{fnr}") {
        authorize(Brukerrolle.Veileder, Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
            call.parameter("fnr")
                .flatMap {
                    Either.catch { Fnr(it) }
                        .mapLeft { Feilresponser.ugyldigFødselsnummer }
                }
                .map { fnr ->
                    call.audit(fnr, AuditLogEvent.Action.SEARCH, null)
                    sakService.hentAlleredeGjeldendeSakForBruker(fnr)
                        .let { info ->
                            AlleredeGjeldendeSakForBrukerJson(
                                uføre = BegrensetSakinfoJson(
                                    harÅpenSøknad = info.uføre.harÅpenSøknad,
                                    iverksattInnvilgetStønadsperiode = info.uføre.iverksattInnvilgetStønadsperiode?.toJson(),
                                ),
                                alder = BegrensetSakinfoJson(
                                    harÅpenSøknad = info.alder.harÅpenSøknad,
                                    iverksattInnvilgetStønadsperiode = info.alder.iverksattInnvilgetStønadsperiode?.toJson(),
                                ),
                            )
                        }
                }.map {
                    Resultat.json(OK, serialize(it))
                }
                .merge()
                .let {
                    call.svar(it)
                }
        }
    }

    get("$SAK_PATH/{sakId}") {
        authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
            call.withSakId { sakId ->
                call.svar(
                    sakService.hentSak(sakId).fold(
                        { NotFound.errorJson("Fant ikke sak med id: $sakId", "fant_ikke_sak") },
                        {
                            call.audit(it.fnr, AuditLogEvent.Action.ACCESS, null)
                            Resultat.json(OK, serialize((it.toJson(clock, formuegrenserFactory))))
                        },
                    ),
                )
            }
        }
    }

    data class Body(val fraOgMed: LocalDate, val tilOgMed: LocalDate?) {
        val periode = Periode.create(fraOgMed, tilOgMed ?: LocalDate.MAX)
    }

    data class Response(val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderingerJson?)

    post("$SAK_PATH/{sakId}/gjeldendeVedtaksdata") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withSakId { sakId ->
                call.withBody<Body> { body ->
                    call.svar(
                        sakService.hentGjeldendeVedtaksdata(sakId, body.periode).fold(
                            {
                                when (it) {
                                    KunneIkkeHenteGjeldendeVedtaksdata.FantIkkeSak -> Feilresponser.fantIkkeSak
                                    KunneIkkeHenteGjeldendeVedtaksdata.IngenVedtak -> NotFound.errorJson(
                                        message = "Fant ingen vedtak for ${body.periode}",
                                        code = "fant_ingen_vedtak_for_periode",
                                    )
                                }
                            },
                            {
                                Resultat.json(
                                    OK,
                                    serialize(
                                        (
                                            Response(
                                                it?.grunnlagsdataOgVilkårsvurderinger?.toJson(
                                                    formuegrenserFactory,
                                                ),
                                            )
                                            ),
                                    ),
                                )
                            },
                        ),
                    )
                }
            }
        }
    }

    get("$SAK_PATH/behandlinger/apne") {
        authorize(Brukerrolle.Saksbehandler) {
            val åpneBehandlinger = sakService.hentÅpneBehandlingerForAlleSaker()
            call.svar(Resultat.json(OK, serialize(åpneBehandlinger.toJson())))
        }
    }

    get("$SAK_PATH/behandlinger/ferdige") {
        authorize(Brukerrolle.Saksbehandler) {
            val ferdigeBehandlinger = sakService.hentFerdigeBehandlingerForAlleSaker()
            call.svar(Resultat.json(OK, serialize(ferdigeBehandlinger.toJson())))
        }
    }

    post("$SAK_PATH/{sakId}/fritekstDokument/lagreOgSend") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withSakId { sakId ->
                when (call.isMultipartFormDataRequest()) {
                    /**
                     * Dersom requesten er multipart, har dem lagt på en allerede generert PDF, og vi skal bare
                     * journalføre, og distribuere denne.
                     */
                    true -> call.lagCommandForLagreOgSendOpplastetPdfPåSak(sakId).let {
                        sakService.lagreOgSendOpplastetPdfPåSak(request = it)
                        call.svar(Resultat.accepted())
                    }

                    /**
                     * så lenge requesten ikke er spesifikk multipart/form-data så vil den bli behandlet som en vanlig text/plain / app/json
                     * vi forventer at frontend sender en body med fritekst, og vi skal generere dokumentet
                     */
                    false -> {
                        call.withBody<DokumentBody> { body ->
                            val res = sakService.genererLagreOgSendFritekstbrevPåSak(
                                OpprettDokumentRequest(
                                    sakId = sakId,
                                    saksbehandler = call.suUserContext.saksbehandler,
                                    tittel = body.tittel,
                                    fritekst = body.fritekst,
                                    distribueringsadresse = body.adresse?.toDomain(),
                                    distribusjonstype = body.distribusjonstype.toDomain(),
                                ),
                            )

                            res.fold(
                                { call.svar(it.tilResultat()) },
                                { call.svar(Resultat.json(Created, it.toJson())) },
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Forhåndsvisning av fritekstdokument
     *
     * Forhåndsvisning baserer seg på at vi skal alltid genere dokument. Tar derfor ikke stilling til formdata
     */
    post("$SAK_PATH/{sakId}/fritekstDokument") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withSakId { sakId ->
                call.withBody<DokumentBody> { body ->
                    val res = sakService.genererFritekstbrevPåSak(
                        OpprettDokumentRequest(
                            sakId = sakId,
                            saksbehandler = call.suUserContext.saksbehandler,
                            tittel = body.tittel,
                            fritekst = body.fritekst,
                            distribueringsadresse = body.adresse?.toDomain(),
                            distribusjonstype = body.distribusjonstype.toDomain(),
                        ),
                    )
                    res.fold(
                        { call.svar(it.tilResultat()) },
                        { call.respondBytes(it.generertDokument.getContent(), ContentType.Application.Pdf) },
                    )
                }
            }
        }
    }

    get("$SAK_PATH/{sakId}/journalposter") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withSakId {
                sakService.hentAlleJournalposter(it).fold(
                    { call.svar(it.tilResultat()) },
                    { call.svar(Resultat.json(OK, it.toJson())) },
                )
            }
        }
    }

    get("$SAK_PATH/{sakId}/epsSak") {
        authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
            call.withSakId {
                val sakIder = sakService.hentEpsSaksIderForBrukersSak(it)
                call.svar(Resultat.json(OK, serialize(sakIder)))
            }
        }
    }
}

fun KunneIkkeOppretteDokument.tilResultat(): Resultat = when (this) {
    is KunneIkkeOppretteDokument.KunneIkkeLageDokument -> this.feil.tilResultat()
    is KunneIkkeOppretteDokument.FeilVedHentingAvSaksbehandlernavn -> this.feil.tilResultat()
}

// frontend bryr seg kanskje ikke så veldig om de tekniske feilene? Dem blir logget i client
internal fun KunneIkkeHenteNavnForNavIdent.tilResultat() = when (this) {
    KunneIkkeHenteNavnForNavIdent.DeserialiseringAvResponsFeilet -> Feilresponser.ukjentFeil
    KunneIkkeHenteNavnForNavIdent.FantIkkeBrukerForNavIdent -> Feilresponser.fantIkkeSaksbehandlerEllerAttestant
    KunneIkkeHenteNavnForNavIdent.FeilVedHentingAvOnBehalfOfToken -> Feilresponser.ukjentFeil
    KunneIkkeHenteNavnForNavIdent.KallTilMicrosoftGraphApiFeilet -> Feilresponser.ukjentFeil
}
