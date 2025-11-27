package no.nav.su.se.bakover.database.grunnlag

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.domain.revurdering.RevurderingId
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.persistence.DbExtension
import no.nav.su.se.bakover.test.persistence.dbMetricsStub
import no.nav.su.se.bakover.test.persistence.withTransaction
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import vilkår.bosituasjon.domain.grunnlag.Bosituasjon
import java.util.UUID
import javax.sql.DataSource

@ExtendWith(DbExtension::class)
internal class BosituasjongrunnlagPostgresRepoTest(private val dataSource: DataSource) {

    @Test
    fun `lagrer og henter bor alene`() {
        val grunnlagRepo = BosituasjongrunnlagPostgresRepo(dbMetrics = dbMetricsStub)
        val id = RevurderingId.generer()
        val grunnlagsId = UUID.randomUUID()
        val periode = januar(2021)
        val bosituasjon = Bosituasjon.Fullstendig.Enslig(
            id = grunnlagsId,
            opprettet = Tidspunkt.EPOCH,
            periode = periode,
        )
        dataSource.withTransaction {
            grunnlagRepo.lagreBosituasjongrunnlag(behandlingId = id, grunnlag = listOf(bosituasjon), it)
            grunnlagRepo.hentBosituasjongrunnlag(id, it).shouldBe(listOf(bosituasjon))
        }
    }

    @Test
    fun `lagrer og henter bor med voksne`() {
        val grunnlagRepo = BosituasjongrunnlagPostgresRepo(dbMetrics = dbMetricsStub)
        val id = RevurderingId.generer()
        val grunnlagsId = UUID.randomUUID()
        val periode = januar(2021)
        val bosituasjon = Bosituasjon.Fullstendig.DelerBoligMedVoksneBarnEllerAnnenVoksen(
            id = grunnlagsId,
            opprettet = Tidspunkt.EPOCH,
            periode = periode,
        )
        dataSource.withTransaction {
            grunnlagRepo.lagreBosituasjongrunnlag(behandlingId = id, grunnlag = listOf(bosituasjon), it)
            grunnlagRepo.hentBosituasjongrunnlag(id, it).shouldBe(listOf(bosituasjon))
        }
    }

    @Test
    fun `lagrer og henter har ikke eps`() {
        val grunnlagRepo = BosituasjongrunnlagPostgresRepo(dbMetrics = dbMetricsStub)
        val id = RevurderingId.generer()
        val grunnlagsId = UUID.randomUUID()
        val periode = januar(2021)
        val bosituasjon = Bosituasjon.Ufullstendig.HarIkkeEps(
            id = grunnlagsId,
            opprettet = Tidspunkt.EPOCH,
            periode = periode,
        )
        dataSource.withTransaction {
            grunnlagRepo.lagreBosituasjongrunnlag(behandlingId = id, grunnlag = listOf(bosituasjon), it)
            grunnlagRepo.hentBosituasjongrunnlag(id, it).shouldBe(listOf(bosituasjon))
        }
    }

    @Test
    fun `lagrer og henter har eps ikke valgt uføre flykting`() {
        val grunnlagRepo = BosituasjongrunnlagPostgresRepo(dbMetrics = dbMetricsStub)
        val id = RevurderingId.generer()
        val grunnlagsId = UUID.randomUUID()
        val periode = januar(2021)
        val bosituasjon = Bosituasjon.Ufullstendig.HarEps(
            id = grunnlagsId,
            opprettet = Tidspunkt.EPOCH,
            periode = periode,
            fnr = Fnr.generer(),
        )
        dataSource.withTransaction {
            grunnlagRepo.lagreBosituasjongrunnlag(behandlingId = id, grunnlag = listOf(bosituasjon), it)
            grunnlagRepo.hentBosituasjongrunnlag(id, it).shouldBe(listOf(bosituasjon))
        }
    }

    @Test
    fun `lagrer og henter eps 67+`() {
        val grunnlagRepo = BosituasjongrunnlagPostgresRepo(dbMetrics = dbMetricsStub)
        val id = RevurderingId.generer()
        val grunnlagsId = UUID.randomUUID()
        val periode = januar(2021)
        val bosituasjon = Bosituasjon.Fullstendig.EktefellePartnerSamboer.SektiSyvEllerEldre(
            id = grunnlagsId,
            opprettet = Tidspunkt.EPOCH,
            periode = periode,
            fnr = Fnr.generer(),
        )
        dataSource.withTransaction {
            grunnlagRepo.lagreBosituasjongrunnlag(behandlingId = id, grunnlag = listOf(bosituasjon), it)
            grunnlagRepo.hentBosituasjongrunnlag(id, it).shouldBe(listOf(bosituasjon))
        }
    }

    @Test
    fun `lagrer og henter eps under 67 ufør flyktning`() {
        val grunnlagRepo = BosituasjongrunnlagPostgresRepo(dbMetrics = dbMetricsStub)
        val id = RevurderingId.generer()
        val grunnlagsId = UUID.randomUUID()
        val periode = januar(2021)
        val bosituasjon = Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning(
            id = grunnlagsId,
            opprettet = Tidspunkt.EPOCH,
            periode = periode,
            fnr = Fnr.generer(),
        )
        dataSource.withTransaction {
            grunnlagRepo.lagreBosituasjongrunnlag(behandlingId = id, grunnlag = listOf(bosituasjon), it)
            grunnlagRepo.hentBosituasjongrunnlag(id, it).shouldBe(listOf(bosituasjon))
        }
    }

    @Test
    fun `lagrer og henter eps under 67 ikke ufør flyktning`() {
        val grunnlagRepo = BosituasjongrunnlagPostgresRepo(dbMetrics = dbMetricsStub)
        val id = RevurderingId.generer()
        val grunnlagsId = UUID.randomUUID()
        val periode = januar(2021)
        val bosituasjon = Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.IkkeUførFlyktning(
            id = grunnlagsId,
            opprettet = Tidspunkt.EPOCH,
            periode = periode,
            fnr = Fnr.generer(),
        )
        dataSource.withTransaction {
            grunnlagRepo.lagreBosituasjongrunnlag(behandlingId = id, grunnlag = listOf(bosituasjon), it)
            grunnlagRepo.hentBosituasjongrunnlag(id, it).shouldBe(listOf(bosituasjon))
        }
    }
}
