package no.nav.su.se.bakover.domain.regulering

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.desember
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.test.fixedClock
import org.junit.jupiter.api.Test
import vilkår.inntekt.domain.grunnlag.FradragForPeriode
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragsgrunnlag
import vilkår.inntekt.domain.grunnlag.Fradragstype
import vilkår.inntekt.domain.grunnlag.UtenlandskInntekt
import java.math.BigDecimal

class UtledningReguleringstypeOgFradragTest {

    companion object {
        val regulerteFradragEksternKilde = RegulerteFradragEksternKilde(
            forBruker = NyttFradragEksternKilde(
                førRegulering = 1000,
                etterRegulering = 1064,
            ),
            forEps = listOf(
                NyttFradragEksternKilde(
                    førRegulering = 1000,
                    etterRegulering = 1064,
                ),
            ),
        )

        fun lagRegulerteFradragEksternKilde(
            etterReguleringBruker: Int = 1064,
            etterReguleringEps: Int = 1064,
        ) = RegulerteFradragEksternKilde(
            forBruker = NyttFradragEksternKilde(
                førRegulering = 1000,
                etterRegulering = etterReguleringBruker,
            ),
            forEps = listOf(
                NyttFradragEksternKilde(
                    førRegulering = 1000,
                    etterRegulering = etterReguleringEps,
                ),
            ),
        )

        fun lagFradragsgrunnlag(
            fradragstypeBruker: Fradragstype,
            fradragstypeEps: Fradragstype = fradragstypeBruker,
            beløpBruker: Double = 1000.0,
            beløpEps: Double = 1000.0,
            utlandskInntektBruker: UtenlandskInntekt? = null,
            utlandskInntektEps: UtenlandskInntekt? = null,
        ) = nonEmptyListOf(
            Fradragsgrunnlag.create(
                opprettet = Tidspunkt.now(fixedClock),
                fradrag = FradragForPeriode(
                    fradragstype = fradragstypeBruker,
                    månedsbeløp = beløpBruker,
                    periode = januar(2026)..desember(2026),
                    utenlandskInntekt = utlandskInntektBruker,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            Fradragsgrunnlag.create(
                opprettet = Tidspunkt.now(fixedClock),
                fradrag = FradragForPeriode(
                    fradragstype = fradragstypeEps,
                    månedsbeløp = beløpEps,
                    periode = januar(2026)..desember(2026),
                    utenlandskInntekt = utlandskInntektEps,
                    tilhører = FradragTilhører.EPS,
                ),
            ),
        )
    }

    // TODO gir denne egt mening? fradragstype uføre er vel bare for eps?
    // TODO Her skal det vel heller være forventet inntekt IEU som per nå blir manuell hvis større enn 0??
    @Test
    fun `utleder automatisk for uføre`() {
        val resultat = utledReguleringstypeOgFradrag(
            fradrag = lagFradragsgrunnlag(Fradragstype.Uføretrygd),
            regulerteFradragEksternKilde = regulerteFradragEksternKilde,
            omregningsfaktor = BigDecimal("1.064076"),
            saksnummer = Saksnummer(8888),
        )

        resultat.first shouldBe Reguleringstype.AUTOMATISK
    }

    // TODO gir dette mening? vil det noen gang være at egen alderspensjon blir fradrag? Trolig ja?
    @Test
    fun `utleder automatisk for alder`() {
        val resultat = utledReguleringstypeOgFradrag(
            fradrag = lagFradragsgrunnlag(Fradragstype.Alderspensjon),
            regulerteFradragEksternKilde = regulerteFradragEksternKilde,
            omregningsfaktor = BigDecimal("1.064076"),
            saksnummer = Saksnummer(8888),
        )
        resultat.first shouldBe Reguleringstype.AUTOMATISK
    }

    @Test
    fun `utleder automatisk for bruker alder og uføre eps`() {
        val resultat = utledReguleringstypeOgFradrag(
            fradrag = lagFradragsgrunnlag(
                fradragstypeBruker = Fradragstype.Alderspensjon,
                fradragstypeEps = Fradragstype.Uføretrygd,
            ),
            regulerteFradragEksternKilde = regulerteFradragEksternKilde,
            omregningsfaktor = BigDecimal("1.064076"),
            saksnummer = Saksnummer(8888),
        )
        resultat.first shouldBe Reguleringstype.AUTOMATISK
    }

    @Test
    fun `utleder automatisk for bruker ufre og eps alder`() {
        val resultat = utledReguleringstypeOgFradrag(
            fradrag = lagFradragsgrunnlag(
                fradragstypeBruker = Fradragstype.Uføretrygd,
                fradragstypeEps = Fradragstype.Alderspensjon,
            ),
            regulerteFradragEksternKilde = regulerteFradragEksternKilde,
            omregningsfaktor = BigDecimal("1.064076"),
            saksnummer = Saksnummer(8888),
        )
        resultat.first shouldBe Reguleringstype.AUTOMATISK
    }

    @Test
    fun `utleder manuell for alder uten supplement`() {
        /*
        TODO Det vil / skal alltid være ??
        val fradragsgrunnlag = nonEmptyListOf(
            Fradragsgrunnlag.create(
                opprettet = Tidspunkt.now(fixedClock),
                fradrag = FradragForPeriode(
                    fradragstype = Fradragstype.Alderspensjon,
                    månedsbeløp = 1000.0,
                    periode = januar(2026)..desember(2026),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
        )

        val resultat = utledReguleringstypeOgFradrag(
            nyttFradrag = NyttFradragEksternKilde(
                førRegulering = 1000,
                etterRegulering = 1064
            ),
            fradragstype = Fradragstype.Alderspensjon,
            originaleFradragsgrunnlag = fradragsgrunnlag,
            fradragTilhører = FradragTilhører.BRUKER,
            merEnn1Eps = false,
            omregningsfaktor = BigDecimal("1.064076"),
            saksnummer = Saksnummer(8888),
        )

        (resultat.first as Reguleringstype.MANUELL).problemer.any {
            it is ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.BrukerManglerSupplement
        }
        val årsak = (resultat.first as Reguleringstype.MANUELL).problemer.single()
            as ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.BrukerManglerSupplement
        årsak.fradragskategori shouldBe Fradragstype.Alderspensjon.kategori
        årsak.fradragTilhører shouldBe FradragTilhører.BRUKER
         */
    }

    @Test
    fun `utleder manuell for uføre eps uten supplement`() {
        /*
                TODO Det vil / skal alltid være ??
                val fradragsgrunnlag = nonEmptyListOf(
                    Fradragsgrunnlag.create(
                        opprettet = Tidspunkt.now(fixedClock),
                        fradrag = FradragForPeriode(
                            fradragstype = Fradragstype.Uføretrygd,
                            månedsbeløp = 1000.0,
                            periode = januar(2026)..desember(2026),
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.EPS,
                        ),
                    ),
                )

                val resultat = utledReguleringstypeOgFradragIndre(
                    eksternSupplementRegulering = EksternSupplementRegulering(
                        supplementId = null,
                        bruker = null,
                        eps = emptyList(),
                    ),
                    fradragstype = Fradragstype.Uføretrygd,
                    originaleFradragsgrunnlag = fradragsgrunnlag,
                    fradragTilhører = FradragTilhører.EPS,
                    merEnn1Eps = false,
                    omregningsfaktor = BigDecimal("1.064076"),
                    saksnummer = Saksnummer(8888),
                )

                (resultat.first as Reguleringstype.MANUELL).problemer.any {
                    it is ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.BrukerManglerSupplement
                }
                val årsak = (resultat.first as Reguleringstype.MANUELL).problemer.single()
                    as ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.BrukerManglerSupplement
                årsak.fradragskategori shouldBe Fradragstype.Uføretrygd.kategori
                årsak.fradragTilhører shouldBe FradragTilhører.EPS
         */
    }

    @Test
    fun `utleder manuell for flere eps`() {
        val resultat = utledReguleringstypeOgFradrag(
            fradrag = lagFradragsgrunnlag(Fradragstype.Uføretrygd),
            regulerteFradragEksternKilde = regulerteFradragEksternKilde.copy(
                forEps = regulerteFradragEksternKilde.forEps + regulerteFradragEksternKilde.forEps,
            ),
            omregningsfaktor = BigDecimal("1.064076"),
            saksnummer = Saksnummer(8888),
        )

        (resultat.first as Reguleringstype.MANUELL).problemer.any {
            it is ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.MerEnn1Eps
        }
        val årsak = (resultat.first as Reguleringstype.MANUELL).problemer.single()
            as ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.MerEnn1Eps
        årsak.fradragskategori shouldBe Fradragstype.Uføretrygd.kategori
        årsak.fradragTilhører shouldBe FradragTilhører.EPS
    }

    @Test
    fun `utleder manuell for fradrag utlandstinntekt bruker`() {
        val resultat = utledReguleringstypeOgFradrag(
            fradrag = lagFradragsgrunnlag(
                Fradragstype.Uføretrygd,
                utlandskInntektBruker = UtenlandskInntekt.create(0, "SEK", 0.0),
            ),
            regulerteFradragEksternKilde = regulerteFradragEksternKilde,
            omregningsfaktor = BigDecimal("1.064076"),
            saksnummer = Saksnummer(8888),
        )

        (resultat.first as Reguleringstype.MANUELL).problemer.any {
            it is ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.FradragErUtenlandsinntekt
        }
        val årsak = (resultat.first as Reguleringstype.MANUELL).problemer.single()
            as ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.FradragErUtenlandsinntekt
        årsak.fradragskategori shouldBe Fradragstype.Uføretrygd.kategori
        årsak.fradragTilhører shouldBe FradragTilhører.BRUKER
    }

    @Test
    fun `utleder manuell for fradrag utlandstinntekt eps`() {
        val resultat = utledReguleringstypeOgFradrag(
            fradrag = lagFradragsgrunnlag(
                Fradragstype.Uføretrygd,
                utlandskInntektEps = UtenlandskInntekt.create(0, "SEK", 0.0),
            ),
            regulerteFradragEksternKilde = regulerteFradragEksternKilde,
            omregningsfaktor = BigDecimal("1.064076"),
            saksnummer = Saksnummer(8888),
        )

        (resultat.first as Reguleringstype.MANUELL).problemer.any {
            it is ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.FradragErUtenlandsinntekt
        }
        val årsak = (resultat.first as Reguleringstype.MANUELL).problemer.single()
            as ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.FradragErUtenlandsinntekt
        årsak.fradragskategori shouldBe Fradragstype.Uføretrygd.kategori
        årsak.fradragTilhører shouldBe FradragTilhører.EPS
    }

    @Test
    fun `utleder manuell for hvis eksternt fradrag før regulering er ulikt brukt fradrag for bruker`() {
        val resultat = utledReguleringstypeOgFradrag(
            fradrag = lagFradragsgrunnlag(Fradragstype.Uføretrygd, beløpBruker = 900.0),
            regulerteFradragEksternKilde = regulerteFradragEksternKilde,
            omregningsfaktor = BigDecimal("1.064076"),
            saksnummer = Saksnummer(8888),
        )

        (resultat.first as Reguleringstype.MANUELL).problemer.any {
            it is ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.DifferanseFørRegulering
        }
        val årsak = (resultat.first as Reguleringstype.MANUELL).problemer.single()
            as ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.DifferanseFørRegulering
        årsak.fradragskategori shouldBe Fradragstype.Uføretrygd.kategori
        årsak.fradragTilhører shouldBe FradragTilhører.BRUKER
        årsak.eksternNettoBeløpFørRegulering shouldBe BigDecimal(1000)
    }

    @Test
    fun `utleder manuell for hvis eksternt fradrag før regulering er ulikt brukt fradrag for eps`() {
        val resultat = utledReguleringstypeOgFradrag(
            fradrag = lagFradragsgrunnlag(Fradragstype.Uføretrygd, beløpEps = 900.0),
            regulerteFradragEksternKilde = regulerteFradragEksternKilde,
            omregningsfaktor = BigDecimal("1.064076"),
            saksnummer = Saksnummer(8888),
        )

        (resultat.first as Reguleringstype.MANUELL).problemer.any {
            it is ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.DifferanseFørRegulering
        }
        val årsak = (resultat.first as Reguleringstype.MANUELL).problemer.single()
            as ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.DifferanseFørRegulering
        årsak.fradragskategori shouldBe Fradragstype.Uføretrygd.kategori
        årsak.fradragTilhører shouldBe FradragTilhører.EPS
        årsak.eksternNettoBeløpFørRegulering shouldBe BigDecimal(1000)
    }

    @Test
    fun `utleder manuell for hvis eksternt fradrag etter regulering er usannsynlig høyt for bruker`() {
        val resultat = utledReguleringstypeOgFradrag(
            fradrag = lagFradragsgrunnlag(Fradragstype.Uføretrygd),
            regulerteFradragEksternKilde = lagRegulerteFradragEksternKilde(etterReguleringBruker = 1075),
            omregningsfaktor = BigDecimal("1.064076"),
            saksnummer = Saksnummer(8888),
        )

        (resultat.first as Reguleringstype.MANUELL).problemer.any {
            it is ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.DifferanseEtterRegulering
        }
        val årsak = (resultat.first as Reguleringstype.MANUELL).problemer.single()
            as ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.DifferanseEtterRegulering
        årsak.fradragskategori shouldBe Fradragstype.Uføretrygd.kategori
        årsak.fradragTilhører shouldBe FradragTilhører.BRUKER
        årsak.vårtBeløpFørRegulering shouldBe BigDecimal("1000.00")
        årsak.forventetBeløpEtterRegulering shouldBe BigDecimal("1064.08")
        årsak.eksternNettoBeløpEtterRegulering shouldBe BigDecimal(1075)
    }

    @Test
    fun `utleder manuell for hvis eksternt fradrag etter regulering er usannsynlig høyt for eps`() {
        val resultat = utledReguleringstypeOgFradrag(
            fradrag = lagFradragsgrunnlag(Fradragstype.Uføretrygd),
            regulerteFradragEksternKilde = lagRegulerteFradragEksternKilde(etterReguleringEps = 1075),
            omregningsfaktor = BigDecimal("1.064076"),
            saksnummer = Saksnummer(8888),
        )

        (resultat.first as Reguleringstype.MANUELL).problemer.any {
            it is ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.DifferanseEtterRegulering
        }
        val årsak = (resultat.first as Reguleringstype.MANUELL).problemer.single()
            as ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.DifferanseEtterRegulering
        årsak.fradragskategori shouldBe Fradragstype.Uføretrygd.kategori
        årsak.fradragTilhører shouldBe FradragTilhører.EPS
        årsak.vårtBeløpFørRegulering shouldBe BigDecimal("1000.00")
        årsak.forventetBeløpEtterRegulering shouldBe BigDecimal("1064.08")
        årsak.eksternNettoBeløpEtterRegulering shouldBe BigDecimal(1075)
    }

    @Test
    fun `utleder manuell hvis flere perioder for en fradragstype`() {
        val fradrag = lagFradragsgrunnlag(Fradragstype.Uføretrygd)
        val resultat = utledReguleringstypeOgFradrag(
            fradrag = fradrag + listOf(fradrag.first()),
            regulerteFradragEksternKilde = regulerteFradragEksternKilde,
            omregningsfaktor = BigDecimal("1.064076"),
            saksnummer = Saksnummer(8888),
        )

        (resultat.first as Reguleringstype.MANUELL).problemer.any {
            it is ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.FinnesFlerePerioderAvFradrag
        }
        val årsak = (resultat.first as Reguleringstype.MANUELL).problemer.single()
            as ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.FinnesFlerePerioderAvFradrag
        årsak.fradragskategori shouldBe Fradragstype.Uføretrygd.kategori
        årsak.fradragTilhører shouldBe FradragTilhører.BRUKER
    }

    // TODO ------ egen testklasse tidligere for disse ----------------

    // TODO SupplementInneholderIkkeFradraget - Bør feile tidligere
    // TODO FantIkkeVedtakForApril - Bør feile tidligere

    // TODO YtelseErMidlertidigStanset - testes ikke her??
    // TODO AutomatiskSendingTilUtbetalingFeilet - testes ikke her?
    // TODO VedtakstidslinjeErIkkeSammenhengende - testes ikke her?
    // TODO DelvisOpphør - testes ikke her?
}
