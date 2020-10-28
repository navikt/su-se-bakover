package no.nav.su.se.bakover.domain.brev.søknad.lukk

import no.nav.su.se.bakover.common.ddMMyyyy
import no.nav.su.se.bakover.domain.brev.Brevdata
import no.nav.su.se.bakover.domain.brev.Brevtype
import java.time.LocalDate

data class TrukketSøknadBrevdata private constructor(
    val personalia: Personalia,
    val datoSøknadOpprettet: String,
    val trukketDato: String
) : Brevdata() {
    override fun brevtype(): Brevtype = Brevtype.TrukketSøknad

    constructor(
        personalia: Personalia,
        datoSøknadOpprettet: LocalDate,
        trukketDato: LocalDate
    ) : this(personalia, datoSøknadOpprettet.ddMMyyyy(), trukketDato.ddMMyyyy())
}
