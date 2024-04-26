package økonomi.domain.kvittering

import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelseskonsumentId
import no.nav.su.se.bakover.hendelse.domain.JMSHendelseMetadata
import java.util.UUID

interface UtbetalingskvitteringRepo {
    fun lagreRåKvitteringHendelse(
        hendelse: RåUtbetalingskvitteringhendelse,
        meta: JMSHendelseMetadata,
        sessionContext: SessionContext? = null,
    )
    fun lagreUtbetalingskvitteringPåSakHendelse(
        hendelse: UtbetalingskvitteringPåSakHendelse,
        meta: DefaultHendelseMetadata,
        sessionContext: SessionContext? = null,
    )

    fun hentUprosesserteMottattUtbetalingskvittering(
        konsumentId: HendelseskonsumentId,
        sessionContext: SessionContext? = null,
    ): Set<HendelseId>
    fun hentRåUtbetalingskvitteringhendelse(
        hendelseId: HendelseId,
        sessionContext: SessionContext? = null,
    ): RåUtbetalingskvitteringhendelse?
    fun hentUprosesserteKnyttetUtbetalingskvitteringTilSak(
        konsumentId: HendelseskonsumentId,
        sessionContext: SessionContext? = null,
    ): Set<HendelseId>
    fun hentKnyttetUtbetalingskvitteringTilSakHendelse(
        hendelseId: HendelseId,
        sessionContext: SessionContext? = null,
    ): UtbetalingskvitteringPåSakHendelse?

    fun hentUtbetalingskvitteringerPåSakHendelser(
        sakId: UUID,
        sessionContext: SessionContext? = null,
    ): UtbetalingskvitteringPåSakHendelser
}
