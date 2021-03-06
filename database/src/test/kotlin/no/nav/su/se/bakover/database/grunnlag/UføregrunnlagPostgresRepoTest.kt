package no.nav.su.se.bakover.database.grunnlag

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.antall
import no.nav.su.se.bakover.database.fixedTidspunkt
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Uføregrunnlag
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import org.junit.jupiter.api.Test

internal class UføregrunnlagPostgresRepoTest {
    private val datasource = EmbeddedDatabase.instance()
    private val testDataHelper = TestDataHelper(datasource)
    private val grunnlagRepo = UføregrunnlagPostgresRepo()

    @Test
    fun `lagrer uføregrunnlag, kobler til behandling og sletter`() {
        withMigratedDb {
            val behandling = testDataHelper.nySøknadsbehandling()

            val uføregrunnlag1 = Uføregrunnlag(
                periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 30.april(2021)),
                uføregrad = Uføregrad.parse(100),
                forventetInntekt = 0,
                opprettet = fixedTidspunkt,
            )

            val uføregrunnlag2 = Uføregrunnlag(
                periode = Periode.create(fraOgMed = 1.mai(2021), tilOgMed = 31.desember(2021)),
                uføregrad = Uføregrad.parse(80),
                forventetInntekt = 14000,
                opprettet = fixedTidspunkt,
            )

            datasource.withSession { session ->
                session.transaction { tx ->
                    grunnlagRepo.lagre(behandling.id, listOf(uføregrunnlag1, uføregrunnlag2), tx)
                }
                grunnlagRepo.hentUføregrunnlag(behandling.id, session) shouldBe listOf(
                    uføregrunnlag1,
                    uføregrunnlag2,
                )
                """
                    select count(*) from grunnlag_uføre
                """.antall(emptyMap(), session) shouldBe 2
            }
        }
    }
}
