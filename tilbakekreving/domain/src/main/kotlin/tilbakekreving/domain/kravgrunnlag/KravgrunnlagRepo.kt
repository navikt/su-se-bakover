package tilbakekreving.domain.kravgrunnlag

import no.nav.su.se.bakover.common.persistence.SessionContext
import java.util.UUID

interface KravgrunnlagRepo {
    fun hentRåttÅpentKravgrunnlagForSak(sakId: UUID): RåttKravgrunnlag?
    fun hentKravgrunnlagForSak(sakId: UUID): List<Kravgrunnlag>

    fun lagreRåKravgrunnlagHendelse(
        hendelse: RåttKravgrunnlagHendelse,
        sessionContext: SessionContext? = null,
    )
}
