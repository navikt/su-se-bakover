package no.nav.su.se.bakover.database.grunnlag

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.database.withTransaction
import no.nav.su.se.bakover.test.beregnetRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertInnvilget
import org.junit.jupiter.api.Test

internal class GrunnlagsdataOgVilkårsvurderingerPostgresRepoTest {

    @Test
    fun `lagre og hent GrunnlagOgVilkårsvurderinger for Søknadsbehandling`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val grunnlagRepo = testDataHelper.grunnlagsdataOgVilkårsvurderingerPostgresRepo
            val søknadsbehandling = søknadsbehandlingVilkårsvurdertInnvilget().second
            dataSource.withTransaction { tx ->
                grunnlagRepo.lagre(
                    behandlingId = søknadsbehandling.id,
                    grunnlagsdataOgVilkårsvurderinger = søknadsbehandling.grunnlagsdataOgVilkårsvurderinger,
                    tx = tx,
                )

                val behandlingEtterHent = grunnlagRepo.hentForSøknadsbehandling(søknadsbehandling.id, tx)

                behandlingEtterHent.grunnlagsdata shouldBe søknadsbehandling.grunnlagsdata
                behandlingEtterHent.vilkårsvurderinger.utenlandsopphold shouldBe søknadsbehandling.vilkårsvurderinger.utenlandsopphold
                behandlingEtterHent.vilkårsvurderinger.uføre shouldBe søknadsbehandling.vilkårsvurderinger.uføre
                behandlingEtterHent.vilkårsvurderinger.formue shouldBe søknadsbehandling.vilkårsvurderinger.formue
            }
        }
    }

    @Test
    fun `lagre og hent GrunnlagOgVilkårsvurderinger for Revurdering`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val grunnlagRepo = testDataHelper.grunnlagsdataOgVilkårsvurderingerPostgresRepo
            val revurdering = beregnetRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak().second
            dataSource.withTransaction { tx ->
                grunnlagRepo.lagre(
                    behandlingId = revurdering.id,
                    grunnlagsdataOgVilkårsvurderinger = revurdering.grunnlagsdataOgVilkårsvurderinger,
                    tx = tx,
                )

                val revurderingEtterHent = grunnlagRepo.hentForRevurdering(revurdering.id, tx)

                revurderingEtterHent.grunnlagsdata.fradragsgrunnlag shouldBe revurdering.grunnlagsdata.fradragsgrunnlag
                revurderingEtterHent.grunnlagsdata.bosituasjon shouldBe revurdering.grunnlagsdata.bosituasjon
                revurderingEtterHent.vilkårsvurderinger.utenlandsopphold shouldBe revurdering.vilkårsvurderinger.utenlandsopphold
                revurderingEtterHent.vilkårsvurderinger.uføre shouldBe revurdering.vilkårsvurderinger.uføre
                revurderingEtterHent.vilkårsvurderinger.formue shouldBe revurdering.vilkårsvurderinger.formue
            }
        }
    }
}
