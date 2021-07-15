package no.nav.su.se.bakover.domain.brev.søknad.lukk

import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.brev.BrevConfig
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.brev.lagPersonalia

data class AvvistSøknadBrevRequest(
    override val person: Person,
    private val brevConfig: BrevConfig,
    private val saksbehandlerNavn: String,
) : LagBrevRequest {
    override val brevInnhold = when (brevConfig) {
        is BrevConfig.Vedtak -> AvvistSøknadVedtakBrevInnhold(
            personalia = lagPersonalia(),
            saksbehandlerNavn = saksbehandlerNavn,
            fritekst = brevConfig.getFritekst(),
        )
        is BrevConfig.Fritekst -> AvvistSøknadFritekstBrevInnhold(
            personalia = lagPersonalia(),
            saksbehandlerNavn = saksbehandlerNavn,
            fritekst = brevConfig.getFritekst(),
        )
    }
}
