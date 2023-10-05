package infrastructure

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.hendelse.domain.HendelseskonsumentId
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.HendelsePostgresRepo
import no.nav.su.se.bakover.test.kravgrunnlag.kravgrunnlagPåSakHendelse
import no.nav.su.se.bakover.test.kravgrunnlag.råttKravgrunnlagHendelse
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import org.junit.jupiter.api.Test
import tilbakekreving.infrastructure.KravgrunnlagPostgresRepo
import java.util.UUID

class KravgrunnlagPostgresRepoTest {

    @Test
    fun `kan lagre og hente rått kravgrunnlag`() {
        val hendelse1 = råttKravgrunnlagHendelse()
        val hendelse2 = råttKravgrunnlagHendelse()
        withMigratedDb { dataSource ->

            val testDataHelper = TestDataHelper(dataSource)
            val hendelseRepo = HendelsePostgresRepo(
                sessionFactory = testDataHelper.sessionFactory,
                dbMetrics = testDataHelper.dbMetrics,
            )
            val kravgrunnlagRepo = KravgrunnlagPostgresRepo(
                hendelseRepo = hendelseRepo,
                hendelsekonsumenterRepo = testDataHelper.hendelsekonsumenterRepo,
            )
            kravgrunnlagRepo.lagreRåttKravgrunnlagHendelse(hendelse1)
            val konsumentId = HendelseskonsumentId("konsumentId")
            kravgrunnlagRepo.hentUprosesserteRåttKravgrunnlagHendelser(
                konsumentId = konsumentId,
            ) shouldBe listOf(hendelse1.hendelseId)
            testDataHelper.hendelsekonsumenterRepo.lagre(hendelseId = hendelse1.hendelseId, konsumentId = konsumentId)
            kravgrunnlagRepo.hentUprosesserteRåttKravgrunnlagHendelser(
                konsumentId = konsumentId,
            ) shouldBe emptyList()
            kravgrunnlagRepo.lagreRåttKravgrunnlagHendelse(hendelse2)
            kravgrunnlagRepo.hentUprosesserteRåttKravgrunnlagHendelser(
                konsumentId = konsumentId,
            ) shouldBe listOf(hendelse2.hendelseId)
        }
    }

    @Test
    fun `kan lagre og hente kravgrunnlag knyttet til sak`() {
        val sakId = UUID.randomUUID()
        val hendelse1 = kravgrunnlagPåSakHendelse(sakId = sakId)
        withMigratedDb { dataSource ->

            val testDataHelper = TestDataHelper(dataSource)
            val hendelseRepo = HendelsePostgresRepo(
                sessionFactory = testDataHelper.sessionFactory,
                dbMetrics = testDataHelper.dbMetrics,
            )
            val kravgrunnlagRepo = KravgrunnlagPostgresRepo(
                hendelseRepo = hendelseRepo,
                hendelsekonsumenterRepo = testDataHelper.hendelsekonsumenterRepo,
            )
            val konsumentId = HendelseskonsumentId("konsumentId")
            testDataHelper.persisterSakMedSøknadUtenJournalføringOgOppgave(
                sakId = sakId,
            )
            kravgrunnlagRepo.hentKravgrunnlagPåSakHendelser(
                sakId = sakId,
            ) shouldBe emptyList()
            kravgrunnlagRepo.hentUprosesserteKravgrunnlagKnyttetTilSakHendelser(
                konsumentId = konsumentId,
            ) shouldBe emptyList()

            kravgrunnlagRepo.lagreKravgrunnlagPåSakHendelse(hendelse1)

            kravgrunnlagRepo.hentKravgrunnlagPåSakHendelser(
                sakId = sakId,
            ) shouldBe listOf(hendelse1)

            kravgrunnlagRepo.hentUprosesserteKravgrunnlagKnyttetTilSakHendelser(
                konsumentId = konsumentId,
            ) shouldBe listOf(hendelse1.hendelseId)

            testDataHelper.hendelsekonsumenterRepo.lagre(hendelseId = hendelse1.hendelseId, konsumentId = konsumentId)

            kravgrunnlagRepo.hentUprosesserteKravgrunnlagKnyttetTilSakHendelser(
                konsumentId = konsumentId,
            ) shouldBe emptyList()
            kravgrunnlagRepo.hentKravgrunnlagPåSakHendelser(
                sakId = sakId,
            ) shouldBe listOf(hendelse1)
        }
    }
}
