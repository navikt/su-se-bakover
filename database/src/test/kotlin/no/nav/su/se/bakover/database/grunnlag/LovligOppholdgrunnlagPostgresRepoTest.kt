package no.nav.su.se.bakover.database.grunnlag

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.grunnlag.LovligOppholdGrunnlag
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.dbMetricsStub
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import no.nav.su.se.bakover.test.persistence.withTransaction
import org.junit.jupiter.api.Test

internal class LovligOppholdgrunnlagPostgresRepoTest {

    @Test
    fun `lagrer og henter lovlig opphold grunnlag`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val lovligOppholdGrunnlagPostgresRepo = LovligOppholdgrunnlagPostgresRepo(dbMetricsStub)
            val søknadsbehandling = testDataHelper.persisterSøknadsbehandlingVilkårsvurdertUavklart().second
            val grunnlag = LovligOppholdGrunnlag(opprettet = fixedTidspunkt, periode = år(2021))

            dataSource.withTransaction { session ->
                lovligOppholdGrunnlagPostgresRepo.lagre(søknadsbehandling.id, listOf(grunnlag), session)
                lovligOppholdGrunnlagPostgresRepo.hent(grunnlag.id, session) shouldBe grunnlag
            }
        }
    }

    @Test
    fun `erstatter gammel grunnlag med ny`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val lovligOppholdGrunnlagPostgresRepo = LovligOppholdgrunnlagPostgresRepo(dbMetricsStub)
            val søknadsbehandling = testDataHelper.persisterSøknadsbehandlingVilkårsvurdertUavklart().second
            val grunnlag = LovligOppholdGrunnlag(opprettet = fixedTidspunkt, periode = år(2021))

            dataSource.withTransaction { session ->
                lovligOppholdGrunnlagPostgresRepo.lagre(søknadsbehandling.id, listOf(grunnlag), session)
                lovligOppholdGrunnlagPostgresRepo.hent(grunnlag.id, session) shouldBe grunnlag

                lovligOppholdGrunnlagPostgresRepo.lagre(søknadsbehandling.id, listOf(grunnlag), session)
                lovligOppholdGrunnlagPostgresRepo.hent(grunnlag.id, session) shouldBe grunnlag
            }
        }
    }
}
