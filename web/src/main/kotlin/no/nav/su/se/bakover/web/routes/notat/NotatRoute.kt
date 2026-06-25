package no.nav.su.se.bakover.web.routes.notat

import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withSakId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.notat.NotatFeil
import no.nav.su.se.bakover.domain.notat.NotatService
import java.time.Clock
import java.util.UUID

internal const val NOTAT_PATH = "/notat"

internal fun Route.notatRoutes(
    notatService: NotatService,
    clock: Clock,
) {
    route("$NOTAT_PATH/{sakId}") {
        get {
            authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
                call.withSakId { sakId ->
                    notatService.hentNotaterForSak(sakId).fold(
                        ifLeft = { call.svar(it.tilResultat()) },
                        ifRight = { call.respond(HttpStatusCode.OK, serialize(it)) },
                    )
                }
            }
        }

        post {
            authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
                call.withSakId { sakId ->
                    call.withBody<OpprettNotatBody> { body ->
                        notatService.opprettNotat(
                            sakId = sakId,
                            referanseId = UUID.fromString(body.referanseId),
                            notat = body.notat,
                            saksbehandler = call.suUserContext.saksbehandler,
                            clock = clock,
                        ).fold(
                            ifLeft = { call.svar(it.tilResultat()) },
                            ifRight = { call.respond(HttpStatusCode.Created, serialize(it)) },
                        )
                    }
                }
            }
        }

        get("/{notatId}") {
            authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
                call.withSakId { sakId ->
                    val notatId = call.lesNotatId() ?: return@withSakId
                    notatService.hentNotatMedVedlegg(sakId, notatId).fold(
                        ifLeft = { call.svar(it.tilResultat()) },
                        ifRight = { call.respond(HttpStatusCode.OK, serialize(it)) },
                    )
                }
            }
        }

        post("/{notatId}") {
            authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
                call.withSakId { sakId ->
                    val notatId = call.lesNotatId() ?: return@withSakId
                    call.withBody<OppdaterNotatBody> { body ->
                        notatService.oppdaterNotat(
                            sakId = sakId,
                            notatId = notatId,
                            notat = body.notat,
                            saksbehandler = call.suUserContext.saksbehandler,
                            clock = clock,
                        ).fold(
                            ifLeft = { call.svar(it.tilResultat()) },
                            ifRight = { call.respond(HttpStatusCode.OK, serialize(it)) },
                        )
                    }
                }
            }
        }

        post("/{notatId}/vedlegg") {
            authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
                call.withSakId { sakId ->
                    val notatId = call.lesNotatId() ?: return@withSakId

                    var filnavn: String? = null
                    var mimeType: String? = null
                    var innhold: ByteArray? = null
                    call.receiveMultipart().forEachPart { part ->
                        when (part) {
                            is PartData.FormItem -> {
                                if (part.name == "filnavn") filnavn = part.value
                                part.dispose()
                            }
                            is PartData.FileItem -> {
                                filnavn = filnavn ?: part.originalFileName
                                mimeType = part.contentType?.toString()?.substringBefore(";")
                                innhold = part.provider().readRemaining().readByteArray()
                                part.dispose()
                            }
                            else -> part.dispose()
                        }
                    }

                    val resolvedFilnavn = filnavn
                        ?: return@withSakId call.svar(
                            HttpStatusCode.BadRequest.errorJson("Mangler filnavn", "mangler_filnavn"),
                        )
                    val resolvedInnhold = innhold
                        ?: return@withSakId call.svar(
                            HttpStatusCode.BadRequest.errorJson("Mangler filinnhold", "mangler_filinnhold"),
                        )
                    val resolvedMimeType = mimeType
                        ?: return@withSakId call.svar(
                            HttpStatusCode.BadRequest.errorJson("Mangler mime type", "mangler_mime_type"),
                        )

                    notatService.leggTilVedlegg(
                        sakId = sakId,
                        notatId = notatId,
                        filnavn = resolvedFilnavn,
                        mimeType = resolvedMimeType,
                        innhold = resolvedInnhold,
                        saksbehandler = call.suUserContext.saksbehandler,
                        clock = clock,
                    ).fold(
                        ifLeft = { call.svar(it.tilResultat()) },
                        ifRight = { call.respond(HttpStatusCode.Created, serialize(it.id)) },
                    )
                }
            }
        }

        delete("/{notatId}/vedlegg/{vedleggId}") {
            authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
                call.withSakId { sakId ->
                    val notatId = call.lesNotatId() ?: return@withSakId
                    val vedleggId = call.parameters["vedleggId"]
                        ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                        ?: return@withSakId call.svar(
                            HttpStatusCode.BadRequest.errorJson("Ugyldig eller manglende vedleggId", "ugyldig_vedlegg_id"),
                        )
                    notatService.slettVedlegg(
                        sakId = sakId,
                        notatId = notatId,
                        vedleggId = vedleggId,
                        saksbehandler = call.suUserContext.saksbehandler,
                        clock = clock,
                    ).fold(
                        ifLeft = { call.svar(it.tilResultat()) },
                        ifRight = { call.respond(HttpStatusCode.NoContent) },
                    )
                }
            }
        }
    }
}

private suspend fun io.ktor.server.application.ApplicationCall.lesNotatId(): UUID? {
    val notatId = parameters["notatId"]
        ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    if (notatId == null) {
        svar(HttpStatusCode.BadRequest.errorJson("Ugyldig eller manglende notatId", "ugyldig_notat_id"))
    }
    return notatId
}

private data class OpprettNotatBody(
    val referanseId: String,
    val notat: String,
)

private data class OppdaterNotatBody(
    val notat: String,
)

private fun NotatFeil.tilResultat() = when (this) {
    NotatFeil.FantIkkeSak -> HttpStatusCode.NotFound.errorJson("Fant ikke sak", "fant_ikke_sak")
    NotatFeil.FantIkkeNotat -> HttpStatusCode.NotFound.errorJson("Fant ikke notat", "fant_ikke_notat")
    NotatFeil.FantIkkeVedlegg -> HttpStatusCode.NotFound.errorJson("Fant ikke vedlegg", "fant_ikke_vedlegg")
    NotatFeil.VedleggTilhørerIkkeNotat -> HttpStatusCode.BadRequest.errorJson("Vedlegg tilhører ikke notatet", "vedlegg_tilhorer_ikke_notat")
    NotatFeil.NotatTilhørerIkkeSak -> HttpStatusCode.BadRequest.errorJson("Notat tilhører ikke saken", "notat_tilhorer_ikke_sak")
    NotatFeil.TomtNotat -> HttpStatusCode.BadRequest.errorJson("Notat kan ikke være tomt", "tomt_notat")
    NotatFeil.ReferanseIdAlleredeIBruk -> HttpStatusCode.Conflict.errorJson("Det finnes allerede et notat for denne referansen", "referanse_id_allerede_i_bruk")
    NotatFeil.UgyldigMimeType -> HttpStatusCode.BadRequest.errorJson("Ugyldig mime type, støtter kun jpeg, png og pdf", "ugyldig_mime_type")
    NotatFeil.MimeTypeMatcherIkkeFilnavn -> HttpStatusCode.BadRequest.errorJson("Mime type matcher ikke filnavn", "mime_type_matcher_ikke_filnavn")
    NotatFeil.FilForStor -> HttpStatusCode.BadRequest.errorJson("Vedlegg er for stort, maks 20 MB", "fil_for_stor")
}
