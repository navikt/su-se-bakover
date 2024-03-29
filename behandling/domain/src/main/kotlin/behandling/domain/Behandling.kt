package behandling.domain

import no.nav.su.se.bakover.common.domain.BehandlingsId
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import java.util.UUID

/**
 * https://jira.adeo.no/browse/BEGREP-201
 */
interface Behandling {
    val id: BehandlingsId
    val opprettet: Tidspunkt
    val sakId: UUID
    val saksnummer: Saksnummer
    val fnr: Fnr
}
