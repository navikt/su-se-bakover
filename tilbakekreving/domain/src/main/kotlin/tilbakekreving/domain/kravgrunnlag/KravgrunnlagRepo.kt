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
        sessionContext: SessionContext? = null,
    ): RåttKravgrunnlagHendelse?

    fun lagreKravgrunnlagPåSakHendelse(
        hendelse: KravgrunnlagPåSakHendelse,
        sessionContext: SessionContext? = null,
    )

    fun hentKravgrunnlagPåSakHendelser(
        sakId: UUID,
        sessionContext: SessionContext? = null,
    ): List<KravgrunnlagPåSakHendelse>

    fun hentUprosesserteKravgrunnlagKnyttetTilSakHendelser(
        konsumentId: HendelseskonsumentId,
        sessionContext: SessionContext? = null,
        limit: Int = 10,
    ): List<HendelseId>

    fun hentKravgrunnlagKnyttetTilSak(
        hendelseId: HendelseId,
        sessionContext: SessionContext? = null,
    ): KravgrunnlagPåSakHendelse?
}
