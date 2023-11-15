package tilbakekreving.domain.kravgrunnlag

import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelseskonsumentId
import java.util.UUID

interface KravgrunnlagRepo {
    fun lagreRåttKravgrunnlagHendelse(
        hendelse: RåttKravgrunnlagHendelse,
        sessionContext: SessionContext? = null,
    )

    fun hentUprosesserteRåttKravgrunnlagHendelser(
        konsumentId: HendelseskonsumentId,
        sessionContext: SessionContext? = null,
        limit: Int = 10,
    ): Set<HendelseId>

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
    ): KravgrunnlagPåSakHendelser

    fun hentUprosesserteKravgrunnlagKnyttetTilSakHendelser(
        konsumentId: HendelseskonsumentId,
        sessionContext: SessionContext? = null,
        limit: Int = 10,
    ): Set<HendelseId>
}
