package økonomi.infrastructure.kvittering.persistence

import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.hendelse.domain.HendelseActionRepo
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.HendelsePostgresRepo
import økonomi.domain.kvittering.KvitteringPåSakHendelse
import økonomi.domain.kvittering.RåKvitteringHendelse
import økonomi.domain.kvittering.UtbetalingKvitteringRepo
import økonomi.infrastructure.kvittering.persistence.KvitteringPåSakHendelseJson.Companion.toJson
import økonomi.infrastructure.kvittering.persistence.KvitteringPåSakHendelseJson.Companion.toKvitteringPåSakHendelse
import økonomi.infrastructure.kvittering.persistence.RåKvitteringHendelseJson.Companion.toJson
import økonomi.infrastructure.kvittering.persistence.RåKvitteringHendelseJson.Companion.toRåKvitteringHendelse

const val UtbetalingKvitteringHendelsestype = "UTBETALING_KVITTERING"
const val UtbetalingKvitteringPåSakHendelsestype = "UTBETALING_KVITTERING_PÅ_SAK"

class UtbetalingKvitteringPostgresRepo(
    private val hendelseRepo: HendelsePostgresRepo,
    private val hendelseActionRepo: HendelseActionRepo,
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

    override fun hentUbehandledeKvitteringer(subscriberId: String): List<HendelseId> {
        return hendelseActionRepo.hentHendelseIderForActionOgType(
            action = subscriberId,
            hendelsestype = UtbetalingKvitteringHendelsestype,
        )
    }

    override fun hentUbehandledeKvitteringerKnyttetMotUtbetaling(subscriberId: String): List<HendelseId> {
        return hendelseActionRepo.hentHendelseIderForActionOgType(
            action = subscriberId,
            hendelsestype = UtbetalingKvitteringPåSakHendelsestype,
        )
    }

    override fun hentRåKvittering(id: HendelseId): RåKvitteringHendelse? {
        val hendelse = hendelseRepo.hentHendelseForHendelseId(id)
        return hendelse?.toRåKvitteringHendelse()
    }

    override fun hentKvittering(hendelseId: HendelseId): KvitteringPåSakHendelse? {
        val hendelse = hendelseRepo.hentHendelseForHendelseId(hendelseId)
        return hendelse?.toKvitteringPåSakHendelse()
    }
}
