package no.nav.su.se.bakover.domain.grunnlag

import arrow.core.left
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.august
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.fixedTidspunkt
import org.junit.jupiter.api.Test

internal class FradragsgrunnlagTest {

    private val behandlingsperiode = Periode.create(1.januar(2021), 31.juli(2021))

    @Test
    fun `ugyldig for enkelte fradragstyper`() {
        Grunnlag.Fradragsgrunnlag.tryCreate(
            fradrag = FradragFactory.ny(
                type = Fradragstype.UnderMinstenivå,
                månedsbeløp = 150.0,
                periode = behandlingsperiode,
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
            opprettet = fixedTidspunkt,
        ) shouldBe Grunnlag.Fradragsgrunnlag.UgyldigFradragsgrunnlag.UgyldigFradragstypeForGrunnlag.left()

        Grunnlag.Fradragsgrunnlag.tryCreate(
            fradrag = FradragFactory.ny(
                type = Fradragstype.BeregnetFradragEPS,
                månedsbeløp = 150.0,
                periode = behandlingsperiode,
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
            opprettet = fixedTidspunkt,
        ) shouldBe Grunnlag.Fradragsgrunnlag.UgyldigFradragsgrunnlag.UgyldigFradragstypeForGrunnlag.left()

        Grunnlag.Fradragsgrunnlag.tryCreate(
            fradrag = FradragFactory.ny(
                type = Fradragstype.ForventetInntekt,
                månedsbeløp = 150.0,
                periode = behandlingsperiode,
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
            opprettet = fixedTidspunkt,
        ) shouldBe Grunnlag.Fradragsgrunnlag.UgyldigFradragsgrunnlag.UgyldigFradragstypeForGrunnlag.left()
    }

    @Test
    fun `kan lage gyldige fradragsgrunnlag`() {
        Fradragstype.values().filterNot {
            listOf(
                Fradragstype.BeregnetFradragEPS,
                Fradragstype.ForventetInntekt,
                Fradragstype.UnderMinstenivå,
            ).contains(it)
        }.forEach {
            Grunnlag.Fradragsgrunnlag.tryCreate(
                fradrag = FradragFactory.ny(
                    type = it,
                    månedsbeløp = 150.0,
                    periode = behandlingsperiode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
                opprettet = fixedTidspunkt,
            ).isRight() shouldBe true
        }
    }

    @Test
    fun `fradrag med periode som er lik stønadsperiode, blir oppdatert til å gjelde for hele stønadsperioden`() {
        val oppdatertPeriode = Periode.create(1.januar(2022), 31.desember(2022))
        val fradragsgrunnlag = Grunnlag.Fradragsgrunnlag.create(
            fradrag = FradragFactory.ny(
                type = Fradragstype.Kontantstøtte,
                månedsbeløp = 200.0,
                periode = Periode.create(1.januar(2021), 31.desember(2021)),
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
        )

        fradragsgrunnlag.oppdaterFradragsperiode(
            oppdatertPeriode,
        ).periode shouldBe oppdatertPeriode
    }

    @Test
    fun `fraOgMed blir kuttet og satt lik stønadsperiode FOM når oppdatertPeriode er etter fraOgMed `() {
        val oppdatertPeriode = Periode.create(1.mai(2021), 31.desember(2021))
        val fradragsgrunnlag = Grunnlag.Fradragsgrunnlag.create(
            fradrag = FradragFactory.ny(
                type = Fradragstype.Kontantstøtte,
                månedsbeløp = 200.0,
                periode = Periode.create(1.februar(2021), 31.desember(2021)),
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
        )

        fradragsgrunnlag.oppdaterFradragsperiode(
            oppdatertPeriode,
        ).periode shouldBe oppdatertPeriode
    }

    @Test
    fun `tilOgMed blir kuttet og satt lik stønadsperiode TOM når oppdatertPeriode er før tilOgMed `() {
        val oppdatertPeriode = Periode.create(1.januar(2021), 31.august(2021))
        val fradragsgrunnlag = Grunnlag.Fradragsgrunnlag.create(
            fradrag = FradragFactory.ny(
                type = Fradragstype.Kontantstøtte,
                månedsbeløp = 200.0,
                periode = Periode.create(1.januar(2021), 31.august(2021)),
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
        )

        fradragsgrunnlag.oppdaterFradragsperiode(
            oppdatertPeriode,
        ).periode shouldBe oppdatertPeriode
    }

    @Test
    fun `fradrag med deler av periode i 2022, oppdaterer periode til å gjelde for 2021, får fradragene til å gjelde for hele 2021`() {
        val oppdatertPeriode = Periode.create(1.januar(2021), 31.desember(2021))
        val fradragsgrunnlag = Grunnlag.Fradragsgrunnlag.create(
            fradrag = FradragFactory.ny(
                type = Fradragstype.Kontantstøtte,
                månedsbeløp = 200.0,
                periode = Periode.create(1.februar(2022), 31.august(2022)),
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
        )

        fradragsgrunnlag.oppdaterFradragsperiode(
            oppdatertPeriode,
        ).periode shouldBe oppdatertPeriode
    }
}
