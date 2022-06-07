package no.nav.su.se.bakover.database.grunnlag

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.database.withTransaction
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.test.beregnetRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.shouldBeType
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertInnvilget
import org.junit.jupiter.api.Test

internal class GrunnlagsdataOgVilkårsvurderingerPostgresRepoTest {

    @Test
    fun `lagre og hent GrunnlagOgVilkårsvurderinger for Søknadsbehandling`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val grunnlagRepo = testDataHelper.grunnlagsdataOgVilkårsvurderingerPostgresRepo
            val (sak, søknadsbehandling) = søknadsbehandlingVilkårsvurdertInnvilget()
            dataSource.withTransaction { tx ->
                grunnlagRepo.lagre(
                    behandlingId = søknadsbehandling.id,
                    grunnlagsdataOgVilkårsvurderinger = søknadsbehandling.grunnlagsdataOgVilkårsvurderinger,
                    tx = tx,
                )

                val behandlingEtterHent = grunnlagRepo.hentForSøknadsbehandling(søknadsbehandling.id, tx, sak.type)

                behandlingEtterHent.grunnlagsdata shouldBe søknadsbehandling.grunnlagsdata
                behandlingEtterHent.vilkårsvurderinger.shouldBeType<Vilkårsvurderinger.Søknadsbehandling.Uføre>().also {
                    it.utenlandsopphold shouldBe søknadsbehandling.vilkårsvurderinger.utenlandsopphold
                    it.uføre shouldBe søknadsbehandling.vilkårsvurderinger.uføreVilkår().getOrFail()
                    it.formue shouldBe søknadsbehandling.vilkårsvurderinger.formue
                }
            }
        }
    }

    @Test
    fun `lagre og hent GrunnlagOgVilkårsvurderinger for Revurdering`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val grunnlagRepo = testDataHelper.grunnlagsdataOgVilkårsvurderingerPostgresRepo
            val (sak, revurdering) = beregnetRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak()
            dataSource.withTransaction { tx ->
                grunnlagRepo.lagre(
                    behandlingId = revurdering.id,
                    grunnlagsdataOgVilkårsvurderinger = revurdering.grunnlagsdataOgVilkårsvurderinger,
                    tx = tx,
                )

                val revurderingEtterHent = grunnlagRepo.hentForRevurdering(revurdering.id, tx, sak.type)

                revurderingEtterHent.grunnlagsdata.fradragsgrunnlag shouldBe revurdering.grunnlagsdata.fradragsgrunnlag
                revurderingEtterHent.grunnlagsdata.bosituasjon shouldBe revurdering.grunnlagsdata.bosituasjon

                revurderingEtterHent.vilkårsvurderinger.shouldBeType<Vilkårsvurderinger.Revurdering.Uføre>().also {
                    it.utenlandsopphold shouldBe revurdering.vilkårsvurderinger.utenlandsopphold
                    it.uføre shouldBe revurdering.vilkårsvurderinger.uføreVilkår().getOrFail()
                    it.formue shouldBe revurdering.vilkårsvurderinger.formue
                }
            }
        }
    }
}
