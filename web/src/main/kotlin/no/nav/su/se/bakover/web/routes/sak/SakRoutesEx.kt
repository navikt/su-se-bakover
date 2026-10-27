package no.nav.su.se.bakover.web.routes.sak

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.core.JsonProcessingException
import dokument.domain.distribuering.Distribueringsadresse
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveMultipart
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.domain.sak.JournalførOgSendOpplastetPdfSomBrevCommand
import java.util.UUID

data class DistribueringsadresseBody(
    val adresselinje1: String,
    val adresselinje2: String?,
    val adresselinje3: String?,
    val postnummer: String,
    val poststed: String,
) {
    fun toDomain(): Distribueringsadresse = Distribueringsadresse(
        adresselinje1 = adresselinje1,
        adresselinje2 = adresselinje2,
        adresselinje3 = adresselinje3,
        postnummer = postnummer,
        poststed = poststed,
    )
}

data class DokumentBody(
    val tittel: String,
    val fritekst: String,
    val adresse: DistribueringsadresseBody?,
    val distribusjonstype: Distribusjonstype,
)

/**
 * Vi forventer disse feltene i multipart formet
 * 1. journaltittel
 * 2. distribusjonstype
 * 3. pdf
 * 4. distribueringsadresse - Denne er den eneste som er optional, og kommer sist i rekkefølgen
 */
suspend fun ApplicationCall.parseMultiFormDataMedPdf(
    sakId: UUID,
): Either<Resultat, JournalførOgSendOpplastetPdfSomBrevCommand> {
    val parts = this.receiveMultipart()

    var pdfContent: ByteArray? = null
    var journaltittel: String? = null
    var distribueringsadresse: Distribueringsadresse? = null
    var distribusjonstype: dokument.domain.Distribusjonstype? = null

    suspend fun lesDel(part: PartData): Either<Resultat, Unit> {
        when (part) {
            is PartData.FormItem -> {
                when (part.name) {
                    "distribusjonstype" -> {
                        distribusjonstype = Distribusjonstype.entries.firstOrNull { it.name == part.value }?.toDomain()
                            ?: return HttpStatusCode.BadRequest.errorJson(
                                "Ugyldig distribusjonstype",
                                "ugyldig_distribusjonstype",
                            ).left()
                    }
                    "tittel" -> {
                        journaltittel = part.value
                    }
                    "adresse" -> {
                        val distribueringsadresseAsJson = part.value
                        distribueringsadresse = try {
                            deserialize<DistribueringsadresseBody>(distribueringsadresseAsJson).toDomain()
                        } catch (_: JsonProcessingException) {
                            return HttpStatusCode.BadRequest.errorJson(
                                "Ugyldig distribueringsadresse",
                                "ugyldig_distribueringsadresse",
                            ).left()
                        }
                    }
                    else -> {
                        return HttpStatusCode.BadRequest.errorJson(
                            "Ukjent felt i multipart",
                            "ukjent_multipart_felt",
                        ).left()
                    }
                }
            }
            is PartData.FileItem -> {
                val innhold = part.provider().readRemaining().readByteArray()
                val minimumPdfStørrelse = 312
                if (innhold.size < minimumPdfStørrelse) {
                    return HttpStatusCode.BadRequest.errorJson(
                        "PDF må inneholde minst $minimumPdfStørrelse bytes",
                        "ugyldig_pdf_størrelse",
                    ).left()
                }
                pdfContent = innhold
            }

            else -> {
                return HttpStatusCode.BadRequest.errorJson(
                    "Støtter kun vanlige felt og PDF-fil",
                    "ugyldig_multipart_type",
                ).left()
            }
        }
        return Unit.right()
    }

    var valideringsfeil: Resultat? = null
    while (true) {
        val part = parts.readPart() ?: break
        try {
            if (valideringsfeil == null) {
                lesDel(part).onLeft { valideringsfeil = it }
            } else if (part is PartData.FileItem) {
                part.provider().readRemaining().close()
            }
        } finally {
            part.dispose()
        }
    }
    valideringsfeil?.let { return it.left() }

    return JournalførOgSendOpplastetPdfSomBrevCommand(
        sakId = sakId,
        saksbehandler = this.suUserContext.saksbehandler,
        journaltittel = journaltittel ?: return HttpStatusCode.BadRequest.errorJson(
            "Mangler journaltittel",
            "mangler_journaltittel",
        ).left(),
        pdf = PdfA(
            content = pdfContent ?: return HttpStatusCode.BadRequest.errorJson(
                "Mangler PDF",
                "mangler_pdf",
            ).left(),
        ),
        distribueringsadresse = distribueringsadresse,
        distribusjonstype = distribusjonstype ?: return HttpStatusCode.BadRequest.errorJson(
            "Mangler distribusjonstype",
            "mangler_distribusjonstype",
        ).left(),
    ).right()
}
