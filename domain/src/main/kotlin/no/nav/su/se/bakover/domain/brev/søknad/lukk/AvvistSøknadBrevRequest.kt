package no.nav.su.se.bakover.domain.brev.søknad.lukk

import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.brev.BrevConfig
import no.nav.su.se.bakover.domain.brev.Brevdata
import no.nav.su.se.bakover.domain.brev.LagBrevRequest

data class AvvistSøknadBrevRequest(
    private val søknad: Søknad,
    private val brevConfig: BrevConfig
) : LagBrevRequest() {
    override fun getFnr(): Fnr = søknad.søknadInnhold.personopplysninger.fnr

    override fun lagBrevdata(personalia: Brevdata.Personalia): Brevdata {
        return when (brevConfig) {
            is BrevConfig.Vedtak -> AvvistSøknadVedtakBrevdata(personalia)
            is BrevConfig.Fritekst -> AvvistSøknadFritekstBrevdata(
                personalia = personalia,
                fritekst = brevConfig.getFritekst()
            )
        }
    }
}
