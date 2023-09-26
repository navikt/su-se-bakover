package infrastructure

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.hendelse.domain.HendelseskonsumentId
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.HendelsePostgresRepo
import no.nav.su.se.bakover.test.kravgrunnlag.råttKravgrunnlagHendelse
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import org.junit.jupiter.api.Test
import tilbakekreving.infrastructure.KravgrunnlagPostgresRepo

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
                sessionFactory = testDataHelper.sessionFactory,
                hendelseRepo = hendelseRepo,
                hendelsekonsumenterRepo = testDataHelper.hendelsekonsumenterRepo,
                mapper = { _ -> TODO("Ikke i bruk her.") },
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
}
