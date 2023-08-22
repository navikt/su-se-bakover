package økonomi.infrastructure.kvittering.persistence

import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelseJobbRepo
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.HendelsePostgresRepo
import økonomi.domain.kvittering.KvitteringPåSakHendelse
import økonomi.domain.kvittering.RåKvitteringHendelse
import økonomi.domain.kvittering.UtbetalingKvitteringRepo
import økonomi.infrastructure.kvittering.persistence.KvitteringPåSakHendelseJson.Companion.toJson
import økonomi.infrastructure.kvittering.persistence.RåKvitteringHendelseJson.Companion.toJson
import økonomi.infrastructure.kvittering.persistence.RåKvitteringHendelseJson.Companion.toRåKvitteringHendelse

const val UtbetalingKvitteringHendelsestype = "UTBETALING_KVITTERING"
const val UtbetalingKvitteringPåSakHendelsestype = "UTBETALING_KVITTERING_PÅ_SAK"

class UtbetalingKvitteringPostgresRepo(
    private val hendelseRepo: HendelsePostgresRepo,
    private val hendelseJobbRepo: HendelseJobbRepo,
    private val dbMetrics: DbMetrics,
) : UtbetalingKvitteringRepo {

    override fun lagre(hendelse: RåKvitteringHendelse) {
        dbMetrics.timeQuery("lagreRåKvitteringHendelse") {
            hendelseRepo.persister(hendelse, UtbetalingKvitteringHendelsestype, hendelse.toJson())
        }
    }

    override fun lagre(hendelse: KvitteringPåSakHendelse, sessionContext: SessionContext) {
        dbMetrics.timeQuery("lagreKvitteringPåSakHendelse") {
            hendelseRepo.persister(hendelse, UtbetalingKvitteringPåSakHendelsestype, hendelse.toJson())
        }
    }

    override fun hentUbehandledeKvitteringer(jobbNavn: String): List<HendelseId> {
        return hendelseJobbRepo.hentHendelseIderForNavnOgType(
            jobbNavn = jobbNavn,
            hendelsestype = UtbetalingKvitteringHendelsestype,
        )
    }

    override fun hentRåKvittering(id: HendelseId): RåKvitteringHendelse? {
        val hendelse = hendelseRepo.hentHendelseForHendelseId(id)
        return hendelse?.toRåKvitteringHendelse()
    }
}
