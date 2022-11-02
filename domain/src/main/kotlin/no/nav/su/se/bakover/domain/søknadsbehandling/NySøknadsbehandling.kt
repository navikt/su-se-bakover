package no.nav.su.se.bakover.domain.søknadsbehandling

import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.søknad.Søknad
import java.util.UUID

data class NySøknadsbehandling(
    val id: UUID,
    val opprettet: Tidspunkt,
    val sakId: UUID,
    val søknad: Søknad.Journalført.MedOppgave,
    val oppgaveId: OppgaveId,
    val fnr: Fnr,
    val avkorting: AvkortingVedSøknadsbehandling.Uhåndtert.KanIkkeHåndtere,
    val sakstype: Sakstype,
    val saksbehandler: NavIdentBruker.Saksbehandler,
)
