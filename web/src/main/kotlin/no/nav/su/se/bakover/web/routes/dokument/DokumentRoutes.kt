package no.nav.su.se.bakover.web.routes.dokument

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import dokument.domain.brev.BrevService
import dokument.domain.brev.HentDokumenterForIdType
import dokument.domain.distribuering.Distribueringsadresse
import dokument.domain.journalføring.KunneIkkeHenteDokument
import dokument.domain.journalføring.KunneIkkeHenteJournalpost
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
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.presentation.web.toJson
import no.nav.su.se.bakover.service.dokument.DistribuerDokumentService
import no.nav.su.se.bakover.service.klage.KlageinstansDokument
import no.nav.su.se.bakover.service.klage.KlageinstansDokumentFeil
import no.nav.su.se.bakover.service.klage.KlageinstansDokumentService
import java.util.Base64
import java.util.UUID

private const val ID_PARAMETER = "id"
private const val ID_TYPE_PARAMETER = "idType"
internal fun Route.dokumentRoutes(
    brevService: BrevService,
    distribuerDokumentService: DistribuerDokumentService,
    klageinstansDokumentService: KlageinstansDokumentService,
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

            klageinstansDokumentService.hentDokumenterForSak(sakUuid).fold(
                ifLeft = { call.svar(it.tilResultat()) },
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

private data class KlageinstansDokumentJson(
    val journalpostId: String,
    val journalpostTittel: String?,
    val datoOpprettet: String?,
    val distribueringsadresse: DistribueringsadresseJson?,
    val dokumentInfoId: String,
    val dokumentTittel: String?,
    val brevkode: String?,
    val dokumentstatus: String?,
    val variantFormat: String,
    val pdfBase64: String,
)

private data class DistribueringsadresseJson(
    val adresselinje1: String?,
    val adresselinje2: String?,
    val adresselinje3: String?,
    val postnummer: String,
    val poststed: String,
)

private fun List<KlageinstansDokument>.toJson(): String {
    return serialize(this.map { it.toJson() })
}

private fun KlageinstansDokument.toJson(): KlageinstansDokumentJson {
    return KlageinstansDokumentJson(
        journalpostId = journalpostId.toString(),
        journalpostTittel = journalpostTittel,
        datoOpprettet = datoOpprettet?.toString(),
        distribueringsadresse = distribueringsadresse?.toJson(),
        dokumentInfoId = dokumentInfoId,
        dokumentTittel = dokumentTittel,
        brevkode = brevkode,
        dokumentstatus = dokumentstatus,
        variantFormat = variantFormat,
        pdfBase64 = Base64.getEncoder().encodeToString(dokument),
    )
}

private fun Distribueringsadresse.toJson(): DistribueringsadresseJson {
    return DistribueringsadresseJson(
        adresselinje1 = adresselinje1,
        adresselinje2 = adresselinje2,
        adresselinje3 = adresselinje3,
        postnummer = postnummer,
        poststed = poststed,
    )
}

private fun KlageinstansDokumentFeil.tilResultat(): Resultat {
    return when (this) {
        is KlageinstansDokumentFeil.KunneIkkeHenteJournalpost -> this.feil.tilResultat()
        is KlageinstansDokumentFeil.KunneIkkeHenteDokument -> this.feil.tilResultat()
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
