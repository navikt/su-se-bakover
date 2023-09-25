package no.nav.su.se.bakover.common.domain

import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt

/**
 * Når noe er avsluttet, betyr det at det ikke kan endres. Eksempler kan være når noe er ferdigbehandlet og skal bli låst.
 * Et eksempel er [no.nav.su.se.bakover.domain.vedtak.Vedtak]
 */
interface Avsluttet {
    // kan kanskje rename bare til avsluttet
    val avsluttetTidspunkt: Tidspunkt
    val avsluttetAv: NavIdentBruker?
}
