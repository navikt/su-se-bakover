package økonomi.infrastructure.kvittering.persistence

import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelsekonsumenterRepo
import no.nav.su.se.bakover.hendelse.domain.HendelseskonsumentId
import no.nav.su.se.bakover.hendelse.domain.Hendelsestype
import no.nav.su.se.bakover.hendelse.domain.JMSHendelseMetadata
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.HendelsePostgresRepo
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.toDbJson
import økonomi.domain.kvittering.KvitteringPåSakHendelse
import økonomi.domain.kvittering.RåKvitteringHendelse
import økonomi.domain.kvittering.UtbetalingKvitteringRepo
import økonomi.infrastructure.kvittering.persistence.KvitteringPåSakHendelseJson.Companion.toDbJson
import økonomi.infrastructure.kvittering.persistence.KvitteringPåSakHendelseJson.Companion.toKvitteringPåSakHendelse
import økonomi.infrastructure.kvittering.persistence.RåKvitteringHendelseJson.Companion.toJson
import økonomi.infrastructure.kvittering.persistence.RåKvitteringHendelseJson.Companion.toRåKvitteringHendelse

val UtbetalingKvitteringHendelsestype = Hendelsestype("UTBETALING_KVITTERING")
val UtbetalingKvitteringPåSakHendelsestype = Hendelsestype("UTBETALING_KVITTERING_PÅ_SAK")

class UtbetalingKvitteringPostgresRepo(
    private val hendelseRepo: HendelsePostgresRepo,
    private val hendelsekonsumenterRepo: HendelsekonsumenterRepo,
    private val dbMetrics: DbMetrics,
) : UtbetalingKvitteringRepo {

    override fun lagre(hendelse: RåKvitteringHendelse, meta: JMSHendelseMetadata) {
        dbMetrics.timeQuery("lagreRåKvitteringHendelse") {
            hendelseRepo.persisterHendelse(
                hendelse = hendelse,
                type = UtbetalingKvitteringHendelsestype,
                data = hendelse.toJson(),
                meta = meta.toDbJson(),
            )
        }
    }

    override fun lagre(
        hendelse: KvitteringPåSakHendelse,
        meta: DefaultHendelseMetadata,
        sessionContext: SessionContext,
    ) {
        dbMetrics.timeQuery("lagreKvitteringPåSakHendelse") {
            hendelseRepo.persisterSakshendelse(
                hendelse = hendelse,
                type = UtbetalingKvitteringPåSakHendelsestype,
                data = hendelse.toDbJson(),
                meta = meta.toDbJson(),
            )
        }
    }

    override fun hentUbehandledeKvitteringer(konsumentId: HendelseskonsumentId): Set<HendelseId> {
        return hendelsekonsumenterRepo.hentHendelseIderForKonsumentOgType(
            konsumentId = konsumentId,
            hendelsestype = UtbetalingKvitteringHendelsestype,
        )
    }

    override fun hentUbehandledeKvitteringerKnyttetMotUtbetaling(konsumentId: HendelseskonsumentId): Set<HendelseId> {
        return hendelsekonsumenterRepo.hentHendelseIderForKonsumentOgType(
            konsumentId = konsumentId,
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
