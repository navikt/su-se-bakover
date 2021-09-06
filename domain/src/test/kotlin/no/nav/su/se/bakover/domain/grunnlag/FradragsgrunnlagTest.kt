package no.nav.su.se.bakover.domain.grunnlag

import arrow.core.left
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juli
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
}
