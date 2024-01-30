package no.nav.su.se.bakover.service.skatt

import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import vilkår.skatt.domain.Skattegrunnlag
import java.time.Year

data class FrioppslagSkattRequest(
    val fnr: Fnr,
    val epsFnr: Fnr?,
    val år: Year,
    val begrunnelse: String,
    val saksbehandler: NavIdentBruker.Saksbehandler,
    val sakstype: Sakstype,
    val fagsystemId: String,
)

/**
 * Knyttet til [FrioppslagSkattRequest] - disse knyttes ikke til en sakId.
 * @param fagsystemId vil være [Saksnummer] for uføre og String for alder (infotrygd sin id)
 */
data class GenererSkattPdfRequest(
    val skattegrunnlagSøkers: Skattegrunnlag,
    val skattegrunnlagEps: Skattegrunnlag?,
    val begrunnelse: String,
    val sakstype: Sakstype,
    val fagsystemId: String,
)
