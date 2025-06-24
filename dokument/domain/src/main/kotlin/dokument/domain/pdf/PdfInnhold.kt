package dokument.domain.pdf

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.serialize

/**
 * TODO jah: Dette er en ren JsonDto som sendes serialisert til su-pdfgen.
 *  Den bør bo under client-modulen eller en tilsvarende infrastruktur-modul.
 */
@JsonPropertyOrder(
    "sakstype",
)
interface PdfInnhold {
    fun toJson(): String = serialize(this)

    @get:JsonIgnore
    val pdfTemplate: PdfTemplateMedDokumentNavn

    @get:JsonProperty
    val sakstype: Sakstype
}
