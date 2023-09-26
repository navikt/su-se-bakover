package tilbakekreving.domain.kravgrunnlag

import no.nav.su.se.bakover.common.persistence.SessionContext
import java.util.UUID

interface KravgrunnlagRepo {
    fun hentRåttÅpentKravgrunnlagForSak(sakId: UUID): RåttKravgrunnlag?
    fun hentKravgrunnlagForSak(sakId: UUID): List<Kravgrunnlag>

    fun lagreRåttKravgrunnlagHendelse(
        hendelse: RåttKravgrunnlagHendelse,
        sessionContext: SessionContext? = null,
    )

    fun hentUprosesserteRåttKravgrunnlagHendelseForSak(
        sakId: UUID,
        sessionContext: SessionContext?,
        limit: Int = 10,
    ): List<RåttKravgrunnlagHendelse>

    fun lagreKravgrunnlagPåSakHendelse(
        hendelse: KravgrunnlagPåSakHendelse,
        sessionContext: SessionContext? = null,
    )

    fun hentKravgrunnlagPåSakHendelser(sakId: UUID, sessionContext: SessionContext?): List<KravgrunnlagPåSakHendelse>
}
