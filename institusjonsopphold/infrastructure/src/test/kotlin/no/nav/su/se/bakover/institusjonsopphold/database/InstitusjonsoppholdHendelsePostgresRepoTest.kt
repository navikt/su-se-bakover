package no.nav.su.se.bakover.institusjonsopphold.database

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.test.nyInstitusjonsoppholdHendelse
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import org.junit.jupiter.api.Test

class InstitusjonsoppholdHendelsePostgresRepoTest {

    @Test
    fun `kan lagre hendelse uten oppgave id`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val (sak, _) = testDataHelper.persisterSøknadsbehandlingIverksattInnvilget()
            val expected = nyInstitusjonsoppholdHendelse(sakId = sak.id)
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

            val expected = nyInstitusjonsoppholdHendelse(sakId = sak.id)
            testDataHelper.institusjonsoppholdHendelseRepo.lagre(expected)
            testDataHelper.institusjonsoppholdHendelseRepo.hentForSak(sak.id).let {
                it shouldNotBe null
                it!!.size shouldBe 1
                it.last() shouldBe expected
            }
        }
    }
}