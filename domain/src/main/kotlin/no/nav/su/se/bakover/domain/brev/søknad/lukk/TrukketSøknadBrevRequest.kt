package no.nav.su.se.bakover.domain.brev.søknad.lukk

import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.brev.BrevInnhold
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import java.time.LocalDate

data class TrukketSøknadBrevRequest(
    private val person: Person,
    private val søknad: Søknad,
    private val trukketDato: LocalDate,
    private val saksbehandlerNavn: String
) : LagBrevRequest {
    override fun getPerson(): Person = person

    override fun lagBrevInnhold(personalia: BrevInnhold.Personalia): BrevInnhold {
        return TrukketSøknadBrevInnhold(
            personalia = personalia,
            datoSøknadOpprettet = søknad.opprettet.toLocalDate(zoneIdOslo),
            trukketDato = trukketDato,
            saksbehandlerNavn = saksbehandlerNavn
        )
    }
}
