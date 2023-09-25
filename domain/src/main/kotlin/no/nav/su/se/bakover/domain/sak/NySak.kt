package no.nav.su.se.bakover.domain.sak

import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.domain.behandling.Behandlinger
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.Utbetalinger
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import java.util.UUID

data class NySak(
    val id: UUID = UUID.randomUUID(),
    val opprettet: Tidspunkt,
    val fnr: Fnr,
    val søknad: Søknad.Ny,
) {
    fun toSak(
        saksnummer: Saksnummer,
        versjon: Hendelsesversjon,
    ): Sak {
        return Sak(
            id = id,
            saksnummer = saksnummer,
            opprettet = opprettet,
            fnr = fnr,
            søknader = listOf(søknad),
            behandlinger = Behandlinger.empty(id),
            utbetalinger = Utbetalinger(),
            vedtakListe = emptyList(),
            type = søknad.type,
            uteståendeAvkorting = Avkortingsvarsel.Ingen,
            versjon = versjon,
        )
    }
}
