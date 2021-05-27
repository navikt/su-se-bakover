package no.nav.su.se.bakover.database.grunnlag

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.database.FnrGenerator
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import org.junit.jupiter.api.Test
import java.util.UUID

internal class BosituasjongrunnlangPostgresRepoTest {

    private val datasource = EmbeddedDatabase.instance()
    private val grunnlagRepo = BosituasjongrunnlangPostgresRepo(
        datasource,
    )

    @Test
    fun `lagrer og henter bor alene`() {
        withMigratedDb {
            val id = UUID.randomUUID()
            val periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021))
            val bosituasjon = Grunnlag.Bosituasjon.Enslig(
                id = id,
                opprettet = Tidspunkt.EPOCH,
                periode = periode,
            )
            grunnlagRepo.lagreBosituasjongrunnlag(behandlingId = id, grunnlag = listOf(bosituasjon))
            grunnlagRepo.hentBosituasjongrunnlag(id).shouldBe(listOf(bosituasjon))
        }
    }

    @Test
    fun `lagrer og henter bor med voksne`() {
        withMigratedDb {
            val id = UUID.randomUUID()
            val periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021))
            val bosituasjon = Grunnlag.Bosituasjon.DelerBoligMedVoksneBarnEllerAnnenVoksen(
                id = id,
                opprettet = Tidspunkt.EPOCH,
                periode = periode,
            )
            grunnlagRepo.lagreBosituasjongrunnlag(behandlingId = id, grunnlag = listOf(bosituasjon))
            grunnlagRepo.hentBosituasjongrunnlag(id).shouldBe(listOf(bosituasjon))
        }
    }

    @Test
    fun `lagrer og henter har ikke eps`() {
        withMigratedDb {
            val id = UUID.randomUUID()
            val periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021))
            val bosituasjon = Grunnlag.Bosituasjon.HarIkkeEPS(
                id = id,
                opprettet = Tidspunkt.EPOCH,
                periode = periode,
            )
            grunnlagRepo.lagreBosituasjongrunnlag(behandlingId = id, grunnlag = listOf(bosituasjon))
            grunnlagRepo.hentBosituasjongrunnlag(id).shouldBe(listOf(bosituasjon))
        }
    }

    @Test
    fun `lagrer og henter eps 67+`() {
        withMigratedDb {
            val id = UUID.randomUUID()
            val periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021))
            val bosituasjon = Grunnlag.Bosituasjon.EktefellePartnerSamboer.SektiSyvEllerEldre(
                id = id,
                opprettet = Tidspunkt.EPOCH,
                periode = periode,
                fnr = FnrGenerator.random(),
            )
            grunnlagRepo.lagreBosituasjongrunnlag(behandlingId = id, grunnlag = listOf(bosituasjon))
            grunnlagRepo.hentBosituasjongrunnlag(id).shouldBe(listOf(bosituasjon))
        }
    }

    @Test
    fun `lagrer og henter eps under 67 ufør flyktning`() {
        withMigratedDb {
            val id = UUID.randomUUID()
            val periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021))
            val bosituasjon = Grunnlag.Bosituasjon.EktefellePartnerSamboer.Under67.UførFlyktning(
                id = id,
                opprettet = Tidspunkt.EPOCH,
                periode = periode,
                fnr = FnrGenerator.random(),
            )
            grunnlagRepo.lagreBosituasjongrunnlag(behandlingId = id, grunnlag = listOf(bosituasjon))
            grunnlagRepo.hentBosituasjongrunnlag(id).shouldBe(listOf(bosituasjon))
        }
    }

    @Test
    fun `lagrer og henter eps under 67 ikke ufør flyktning`() {
        withMigratedDb {
            val id = UUID.randomUUID()
            val periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021))
            val bosituasjon = Grunnlag.Bosituasjon.EktefellePartnerSamboer.Under67.IkkeUførFlyktning(
                id = id,
                opprettet = Tidspunkt.EPOCH,
                periode = periode,
                fnr = FnrGenerator.random(),
            )
            grunnlagRepo.lagreBosituasjongrunnlag(behandlingId = id, grunnlag = listOf(bosituasjon))
            grunnlagRepo.hentBosituasjongrunnlag(id).shouldBe(listOf(bosituasjon))
        }
    }
}
