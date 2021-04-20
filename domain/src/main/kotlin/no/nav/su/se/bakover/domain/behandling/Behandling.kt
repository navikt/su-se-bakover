package no.nav.su.se.bakover.domain.behandling

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import java.util.UUID

interface Behandling {
    val id: UUID
    val opprettet: Tidspunkt
    val sakId: UUID
    val saksnummer: Saksnummer
    val fnr: Fnr
    val oppgaveId: OppgaveId
    val periode: Periode
    val grunnlagsdata: Grunnlagsdata
}
