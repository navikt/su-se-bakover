package dokument.domain.generer

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import dokument.domain.generer.bygger.PdfBestilling
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.serialize

/**
 * Innholdet i ett dokument. [bestilling] er markupen som sendes til pdf-bygger.
 * JSON-en fra [toJson] lagres som `generertDokumentJson` og sendes til dokarkiv som `originalJson`,
 * og noen spørringer leser feltene i den. Endringer i feltene er derfor en kontraktsendring.
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
