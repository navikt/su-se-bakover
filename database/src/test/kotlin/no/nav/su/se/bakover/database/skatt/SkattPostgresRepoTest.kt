package no.nav.su.se.bakover.database.skatt

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.grunnlag.EksterneGrunnlagSkatt
import no.nav.su.se.bakover.domain.grunnlag.SkattegrunnlagMedId
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import no.nav.su.se.bakover.test.skatt.nySkattegrunnlag
import org.junit.jupiter.api.Test
import java.util.UUID

internal class SkattPostgresRepoTest {

    @Test
    fun `lagrer for søker - så lagrer for søker & eps`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            testDataHelper.sessionFactory.withSession { session ->
                val repo = SkattPostgresRepo

                val (sak, _) = testDataHelper.persisterSøknadsbehandlingVilkårsvurdertUavklart()

                val skatt =
                    EksterneGrunnlagSkatt.Hentet(SkattegrunnlagMedId(UUID.randomUUID(), nySkattegrunnlag()), null)
                repo.lagre(sak.id, skatt, session)
                repo.hent(skatt.søkers.id, session) shouldBe nySkattegrunnlag()

                Skattereferanser(skatt.søkers.id, null)
                val skattMedEps = skatt.copy(
                    eps = SkattegrunnlagMedId(id = UUID.randomUUID(), skattegrunnlag = nySkattegrunnlag()),
                )
                repo.lagre(sak.id, skattMedEps, session)
                repo.hent(skattMedEps.søkers.id, session) shouldBe nySkattegrunnlag()
                repo.hent(skattMedEps.eps!!.id, session) shouldBe nySkattegrunnlag()
            }
        }
    }
}
