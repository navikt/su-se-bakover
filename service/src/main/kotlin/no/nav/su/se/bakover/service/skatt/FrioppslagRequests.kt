package no.nav.su.se.bakover.service.skatt

import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.skatt.Skattegrunnlag
import java.time.Year

data class FrioppslagSkattRequest(
    val fnr: Fnr,
    val år: Year,
    val begrunnelse: String,
    val saksbehandler: NavIdentBruker.Saksbehandler,
    val sakstype: Sakstype,
    val fagsystemId: String,
)

data class GenererSkattPdfOgJournalførRequest(
    val skattegrunnlag: Skattegrunnlag,
    val begrunnelse: String,
    val fnr: Fnr,
    val sakstype: Sakstype,
    val fagsystemId: String,
)
