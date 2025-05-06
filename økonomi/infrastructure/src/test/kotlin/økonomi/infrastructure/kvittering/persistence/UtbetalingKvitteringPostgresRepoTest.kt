package økonomi.infrastructure.kvittering.persistence

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.hendelse.domain.HendelseskonsumentId
import no.nav.su.se.bakover.test.hendelse.defaultHendelseMetadata
import no.nav.su.se.bakover.test.hendelse.jmsHendelseMetadata
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import no.nav.su.se.bakover.test.utbetaling.kvittering.råUtbetalingskvitteringhendelse
import no.nav.su.se.bakover.test.utbetaling.kvittering.utbetalingskvitteringPåSakHendelse
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import økonomi.domain.utbetaling.Utbetaling

class UtbetalingKvitteringPostgresRepoTest {

    @Test
    fun `lagrer og henter RåKvitteringHendelse`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val (_, _, _, hendelse) = testDataHelper.persisterRåUtbetalingskvittering()
            testDataHelper.utbetalingskvitteringrepo.hentRåUtbetalingskvitteringhendelse(hendelse.hendelseId)!! shouldBe hendelse
        }
    }

    @Test
    fun `lagrer og henter kvittering knyttet til sak`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val (_, _, _, _, hendelse) = testDataHelper.persisterUtbetalingskvitteringKnyttetTilSak()
            testDataHelper.utbetalingskvitteringrepo.hentKnyttetUtbetalingskvitteringTilSakHendelse(hendelse.hendelseId)!! shouldBe hendelse
        }
    }

    @Test
    fun `ubehandlet rå`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val hendelse = råUtbetalingskvitteringhendelse()
            testDataHelper.utbetalingskvitteringrepo.lagreRåKvitteringHendelse(hendelse, jmsHendelseMetadata())
            testDataHelper.utbetalingskvitteringrepo.hentUprosesserteMottattUtbetalingskvittering(HendelseskonsumentId("KnyttKvitteringTilSakOgUtbetaling")) shouldBe setOf(hendelse.hendelseId)
        }
    }

    // TODO: for testing enablet
    @Disabled
    @Test
    fun `ubehandlet knyttet til sak`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val (sak: Sak, _, _, utbetaling: Utbetaling.OversendtUtbetaling) = testDataHelper.persisterSøknadsbehandlingIverksattInnvilget()
            val råHendelse = råUtbetalingskvitteringhendelse(
                saksnummer = sak.saksnummer,
                fnr = sak.fnr,
                avstemmingsnøkkel = utbetaling.avstemmingsnøkkel,
                utbetalingId = utbetaling.id,
            )
            testDataHelper.utbetalingskvitteringrepo.lagreRåKvitteringHendelse(råHendelse, jmsHendelseMetadata())
            val hendelse = utbetalingskvitteringPåSakHendelse(
                sakId = sak.id,
                utbetalingId = utbetaling.id,
                saksnummer = sak.saksnummer,
                fnr = sak.fnr,
                avstemmingsnøkkel = utbetaling.avstemmingsnøkkel,
                tidligereHendelseId = råHendelse.hendelseId,
            )
            testDataHelper.utbetalingskvitteringrepo.lagreUtbetalingskvitteringPåSakHendelse(hendelse, defaultHendelseMetadata())
            testDataHelper.utbetalingskvitteringrepo.hentUprosesserteKnyttetUtbetalingskvitteringTilSak(HendelseskonsumentId("FerdigstillVedtakEtterMottattKvittering")) shouldBe setOf(hendelse.hendelseId)
        }
    }
}
