package økonomi.domain.kvittering

import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.hendelse.domain.HendelseId

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

    fun hentUbehandledeKvitteringer(subscriberId: String): List<HendelseId>
    fun hentRåKvittering(id: HendelseId): RåKvitteringHendelse?
    fun hentUbehandledeKvitteringerKnyttetMotUtbetaling(subscriberId: String): List<HendelseId>
    fun hentKvittering(hendelseId: HendelseId): KvitteringPåSakHendelse?
}
