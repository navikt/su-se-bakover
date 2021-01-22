package no.nav.su.se.bakover.database.hendelseslogg

import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.domain.hendelseslogg.Hendelseslogg
import no.nav.su.se.bakover.domain.hendelseslogg.hendelse.behandling.UnderkjentAttestering
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.ZoneOffset

internal class HendelsesloggPostgresRepoTest {

    private val fixedClock: Clock = Clock.fixed(1.januar(2021).startOfDay().instant, ZoneOffset.UTC)

    private val testDataHelper = TestDataHelper(EmbeddedDatabase.instance(), fixedClock)
    private val repo = HendelsesloggPostgresRepo(EmbeddedDatabase.instance())

    @Test
    fun `hent hendelseslogg`() {
        withMigratedDb {
            val tidspunkt = Tidspunkt.now(fixedClock)
            val underkjentAttestering = UnderkjentAttestering(
                attestant = "attestant",
                begrunnelse = "Dette er feil begrunnelse",
                tidspunkt = tidspunkt
            )

            val opprettet = Hendelseslogg("id").also { testDataHelper.oppdaterHendelseslogg(it) }
            val hentet = repo.hentHendelseslogg("id")!!
            hentet shouldBe opprettet

            testDataHelper.oppdaterHendelseslogg(Hendelseslogg("id", mutableListOf(underkjentAttestering)))
            testDataHelper.oppdaterHendelseslogg(Hendelseslogg("id", mutableListOf(underkjentAttestering)))

            val medHendelse = repo.hentHendelseslogg("id")!!
            medHendelse.id shouldBe "id"
            medHendelse.hendelser() shouldContainAll listOf(underkjentAttestering)
        }
    }
}
