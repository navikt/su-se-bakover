package no.nav.su.se.bakover.behandling

import no.nav.su.se.bakover.common.domain.BehandlingsId
import no.nav.su.se.bakover.common.tid.Tidspunkt
import java.util.UUID

/**
 * https://jira.adeo.no/browse/BEGREP-201
 */
interface Behandling {
    val id: BehandlingsId
    val opprettet: Tidspunkt
    val sakId: UUID
}
