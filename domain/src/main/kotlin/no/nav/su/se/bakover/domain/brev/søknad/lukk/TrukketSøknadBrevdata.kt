package no.nav.su.se.bakover.domain.brev.søknad.lukk

import no.nav.su.se.bakover.common.ddMMyyyy
import no.nav.su.se.bakover.domain.brev.BrevTemplate
import no.nav.su.se.bakover.domain.brev.Brevdata
import java.time.LocalDate

data class TrukketSøknadBrevdata private constructor(
    val personalia: Personalia,
    val datoSøknadOpprettet: String,
    val trukketDato: String
) : Brevdata() {
    override fun brevtype(): BrevTemplate = BrevTemplate.TrukketSøknad

    constructor(
        personalia: Personalia,
        datoSøknadOpprettet: LocalDate,
        trukketDato: LocalDate
    ) : this(personalia, datoSøknadOpprettet.ddMMyyyy(), trukketDato.ddMMyyyy())
}
