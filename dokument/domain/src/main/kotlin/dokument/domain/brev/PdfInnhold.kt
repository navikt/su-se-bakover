package dokument.domain.brev

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import dokument.domain.PdfTemplateMedDokumentNavn
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.serialize

/**
 * TODO jah: Dette er en ren JsonDto som sendes serialisert til su-pdfgen.
 *  Den bør bo under client-modulen eller en tilsvarende infrastruktur-modul.
 */
abstract class PdfInnhold {
    fun toJson(): String = serialize(this)

    @get:JsonIgnore
    abstract val pdfTemplate: PdfTemplateMedDokumentNavn
    // TODO CHM 05.05.2021: Se på å samle mer av det som er felles for brevinnholdene, f.eks. personalia

    // TODO ØH 21.06.2022: Denne bør være abstract på sikt, og settes for alle brev eksplisitt
    open val sakstype: Sakstype = Sakstype.UFØRE

    @JsonProperty
    fun erAldersbrev(): Boolean = this.sakstype == Sakstype.ALDER
}
