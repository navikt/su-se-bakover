package no.nav.su.se.bakover.domain.regulering

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.desember
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.lagFradragsgrunnlag
import org.junit.jupiter.api.Test
import vilkår.inntekt.domain.grunnlag.FradragForPeriode
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragsgrunnlag
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.math.BigDecimal

class UtledningReguleringstypeOgFradragTest {

    @Test
    fun `utleder automatisk og oppdaterer fradrag`() {
        val eksisterende = nonEmptyListOf(
            lagFradragsgrunnlag(Fradragstype.Uføretrygd, 1000.0, FradragTilhører.BRUKER),
            lagFradragsgrunnlag(Fradragstype.Alderspensjon, 2000.0, FradragTilhører.EPS),
        )

        val eksterntRegulerteBeløp = EksterntRegulerteBeløp(
            beløpBruker = listOf(
                RegulertBeløp(
                    fnr = fnr,
                    førRegulering = 1000,
                    etterRegulering = 1064,
                    fradragstype = Fradragstype.Uføretrygd,
                ),
            ),
            beløpEps = listOf(
                RegulertBeløp(
                    fnr = fnr,
                    førRegulering = 2000,
                    etterRegulering = 2128,
                    fradragstype = Fradragstype.Alderspensjon,
                ),
            ),
        )

        val resultat = utledReguleringstypeOgFradrag(
            fradrag = eksisterende,
            eksterntRegulerteBeløp = eksterntRegulerteBeløp,
            omregningsfaktor = BigDecimal("1.064076"),
            saksnummer = Saksnummer(8888),
        )

        resultat.first shouldBe Reguleringstype.AUTOMATISK
        with(resultat.second) {
            size shouldBe 2
            single { it.fradragstype == Fradragstype.Uføretrygd }.månedsbeløp shouldBe 1064
            single { it.fradragstype == Fradragstype.Alderspensjon }.månedsbeløp shouldBe 2128
        }
    }

    @Test
    fun `utleder manuell og oppdaterer fradrag`() {
        val eksisterende = nonEmptyListOf(
            lagFradragsgrunnlag(Fradragstype.Uføretrygd, 1000.0, FradragTilhører.BRUKER),
            lagFradragsgrunnlag(Fradragstype.Kvalifiseringsstønad, 2000.0, FradragTilhører.EPS),
        )

        val eksterntRegulerteBeløp = EksterntRegulerteBeløp(
            beløpBruker = listOf(
                RegulertBeløp(
                    fnr = fnr,
                    førRegulering = 1000,
                    etterRegulering = 1064,
                    fradragstype = Fradragstype.Uføretrygd,
                ),
            ),
            beløpEps = emptyList(),
        )

        val resultat = utledReguleringstypeOgFradrag(
            fradrag = eksisterende,
            eksterntRegulerteBeløp = eksterntRegulerteBeløp,
            omregningsfaktor = BigDecimal("1.064076"),
            saksnummer = Saksnummer(8888),
        )

        resultat.first shouldNotBe Reguleringstype.AUTOMATISK
        with(resultat.second) {
            size shouldBe 2
            single { it.fradragstype == Fradragstype.Uføretrygd }.månedsbeløp shouldBe 1064
            single { it.fradragstype == Fradragstype.Kvalifiseringsstønad }.månedsbeløp shouldBe 2000
        }
    }

    @Test
    fun `utleder fradrag med flere eksterne reguleringer`() {
        val eksisterende = nonEmptyListOf(
            lagFradragsgrunnlag(Fradragstype.Uføretrygd, 1000.0, FradragTilhører.BRUKER),
            lagFradragsgrunnlag(Fradragstype.Arbeidsavklaringspenger, 2000.0, FradragTilhører.BRUKER),
        )

        val eksterntRegulerteBeløp = EksterntRegulerteBeløp(
            beløpBruker = listOf(
                RegulertBeløp(
                    fnr = fnr,
                    førRegulering = 1000,
                    etterRegulering = 1064,
                    fradragstype = Fradragstype.Uføretrygd,
                ),
                RegulertBeløp(
                    fnr = fnr,
                    førRegulering = 2000,
                    etterRegulering = 2128,
                    fradragstype = Fradragstype.Arbeidsavklaringspenger,
                ),
            ),
            beløpEps = emptyList(),
        )

        val resultat = utledReguleringstypeOgFradrag(
            fradrag = eksisterende,
            eksterntRegulerteBeløp = eksterntRegulerteBeløp,
            omregningsfaktor = BigDecimal("1.064076"),
            saksnummer = Saksnummer(8888),
        )

        resultat.first shouldBe Reguleringstype.AUTOMATISK
        with(resultat.second) {
            size shouldBe 2
            single { it.fradragstype == Fradragstype.Uføretrygd }.månedsbeløp shouldBe 1064
            single { it.fradragstype == Fradragstype.Arbeidsavklaringspenger }.månedsbeløp shouldBe 2128
        }
    }
    /*
    @Test
    fun `utleder automatisk for bruker ufre og eps alder`() {
        val resultat = utledReguleringstypeOgFradrag(
            fradrag = lagFradragsgrunnlag(
                fradragstypeBruker = Fradragstype.Uføretrygd,
                fradragstypeEps = Fradragstype.Alderspensjon,
            ),
            eksterntRegulerteBeløp = eksterntRegulerteBeløp,
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
            eksterntRegulerteBeløp = eksterntRegulerteBeløp.copy(
                beløpEps = eksterntRegulerteBeløp.beløpEps + eksterntRegulerteBeløp.beløpEps,
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
            eksterntRegulerteBeløp = eksterntRegulerteBeløp,
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
            eksterntRegulerteBeløp = eksterntRegulerteBeløp,
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
            eksterntRegulerteBeløp = eksterntRegulerteBeløp,
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
            eksterntRegulerteBeløp = eksterntRegulerteBeløp,
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
            eksterntRegulerteBeløp = lagEksterntRegulerteBeløp(etterReguleringBruker = 1075),
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
            eksterntRegulerteBeløp = lagEksterntRegulerteBeløp(etterReguleringEps = 1075),
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
            eksterntRegulerteBeløp = eksterntRegulerteBeløp,
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


     */
    // TODO ------ egen testklasse tidligere for disse ----------------

    // TODO SupplementInneholderIkkeFradraget - Bør feile tidligere
    // TODO FantIkkeVedtakForApril - Bør feile tidligere

    // TODO YtelseErMidlertidigStanset - testes ikke her??
    // TODO AutomatiskSendingTilUtbetalingFeilet - testes ikke her?
    // TODO VedtakstidslinjeErIkkeSammenhengende - testes ikke her?
    // TODO DelvisOpphør - testes ikke her?

    companion object {

        fun lagFradragsgrunnlag(
            fradragstypeBruker: Fradragstype,
            månedsbeløp: Double = 1000.0,
            tilhører: FradragTilhører,
        ) = Fradragsgrunnlag.create(
            opprettet = Tidspunkt.now(fixedClock),
            fradrag = FradragForPeriode(
                fradragstype = fradragstypeBruker,
                månedsbeløp = månedsbeløp,
                periode = januar(2026)..desember(2026),
                utenlandskInntekt = null,
                tilhører = tilhører,
            ),
        )
    }
}
