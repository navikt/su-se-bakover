package vilkår.inntekt.domain

import no.nav.su.se.bakover.common.tid.periode.april
import no.nav.su.se.bakover.common.tid.periode.august
import no.nav.su.se.bakover.common.tid.periode.desember
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.juni
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.common.tid.periode.mars
import no.nav.su.se.bakover.common.tid.periode.september
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.grunnlag.nyFradragsgrunnlag
import no.nav.su.se.bakover.test.shouldBeEqualToExceptId
import org.junit.jupiter.api.Test
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragsgrunnlag
import vilkår.inntekt.domain.grunnlag.Fradragstype
import vilkår.inntekt.domain.grunnlag.UtenlandskInntekt
import vilkår.inntekt.domain.grunnlag.slåSammen
import java.util.UUID

internal class FradragsgrunnlagSlåSammenTest {
    @Test
    fun `slår sammen fradrag som er like og tilstøter`() {
        val dagpengerAlene = nyFradragsgrunnlag(
            periode = januar(2021),
            månedsbeløp = 99.0,
            type = Fradragstype.Dagpenger,
            tilhører = FradragTilhører.BRUKER,
            utenlandskInntekt = null,
        )
        // ----------- BRUKER SOSIALSTØNAD --------------
        val sb1 = nyFradragsgrunnlag(
            periode = januar(2021),
            månedsbeløp = 1.0,
            type = Fradragstype.Sosialstønad,
            tilhører = FradragTilhører.BRUKER,
            utenlandskInntekt = null,
        )
        val sb2 = nyFradragsgrunnlag(
            periode = januar(2021),
            månedsbeløp = 2.0,
            type = Fradragstype.Sosialstønad,
            tilhører = FradragTilhører.BRUKER,
            utenlandskInntekt = null,
        )
        val sb3 = nyFradragsgrunnlag(
            periode = februar(2021),
            månedsbeløp = 3.5,
            type = Fradragstype.Sosialstønad,
            tilhører = FradragTilhører.BRUKER,
            utenlandskInntekt = null,
        )
        // ----------- ------------------ --------------
        // ----------- EPS SOSIALSTØNAD --------------
        val se4 = nyFradragsgrunnlag(
            periode = januar(2021),
            månedsbeløp = 4.0,
            type = Fradragstype.Sosialstønad,
            tilhører = FradragTilhører.EPS,
            utenlandskInntekt = null,
        )
        val se5 = nyFradragsgrunnlag(
            periode = januar(2021),
            månedsbeløp = 5.0,
            type = Fradragstype.Sosialstønad,
            tilhører = FradragTilhører.EPS,
            utenlandskInntekt = null,
        )
        val se6 = nyFradragsgrunnlag(
            periode = februar(2021),
            månedsbeløp = 6.5,
            type = Fradragstype.Sosialstønad,
            tilhører = FradragTilhører.EPS,
            utenlandskInntekt = null,
        )
        // ----------- ------------------ --------------
        // ----------- BRUKER SOSIALSTØNAD UTENLANDSINNTEKT --------------
        val sbu7 = nyFradragsgrunnlag(
            periode = januar(2021),
            månedsbeløp = 7.0,
            type = Fradragstype.Sosialstønad,
            tilhører = FradragTilhører.BRUKER,
            utenlandskInntekt = UtenlandskInntekt.create(
                beløpIUtenlandskValuta = 7,
                valuta = "ValutisValutas",
                kurs = 1.0,
            ),
        )
        val sbu8 = nyFradragsgrunnlag(
            periode = januar(2021),
            månedsbeløp = 8.0,
            type = Fradragstype.Sosialstønad,
            tilhører = FradragTilhører.BRUKER,
            utenlandskInntekt = UtenlandskInntekt.create(
                beløpIUtenlandskValuta = 7,
                valuta = "ValutisValutas",
                kurs = 1.0,
            ),
        )
        val sbu9 = nyFradragsgrunnlag(
            periode = februar(2021),
            månedsbeløp = 9.5,
            type = Fradragstype.Sosialstønad,
            tilhører = FradragTilhører.BRUKER,
            utenlandskInntekt = UtenlandskInntekt.create(
                beløpIUtenlandskValuta = 7,
                valuta = "ValutisValutas",
                kurs = 1.0,
            ),
        )
        // ----------- ------------------ -----------------------------
        // ----------- EPS SOSIALSTØNAD UTENLANDSINNTEKT --------------
        val seu10 = nyFradragsgrunnlag(
            periode = januar(2021),
            månedsbeløp = 10.0,
            type = Fradragstype.Sosialstønad,
            tilhører = FradragTilhører.EPS,
            utenlandskInntekt = UtenlandskInntekt.create(
                beløpIUtenlandskValuta = 7,
                valuta = "ValutisValutas",
                kurs = 1.0,
            ),
        )
        val seu11 = nyFradragsgrunnlag(
            periode = januar(2021),
            månedsbeløp = 11.0,
            type = Fradragstype.Sosialstønad,
            tilhører = FradragTilhører.EPS,
            utenlandskInntekt = UtenlandskInntekt.create(
                beløpIUtenlandskValuta = 7,
                valuta = "ValutisValutas",
                kurs = 1.0,
            ),
        )
        val seu12 = nyFradragsgrunnlag(
            periode = februar(2021),
            månedsbeløp = 12.5,
            type = Fradragstype.Sosialstønad,
            tilhører = FradragTilhører.EPS,
            utenlandskInntekt = UtenlandskInntekt.create(
                beløpIUtenlandskValuta = 7,
                valuta = "ValutisValutas",
                kurs = 1.0,
            ),
        )
        // ----------- ------------------ -----------------------------

        val actual = listOf(
            dagpengerAlene,
            sb1, sb2, sb3,
            se4, se5, se6,
            sbu7, sbu8, sbu9,
            seu10, seu11, seu12,
        ).slåSammen(fixedClock)

        actual.shouldBeEqualToExceptId(
            listOf(
                nyFradragsgrunnlag(
                    periode = januar(2021),
                    månedsbeløp = 99.0,
                    type = Fradragstype.Dagpenger,
                    tilhører = FradragTilhører.BRUKER,
                    utenlandskInntekt = null,
                ),
                // BRUKER
                nyFradragsgrunnlag(
                    periode = januar(2021),
                    månedsbeløp = 15.0,
                    type = Fradragstype.Sosialstønad,
                    tilhører = FradragTilhører.BRUKER,
                    utenlandskInntekt = UtenlandskInntekt.create(
                        beløpIUtenlandskValuta = 7,
                        valuta = "ValutisValutas",
                        kurs = 1.0,
                    ),
                ),
                Fradragsgrunnlag.create(
                    fradrag = sbu9.fradrag,
                    opprettet = sbu9.opprettet,
                    id = UUID.randomUUID(),
                ),
                nyFradragsgrunnlag(
                    periode = januar(2021),
                    månedsbeløp = 3.0,
                    type = Fradragstype.Sosialstønad,
                    tilhører = FradragTilhører.BRUKER,
                    utenlandskInntekt = null,
                ),
                Fradragsgrunnlag.create(
                    fradrag = sb3.fradrag,
                    opprettet = sb3.opprettet,
                    id = UUID.randomUUID(),
                ),

                // EPS
                nyFradragsgrunnlag(
                    periode = januar(2021),
                    månedsbeløp = 21.0,
                    type = Fradragstype.Sosialstønad,
                    tilhører = FradragTilhører.EPS,
                    utenlandskInntekt = UtenlandskInntekt.create(
                        beløpIUtenlandskValuta = 7,
                        valuta = "ValutisValutas",
                        kurs = 1.0,
                    ),
                ),
                Fradragsgrunnlag.create(
                    fradrag = seu12.fradrag,
                    opprettet = seu12.opprettet,
                    id = UUID.randomUUID(),
                ),
                nyFradragsgrunnlag(
                    periode = januar(2021),
                    månedsbeløp = 9.0,
                    type = Fradragstype.Sosialstønad,
                    tilhører = FradragTilhører.EPS,
                    utenlandskInntekt = null,
                ),
                Fradragsgrunnlag.create(
                    fradrag = se6.fradrag,
                    opprettet = se6.opprettet,
                    id = UUID.randomUUID(),
                ),
            ),
        )
    }

    @Test
    fun `slår sammen tilstøtende måneder`() {
        val input1 = listOf(
            nyFradragsgrunnlag(periode = januar(2021)),
            nyFradragsgrunnlag(periode = februar(2021)),
        )
        val expected1 = listOf(nyFradragsgrunnlag(periode = januar(2021)..februar(2021)))
        val actual1 = input1.slåSammen(fixedClock)
        actual1.shouldBeEqualToExceptId(expected1)

        val input2 = listOf(
            nyFradragsgrunnlag(periode = februar(2021)),
            nyFradragsgrunnlag(periode = januar(2021)),
        )
        val expected2 = listOf(
            nyFradragsgrunnlag(periode = januar(2021)..februar(2021)),
        )
        val actual2 = input2.slåSammen(fixedClock)
        actual2.shouldBeEqualToExceptId(expected2)
    }

    @Test
    fun `slår sammen tilstøtende periode og måned`() {
        listOf(
            nyFradragsgrunnlag(periode = januar(2021)..mars(2021)),
            nyFradragsgrunnlag(periode = april(2021)),
        ).slåSammen(fixedClock).shouldBeEqualToExceptId(
            listOf(
                nyFradragsgrunnlag(periode = januar(2021)..april(2021)),
            ),
        )

        listOf(

            nyFradragsgrunnlag(periode = april(2021)),
            nyFradragsgrunnlag(periode = januar(2021)..mars(2021)),
        ).slåSammen(fixedClock).shouldBeEqualToExceptId(
            listOf(
                nyFradragsgrunnlag(periode = januar(2021)..april(2021)),
            ),
        )
    }

    @Test
    fun `slår sammen tilstøtende måned og periode`() {
        listOf(
            nyFradragsgrunnlag(periode = desember(2020)),
            nyFradragsgrunnlag(periode = januar(2021)..mars(2021)),
        ).slåSammen(fixedClock).shouldBeEqualToExceptId(
            listOf(
                nyFradragsgrunnlag(periode = desember(2020)..mars(2021)),
            ),
        )
        listOf(
            nyFradragsgrunnlag(periode = januar(2021)..mars(2021)),
            nyFradragsgrunnlag(periode = desember(2020)),
        ).slåSammen(fixedClock).shouldBeEqualToExceptId(
            listOf(
                nyFradragsgrunnlag(periode = desember(2020)..mars(2021)),
            ),
        )
    }

    @Test
    fun `slår sammen tilstøtende periode og periode`() {
        listOf(
            nyFradragsgrunnlag(periode = januar(2021)..mars(2021)),
            nyFradragsgrunnlag(periode = april(2021)..mai(2021)),
        ).slåSammen(fixedClock).shouldBeEqualToExceptId(
            listOf(
                nyFradragsgrunnlag(periode = januar(2021)..mai(2021)),
            ),
        )
        listOf(
            nyFradragsgrunnlag(periode = april(2021)..mai(2021)),
            nyFradragsgrunnlag(periode = januar(2021)..mars(2021)),
        ).slåSammen(fixedClock).shouldBeEqualToExceptId(
            listOf(
                nyFradragsgrunnlag(periode = januar(2021)..mai(2021)),
            ),
        )
    }

    @Test
    fun `slår ikke sammen ikke-tilstøtende måneder`() {
        listOf(
            nyFradragsgrunnlag(periode = januar(2021)),
            nyFradragsgrunnlag(periode = mars(2021)),
        ).slåSammen(fixedClock).shouldBeEqualToExceptId(
            listOf(
                nyFradragsgrunnlag(periode = januar(2021)),
                nyFradragsgrunnlag(periode = mars(2021)),
            ),
        )

        listOf(
            nyFradragsgrunnlag(periode = mars(2021)),
            nyFradragsgrunnlag(periode = januar(2021)),
        ).slåSammen(fixedClock).shouldBeEqualToExceptId(
            listOf(
                nyFradragsgrunnlag(periode = januar(2021)),
                nyFradragsgrunnlag(periode = mars(2021)),
            ),
        )
    }

    @Test
    fun `mix av overlapp og tilstøt og ikke-tilstøter`() {
        listOf(
            nyFradragsgrunnlag(periode = januar(2021)..mars(2021)),
            nyFradragsgrunnlag(periode = mars(2021)..april(2021)),
            nyFradragsgrunnlag(periode = mai(2021)..juni(2021)),
            nyFradragsgrunnlag(periode = august(2021)..september(2021)),
        ).slåSammen(fixedClock).shouldBeEqualToExceptId(
            listOf(
                nyFradragsgrunnlag(periode = januar(2021)..februar(2021)),
                nyFradragsgrunnlag(periode = mars(2021), månedsbeløp = 400.0),
                nyFradragsgrunnlag(periode = april(2021)..juni(2021)),
                nyFradragsgrunnlag(periode = august(2021)..september(2021)),
            ),
        )
        listOf(
            nyFradragsgrunnlag(periode = august(2021)..september(2021)),
            nyFradragsgrunnlag(periode = mai(2021)..juni(2021)),
            nyFradragsgrunnlag(periode = januar(2021)..mars(2021)),
            nyFradragsgrunnlag(periode = mars(2021)..april(2021)),
        ).slåSammen(fixedClock).shouldBeEqualToExceptId(
            listOf(
                nyFradragsgrunnlag(periode = januar(2021)..februar(2021)),
                nyFradragsgrunnlag(periode = mars(2021), månedsbeløp = 400.0),
                nyFradragsgrunnlag(periode = april(2021)..juni(2021)),
                nyFradragsgrunnlag(periode = august(2021)..september(2021)),
            ),
        )
    }
}
