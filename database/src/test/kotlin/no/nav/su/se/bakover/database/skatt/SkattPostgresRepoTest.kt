package no.nav.su.se.bakover.database.skatt

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.test.persistence.DbExtension
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.skatt.nySkattegrunnlag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import vilkår.vurderinger.domain.EksterneGrunnlagSkatt
import java.util.UUID
import javax.sql.DataSource

@ExtendWith(DbExtension::class)
internal class SkattPostgresRepoTest(private val dataSource: DataSource) {

    @Test
    fun `lagrer for søker - så lagrer for søker & eps`() {
        val testDataHelper = TestDataHelper(dataSource)
        testDataHelper.sessionFactory.withSession { session ->
            val repo = SkattPostgresRepo
            val (sak, _) = testDataHelper.persisternySøknadsbehandlingMedStønadsperiode()

            val søkersId = UUID.randomUUID()

            val skatt = EksterneGrunnlagSkatt.Hentet(nySkattegrunnlag(søkersId), null)
            repo.lagre(sak.id, skatt, session)
            repo.hent(skatt.søkers.id, session) shouldBe nySkattegrunnlag(søkersId)

            Skattereferanser(skatt.søkers.id, null)
            val epsId = UUID.randomUUID()
            val skattMedEps = skatt.copy(eps = nySkattegrunnlag(epsId))
            repo.lagre(sak.id, skattMedEps, session)
            repo.hent(skattMedEps.søkers.id, session) shouldBe nySkattegrunnlag(søkersId)
            repo.hent(skattMedEps.eps!!.id, session) shouldBe nySkattegrunnlag(epsId)
        }
    }
}
