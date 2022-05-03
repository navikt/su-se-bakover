package no.nav.su.se.bakover.database.grunnlag

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.grunnlag.OpplysningspliktBeskrivelse
import no.nav.su.se.bakover.domain.vilkår.OpplysningspliktVilkår
import no.nav.su.se.bakover.test.tilstrekkeligDokumentert
import org.junit.jupiter.api.Test

internal class OpplysningspliktVilkårsvurderingPostgresRepoTest {

    internal class UtenlandsoppholdVilkårsvurderingPostgresRepoTest {

        @Test
        fun `lagrer og henter vilkårsvurdering`() {
            withMigratedDb { dataSource ->
                val testDataHelper = TestDataHelper(dataSource)
                val søknadsbehandling = testDataHelper.persisterSøknadsbehandlingVilkårsvurdertUavklart().second
                val vilkår = tilstrekkeligDokumentert()

                testDataHelper.sessionFactory.withTransaction { tx ->
                    testDataHelper.opplysningspliktVilkårsvurderingPostgresRepo.lagre(søknadsbehandling.id, vilkår, tx)
                }

                testDataHelper.sessionFactory.withSession { session ->
                    testDataHelper.opplysningspliktVilkårsvurderingPostgresRepo.hent(
                        søknadsbehandling.id,
                        session,
                    ) shouldBe vilkår
                }
            }
        }

        @Test
        fun `sletter grunnlag hvis vurdering går fra vurdert til ikke vurdert`() {
            withMigratedDb { dataSource ->
                val testDataHelper = TestDataHelper(dataSource)
                val søknadsbehandling = testDataHelper.persisterSøknadsbehandlingVilkårsvurdertUavklart().second
                val (vilkår, grunnlag) = tilstrekkeligDokumentert(periode = søknadsbehandling.periode).let {
                    it to it.grunnlag
                }

                testDataHelper.sessionFactory.withTransaction { tx ->
                    testDataHelper.opplysningspliktVilkårsvurderingPostgresRepo.lagre(
                        behandlingId = søknadsbehandling.id,
                        vilkår = vilkår,
                        tx = tx,
                    )
                }

                testDataHelper.sessionFactory.withSession { session ->
                    testDataHelper.opplysningspliktVilkårsvurderingPostgresRepo.hent(
                        behandlingId = søknadsbehandling.id,
                        session = session,
                    ) shouldBe vilkår
                }

                testDataHelper.sessionFactory.withTransaction { tx ->
                    testDataHelper.opplysningspliktVilkårsvurderingPostgresRepo.lagre(
                        behandlingId = søknadsbehandling.id,
                        vilkår = OpplysningspliktVilkår.IkkeVurdert,
                        tx = tx,
                    )
                }
                dataSource.withSession { session ->
                    testDataHelper.opplysningspliktVilkårsvurderingPostgresRepo.hent(
                        behandlingId = søknadsbehandling.id,
                        session = session,
                    ) shouldBe OpplysningspliktVilkår.IkkeVurdert

                    testDataHelper.utenlandsoppholdgrunnlagPostgresRepo.hent(
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
}
