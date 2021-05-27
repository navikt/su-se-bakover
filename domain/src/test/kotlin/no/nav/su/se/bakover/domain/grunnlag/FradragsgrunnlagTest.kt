package no.nav.su.se.bakover.domain.grunnlag

import io.kotest.assertions.arrow.either.shouldBeLeft
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Fradragsgrunnlag.Validator.valider
import org.junit.jupiter.api.Test

internal class FradragsgrunnlagTest {

    private val behandlingsperiode = Periode.create(1.januar(2021), 31.juli(2021))

    @Test
    fun `ugyldig hvis utenfor aktuell behandlingsperiode`() {
        Grunnlag.Fradragsgrunnlag(
            fradrag = FradragFactory.ny(
                type = Fradragstype.Arbeidsinntekt,
                månedsbeløp = 0.0,
                periode = Periode.create(fraOgMed = 1.mai(2021), tilOgMed = 31.desember(2021)),
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
        ).valider(behandlingsperiode) shouldBeLeft Grunnlag.Fradragsgrunnlag.Validator.UgyldigFradragsgrunnlag.UtenforBehandlingsperiode
    }

    @Test
    fun `ugyldig for enkelte fradragstyper`() {
        Grunnlag.Fradragsgrunnlag(
            fradrag = FradragFactory.ny(
                type = Fradragstype.UnderMinstenivå,
                månedsbeløp = 150.0,
                periode = behandlingsperiode,
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
        ).valider(behandlingsperiode) shouldBeLeft Grunnlag.Fradragsgrunnlag.Validator.UgyldigFradragsgrunnlag.UgyldigFradragstypeForGrunnlag

        Grunnlag.Fradragsgrunnlag(
            fradrag = FradragFactory.ny(
                type = Fradragstype.BeregnetFradragEPS,
                månedsbeløp = 150.0,
                periode = behandlingsperiode,
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
        ).valider(behandlingsperiode) shouldBeLeft Grunnlag.Fradragsgrunnlag.Validator.UgyldigFradragsgrunnlag.UgyldigFradragstypeForGrunnlag

        Grunnlag.Fradragsgrunnlag(
            fradrag = FradragFactory.ny(
                type = Fradragstype.ForventetInntekt,
                månedsbeløp = 150.0,
                periode = behandlingsperiode,
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
        ).valider(behandlingsperiode) shouldBeLeft Grunnlag.Fradragsgrunnlag.Validator.UgyldigFradragsgrunnlag.UgyldigFradragstypeForGrunnlag
    }

    @Test
    fun `ugyldig hvis et element i en liste er ugyldig`() {
        listOf(
            Grunnlag.Fradragsgrunnlag(
                fradrag = FradragFactory.ny(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 150.0,
                    periode = behandlingsperiode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            Grunnlag.Fradragsgrunnlag(
                fradrag = FradragFactory.ny(
                    type = Fradragstype.BeregnetFradragEPS,
                    månedsbeløp = 150.0,
                    periode = behandlingsperiode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
        ).valider(behandlingsperiode) shouldBeLeft Grunnlag.Fradragsgrunnlag.Validator.UgyldigFradragsgrunnlag.UgyldigFradragstypeForGrunnlag
    }
}
