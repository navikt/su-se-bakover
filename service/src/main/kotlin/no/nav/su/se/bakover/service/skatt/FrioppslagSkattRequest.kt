package no.nav.su.se.bakover.service.skatt

import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import java.time.Year

data class FrioppslagSkattRequest(
    val fnr: Fnr,
    val år: Year,
    val begrunnelse: String,
    val saksbehandler: NavIdentBruker.Saksbehandler,
)
