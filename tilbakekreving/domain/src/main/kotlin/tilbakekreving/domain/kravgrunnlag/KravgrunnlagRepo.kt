package tilbakekreving.domain.kravgrunnlag

import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelseskonsumentId
import no.nav.su.se.bakover.hendelse.domain.JMSHendelseMetadata
import java.util.UUID

interface KravgrunnlagRepo {
    fun lagreRåttKravgrunnlagHendelse(
        hendelse: RåttKravgrunnlagHendelse,
        meta: JMSHendelseMetadata,
        sessionContext: SessionContext? = null,
    )

    fun hentUprosesserteRåttKravgrunnlagHendelser(
        konsumentId: HendelseskonsumentId,
        sessionContext: SessionContext? = null,
        limit: Int = 10,
    ): Set<HendelseId>

    fun hentRåttKravgrunnlagHendelseMedMetadataForHendelseId(
        hendelseId: HendelseId,
        sessionContext: SessionContext? = null,
    ): Pair<RåttKravgrunnlagHendelse, JMSHendelseMetadata>?

    fun lagreKravgrunnlagPåSakHendelse(
        hendelse: KravgrunnlagPåSakHendelse,
        meta: DefaultHendelseMetadata,
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
