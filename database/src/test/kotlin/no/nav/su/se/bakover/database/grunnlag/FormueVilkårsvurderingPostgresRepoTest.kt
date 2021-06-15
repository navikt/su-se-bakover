package no.nav.su.se.bakover.database.grunnlag

import arrow.core.NonEmptyList
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.database.FnrGenerator
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.grunnlag.Formuegrunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import org.junit.jupiter.api.Test
import java.util.UUID

internal class FormueVilkårsvurderingPostgresRepoTest {

    private val datasource = EmbeddedDatabase.instance()
    private val repo = FormueVilkårsvurderingPostgresRepo(
        datasource,
        FormuegrunnlagPostgresRepo(),
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

    @Test
    fun `lagrer og henter Vurdert`() {
        val periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.desember(2021))
        withMigratedDb {
            val behandlingId = UUID.randomUUID()
            val vilkår = Vilkår.Formue.Vurdert.create(
                grunnlag = NonEmptyList.fromListUnsafe(
                    listOf(
                        Formuegrunnlag.create(
                            id = UUID.randomUUID(),
                            opprettet = Tidspunkt.now(),
                            periode = periode,
                            epsFormue = Formuegrunnlag.Verdier(
                                verdiIkkePrimærbolig = 1,
                                verdiEiendommer = 2,
                                verdiKjøretøy = 3,
                                innskudd = 4,
                                verdipapir = 5,
                                pengerSkyldt = 6,
                                kontanter = 7,
                                depositumskonto = 8,
                            ),
                            søkersFormue = Formuegrunnlag.Verdier(
                                verdiIkkePrimærbolig = 9,
                                verdiEiendommer = 10,
                                verdiKjøretøy = 11,
                                innskudd = 12,
                                verdipapir = 13,
                                pengerSkyldt = 14,
                                kontanter = 15,
                                depositumskonto = 16,
                            ),
                            begrunnelse = "dfgdfgdfgsdfgdfg",
                            bosituasjon = Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning(
                                id = UUID.randomUUID(),
                                opprettet = Tidspunkt.now(),
                                periode = periode,
                                fnr = FnrGenerator.random(),
                                begrunnelse = "i87i78i78i87i",
                            ),
                            behandlingsPeriode = periode,
                        ),
                    ),
                ),
            )
            repo.lagre(behandlingId, vilkår)
            datasource.withSession { session ->
                repo.hent(behandlingId, session) shouldBe vilkår
            }
        }
    }
}
