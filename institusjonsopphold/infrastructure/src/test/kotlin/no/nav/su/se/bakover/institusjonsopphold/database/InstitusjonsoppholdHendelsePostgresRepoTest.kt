package no.nav.su.se.bakover.institusjonsopphold.database

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.test.nyInstitusjonsoppholdHendelseMedOppgaveId
import no.nav.su.se.bakover.test.nyInstitusjonsoppholdHendelseUtenOppgaveId
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import org.junit.jupiter.api.Test

class InstitusjonsoppholdHendelsePostgresRepoTest {

    @Test
    fun `kan lagre hendelse uten oppgave id`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val (sak, _) = testDataHelper.persisterSøknadsbehandlingIverksattInnvilget()
            val expected = nyInstitusjonsoppholdHendelseUtenOppgaveId(hendelseSakId = sak.id)
            testDataHelper.institusjonsoppholdHendelseRepo.lagre(expected)
            testDataHelper.institusjonsoppholdHendelseRepo.hentForSak(sak.id).let {
                it shouldNotBe null
                it!!.size shouldBe 1
                it.first() shouldBe expected
            }
        }
    }

    @Test
    fun `kan lagre hendelse med oppgave id`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val (sak, _) = testDataHelper.persisterSøknadsbehandlingIverksattInnvilget()

            val expected = nyInstitusjonsoppholdHendelseMedOppgaveId(sakId = sak.id)
            testDataHelper.institusjonsoppholdHendelseRepo.lagre(expected)
            testDataHelper.institusjonsoppholdHendelseRepo.hentForSak(sak.id).let {
                it shouldNotBe null
                it!!.size shouldBe 1
                it.last() shouldBe expected
            }
        }
    }

    @Test
    fun `henter alle hendelser uten oppgave id`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            testDataHelper.persisterInstitusjonsoppholdHendelseUtenOppgaveId()
            testDataHelper.persisterInstitusjonsoppholdHendelseUtenOppgaveId()
            testDataHelper.persisterInstitusjonsoppholdHendelseMedOppgaveId()

            testDataHelper.institusjonsoppholdHendelseRepo.hentHendelserUtenOppgaveId().let {
                it.size shouldBe 2
            }
        }
    }
}
