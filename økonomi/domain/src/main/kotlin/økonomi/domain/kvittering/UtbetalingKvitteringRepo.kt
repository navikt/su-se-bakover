package økonomi.domain.kvittering

import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelseskonsumentId

interface UtbetalingKvitteringRepo {
    fun lagre(hendelse: RåKvitteringHendelse)
    fun lagre(
        hendelse: KvitteringPåSakHendelse,
        sessionContext: SessionContext,
    )
//    fun lagre(
//        hendelse: LagretDokumentHendelse,
//        sessionContext: SessionContext,
//    )

    fun hentUbehandledeKvitteringer(konsumentId: HendelseskonsumentId): Set<HendelseId>
    fun hentRåKvittering(id: HendelseId): RåKvitteringHendelse?
    fun hentUbehandledeKvitteringerKnyttetMotUtbetaling(konsumentId: HendelseskonsumentId): Set<HendelseId>
    fun hentKvittering(hendelseId: HendelseId): KvitteringPåSakHendelse?
}
