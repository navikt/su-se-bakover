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
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.fantIkkeSak
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withSakId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.kontrollnotat.KontrollsamtaleNotat
import no.nav.su.se.bakover.domain.kontrollnotat.KontrollsamtaleReiseDato
import no.nav.su.se.bakover.service.kontrollsamtale.KontrollsamtaleNotatService
import no.nav.su.se.bakover.service.kontrollsamtale.KontrollsamtaleNotatVedleggService
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

fun Route.kontrollsamtaleNotatRoute(
    kontrollsamtaleNotatService: KontrollsamtaleNotatService,
    kontrollsamtaleNotatVedleggService: KontrollsamtaleNotatVedleggService,
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

    post("/saker/{sakId}/kontrollsamtaler/notat/{kontrollsamtaleId}") {
        authorize(Brukerrolle.Veileder, Brukerrolle.Saksbehandler) {
            call.withSakId { sakId ->
                var body: Body? = null
                val kontrollsamtaleId =
                    call.parameters["kontrollsamtaleId"]?.let(UUID::fromString) ?: return@authorize

                call.receiveMultipart().forEachPart { part ->
                    when (part) {
                        is PartData.FormItem -> {
                            if (part.name == "body") {
                                body = deserialize<Body>(part.value)
                            }
                            part.dispose()
                        }

                        else -> part.dispose()
                    }
                }

                val resolvedBody = body

                if (resolvedBody == null) {
                    call.svar(
                        resultat = HttpStatusCode.BadRequest.errorJson(
                            message = "Mangler kontrollsamtalenotat",
                            code = "mangler_kontrollsamtalenotat",
                        ),
                    )
                } else {
                    val notat = KontrollsamtaleNotat(
                        sakId = sakId,
                        kontrollsamtaleId = kontrollsamtaleId,
                        personligOppmøte = resolvedBody.personligOppmøte,
                        fullmaktOgLegeerklæring = resolvedBody.fullmaktOgLegeerklæring,
                        originalPass = resolvedBody.originalPass,
                        gyldigPass = resolvedBody.gyldigPass,
                        harVærtUtenlands = resolvedBody.harVærtUtenlands,
                        utenlandsoppholdDatoer = resolvedBody.utenlandsoppholdDatoer.map {
                            KontrollsamtaleReiseDato(
                                utreiseDato = it.utreiseDato,
                                innreiseDato = it.innreiseDato,
                            )
                        },
                        harPlanerOmUtenlandsreise = resolvedBody.harPlanerOmUtenlandsreise,
                        planlagteUtenlandsreiseDatoer = resolvedBody.planlagteUtenlandsreiseDatoer.map {
                            KontrollsamtaleReiseDato(
                                utreiseDato = it.utreiseDato,
                                innreiseDato = it.innreiseDato,
                            )
                        },
                        reiseDokumentasjon = resolvedBody.reiseDokumentasjon,
                        økonomiskSituasjon = resolvedBody.økonomiskSituasjon,
                        andreForhold = resolvedBody.andreForhold,
                        skatteOpplysninger = resolvedBody.skatteOpplysninger,
                        opprettet = Tidspunkt.now(clock),
                        fritekst = resolvedBody.fritekst,
                    )
                    val resultat = kontrollsamtaleNotatService.lagre(
                        kontrollsamtaleNotat = notat,
                        sakId = sakId,
                        kontrollsamtaleId = kontrollsamtaleId,
                    ).fold(
                        ifLeft = { feil ->
                            when (feil) {
                                is KontrollsamtaleNotatService.KunneIkkeOppretteJournalpost -> InternalServerError.errorJson(
                                    message = feil.grunn,
                                    code = "kunne_ikke_opprette_journalpost",
                                )
                            }
                        },
                        ifRight = { Resultat.okJson() },
                    )

                    call.svar(resultat)
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

                            KontrollsamtaleNotatService.KunneIkkeLageKontrollnotatPdf.KunneIkkeGenerereForside ->
                                InternalServerError.errorJson(
                                    message = "Kunne ikke generere forside",
                                    code = "kunne_ikke_generere_forside",
                                )
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

    post("/saker/{sakId}/kontrollsamtaler/notat/{kontrollsamtaleId}/vedlegg") {
        authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
            val kontrollsamtaleId =
                call.parameters["kontrollsamtaleId"]?.let(UUID::fromString) ?: return@authorize

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
                        mimeType = part.contentType
                            ?.toString()
                            ?.substringBefore(";")

                        innhold = part.provider()
                            .readRemaining()
                            .readByteArray()

                        part.dispose()
                    }

                    else -> part.dispose()
                }
            }
            val resolvedFilnavn = filnavn
                ?: return@authorize call.svar(
                    InternalServerError.errorJson(
                        message = "Mangler filnavn",
                        code = "mangler_filnavn",
                    ),
                )
            val resolvedMimeType = mimeType
                ?: return@authorize call.svar(
                    InternalServerError.errorJson(
                        message = "Mangler mimeType",
                        code = "mangler_mimetype",
                    ),
                )
            val resolvedInnhold = innhold
                ?: return@authorize call.svar(
                    InternalServerError.errorJson(
                        message = "Mangler innhold",
                        code = "mangler_innhold",
                    ),
                )
            kontrollsamtaleNotatVedleggService.leggTilVedlegg(
                kontrollsamtaleId = kontrollsamtaleId,
                filnavn = resolvedFilnavn,
                mimeType = resolvedMimeType,
                innhold = resolvedInnhold,
            ).fold(
                ifLeft = {
                    call.svar(
                        resultat = it.tilResultat(),
                    )
                },
                ifRight = { call.respond(HttpStatusCode.Created, serialize(it.id)) },
            )
        }
    }

    get("/saker/{sakId}/kontrollsamtaler/notat/{kontrollsamtaleId}/vedlegg") {
        authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
            val kontrollsamtaleId =
                call.parameters["kontrollsamtaleId"]?.let(UUID::fromString) ?: return@authorize
            val vedlegg = kontrollsamtaleNotatVedleggService.hentVedlegg(kontrollsamtaleId)
            call.respond(
                HttpStatusCode.OK,
                serialize(vedlegg),
            )
        }
    }

    delete("/saker/{sakId}/kontrollsamtaler/notat/{kontrollsamtaleId}/vedlegg/{vedleggId}") {
        authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
            val kontrollsamtaleId = call.parameters["kontrollsamtaleId"]?.let(UUID::fromString) ?: return@authorize

            val vedleggId = call.parameters["vedleggId"]?.let(UUID::fromString) ?: return@authorize

            kontrollsamtaleNotatVedleggService.slettVedlegg(vedleggId)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

private fun KontrollsamtaleNotatVedleggService.KontrollsamtaleNotatVedleggFeil.tilResultat() = when (this) {
    KontrollsamtaleNotatVedleggService.KontrollsamtaleNotatVedleggFeil.UgyldigMimeType -> HttpStatusCode.BadRequest.errorJson(
        message = "Ugyldig mimeType",
        code = "ugyldig_mimetype",
    )

    KontrollsamtaleNotatVedleggService.KontrollsamtaleNotatVedleggFeil.MimeTypeMatcherIkkeFilnavn -> HttpStatusCode.BadRequest.errorJson(
        message = "MimeType matcher ikke filnavn",
        code = "mimetype_matcher_ikke_filnavn",
    )

    KontrollsamtaleNotatVedleggService.KontrollsamtaleNotatVedleggFeil.FilForStor -> HttpStatusCode.BadRequest.errorJson(
        message = "Fil for stor",
        code = "fil_for_stor",
    )

    KontrollsamtaleNotatVedleggService.KontrollsamtaleNotatVedleggFeil.FantIkkeKontrollsamtale -> HttpStatusCode.NotFound.errorJson(
        message = "Fant ikke kontrollsamtale notat",
        code = "fant_ikke_kontrollsamtale_notat",
    )
}
