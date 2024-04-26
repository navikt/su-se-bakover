package økonomi.infrastructure.kvittering.persistence

import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelseRepo
import no.nav.su.se.bakover.hendelse.domain.HendelsekonsumenterRepo
import no.nav.su.se.bakover.hendelse.domain.HendelseskonsumentId
import no.nav.su.se.bakover.hendelse.domain.Hendelsestype
import no.nav.su.se.bakover.hendelse.domain.JMSHendelseMetadata
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.HendelsePostgresRepo
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.toDbJson
import økonomi.domain.kvittering.RåUtbetalingskvitteringhendelse
import økonomi.domain.kvittering.UtbetalingskvitteringPåSakHendelse
import økonomi.domain.kvittering.UtbetalingskvitteringPåSakHendelser
import økonomi.domain.kvittering.UtbetalingskvitteringRepo
import økonomi.infrastructure.kvittering.persistence.KvitteringPåSakHendelseJson.Companion.toDbJson
import økonomi.infrastructure.kvittering.persistence.KvitteringPåSakHendelseJson.Companion.toKvitteringPåSakHendelse
import java.util.UUID

val MottattUtbetalingskvitteringHendelsestype = Hendelsestype("MOTTATT_UTBETALINGSKVITTERING")
val KnyttetUtbetalingskvitteringTilSakHendelsestype = Hendelsestype("KNYTTET_UTBETALINGSKVITTERING_TIL_SAK")

class UtbetalingKvitteringPostgresRepo(
    private val hendelseRepo: HendelseRepo,
    private val hendelsekonsumenterRepo: HendelsekonsumenterRepo,
    private val dbMetrics: DbMetrics,
) : UtbetalingskvitteringRepo {

    override fun lagreRåKvitteringHendelse(
        hendelse: RåUtbetalingskvitteringhendelse,
        meta: JMSHendelseMetadata,
        sessionContext: SessionContext?,
    ) {
        dbMetrics.timeQuery("lagreRåKvitteringHendelse") {
            (hendelseRepo as HendelsePostgresRepo).persisterHendelse(
                hendelse = hendelse,
                type = MottattUtbetalingskvitteringHendelsestype,
                data = hendelse.toJson(),
                meta = meta.toDbJson(),
                sessionContext = sessionContext,
            )
        }
    }

    override fun lagreUtbetalingskvitteringPåSakHendelse(
        hendelse: UtbetalingskvitteringPåSakHendelse,
        meta: DefaultHendelseMetadata,
        sessionContext: SessionContext?,
    ) {
        dbMetrics.timeQuery("lagreUtbetalingskvitteringPåSakHendelse") {
            (hendelseRepo as HendelsePostgresRepo).persisterSakshendelse(
                hendelse = hendelse,
                type = KnyttetUtbetalingskvitteringTilSakHendelsestype,
                data = hendelse.toDbJson(),
                meta = meta.toDbJson(),
                sessionContext = sessionContext,
            )
        }
    }

    override fun hentUprosesserteMottattUtbetalingskvittering(
        konsumentId: HendelseskonsumentId,
        sessionContext: SessionContext?,
    ): Set<HendelseId> {
        return dbMetrics.timeQuery("hentUbehandledeMottattUtbetalingskvittering") {
            hendelsekonsumenterRepo.hentHendelseIderForKonsumentOgType(
                konsumentId = konsumentId,
                hendelsestype = MottattUtbetalingskvitteringHendelsestype,
                sessionContext = sessionContext,
            )
        }
    }

    override fun hentUprosesserteKnyttetUtbetalingskvitteringTilSak(
        konsumentId: HendelseskonsumentId,
        sessionContext: SessionContext?,
    ): Set<HendelseId> {
        return dbMetrics.timeQuery("hentUbehandledeKnyttetUtbetalingskvitteringTilSak") {
            hendelsekonsumenterRepo.hentHendelseIderForKonsumentOgType(
                konsumentId = konsumentId,
                hendelsestype = KnyttetUtbetalingskvitteringTilSakHendelsestype,
                sessionContext = sessionContext,
            )
        }
    }

    override fun hentRåUtbetalingskvitteringhendelse(
        hendelseId: HendelseId,
        sessionContext: SessionContext?,
    ): RåUtbetalingskvitteringhendelse? {
        return dbMetrics.timeQuery("hentRåKvitteringHendelse") {
            (hendelseRepo as HendelsePostgresRepo).hentHendelseForHendelseId(
                hendelseId = hendelseId,
                sessionContext = sessionContext,
            )?.toRåKvitteringHendelse()
        }
    }

    override fun hentKnyttetUtbetalingskvitteringTilSakHendelse(
        hendelseId: HendelseId,
        sessionContext: SessionContext?,
    ): UtbetalingskvitteringPåSakHendelse? {
        return dbMetrics.timeQuery("hentKnyttetUtbetalingskvitteringTilSakHendelse") {
            (hendelseRepo as HendelsePostgresRepo).hentHendelseForHendelseId(
                hendelseId = hendelseId,
                sessionContext = sessionContext,
            )?.toKvitteringPåSakHendelse()
        }
    }

    override fun hentUtbetalingskvitteringerPåSakHendelser(
        sakId: UUID,
        sessionContext: SessionContext?,
    ): UtbetalingskvitteringPåSakHendelser {
        return dbMetrics.timeQuery("hentKravgrunnlagPåSakHendelser") {
            (hendelseRepo as HendelsePostgresRepo).hentHendelserForSakIdOgType(
                sakId = sakId,
                type = KnyttetUtbetalingskvitteringTilSakHendelsestype,
                sessionContext = sessionContext,
            ).map {
                it.toKvitteringPåSakHendelse()
            }.let { UtbetalingskvitteringPåSakHendelser(it) }
        }
    }
}
