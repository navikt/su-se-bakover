package vilkår.vurderinger

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.tid.periode.april
import no.nav.su.se.bakover.test.fullstendigUtenEPS
import no.nav.su.se.bakover.test.grunnlag.nyFradragsgrunnlag
import org.junit.jupiter.api.Test
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype
import vilkår.inntekt.domain.grunnlag.UtenlandskInntekt
import vilkår.vurderinger.domain.Grunnlagsdata

internal class GrunnlagsdataTest {

    @Test
    fun `hentBrukteFradragstyperBasertPå ignorerer utenlandsk fradrag`() {
        val måned = april(2026)
        val grunnlagsdata = Grunnlagsdata.create(
            fradragsgrunnlag = listOf(
                nyFradragsgrunnlag(
                    type = Fradragstype.Alderspensjon,
                    periode = måned,
                    utenlandskInntekt = UtenlandskInntekt.create(
                        beløpIUtenlandskValuta = 2500,
                        valuta = "SEK",
                        kurs = 1.0,
                    ),
                ),
            ),
            bosituasjon = listOf(fullstendigUtenEPS(periode = måned)),
        )

        grunnlagsdata.hentBrukteFradragstyperBasertPå(
            fradragstyper = listOf(Fradragstype.Alderspensjon),
            måned = måned,
            tilhører = FradragTilhører.BRUKER,
        ) shouldBe emptyList()
    }

    @Test
    fun `hentBrukteFradragstyperBasertPå returnerer kun ikke-utenlandsk fradragstype`() {
        val måned = april(2026)
        val grunnlagsdata = Grunnlagsdata.create(
            fradragsgrunnlag = listOf(
                nyFradragsgrunnlag(
                    type = Fradragstype.Alderspensjon,
                    periode = måned,
                    utenlandskInntekt = UtenlandskInntekt.create(
                        beløpIUtenlandskValuta = 2500,
                        valuta = "SEK",
                        kurs = 1.0,
                    ),
                ),
                nyFradragsgrunnlag(
                    type = Fradragstype.Alderspensjon,
                    periode = måned,
                ),
            ),
            bosituasjon = listOf(fullstendigUtenEPS(periode = måned)),
        )

        grunnlagsdata.hentBrukteFradragstyperBasertPå(
            fradragstyper = listOf(Fradragstype.Alderspensjon),
            måned = måned,
            tilhører = FradragTilhører.BRUKER,
        ) shouldBe listOf(Fradragstype.Alderspensjon)
    }
}
