package no.nav.su.se.bakover.database.skatt

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import no.nav.su.se.bakover.test.skatt.nySkattegrunnlag
import org.junit.jupiter.api.Test
import java.util.UUID

internal class SkattPostgresRepoTest {

    @Test
    fun `lagrer & henter & sletter skattegrunnlag fra basen`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            testDataHelper.sessionFactory.withSession { session ->
                val repo = SkattPostgresRepo

                val (sak, _) = testDataHelper.persisterSøknadsbehandlingVilkårsvurdertUavklart()
                val id = UUID.randomUUID()
                val skattegrunnlag = nySkattegrunnlag()
                repo.lagreSkattegrunnlag(
                    id = id,
                    sakId = sak.id,
                    fnr = skattegrunnlag.fnr,
                    erEps = false,
                    opprettet = fixedTidspunkt,
                    data = skattegrunnlag,
                    saksbehandler = skattegrunnlag.saksbehandler,
                    årSpurtFor = skattegrunnlag.årSpurtFor,
                    session = session,
                )
                repo.hent(id, session) shouldBe skattegrunnlag
                repo.slettSkattegrunnlag(id, session)
                repo.hent(id, session) shouldBe null
            }
        }
    }
}
