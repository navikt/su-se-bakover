package no.nav.su.se.bakover.database.grunnlag

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import org.junit.jupiter.api.Test
import java.util.UUID

internal class FormueVilkårsvurderingPostgresRepoTest {

    private val datasource = EmbeddedDatabase.instance()
    private val repo = FormueVilkårsvurderingPostgresRepo(
        datasource,
        FormuegrunnlagPostgresRepo(datasource),
    )

    @Test
    fun `lagrer og henter IkkeVurdert`() {
        withMigratedDb {
            val behandlingId = UUID.randomUUID()
            val vilkår = Vilkår.Formue.IkkeVurdert
            repo.lagre(behandlingId, vilkår)
            datasource.withSession { session ->
                repo.hent(behandlingId, session) shouldBe vilkår
            }
        }
    }

    // @Test
    // fun `lagrer og henter Vurdert`() {
    //     withMigratedDb {
    //         val behandlingId = UUID.randomUUID()
    //         val vilkår = Vilkår.Formue.Vurdert.create(
    //
    //         )
    //         repo.lagre(behandlingId, vilkår)
    //         datasource.withSession { session ->
    //             repo.hent(behandlingId, session) shouldBe vilkår
    //         }
    //     }
    // }
}
