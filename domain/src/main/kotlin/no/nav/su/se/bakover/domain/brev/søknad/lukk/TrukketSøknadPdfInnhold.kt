package no.nav.su.se.bakover.domain.brev.søknad.lukk

import no.nav.su.se.bakover.common.extensions.ddMMyyyy
import no.nav.su.se.bakover.domain.brev.BrevTemplate
import no.nav.su.se.bakover.domain.brev.PdfInnhold
import java.time.LocalDate

data class TrukketSøknadPdfInnhold private constructor(
    val personalia: Personalia,
    val datoSøknadOpprettet: String,
    val trukketDato: String,
    val saksbehandlerNavn: String,
) : PdfInnhold() {
    override val brevTemplate: BrevTemplate = BrevTemplate.TrukketSøknad

    constructor(
        personalia: Personalia,
        datoSøknadOpprettet: LocalDate,
        trukketDato: LocalDate,
        saksbehandlerNavn: String,
    ) : this(personalia, datoSøknadOpprettet.ddMMyyyy(), trukketDato.ddMMyyyy(), saksbehandlerNavn)
}
