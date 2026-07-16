package no.nav.su.se.bakover.web.routes.kontrollsamtale

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.fantIkkeSak
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withSakId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.kontrollnotat.KontrollsamtaleNotat
import no.nav.su.se.bakover.domain.kontrollnotat.KontrollsamtaleReiseDato
import no.nav.su.se.bakover.service.kontrollsamtale.KontrollsamtaleNotatService
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

fun Route.kontrollsamtaleNotatRoute(
    kontrollsamtaleNotatService: KontrollsamtaleNotatService,
    clock: Clock,
) {
    data class ReiseDatoBody(
        val utreiseDato: LocalDate,
        val innreiseDato: LocalDate,
    )

    data class Body(
        val personligOppmøte: Boolean,
        val fullmaktOgLegeerklæring: Boolean?,
        val originalPass: Boolean,
        val gyldigPass: Boolean,
        val harVærtUtenlands: Boolean,
        val utenlandsoppholdDatoer: List<ReiseDatoBody>,
        val harPlanerOmUtenlandsreise: Boolean,
        val planlagteUtenlandsreiseDatoer: List<ReiseDatoBody>,
        val reiseDokumentasjon: Boolean,
        val økonomiskSituasjon: Boolean,
        val andreForhold: Boolean,
        val skatteOpplysninger: Boolean,
        val fritekst: String?,
    )

    data class KontrollsamtaleNotatVedleggResponse(
        val id: UUID,
        val filnavn: String,
        val mimeType: String,
        val opprettet: Tidspunkt,
    )

    post("/saker/{sakId}/kontrollsamtaler/notat") {
        authorize(Brukerrolle.Veileder, Brukerrolle.Saksbehandler) {
            call.withSakId { sakId ->
                call.withBody<Body> { body ->
                    val notat = KontrollsamtaleNotat(
                        personligOppmøte = body.personligOppmøte,
                        fullmaktOgLegeerklæring = body.fullmaktOgLegeerklæring,
                        originalPass = body.originalPass,
                        gyldigPass = body.gyldigPass,
                        harVærtUtenlands = body.harVærtUtenlands,
                        utenlandsoppholdDatoer = body.utenlandsoppholdDatoer.map {
                            KontrollsamtaleReiseDato(
                                utreiseDato = it.utreiseDato,
                                innreiseDato = it.innreiseDato,
                            )
                        },
                        harPlanerOmUtenlandsreise = body.harPlanerOmUtenlandsreise,
                        planlagteUtenlandsreiseDatoer = body.planlagteUtenlandsreiseDatoer.map {
                            KontrollsamtaleReiseDato(
                                utreiseDato = it.utreiseDato,
                                innreiseDato = it.innreiseDato,
                            )
                        },
                        reiseDokumentasjon = body.reiseDokumentasjon,
                        økonomiskSituasjon = body.økonomiskSituasjon,
                        andreForhold = body.andreForhold,
                        skatteOpplysninger = body.skatteOpplysninger,
                        opprettet = Tidspunkt.now(clock),
                        fritekst = body.fritekst,
                    )
                    kontrollsamtaleNotatService.lagre(
                        kontrollsamtaleNotat = notat,
                        sakId = sakId,
                    )

                    call.svar(Resultat.okJson())
                }
            }
        }
    }

    get("/saker/{sakId}/kontrollsamtaler/notat/pdf") {
        authorize(Brukerrolle.Veileder, Brukerrolle.Saksbehandler) {
            call.withSakId { sakId ->
                kontrollsamtaleNotatService.hentKontrollsamtaleNotatPdf(sakId).fold(
                    ifLeft = {
                        val responseMessage = when (it) {
                            KontrollsamtaleNotatService.KunneIkkeLageKontrollnotatPdf.FantIkkeSak -> fantIkkeSak
                            KontrollsamtaleNotatService.KunneIkkeLageKontrollnotatPdf.KunneIkkeLagePdf ->
                                InternalServerError.errorJson(
                                    message = "Kunne ikke lage pdf",
                                    code = "kunne_ikke_lage_pdf",

                                )
                            KontrollsamtaleNotatService.KunneIkkeLageKontrollnotatPdf.FantIkkePerson ->
                                Feilresponser.fantIkkePerson
                            KontrollsamtaleNotatService.KunneIkkeLageKontrollnotatPdf.FantIkkeKontrollnotat ->
                                Feilresponser.fantIkkeKontrollnotat
                        }
                        call.svar(resultat = responseMessage)
                    },
                    ifRight = {
                        call.respondBytes(
                            bytes = it.getContent(),
                            contentType = ContentType.Application.Pdf,
                        )
                    },
                )
            }
        }
    }

    get("/saker/{sakId}/kontrollsamtaler/notat/vedlegg") {
        authorize(Brukerrolle.Veileder, Brukerrolle.Saksbehandler) {
            call.withSakId { sakId ->
                kontrollsamtaleNotatService.hentVedlegg(sakId).fold(
                    ifLeft = {
                        val responseMessage = when (it) {
                            KontrollsamtaleNotatService.KontrollsamtaleNotatVedleggFeil.FantIkkeKontrollnotat -> Feilresponser.fantIkkeKontrollnotat
                            KontrollsamtaleNotatService.KontrollsamtaleNotatVedleggFeil.FantIkkeVedlegg -> Feilresponser.fantIkkeVedlegg

                            else ->
                                HttpStatusCode.InternalServerError.errorJson(
                                    message = "Kunne ikke hente vedleff",
                                    code = "kunne_ikke_hente_vedlegg",
                                )
                        }
                        call.svar(resultat = responseMessage)
                    },
                    ifRight = { vedlegg ->
                        val response =
                            vedlegg.map {
                                KontrollsamtaleNotatVedleggResponse(
                                    id = it.id,
                                    filnavn = it.filnavn,
                                    mimeType = it.mimeType,
                                    opprettet = it.opprettet,
                                )
                            }
                        call.respond(
                            status = HttpStatusCode.OK,
                            message = serialize(response),
                        )
                    },
                )
            }
        }
    }

    post("/saker/{sakId}/kontrollsamtaler/notat/vedlegg") {
        authorize(Brukerrolle.Veileder, Brukerrolle.Saksbehandler) {
            call.withSakId { sakId ->
                var filnavn: String? = null
                var mimeType: String? = null
                var innhold: ByteArray? = null

                call.receiveMultipart().forEachPart { part ->
                    when (part) {
                        is PartData.FormItem -> {
                            if (part.name == "filnavn") {
                                filnavn = part.value
                            }
                        }
                        is PartData.FileItem -> {
                            filnavn = filnavn ?: part.originalFileName
                            mimeType = part.contentType?.toString()
                                ?.substringBefore(";")
                            innhold = part.provider().readRemaining().readByteArray()
                        }
                        else -> Unit
                    }
                    part.dispose()
                }

                val resolvedFilnavn =
                    filnavn ?: return@withSakId call.svar(
                        resultat = HttpStatusCode.BadRequest.errorJson(
                            message = "Mangler filnavn",
                            code = "mangler_filnavn",
                        ),
                    )
                val resolvedMimeType =
                    mimeType ?: return@withSakId call.svar(
                        resultat = HttpStatusCode.BadRequest.errorJson(
                            message = "Mangler mimeType",
                            code = "mangler_mimetype",
                        ),
                    )
                val resolvedInnhold =
                    innhold ?: return@withSakId call.svar(
                        resultat = HttpStatusCode.BadRequest.errorJson(
                            message = "Mangler innhold",
                            code = "mangler_innhold",
                        ),
                    )
                kontrollsamtaleNotatService.leggTilVedlegg(
                    sakId = sakId,
                    filnavn = resolvedFilnavn,
                    mimeType = resolvedMimeType,
                    innhold = resolvedInnhold,
                ).fold(
                    ifLeft = {
                        call.svar(it.tilResultat())
                    },
                    ifRight = {
                        call.respond(
                            status = HttpStatusCode.Created,
                            message = serialize(it),
                        )
                    },
                )
            }
        }
    }

    delete("/saker/{sakId}/kontrollsamtaler/notat/vedlegg/{vedleggId}") {
        authorize(Brukerrolle.Veileder, Brukerrolle.Saksbehandler) {
            call.withSakId { sakId ->
                val vedleggId = call.parameters["vedleggId"]
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: return@withSakId call.svar(
                        resultat = HttpStatusCode.BadRequest.errorJson(
                            message = "Ugyldig eller manglende vedleggId",
                            code = "ugyldig_vedlegg_id",
                        ),
                    )
                kontrollsamtaleNotatService.slettVedlegg(
                    sakId = sakId,
                    vedleggId = vedleggId,
                ).fold(
                    ifLeft = {
                        val responseMessage = when (it) {
                            KontrollsamtaleNotatService.KontrollsamtaleNotatVedleggFeil.FantIkkeKontrollnotat -> Feilresponser.fantIkkeKontrollnotat

                            KontrollsamtaleNotatService.KontrollsamtaleNotatVedleggFeil.FantIkkeVedlegg -> Feilresponser.fantIkkeVedlegg
                            else ->
                                HttpStatusCode.InternalServerError.errorJson(
                                    message = "Kunne ikke slette vedlegg",
                                    code = "kunne_ikke_slette_vedlegg",
                                )
                        }
                        call.svar(resultat = responseMessage)
                    },
                    ifRight = {
                        call.respond(
                            HttpStatusCode.NoContent,
                        )
                    },
                )
            }
        }
    }
}
private fun KontrollsamtaleNotatService.KontrollsamtaleNotatVedleggFeil.tilResultat(): Resultat =
    when (this) {
        KontrollsamtaleNotatService.KontrollsamtaleNotatVedleggFeil.FantIkkeKontrollnotat -> Feilresponser.fantIkkeKontrollnotat
        KontrollsamtaleNotatService.KontrollsamtaleNotatVedleggFeil.FantIkkeVedlegg -> Feilresponser.fantIkkeVedlegg
        KontrollsamtaleNotatService.KontrollsamtaleNotatVedleggFeil.UgyldigMimeType -> HttpStatusCode.BadRequest.errorJson(
            message = "Ugyldig mimeType",
            code = "ugyldig_mimetype",
        )
        KontrollsamtaleNotatService.KontrollsamtaleNotatVedleggFeil.MimeTypeMatcherIkkeFilnavn -> HttpStatusCode.BadRequest.errorJson(
            message = "MimeType matcher ikke filnavn",
            code = "mimetype_matcher_ikke_filnavn",
        )
        KontrollsamtaleNotatService.KontrollsamtaleNotatVedleggFeil.VedleggForStort -> HttpStatusCode.BadRequest.errorJson(
            message = "Vedlegg for stort",
            code = "vedlegg_for_stort",
        )
    }
