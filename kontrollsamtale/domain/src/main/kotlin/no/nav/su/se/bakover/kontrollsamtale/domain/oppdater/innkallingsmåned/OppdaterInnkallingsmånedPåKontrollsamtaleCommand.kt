package no.nav.su.se.bakover.kontrollsamtale.domain.oppdater.innkallingsmåned

import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.periode.Måned
import java.util.UUID

data class OppdaterInnkallingsmånedPåKontrollsamtaleCommand(
    val sakId: UUID,
    val kontrollsamtaleId: UUID,
    val saksbehandler: NavIdentBruker.Saksbehandler,
    val nyInnkallingsmåned: Måned,
)
