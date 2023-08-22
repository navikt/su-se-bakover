package økonomi.domain.kvittering

import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.hendelse.domain.HendelseId

interface UtbetalingKvitteringRepo {
    fun lagre(hendelse: RåKvitteringHendelse)
    fun lagre(
        hendelse: KvitteringPåSakHendelse,
        sessionContext: SessionContext,
    )

    fun hentUbehandledeKvitteringer(jobbNavn: String): List<HendelseId>
    fun hentRåKvittering(id: HendelseId): RåKvitteringHendelse?
}
