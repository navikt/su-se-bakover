package dokument.domain.generer.bygger.maler

import com.fasterxml.jackson.annotation.JsonInclude
import dokument.domain.generer.DokumentMal
import dokument.domain.generer.PdfInnhold
import dokument.domain.generer.PersonaliaPdfInnhold
import dokument.domain.generer.bygger.dokument
import dokument.domain.generer.bygger.fritekst
import dokument.domain.generer.bygger.fødselsnummerAvsnitt
import dokument.domain.generer.bygger.hilsen
import no.nav.su.se.bakover.common.domain.sak.Sakstype

data class AvvistSøknadFritekstPdfInnhold(
    override val sakstype: Sakstype,
    val personalia: PersonaliaPdfInnhold,
    val saksbehandlerNavn: String,
    val fritekst: String,
) : PdfInnhold {

    override val dokumentMal = DokumentMal.Søknadsbehandling.AvvistSøknadFritekst

    @Suppress("unused")
    @JsonInclude
    val tittel: String = dokumentMal.tittel
}

val avvistSøknadFritekst = { tittel: String, pdfInnhold: AvvistSøknadFritekstPdfInnhold ->
    dokument(tittel, pdfInnhold.personalia) {
        fødselsnummerAvsnitt(pdfInnhold.personalia)
        fritekst(pdfInnhold.fritekst)
        hilsen(saksbehandler = pdfInnhold.saksbehandlerNavn)
    }
}
