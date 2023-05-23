package no.nav.su.se.bakover.domain.brev.søknad.lukk

import no.nav.su.se.bakover.common.extensions.ddMMyyyy
import no.nav.su.se.bakover.domain.brev.PdfInnhold
import no.nav.su.se.bakover.domain.brev.PdfTemplateMedDokumentNavn
import java.time.LocalDate

data class TrukketSøknadPdfInnhold private constructor(
    val personalia: Personalia,
    val datoSøknadOpprettet: String,
    val trukketDato: String,
    val saksbehandlerNavn: String,
) : PdfInnhold() {
    override val pdfTemplate: PdfTemplateMedDokumentNavn = PdfTemplateMedDokumentNavn.TrukketSøknad

    constructor(
        personalia: Personalia,
        datoSøknadOpprettet: LocalDate,
        trukketDato: LocalDate,
        saksbehandlerNavn: String,
    ) : this(personalia, datoSøknadOpprettet.ddMMyyyy(), trukketDato.ddMMyyyy(), saksbehandlerNavn)
}
