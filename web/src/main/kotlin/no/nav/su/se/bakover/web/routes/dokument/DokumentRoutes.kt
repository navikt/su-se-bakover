package no.nav.su.se.bakover.web.routes.dokument

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import dokument.domain.brev.BrevService
import dokument.domain.brev.HentDokumenterForIdType
import dokument.domain.journalføring.KunneIkkeHenteDokument
import dokument.domain.journalføring.KunneIkkeHenteJournalpost
import dokument.domain.journalføring.Utsendingsinfo
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.domain.extensions.toUUID
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.parameter
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withDokumentId
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.presentation.web.toJson
import no.nav.su.se.bakover.service.dokument.DistribuerDokumentService
import no.nav.su.se.bakover.service.klage.AdresseServiceFeil
import no.nav.su.se.bakover.service.klage.DokumentUtsendingsinfo
import no.nav.su.se.bakover.service.klage.JournalpostAdresseService
import no.nav.su.se.bakover.service.klage.JournalpostMedDokumentPdfOgAdresse
import java.util.Base64
import java.util.UUID

private const val ID_PARAMETER = "id"
private const val ID_TYPE_PARAMETER = "idType"

internal fun Route.dokumentRoutes(
    brevService: BrevService,
    distribuerDokumentService: DistribuerDokumentService,
    journalpostAdresseService: JournalpostAdresseService,
) {
    get("/dokumenter") {
        authorize(Brukerrolle.Saksbehandler) {
            val id = call.parameter(ID_PARAMETER)
                .getOrElse {
                    return@authorize call.svar(
                        HttpStatusCode.BadRequest.errorJson(
                            "Parameter '$ID_PARAMETER' mangler",
                            "mangler_$ID_PARAMETER",
                        ),
                    )
                }
            val type = call.parameter(ID_TYPE_PARAMETER)
                .getOrElse {
                    return@authorize call.svar(
                        HttpStatusCode.BadRequest.errorJson(
                            "Parameter '$ID_TYPE_PARAMETER' mangler",
                            "mangler_$ID_TYPE_PARAMETER",
                        ),
                    )
                }

            val parameters = HentDokumentParameters.tryCreate(id, type)
                .getOrElse { error ->
                    return@authorize when (error) {
                        HentDokumentParameters.Companion.UgyldigParameter.UgyldigType -> {
                            call.svar(
                                HttpStatusCode.BadRequest.errorJson(
                                    "Ugyldig parameter '$ID_TYPE_PARAMETER'",
                                    "ugyldig_parameter_$ID_TYPE_PARAMETER",
                                ),
                            )
                        }

                        HentDokumentParameters.Companion.UgyldigParameter.UgyldigUUID -> {
                            call.svar(
                                HttpStatusCode.BadRequest.errorJson(
                                    "Ugyldig parameter '$ID_PARAMETER'",
                                    "ugyldig_parameter_$ID_PARAMETER",
                                ),
                            )
                        }
                    }
                }

            brevService.hentDokumenterFor(parameters.toDomain())
                .let { dokumenter ->
                    call.svar(
                        Resultat.json(
                            httpCode = HttpStatusCode.OK,
                            json = dokumenter.toJson(),
                        ),
                    )
                }
        }
    }

    get("/dokumenter/eksterne") {
        authorize(Brukerrolle.Saksbehandler) {
            val sakId = call.parameter("sakId")
                .getOrElse {
                    return@authorize call.svar(
                        HttpStatusCode.BadRequest.errorJson(
                            "Parameter 'sakId' mangler",
                            "mangler_sakId",
                        ),
                    )
                }

            val sakUuid = sakId.toUUID()
                .getOrElse {
                    return@authorize call.svar(
                        HttpStatusCode.BadRequest.errorJson(
                            "Ugyldig parameter 'sakId'",
                            "ugyldig_parameter_sakId",
                        ),
                    )
                }

            journalpostAdresseService.hentKlageDokumenterMedAdresseForSak(sakUuid).fold(
                ifLeft = {
                    call.svar(it.tilResultat())
                },
                ifRight = { dokumenter ->
                    call.svar(
                        Resultat.json(
                            httpCode = HttpStatusCode.OK,
                            json = dokumenter.toJson(),
                        ),
                    )
                },
            )
        }
    }

    get("/dokumenter/intern/{dokumentId}/{journalpostId}") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withDokumentId { dokumentId ->
                val journalpostId = call.parameter("journalpostId")
                    .getOrElse {
                        return@authorize call.svar(
                            HttpStatusCode.BadRequest.errorJson(
                                "Parameter 'journalpostId' mangler",
                                "mangler_journalpostId",
                            ),
                        )
                    }

                journalpostAdresseService.hentAdresseForDokumentIdForInterneDokumenter(
                    dokumentId = dokumentId,
                    journalpostId = JournalpostId(journalpostId),
                ).fold(
                    ifLeft = { call.svar(it.tilResultat()) },
                    ifRight = { adresse ->
                        call.svar(
                            Resultat.json(
                                httpCode = HttpStatusCode.OK,
                                json = adresse.toJson(),
                            ),
                        )
                    },
                )
            }
        }
    }

    get("/dokumenter/{dokumentId}") {
        authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
            call.withDokumentId { id ->
                brevService.hentDokument(id).fold(
                    ifLeft = {
                        call.svar(
                            HttpStatusCode.NotFound.errorJson(
                                "Fant ikke dokument med id $id",
                                "fant_ikke_dokument",
                            ),
                        )
                    },
                    ifRight = {
                        call.respondBytes(it.generertDokument.getContent(), ContentType.Application.Pdf)
                    },
                )
            }
        }
    }
    sendDokumentMedAdresse(distribuerDokumentService)
}

private data class JournalPostDokumentMedPdf(
    val journalpostId: String,
    val journalpostTittel: String?,
    val datoOpprettet: String?,
    val utsendingsinfo: UtsendingsinfoJson?,
    val dokumentInfoId: String,
    val dokumentTittel: String?,
    val brevkode: String?,
    val dokumentstatus: String?,
    val variantFormat: String,
    val pdfBase64: String,
)

private data class DokumentUtsendingsinfoJson(
    val utsendingsinfo: UtsendingsinfoJson?,
)

private data class UtsendingsinfoJson(
    val fysiskpostSendt: String?,
    val digitalpostSendt: Boolean,
    val varselSendt: List<VarselSendtJson>,
)

private data class VarselSendtJson(
    val type: String,
    val adresse: String,
    val varslingstidspunkt: String?,
    val passert40TimerSidenVarsling: Boolean?,
)

private fun List<JournalpostMedDokumentPdfOgAdresse>.toJson(): String {
    return serialize(this.map { it.toJson() })
}

private fun DokumentUtsendingsinfo.toJson(): String {
    return serialize(
        DokumentUtsendingsinfoJson(
            utsendingsinfo = utsendingsinfo?.toJson(),
        ),
    )
}

private fun JournalpostMedDokumentPdfOgAdresse.toJson(): JournalPostDokumentMedPdf {
    return JournalPostDokumentMedPdf(
        journalpostId = journalpostId.toString(),
        journalpostTittel = journalpostTittel,
        datoOpprettet = datoOpprettet?.toString(),
        utsendingsinfo = utsendingsinfo?.toJson(),
        dokumentInfoId = dokumentInfoId,
        dokumentTittel = dokumentTittel,
        brevkode = brevkode,
        dokumentstatus = dokumentstatus,
        variantFormat = variantFormat,
        pdfBase64 = Base64.getEncoder().encodeToString(dokument),
    )
}

private fun Utsendingsinfo.toJson(): UtsendingsinfoJson {
    return UtsendingsinfoJson(
        fysiskpostSendt = fysiskpostSendt,
        digitalpostSendt = digitalpostSendt,
        varselSendt = varselSendt.map {
            VarselSendtJson(
                type = it.type,
                adresse = it.adresse,
                varslingstidspunkt = it.varslingstidspunkt,
                passert40TimerSidenVarsling = it.passert40TimerSidenVarsling,
            )
        },
    )
}

private fun AdresseServiceFeil.tilResultat(): Resultat {
    return when (this) {
        is AdresseServiceFeil.KunneIkkeHenteJournalpost -> this.feil.tilResultat()
        is AdresseServiceFeil.KunneIkkeHenteDokument -> this.feil.tilResultat()
        AdresseServiceFeil.FantIkkeDokument -> HttpStatusCode.NotFound.errorJson(
            "Fant ikke dokument",
            "fant_ikke_dokument",
        )

        AdresseServiceFeil.JournalpostIkkeKnyttetTilDokument -> HttpStatusCode.NotFound.errorJson(
            "Journalpostiden er ikke lik som dokument sin journalpostId",
            "journalpost_ikke_knyttet_til_dokument",
        )

        AdresseServiceFeil.FantIkkeJournalpostForDokument -> HttpStatusCode.NotFound.errorJson(
            "Fant ikke journalpost for dokument",
            "fant_ikke_journalpost_for_dokument",
        )

        AdresseServiceFeil.JournalpostManglerBrevbestilling -> HttpStatusCode.NotFound.errorJson(
            "Journalpost mangler brevbestilling, men er journalført. Normalt vil dette ta 5 minutter ekstra.",
            "journalpost_mangler_brevbestilling",
        )

        AdresseServiceFeil.ErIkkeJournalført -> HttpStatusCode.NotFound.errorJson(
            "Journalposten er ikke journalført, dette er steg 1 av 2 der steg 2 er brev distribuering. Normalt vil dette ta 5 minutter ekstra.",
            "journalpost_mangler",
        )
    }
}

private fun KunneIkkeHenteJournalpost.tilResultat(): Resultat {
    return when (this) {
        KunneIkkeHenteJournalpost.FantIkkeJournalpost -> HttpStatusCode.NotFound.errorJson(
            "Fant ikke journalpost",
            "fant_ikke_journalpost",
        )

        KunneIkkeHenteJournalpost.IkkeTilgang -> HttpStatusCode.Forbidden.errorJson(
            "Ikke tilgang til journalpost",
            "ikke_tilgang_til_journalpost",
        )

        KunneIkkeHenteJournalpost.UgyldigInput -> Feilresponser.ugyldigInput
        KunneIkkeHenteJournalpost.TekniskFeil,
        KunneIkkeHenteJournalpost.Ukjent,
        -> Feilresponser.ukjentFeil
    }
}

private fun KunneIkkeHenteDokument.tilResultat(): Resultat {
    return when (this) {
        KunneIkkeHenteDokument.FantIkkeDokument -> HttpStatusCode.NotFound.errorJson(
            "Fant ikke dokument",
            "fant_ikke_dokument",
        )

        KunneIkkeHenteDokument.IkkeTilgang,
        KunneIkkeHenteDokument.IkkeAutorisert,
        -> HttpStatusCode.Forbidden.errorJson(
            "Ikke tilgang til dokument",
            "ikke_tilgang_til_dokument",
        )

        KunneIkkeHenteDokument.UgyldigInput -> Feilresponser.ugyldigInput
        is KunneIkkeHenteDokument.TekniskFeil,
        is KunneIkkeHenteDokument.Ukjent,
        -> Feilresponser.ukjentFeil
    }
}

private data class HentDokumentParameters(
    val id: UUID,
    val idType: IdType,
) {
    companion object {
        fun tryCreate(id: String, type: String): Either<UgyldigParameter, HentDokumentParameters> {
            return HentDokumentParameters(
                id = id.toUUID()
                    .getOrElse { return UgyldigParameter.UgyldigUUID.left() },
                idType = Either.catch { IdType.valueOf(type.uppercase()) }
                    .getOrElse { return UgyldigParameter.UgyldigType.left() },
            ).right()
        }

        sealed interface UgyldigParameter {
            data object UgyldigUUID : UgyldigParameter
            data object UgyldigType : UgyldigParameter
        }
    }

    fun toDomain(): HentDokumenterForIdType {
        return when (idType) {
            IdType.SAK -> HentDokumenterForIdType.HentDokumenterForSak(id)
            IdType.SØKNAD -> HentDokumenterForIdType.HentDokumenterForSøknad(id)
            IdType.VEDTAK -> HentDokumenterForIdType.HentDokumenterForVedtak(id)
            IdType.REVURDERING -> HentDokumenterForIdType.HentDokumenterForRevurdering(id)
            IdType.KLAGE -> HentDokumenterForIdType.HentDokumenterForKlage(id)
        }
    }
}

private enum class IdType {
    SAK,
    SØKNAD,
    VEDTAK,
    REVURDERING,
    KLAGE,
}
