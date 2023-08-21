package økonomi.infrastructure.kvittering.persistence

import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.HendelsePostgresRepo
import økonomi.domain.kvittering.NyKvitteringHendelse
import økonomi.domain.kvittering.UtbetalingKvitteringRepo
import økonomi.infrastructure.kvittering.persistence.NyKvitteringHendelseJson.Companion.toJson

private const val UtbetalingKvitteringHendelsestype = "UTBETALING_KVITTERING"
private const val KnyttUtbetalingKvitteringTilSakHendelsestype = "KNYTT_KVITTERING_TIL_SAK_OG_UTBETALING"

class UtbetalingKvitteringPostgresRepo(
    private val hendelseRepo: HendelsePostgresRepo,
    private val dbMetrics: DbMetrics,
) : UtbetalingKvitteringRepo {

    override fun lagre(hendelse: NyKvitteringHendelse) {
        dbMetrics.timeQuery("lagreNyKvitteringHendelse") {
            hendelseRepo.persister(hendelse, UtbetalingKvitteringHendelsestype, hendelse.toJson())
        }
    }
}
