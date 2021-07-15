package no.nav.su.se.bakover.domain.brev.søknad.lukk

import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.brev.lagPersonalia
import java.time.LocalDate

data class TrukketSøknadBrevRequest(
    override val person: Person,
    private val søknad: Søknad,
    private val trukketDato: LocalDate,
    private val saksbehandlerNavn: String,
) : LagBrevRequest {
    override val brevInnhold = TrukketSøknadBrevInnhold(
        personalia = lagPersonalia(),
        datoSøknadOpprettet = søknad.opprettet.toLocalDate(zoneIdOslo),
        trukketDato = trukketDato,
        saksbehandlerNavn = saksbehandlerNavn,
    )
}
