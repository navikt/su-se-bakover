package tilbakekreving.domain.kravgrunnlag

import no.nav.su.se.bakover.common.persistence.SessionContext
import java.util.UUID

interface KravgrunnlagRepo {
    fun hentÅpentKravgrunnlagForSak(sakId: UUID): RåttKravgrunnlag?
    fun hentRåttKravgrunnlag(id: String): RåttKravgrunnlag?
    fun hentKravgrunnlag(id: String): Kravgrunnlag?

    fun lagreRåKravgrunnlagHendelse(
        hendelse: RåttKravgrunnlagHendelse,
        sessionContext: SessionContext? = null,
    )
}
