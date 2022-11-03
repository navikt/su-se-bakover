package no.nav.su.se.bakover.hendelse.infrastructure.persistence

import org.junit.jupiter.api.Test

internal class HendelsePostgresRepoTest {

    // TODO jah: Flytt inn i generell SakRepo
    @Test
    fun `kan lagre og hente sak opprettet hendelse`() {
        // withMigratedDb { dataSource ->
        //     val testDataHelper = TestDataHelper(dataSource)
        //     val repo = testDataHelper.hendelsePostgresRepo
        //
        //     testDataHelper.sessionFactory.withSessionContext {
        //         val (sak, _) = testDataHelper.persisterSakOgJournalførtSøknadUtenOppgave()
        //         val hendelse = sakOpprettetHendelse(sakId = sak.id, fnr = sak.fnr)
        //         repo.persister(
        //             hendelse=hendelse,
        //             type=TODO(),
        //             data=TODO(),
        //             sessionContext = it,
        //         )
        //         repo.hentSakOpprettetHendelse(sak.id) shouldBe listOf(
        //             hendelse,
        //         )
        //     }
        // }
    }
}
