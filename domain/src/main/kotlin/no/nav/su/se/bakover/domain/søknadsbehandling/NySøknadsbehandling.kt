package no.nav.su.se.bakover.domain.søknadsbehandling

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.Fnr
import no.nav.su.se.bakover.domain.søknad.Søknad
import java.util.UUID

data class NySøknadsbehandling(
    val id: UUID,
    val opprettet: Tidspunkt,
    val sakId: UUID,
    val søknad: Søknad.Journalført.MedOppgave,
    val oppgaveId: OppgaveId,
    val behandlingsinformasjon: Behandlingsinformasjon,
    val fnr: Fnr,
    val avkorting: AvkortingVedSøknadsbehandling.Uhåndtert.KanIkkeHåndtere
)
