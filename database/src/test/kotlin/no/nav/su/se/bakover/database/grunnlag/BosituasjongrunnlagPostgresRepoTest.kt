package no.nav.su.se.bakover.database.grunnlag

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.test.generer
import org.junit.jupiter.api.Test
import java.util.UUID

internal class BosituasjongrunnlagPostgresRepoTest {

    @Test
    fun `lagrer og henter bor alene`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val grunnlagRepo = testDataHelper.grunnlagRepo
            val id = UUID.randomUUID()
            val periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021))
            val bosituasjon = Grunnlag.Bosituasjon.Fullstendig.Enslig(
                id = id,
                opprettet = Tidspunkt.EPOCH,
                periode = periode,
                begrunnelse = null,
            )
            grunnlagRepo.lagreBosituasjongrunnlag(behandlingId = id, grunnlag = listOf(bosituasjon))
            grunnlagRepo.hentBosituasjongrunnlag(id).shouldBe(listOf(bosituasjon))
        }
    }

    @Test
    fun `lagrer og henter bor med voksne`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val grunnlagRepo = testDataHelper.grunnlagRepo
            val id = UUID.randomUUID()
            val periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021))
            val bosituasjon = Grunnlag.Bosituasjon.Fullstendig.DelerBoligMedVoksneBarnEllerAnnenVoksen(
                id = id,
                opprettet = Tidspunkt.EPOCH,
                periode = periode,
                begrunnelse = null,
            )
            grunnlagRepo.lagreBosituasjongrunnlag(behandlingId = id, grunnlag = listOf(bosituasjon))
            grunnlagRepo.hentBosituasjongrunnlag(id).shouldBe(listOf(bosituasjon))
        }
    }

    @Test
    fun `lagrer og henter har ikke eps`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val grunnlagRepo = testDataHelper.grunnlagRepo
            val id = UUID.randomUUID()
            val periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021))
            val bosituasjon = Grunnlag.Bosituasjon.Ufullstendig.HarIkkeEps(
                id = id,
                opprettet = Tidspunkt.EPOCH,
                periode = periode,
            )
            grunnlagRepo.lagreBosituasjongrunnlag(behandlingId = id, grunnlag = listOf(bosituasjon))
            grunnlagRepo.hentBosituasjongrunnlag(id).shouldBe(listOf(bosituasjon))
        }
    }

    @Test
    fun `lagrer og henter har eps ikke valgt uføre flykting`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val grunnlagRepo = testDataHelper.grunnlagRepo
            val id = UUID.randomUUID()
            val periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021))
            val bosituasjon = Grunnlag.Bosituasjon.Ufullstendig.HarEps(
                id = id,
                opprettet = Tidspunkt.EPOCH,
                periode = periode,
                fnr = Fnr.generer(),
            )
            grunnlagRepo.lagreBosituasjongrunnlag(behandlingId = id, grunnlag = listOf(bosituasjon))
            grunnlagRepo.hentBosituasjongrunnlag(id).shouldBe(listOf(bosituasjon))
        }
    }

    @Test
    fun `lagrer og henter eps 67+`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val grunnlagRepo = testDataHelper.grunnlagRepo
            val id = UUID.randomUUID()
            val periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021))
            val bosituasjon = Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.SektiSyvEllerEldre(
                id = id,
                opprettet = Tidspunkt.EPOCH,
                periode = periode,
                fnr = Fnr.generer(),
                begrunnelse = null,
            )
            grunnlagRepo.lagreBosituasjongrunnlag(behandlingId = id, grunnlag = listOf(bosituasjon))
            grunnlagRepo.hentBosituasjongrunnlag(id).shouldBe(listOf(bosituasjon))
        }
    }

    @Test
    fun `lagrer og henter eps under 67 ufør flyktning`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val grunnlagRepo = testDataHelper.grunnlagRepo
            val id = UUID.randomUUID()
            val periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021))
            val bosituasjon = Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning(
                id = id,
                opprettet = Tidspunkt.EPOCH,
                periode = periode,
                fnr = Fnr.generer(),
                begrunnelse = null,
            )
            grunnlagRepo.lagreBosituasjongrunnlag(behandlingId = id, grunnlag = listOf(bosituasjon))
            grunnlagRepo.hentBosituasjongrunnlag(id).shouldBe(listOf(bosituasjon))
        }
    }

    @Test
    fun `lagrer og henter eps under 67 ikke ufør flyktning`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val grunnlagRepo = testDataHelper.grunnlagRepo
            val id = UUID.randomUUID()
            val periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021))
            val bosituasjon = Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.IkkeUførFlyktning(
                id = id,
                opprettet = Tidspunkt.EPOCH,
                periode = periode,
                fnr = Fnr.generer(),
                begrunnelse = null,
            )
            grunnlagRepo.lagreBosituasjongrunnlag(behandlingId = id, grunnlag = listOf(bosituasjon))
            grunnlagRepo.hentBosituasjongrunnlag(id).shouldBe(listOf(bosituasjon))
        }
    }
}
