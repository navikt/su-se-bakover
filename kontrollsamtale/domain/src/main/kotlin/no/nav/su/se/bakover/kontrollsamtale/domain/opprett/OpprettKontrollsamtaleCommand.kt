package no.nav.su.se.bakover.kontrollsamtale.domain.opprett

import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.periode.Måned
import java.util.UUID

data class OpprettKontrollsamtaleCommand(
    val sakId: UUID,
    val saksbehandler: NavIdentBruker.Saksbehandler,
    val innkallingsmåned: Måned,
)
