package no.nav.su.se.bakover.domain.brev.søknad.lukk

import no.nav.su.se.bakover.common.ddMMyyyy
import no.nav.su.se.bakover.domain.brev.BrevInnhold
import no.nav.su.se.bakover.domain.brev.BrevTemplate
import java.time.LocalDate

data class TrukketSøknadBrevInnhold private constructor(
    val personalia: Personalia,
    val datoSøknadOpprettet: String,
    val trukketDato: String,
    val saksbehandlerNavn: String,
) : BrevInnhold() {
    override val brevTemplate: BrevTemplate = BrevTemplate.TrukketSøknad

    constructor(
        personalia: Personalia,
        datoSøknadOpprettet: LocalDate,
        trukketDato: LocalDate,
        saksbehandlerNavn: String,
    ) : this(personalia, datoSøknadOpprettet.ddMMyyyy(), trukketDato.ddMMyyyy(), saksbehandlerNavn)
}
