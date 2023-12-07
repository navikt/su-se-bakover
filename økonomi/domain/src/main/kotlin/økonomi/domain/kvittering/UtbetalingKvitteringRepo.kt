package økonomi.domain.kvittering

import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelseskonsumentId
import no.nav.su.se.bakover.hendelse.domain.JMSHendelseMetadata

interface UtbetalingKvitteringRepo {
    fun lagre(
        hendelse: RåKvitteringHendelse,
        meta: JMSHendelseMetadata,
    )
    fun lagre(
        hendelse: KvitteringPåSakHendelse,
        meta: DefaultHendelseMetadata,
        sessionContext: SessionContext,
    )

    fun hentUbehandledeKvitteringer(konsumentId: HendelseskonsumentId): Set<HendelseId>
    fun hentRåKvittering(id: HendelseId): RåKvitteringHendelse?
    fun hentUbehandledeKvitteringerKnyttetMotUtbetaling(konsumentId: HendelseskonsumentId): Set<HendelseId>
    fun hentKvittering(hendelseId: HendelseId): KvitteringPåSakHendelse?
}
