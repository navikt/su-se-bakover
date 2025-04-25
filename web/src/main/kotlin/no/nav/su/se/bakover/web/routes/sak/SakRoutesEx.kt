package no.nav.su.se.bakover.web.routes.sak

import dokument.domain.distribuering.Distribueringsadresse
import io.ktor.http.content.MultiPartData
import io.ktor.http.content.PartData
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveMultipart
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.domain.sak.JournalførOgSendOpplastetPdfSomBrevCommand
import java.util.ArrayList
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
    val journaltittel: String = (parts[0] as PartData.FormItem).let { partdata ->
        partdata.value.also {
            partdata.dispose
        }
    }
    val distribusjonstype: dokument.domain.Distribusjonstype =
        (parts[1] as PartData.FormItem).let { partdata ->
            Distribusjonstype.valueOf(partdata.value).toDomain().also {
                partdata.dispose
            }
        }
    val pdfContent: ByteArray = (parts[2] as PartData.FileItem).let { partdata ->
        partdata.provider().readRemaining().readByteArray().also {
            partdata.dispose
        }
    }
    val distribueringsadresse: Distribueringsadresse? = parts.getOrNull(3)?.let {
        val partdata = it as PartData.FormItem
        val distribueringsadresseAsJson = partdata.value
        partdata.dispose
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

// Kopiert fra ktor 3.1.2 - io.ktor.http.content
// TODO jah: Bør skrive om dette til forEachPart, men kan kanskje være en idé og se på requesten fra frontend samtidig.
private suspend fun MultiPartData.readAllParts(): List<PartData> {
    var part = readPart() ?: return emptyList()
    val parts = ArrayList<PartData>()
    parts.add(part)

    do {
        part = readPart() ?: break
        parts.add(part)
    } while (true)

    return parts
}
