package tilbakekreving.domain.kravgrunnlag

import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelseskonsumentId
import java.util.UUID

interface KravgrunnlagRepo {
    /**
     * TODO jah: Slett når kravgrunnlaghendelsene er i prod
     */
    fun hentRåttÅpentKravgrunnlagForSak(sakId: UUID): RåttKravgrunnlag?

    /**
     * TODO jah: Slett når kravgrunnlaghendelsene er i prod
     */
    fun hentKravgrunnlagForSak(sakId: UUID): List<Kravgrunnlag>

    fun lagreRåttKravgrunnlagHendelse(
        hendelse: RåttKravgrunnlagHendelse,
        sessionContext: SessionContext? = null,
    )

    fun hentUprosesserteRåttKravgrunnlagHendelser(
        konsumentId: HendelseskonsumentId,
        sessionContext: SessionContext? = null,
        limit: Int = 10,
    ): List<HendelseId>

    fun hentRåttKravgrunnlagHendelseForHendelseId(
        hendelseId: HendelseId,
        sessionContext: SessionContext?,
    ): RåttKravgrunnlagHendelse?

    fun lagreKravgrunnlagPåSakHendelse(
        hendelse: KravgrunnlagPåSakHendelse,
        sessionContext: SessionContext? = null,
    )

    fun hentKravgrunnlagPåSakHendelser(sakId: UUID, sessionContext: SessionContext?): List<KravgrunnlagPåSakHendelse>
}
