package økonomi.infrastructure.kvittering

import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.hendelse.domain.HendelseRepo
import no.nav.su.se.bakover.hendelse.domain.HendelsekonsumenterRepo
import no.nav.su.se.bakover.vedtak.application.FerdigstillVedtakService
import økonomi.application.kvittering.FerdigstillVedtakEtterMottattKvitteringKonsument
import økonomi.application.kvittering.KnyttKvitteringTilSakOgUtbetalingKonsument
import økonomi.application.kvittering.RåKvitteringService
import økonomi.application.utbetaling.UtbetalingService
import økonomi.domain.kvittering.Kvittering
import økonomi.domain.kvittering.UtbetalingskvitteringRepo
import økonomi.infrastructure.kvittering.persistence.UtbetalingKvitteringPostgresRepo
import java.time.Clock

data class UtbetalingskvitteringKomponenter private constructor(
    val utbetalingKvitteringRepo: UtbetalingskvitteringRepo,
    val knyttKvitteringTilSakOgUtbetalingService: KnyttKvitteringTilSakOgUtbetalingKonsument,
    val råKvitteringService: RåKvitteringService,
    val ferdigstillVedtakEtterMottattKvitteringKonsument: FerdigstillVedtakEtterMottattKvitteringKonsument,
) {
    companion object {
        fun create(
            sakService: SakService,
            sessionFactory: SessionFactory,
            clock: Clock,
            hendelsekonsumenterRepo: HendelsekonsumenterRepo,
            hendelseRepo: HendelseRepo,
            dbMetrics: DbMetrics,
            utbetalingService: UtbetalingService,
            ferdigstillVedtakService: FerdigstillVedtakService,
            xmlMapperForUtbetalingskvittering: (String) -> Triple<Saksnummer, UUID30, Kvittering.Utbetalingsstatus>,
        ): UtbetalingskvitteringKomponenter {
            val utbetalingKvitteringRepo = UtbetalingKvitteringPostgresRepo(
                hendelseRepo = hendelseRepo,
                hendelsekonsumenterRepo = hendelsekonsumenterRepo,
                dbMetrics = dbMetrics,
            )
            val knyttKvitteringTilSakOgUtbetalingService =
                KnyttKvitteringTilSakOgUtbetalingKonsument(
                    utbetalingKvitteringRepo = utbetalingKvitteringRepo,
                    sakService = sakService,
                    hendelsekonsumenterRepo = hendelsekonsumenterRepo,
                    mapRåXmlTilSaksnummerOgUtbetalingId = xmlMapperForUtbetalingskvittering,
                    clock = clock,
                    sessionFactory = sessionFactory,
                    utbetalingService = utbetalingService,
                )
            val råKvitteringService = RåKvitteringService(
                utbetalingKvitteringRepo = utbetalingKvitteringRepo,
                clock = clock,
            )
            val ferdigstillVedtakEtterMottattKvitteringKonsument = FerdigstillVedtakEtterMottattKvitteringKonsument(
                ferdigstillVedtakService = ferdigstillVedtakService,
                utbetalingKvitteringRepo = utbetalingKvitteringRepo,
                sakService = sakService,
                sessionFactory = sessionFactory,
                hendelsekonsumenterRepo = hendelsekonsumenterRepo,
            )
            return UtbetalingskvitteringKomponenter(
                utbetalingKvitteringRepo = utbetalingKvitteringRepo,
                knyttKvitteringTilSakOgUtbetalingService = knyttKvitteringTilSakOgUtbetalingService,
                råKvitteringService = råKvitteringService,
                ferdigstillVedtakEtterMottattKvitteringKonsument = ferdigstillVedtakEtterMottattKvitteringKonsument,
            )
        }
    }
}
