package no.nav.su.se.bakover.institusjonsopphold.database

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.domain.OppholdId
import no.nav.su.se.bakover.test.hendelse.defaultHendelseMetadata
import no.nav.su.se.bakover.test.nyEksternInstitusjonsoppholdHendelse
import no.nav.su.se.bakover.test.nyInstitusjonsoppholdHendelse
import no.nav.su.se.bakover.test.persistence.DbExtension
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import javax.sql.DataSource

@ExtendWith(DbExtension::class)
class InstitusjonsoppholdHendelsePostgresRepoTest(private val dataSource: DataSource) {

    @Test
    fun `Kan hente hendelser for sak uten at det finnes en inst2 hendelse fra før av`() {
        val testDataHelper = TestDataHelper(dataSource)
        val (sak, _) = testDataHelper.persisterSøknadsbehandlingIverksattInnvilget()
        testDataHelper.institusjonsoppholdHendelseRepo.hentForSak(sak.id).let {
            it shouldNotBe null // Er bare en wrapperklasse med hendelser inni som er det man skal sjekke på
            it.size shouldBe 0
        }
        val expected = nyInstitusjonsoppholdHendelse(sakId = sak.id)
        testDataHelper.institusjonsoppholdHendelseRepo.lagre(expected, defaultHendelseMetadata())
        testDataHelper.institusjonsoppholdHendelseRepo.hentForSak(sak.id).let {
            it shouldNotBe null
            it.size shouldBe 1
            it.first shouldBe expected
        }
    }

    @Test
    fun `kan lagre hendelse uten oppgave id`() {
        val testDataHelper = TestDataHelper(dataSource)
        val (sak, _) = testDataHelper.persisterSøknadsbehandlingIverksattInnvilget()
        val expected = nyInstitusjonsoppholdHendelse(sakId = sak.id)
        testDataHelper.institusjonsoppholdHendelseRepo.lagre(expected, defaultHendelseMetadata())
        testDataHelper.institusjonsoppholdHendelseRepo.hentForSak(sak.id).let {
            it shouldNotBe null
            it.size shouldBe 1
            it.first shouldBe expected
        }
    }

    @Test
    fun `kan lagre hendelse med oppgave id`() {
        val testDataHelper = TestDataHelper(dataSource)
        val (sak, _) = testDataHelper.persisterSøknadsbehandlingIverksattInnvilget()

        val expected = nyInstitusjonsoppholdHendelse(sakId = sak.id)
        testDataHelper.institusjonsoppholdHendelseRepo.lagre(expected, defaultHendelseMetadata())
        testDataHelper.institusjonsoppholdHendelseRepo.hentForSak(sak.id).let {
            it shouldNotBe null
            it.size shouldBe 1
            it.last shouldBe expected
        }
    }

    @Test
    fun `henter tidligere hendelser basert på opphold id for gitt sak`() {
        val testDataHelper = TestDataHelper(dataSource)
        val (sak, _) = testDataHelper.persisterSøknadsbehandlingIverksattInnvilget()

        val expected = nyInstitusjonsoppholdHendelse(sakId = sak.id)
        val unexpected = nyInstitusjonsoppholdHendelse(
            sakId = sak.id,
            eksternHendelse = nyEksternInstitusjonsoppholdHendelse(oppholdId = OppholdId(3)),
            versjon = expected.versjon.inc(),
        )

        testDataHelper.institusjonsoppholdHendelseRepo.lagre(expected, defaultHendelseMetadata())
        testDataHelper.institusjonsoppholdHendelseRepo.lagre(unexpected, defaultHendelseMetadata())
        testDataHelper.institusjonsoppholdHendelseRepo.hentTidligereInstHendelserForOpphold(
            sak.id,
            OppholdId(2),
        ) shouldBe listOf(expected)
    }
}
