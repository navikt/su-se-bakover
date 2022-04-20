package no.nav.su.se.bakover.database.grunnlag

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.beregning.PersistertFradrag
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.database.withTransaction
import no.nav.su.se.bakover.domain.beregning.fradrag.F
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.beregning.fradrag.UtenlandskInntekt
import no.nav.su.se.bakover.test.lagFradragsgrunnlag
import org.junit.jupiter.api.Test

internal class FradragsgrunnlagPostgresRepoTest {

    @Test
    fun `lagrer, henter og erstatter fradragsgrunnlag`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val grunnlagRepo = testDataHelper.fradragsgrunnlagPostgresRepo
            val behandling = testDataHelper.persisterSøknadsbehandlingVilkårsvurdertUavklart().second

            val fradragsgrunnlag1 = lagFradragsgrunnlag(
                type = Fradragstype(F.Arbeidsinntekt),
                månedsbeløp = 5000.0,
                periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.desember(2021)),
                utenlandskInntekt = UtenlandskInntekt.create(
                    beløpIUtenlandskValuta = 5, valuta = "DKK", kurs = 10.5,
                ),
                tilhører = FradragTilhører.BRUKER,
            )

            val fradragsgrunnlag2 = lagFradragsgrunnlag(
                type = Fradragstype(F.Kontantstøtte),
                månedsbeløp = 15000.0,
                periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.desember(2021)),
                tilhører = FradragTilhører.EPS,
            )

            dataSource.withTransaction { tx ->
                grunnlagRepo.lagreFradragsgrunnlag(
                    behandlingId = behandling.id,
                    fradragsgrunnlag = listOf(
                        fradragsgrunnlag1,
                        fradragsgrunnlag2,
                    ),
                    tx,
                )

                testDataHelper.fradragsgrunnlagPostgresRepo.hentFradragsgrunnlag(behandling.id, tx) shouldBe listOf(
                    fradragsgrunnlag1.copy(
                        fradrag = PersistertFradrag(
                            fradragstype = Fradragstype(F.Arbeidsinntekt),
                            månedsbeløp = 5000.0,
                            periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.desember(2021)),
                            utenlandskInntekt = UtenlandskInntekt.create(
                                beløpIUtenlandskValuta = 5, valuta = "DKK", kurs = 10.5,
                            ),
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),
                    fradragsgrunnlag2.copy(
                        fradrag = PersistertFradrag(
                            fradragstype = Fradragstype(F.Kontantstøtte),
                            månedsbeløp = 15000.0,
                            periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.desember(2021)),
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.EPS,
                        ),
                    ),
                )

                grunnlagRepo.lagreFradragsgrunnlag(
                    behandlingId = behandling.id,
                    fradragsgrunnlag = emptyList(),
                    tx = tx,
                )

                testDataHelper.fradragsgrunnlagPostgresRepo.hentFradragsgrunnlag(behandling.id, tx) shouldBe emptyList()
            }
        }
    }
}
