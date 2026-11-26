package dokument.domain.generer

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import dokument.domain.generer.bygger.PdfBestilling
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

    /** Markup-bestillingen til pdf-bygger. Hver innholdsklasse eier sin egen, så en ny mal uten bestilling kompilerer ikke. */
    fun bestilling(): PdfBestilling

    @get:JsonIgnore
    val dokumentMal: DokumentMal

    @get:JsonProperty
    val sakstype: Sakstype
}
