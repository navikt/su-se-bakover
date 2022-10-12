package no.nav.su.se.bakover.database.grunnlag

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.common.toNonEmptyList
import no.nav.su.se.bakover.domain.grunnlag.OpplysningspliktBeskrivelse
import no.nav.su.se.bakover.domain.vilkår.OpplysningspliktVilkår
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.dbMetricsStub
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import no.nav.su.se.bakover.test.persistence.withSession
import no.nav.su.se.bakover.test.vilkår.tilstrekkeligDokumentert
import no.nav.su.se.bakover.test.vilkår.utilstrekkeligDokumentert
import org.junit.jupiter.api.Test

internal class OpplysningspliktVilkårsvurderingPostgresRepoTest {

    @Test
    fun `lagrer og henter vilkårsvurdering`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val opplysningspliktGrunnlagRepo = OpplysningspliktGrunnlagPostgresRepo(dbMetricsStub)
            val opplysningspliktVilkårsvurderingPostgresRepo = OpplysningspliktVilkårsvurderingPostgresRepo(
                opplysningspliktGrunnlagRepo,
                dbMetricsStub,
            )
            val søknadsbehandling = testDataHelper.persisterSøknadsbehandlingVilkårsvurdert().second
            val vilkår = tilstrekkeligDokumentert()

            testDataHelper.sessionFactory.withTransaction { tx ->
                opplysningspliktVilkårsvurderingPostgresRepo.lagre(søknadsbehandling.id, vilkår, tx)
            }

            testDataHelper.sessionFactory.withSession { session ->
                opplysningspliktVilkårsvurderingPostgresRepo.hent(
                    søknadsbehandling.id,
                    session,
                ) shouldBe vilkår
            }
        }
    }

    @Test
    fun `sletting av eksisterende og lagrer nytt med flere vurderingsperioder og grunnlag`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val opplysningspliktGrunnlagRepo = OpplysningspliktGrunnlagPostgresRepo(dbMetricsStub)
            val opplysningspliktVilkårsvurderingPostgresRepo = OpplysningspliktVilkårsvurderingPostgresRepo(
                opplysningspliktGrunnlagRepo,
                dbMetricsStub,
            )
            val søknadsbehandling = testDataHelper.persisterSøknadsbehandlingVilkårsvurdert().second
            val vilkår = tilstrekkeligDokumentert(periode = år(2021))

            testDataHelper.sessionFactory.withTransaction { tx ->
                opplysningspliktVilkårsvurderingPostgresRepo.lagre(søknadsbehandling.id, vilkår, tx)
            }

            testDataHelper.sessionFactory.withSession { session ->
                opplysningspliktVilkårsvurderingPostgresRepo.hent(
                    søknadsbehandling.id,
                    session,
                ) shouldBe vilkår
            }

            val utilstrekkelig = utilstrekkeligDokumentert(periode = år(2022))

            val flerePerioder = OpplysningspliktVilkår.Vurdert.createFromVilkårsvurderinger(
                (vilkår.vurderingsperioder + utilstrekkelig.vurderingsperioder).toNonEmptyList(),
            )

            testDataHelper.sessionFactory.withTransaction { tx ->
                opplysningspliktVilkårsvurderingPostgresRepo.lagre(
                    søknadsbehandling.id,
                    flerePerioder,
                    tx,
                )
            }

            testDataHelper.sessionFactory.withSession { session ->
                opplysningspliktVilkårsvurderingPostgresRepo.hent(
                    søknadsbehandling.id,
                    session,
                ) shouldBe flerePerioder
            }
        }
    }

    @Test
    fun `sletter grunnlag hvis vurdering går fra vurdert til ikke vurdert`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val opplysningspliktGrunnlagRepo = OpplysningspliktGrunnlagPostgresRepo(dbMetricsStub)
            val opplysningspliktVilkårsvurderingPostgresRepo = OpplysningspliktVilkårsvurderingPostgresRepo(
                opplysningspliktGrunnlagRepo,
                dbMetricsStub,
            )
            val søknadsbehandling = testDataHelper.persisterSøknadsbehandlingVilkårsvurdert().second
            val (vilkår, grunnlag) = tilstrekkeligDokumentert(periode = søknadsbehandling.periode).let {
                it to it.grunnlag
            }

            testDataHelper.sessionFactory.withTransaction { tx ->
                opplysningspliktVilkårsvurderingPostgresRepo.lagre(
                    behandlingId = søknadsbehandling.id,
                    vilkår = vilkår,
                    tx = tx,
                )
            }

            testDataHelper.sessionFactory.withSession { session ->
                opplysningspliktVilkårsvurderingPostgresRepo.hent(
                    behandlingId = søknadsbehandling.id,
                    session = session,
                ) shouldBe vilkår
            }

            testDataHelper.sessionFactory.withTransaction { tx ->
                opplysningspliktVilkårsvurderingPostgresRepo.lagre(
                    behandlingId = søknadsbehandling.id,
                    vilkår = OpplysningspliktVilkår.IkkeVurdert,
                    tx = tx,
                )
            }
            dataSource.withSession { session ->
                opplysningspliktVilkårsvurderingPostgresRepo.hent(
                    behandlingId = søknadsbehandling.id,
                    session = session,
                ) shouldBe OpplysningspliktVilkår.IkkeVurdert

                opplysningspliktGrunnlagRepo.hent(
                    id = grunnlag.first().id,
                    session = session,
                ) shouldBe null
            }
        }
    }

    @Test
    fun `mapping for beskrivelse`() {
        OpplysningspliktBeskrivelse.TilstrekkeligDokumentasjon.toDb() shouldBe """{"type":"TilstrekkeligDokumentasjon"}"""
        OpplysningspliktBeskrivelse.UtilstrekkeligDokumentasjon.toDb() shouldBe """{"type":"UtilstrekkeligDokumentasjon"}"""
    }
}
