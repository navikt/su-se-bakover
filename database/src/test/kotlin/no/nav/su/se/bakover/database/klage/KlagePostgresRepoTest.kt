package no.nav.su.se.bakover.database.klage

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.SakFactory
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.generer
import org.junit.jupiter.api.Test
import java.util.UUID

internal class KlagePostgresRepoTest {
    val fnr = Fnr.generer()

    @Test
    fun `kan opprette og hente klager`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.klagePostgresRepo
            val nySak = SakFactory(clock = fixedClock).nySakMedNySøknad(fnr, SøknadInnholdTestdataBuilder.build())
            testDataHelper.sakRepo.opprettSak(nySak)

            val klage = Klage(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(),
                sakId = nySak.id,
                journalpostId = JournalpostId(value = "1"),
                saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "2"),
            )

            repo.opprett(klage)
            repo.hentKlager(nySak.id).first() shouldBe klage
        }
    }
}
