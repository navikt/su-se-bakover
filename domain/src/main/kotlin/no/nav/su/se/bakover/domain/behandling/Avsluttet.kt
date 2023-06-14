package no.nav.su.se.bakover.domain.behandling

import no.nav.su.se.bakover.common.tid.Tidspunkt
import java.util.UUID

/**
 * Når noe er avsluttet, betyr det at det ikke kan endres. Eksempler kan være når noe er ferdigbehandlet og skal bli låst.
 * Et eksempel er [no.nav.su.se.bakover.domain.vedtak.Vedtak]
 */
interface Avsluttet {
    val id: UUID

    // kan kanskje rename bare til avsluttet
    val avsluttetTidspunkt: Tidspunkt
}
