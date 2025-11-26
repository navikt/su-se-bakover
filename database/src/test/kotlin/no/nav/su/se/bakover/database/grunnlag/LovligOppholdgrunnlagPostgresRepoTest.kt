package no.nav.su.se.bakover.database.grunnlag

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.persistence.DbExtension
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.dbMetricsStub
import no.nav.su.se.bakover.test.persistence.withTransaction
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import vilkår.lovligopphold.domain.LovligOppholdGrunnlag
import javax.sql.DataSource

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DbExtension::class)
internal class LovligOppholdgrunnlagPostgresRepoTest(private val dataSource: DataSource) {

    @Test
    fun `lagrer og henter lovlig opphold grunnlag`() {
        val testDataHelper = TestDataHelper(dataSource)
        val lovligOppholdGrunnlagPostgresRepo = LovligOppholdgrunnlagPostgresRepo(dbMetricsStub)
        val søknadsbehandling = testDataHelper.persisterSøknadsbehandlingVilkårsvurdert().second
        val grunnlag = LovligOppholdGrunnlag(opprettet = fixedTidspunkt, periode = år(2021))

        dataSource.withTransaction { session ->
            lovligOppholdGrunnlagPostgresRepo.lagre(søknadsbehandling.id, listOf(grunnlag), session)
            lovligOppholdGrunnlagPostgresRepo.hent(grunnlag.id, session) shouldBe grunnlag
        }
    }

    @Test
    fun `erstatter gammel grunnlag med ny`() {
        val testDataHelper = TestDataHelper(dataSource)
        val lovligOppholdGrunnlagPostgresRepo = LovligOppholdgrunnlagPostgresRepo(dbMetricsStub)
        val søknadsbehandling = testDataHelper.persisterSøknadsbehandlingVilkårsvurdert().second
        val grunnlag = LovligOppholdGrunnlag(opprettet = fixedTidspunkt, periode = år(2021))

        dataSource.withTransaction { session ->
            lovligOppholdGrunnlagPostgresRepo.lagre(søknadsbehandling.id, listOf(grunnlag), session)
            lovligOppholdGrunnlagPostgresRepo.hent(grunnlag.id, session) shouldBe grunnlag

            lovligOppholdGrunnlagPostgresRepo.lagre(søknadsbehandling.id, listOf(grunnlag), session)
            lovligOppholdGrunnlagPostgresRepo.hent(grunnlag.id, session) shouldBe grunnlag
        }
    }
}
