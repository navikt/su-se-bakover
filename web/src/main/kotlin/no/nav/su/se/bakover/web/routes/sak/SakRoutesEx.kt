package no.nav.su.se.bakover.web.routes.sak

import dokument.domain.distribuering.Distribueringsadresse
import io.ktor.http.content.PartData
import io.ktor.http.content.readAllParts
import io.ktor.http.content.streamProvider
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveMultipart
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

suspend fun ApplicationCall.lagCommandForLagreOgSendOpplastetPdfPåSak(
    sakId: UUID,
): JournalførOgSendOpplastetPdfSomBrevCommand {
    val parts = this.receiveMultipart().readAllParts()

    /**
     * Vi forventer en viss rekkefølge fra frontend på innholdet i formdata
     * 1. journaltittel
     * 2. distribusjonstype
     * 3. pdf
     * 4. distribueringsadresse - Denne er den eneste som er optional, og kommer sist i rekkefølgen
     */
    val journaltittel: String = (parts[0] as PartData.FormItem).value
    val distribusjonstype: dokument.domain.Distribusjonstype =
        Distribusjonstype.valueOf((parts[1] as PartData.FormItem).value).toDomain()
    val pdfContent: ByteArray = (parts[2] as PartData.FileItem).streamProvider().readBytes()
    val distribueringsadresse: Distribueringsadresse? = parts.getOrNull(3)?.let {
        val distribueringsadresseAsJson = (it as PartData.FormItem).value
        deserialize<DistribueringsadresseBody>(distribueringsadresseAsJson).toDomain()
    }

    return JournalførOgSendOpplastetPdfSomBrevCommand(
        sakId = sakId,
        saksbehandler = this.suUserContext.saksbehandler,
        journaltittel = journaltittel,
        pdf = PdfA(content = pdfContent),
        distribueringsadresse = distribueringsadresse,
        distribusjonstype = distribusjonstype,
    )
}
