package no.nav.su.se.bakover.web.routes.sak

import dokument.domain.distribuering.Distribueringsadresse
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveMultipart
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.domain.PdfA
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
// TODO: SOS, burde vært flyttet til egen route modul eller i det minste hatt en test som avslører om den brekkes
suspend fun ApplicationCall.lagCommandForLagreOgSendOpplastetPdfPåSak(
    sakId: UUID,
): JournalførOgSendOpplastetPdfSomBrevCommand {
    val parts = this.receiveMultipart()

    var pdfContent: ByteArray? = null
    var journaltittel: String? = null
    var distribueringsadresse: Distribueringsadresse? = null
    var distribusjonstype: dokument.domain.Distribusjonstype? = null
    parts.forEachPart { part ->
        when (part) {
            is PartData.FormItem -> {
                when (part.name) {
                    "distribusjonstype" -> {
                        distribusjonstype = Distribusjonstype.valueOf(part.value).toDomain()
                        part.dispose()
                    }
                    "tittel" -> {
                        journaltittel = part.value
                        part.dispose()
                    }
                    "adresse" -> {
                        val distribueringsadresseAsJson = part.value
                        part.dispose()
                        distribueringsadresse = deserialize<DistribueringsadresseBody>(distribueringsadresseAsJson).toDomain()
                    }
                    else -> {
                        throw IllegalArgumentException("Ikke riktig name i multipart ${part.name}")
                    }
                }
            }
            is PartData.FileItem -> {
                pdfContent = part.provider().readRemaining().readByteArray()
                require(pdfContent.isNotEmpty()) { "Pdf innhold må ha størrelse, denne var 0 bytes" }
                val minimumSizeForPdfv1 = 311
                require(pdfContent.size > minimumSizeForPdfv1) { "Pdf er minimum 312 bytes, denne var ${pdfContent.size} bytes" }
                part.dispose()
            }

            else -> {
                throw IllegalStateException("Støtter kun vanlig verdi og pdf fil")
            }
        }
    }

    return JournalførOgSendOpplastetPdfSomBrevCommand(
        sakId = sakId,
        saksbehandler = this.suUserContext.saksbehandler,
        journaltittel = journaltittel ?: throw IllegalStateException("Mangler journaltittel"),
        pdf = PdfA(content = pdfContent ?: throw IllegalStateException("Mangler Pdf innholt")),
        distribueringsadresse = distribueringsadresse,
        distribusjonstype = distribusjonstype ?: throw IllegalStateException("Mangler distribusjonstype"),
    )
}
