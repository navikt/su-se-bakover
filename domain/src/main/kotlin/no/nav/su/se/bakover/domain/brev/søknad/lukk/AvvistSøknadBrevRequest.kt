package no.nav.su.se.bakover.domain.brev.søknad.lukk

import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.brev.BrevConfig
import no.nav.su.se.bakover.domain.brev.BrevInnhold
import no.nav.su.se.bakover.domain.brev.LagBrevRequest

data class AvvistSøknadBrevRequest(
    private val person: Person,
    private val brevConfig: BrevConfig
) : LagBrevRequest {
    override fun getPerson(): Person = person

    override fun lagBrevInnhold(personalia: BrevInnhold.Personalia): BrevInnhold {
        return when (brevConfig) {
            is BrevConfig.Vedtak -> AvvistSøknadVedtakBrevInnhold(personalia, brevConfig.getFritekst())
            is BrevConfig.Fritekst -> AvvistSøknadFritekstBrevInnhold(
                personalia = personalia,
                fritekst = brevConfig.getFritekst()
            )
        }
    }
}
