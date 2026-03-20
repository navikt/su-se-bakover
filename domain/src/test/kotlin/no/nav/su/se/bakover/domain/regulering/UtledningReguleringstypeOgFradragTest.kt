package no.nav.su.se.bakover.domain.regulering

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.desember
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fnr
import org.junit.jupiter.api.Test
import vilkår.inntekt.domain.grunnlag.FradragForPeriode
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragsgrunnlag
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.math.BigDecimal

class UtledningReguleringstypeOgFradragTest {

    @Test
    fun `oppdaterer fradrag som har ekstern regulering tilgjengelig`() {
        val eksisterende = nonEmptyListOf(
            lagFradragsgrunnlag(Fradragstype.Uføretrygd, 1000.0, FradragTilhører.BRUKER),
            lagFradragsgrunnlag(Fradragstype.Alderspensjon, 2000.0, FradragTilhører.EPS),
        )

        val eksterntRegulerteBeløp = EksterntRegulerteBeløp(
            fnr = fnr,
            beløpBruker = listOf(
                RegulertBeløp(
                    fnr = fnr,
                    fradragstype = Fradragstype.Uføretrygd,
                    førRegulering = BigDecimal("1000.00"),
                    etterRegulering = BigDecimal("1064.00"),
                ),
            ),
            beløpEps = listOf(
                RegulertBeløp(
                    fnr = fnr,
                    fradragstype = Fradragstype.Alderspensjon,
                    førRegulering = BigDecimal("2000.00"),
                    etterRegulering = BigDecimal("2128.00"),
                ),
            ),
        )

        val resultat = utledReguleringstypeOgOppdaterFradrag(
            fradrag = eksisterende,
            eksterntRegulerteBeløp = eksterntRegulerteBeløp,
            omregningsfaktor = BigDecimal("1.064076"),
        )

        resultat.first shouldBe Reguleringstype.AUTOMATISK
        with(resultat.second) {
            size shouldBe 2
            single { it.fradragstype == Fradragstype.Uføretrygd }.månedsbeløp shouldBe 1064
            single { it.fradragstype == Fradragstype.Alderspensjon }.månedsbeløp shouldBe 2128
        }
    }

    @Test
    fun `oppdaterer fradrag med ekstern regulering selv om det finnes andre fradrag som ikke kan oppdateres automatisk`() {
        val eksisterende = nonEmptyListOf(
            lagFradragsgrunnlag(Fradragstype.Uføretrygd, 1000.0, FradragTilhører.BRUKER),
            lagFradragsgrunnlag(Fradragstype.Kvalifiseringsstønad, 2000.0, FradragTilhører.EPS),
        )

        val eksterntRegulerteBeløp = EksterntRegulerteBeløp(
            fnr = fnr,
            beløpBruker = listOf(
                RegulertBeløp(
                    fnr = fnr,
                    fradragstype = Fradragstype.Uføretrygd,
                    førRegulering = BigDecimal("1000.00"),
                    etterRegulering = BigDecimal("1064.00"),
                ),
            ),
            beløpEps = emptyList(),
        )

        val resultat = utledReguleringstypeOgOppdaterFradrag(
            fradrag = eksisterende,
            eksterntRegulerteBeløp = eksterntRegulerteBeløp,
            omregningsfaktor = BigDecimal("1.064076"),
        )

        resultat.first shouldNotBe Reguleringstype.AUTOMATISK
        with(resultat.second) {
            size shouldBe 2
            single { it.fradragstype == Fradragstype.Uføretrygd }.månedsbeløp shouldBe 1064
            single { it.fradragstype == Fradragstype.Kvalifiseringsstønad }.månedsbeløp shouldBe 2000
        }
    }

    @Test
    fun `oppdaterer fradrag når en person har flere fradrag med eksterne reguleringer tilgjengelig`() {
        val eksisterende = nonEmptyListOf(
            lagFradragsgrunnlag(Fradragstype.Uføretrygd, 1000.0, FradragTilhører.BRUKER),
            lagFradragsgrunnlag(Fradragstype.Arbeidsavklaringspenger, 2000.0, FradragTilhører.BRUKER),
        )

        val eksterntRegulerteBeløp = EksterntRegulerteBeløp(
            fnr = fnr,
            beløpBruker = listOf(
                RegulertBeløp(
                    fnr = fnr,
                    fradragstype = Fradragstype.Uføretrygd,
                    førRegulering = BigDecimal("1000.00"),
                    etterRegulering = BigDecimal("1064.00"),
                ),
                RegulertBeløp(
                    fnr = fnr,
                    fradragstype = Fradragstype.Arbeidsavklaringspenger,
                    førRegulering = BigDecimal("2000.00"),
                    etterRegulering = BigDecimal("2128.00"),
                ),
            ),
            beløpEps = emptyList(),
        )

        val resultat = utledReguleringstypeOgOppdaterFradrag(
            fradrag = eksisterende,
            eksterntRegulerteBeløp = eksterntRegulerteBeløp,
            omregningsfaktor = BigDecimal("1.064076"),
        )

        resultat.first shouldBe Reguleringstype.AUTOMATISK
        with(resultat.second) {
            size shouldBe 2
            single { it.fradragstype == Fradragstype.Uføretrygd }.månedsbeløp shouldBe 1064
            single { it.fradragstype == Fradragstype.Arbeidsavklaringspenger }.månedsbeløp shouldBe 2128
        }
    }

    @Test
    fun `utleder manuell regulering når fradrag inneholder en fradragstype som ikke kan justeres automatisk`() {
        val eksisterende = nonEmptyListOf(
            lagFradragsgrunnlag(Fradragstype.Uføretrygd, 1000.0, FradragTilhører.BRUKER),
            lagFradragsgrunnlag(Fradragstype.Kvalifiseringsstønad, 2000.0, FradragTilhører.BRUKER),
        )

        val eksterntRegulerteBeløp = EksterntRegulerteBeløp(
            fnr = fnr,
            beløpBruker = listOf(
                RegulertBeløp(
                    fnr = fnr,
                    fradragstype = Fradragstype.Uføretrygd,
                    førRegulering = BigDecimal("1000.00"),
                    etterRegulering = BigDecimal("1064.00"),
                ),
            ),
            beløpEps = emptyList(),
        )

        val resultat = utledReguleringstypeOgOppdaterFradrag(
            fradrag = eksisterende,
            eksterntRegulerteBeløp = eksterntRegulerteBeløp,
            omregningsfaktor = BigDecimal("1.064076"),
        )

        resultat.first shouldBe Reguleringstype.MANUELL(
            ÅrsakTilManuellRegulering.ManglerRegulertBeløpForFradrag(
                fradragskategori = Fradragstype.Kvalifiseringsstønad.kategori,
                fradragTilhører = FradragTilhører.BRUKER,
            ),
        )
    }

    @Test
    fun `utleder manuell regulering med flere årsaker når flere fradrag mangler ekstern regulering`() {
        val eksisterende = nonEmptyListOf(
            lagFradragsgrunnlag(Fradragstype.Uføretrygd, 1000.0, FradragTilhører.BRUKER),
            lagFradragsgrunnlag(Fradragstype.Kvalifiseringsstønad, 2000.0, FradragTilhører.BRUKER),
            lagFradragsgrunnlag(Fradragstype.Kvalifiseringsstønad, 3000.0, FradragTilhører.EPS),
        )

        val eksterntRegulerteBeløp = EksterntRegulerteBeløp(
            fnr = fnr,
            beløpBruker = listOf(
                RegulertBeløp(
                    fnr = fnr,
                    fradragstype = Fradragstype.Uføretrygd,
                    førRegulering = BigDecimal("1000.00"),
                    etterRegulering = BigDecimal("1064.00"),
                ),
            ),
            beløpEps = emptyList(),
        )

        val resultat = utledReguleringstypeOgOppdaterFradrag(
            fradrag = eksisterende,
            eksterntRegulerteBeløp = eksterntRegulerteBeløp,
            omregningsfaktor = BigDecimal("1.064076"),
        )

        val reguleringstype = resultat.first as Reguleringstype.MANUELL
        reguleringstype.problemer.size shouldBe 2
        with(resultat.second) {
            size shouldBe 3
            single { it.fradragstype == Fradragstype.Uføretrygd }.månedsbeløp shouldBe 1064
            single { it.fradragstype == Fradragstype.Kvalifiseringsstønad && it.tilhører == FradragTilhører.BRUKER }.månedsbeløp shouldBe 2000
            single { it.fradragstype == Fradragstype.Kvalifiseringsstønad && it.tilhører == FradragTilhører.EPS }.månedsbeløp shouldBe 3000
        }
    }

    @Test
    fun `utleder manuell regulering med årsak DifferanseFørRegulering når beløp før regulering avviker`() {
        val eksisterende = nonEmptyListOf(
            lagFradragsgrunnlag(Fradragstype.Uføretrygd, 1000.0, FradragTilhører.BRUKER),
        )

        val eksterntRegulerteBeløp = EksterntRegulerteBeløp(
            fnr = fnr,
            beløpBruker = listOf(
                RegulertBeløp(
                    fnr = fnr,
                    fradragstype = Fradragstype.Uføretrygd,
                    førRegulering = BigDecimal("900.00"),
                    etterRegulering = BigDecimal("958.00"),
                ),
            ),
            beløpEps = emptyList(),
        )

        val resultat = utledReguleringstypeOgOppdaterFradrag(
            fradrag = eksisterende,
            eksterntRegulerteBeløp = eksterntRegulerteBeløp,
            omregningsfaktor = BigDecimal("1.064076"),
        )

        val reguleringstype = resultat.first as Reguleringstype.MANUELL
        reguleringstype.problemer.size shouldBe 1
        val årsak = reguleringstype.problemer.single() as ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.DifferanseFørRegulering
        årsak.vårtBeløpFørRegulering shouldBe BigDecimal("1000.00")
        årsak.eksternNettoBeløpFørRegulering shouldBe BigDecimal("900.00")
        resultat.second.single().månedsbeløp shouldBe 1000
    }

    @Test
    fun `utleder manuell regulering med årsak DifferanseEtterRegulering når beløp etter regulering avviker for mye`() {
        val eksisterende = nonEmptyListOf(
            lagFradragsgrunnlag(Fradragstype.Uføretrygd, 1000.0, FradragTilhører.BRUKER),
        )

        val eksterntRegulerteBeløp = EksterntRegulerteBeløp(
            fnr = fnr,
            beløpBruker = listOf(
                RegulertBeløp(
                    fnr = fnr,
                    fradragstype = Fradragstype.Uføretrygd,
                    førRegulering = BigDecimal("1000.00"),
                    etterRegulering = BigDecimal("1075.00"),
                ),
            ),
            beløpEps = emptyList(),
        )

        val resultat = utledReguleringstypeOgOppdaterFradrag(
            fradrag = eksisterende,
            eksterntRegulerteBeløp = eksterntRegulerteBeløp,
            omregningsfaktor = BigDecimal("1.064076"),
        )

        val reguleringstype = resultat.first as Reguleringstype.MANUELL
        val årsak = reguleringstype.problemer.single() as ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.DifferanseEtterRegulering
        årsak.forventetBeløpEtterRegulering shouldBe BigDecimal("1064.08")
        årsak.eksternNettoBeløpEtterRegulering shouldBe BigDecimal("1075.00")
        resultat.second.single().månedsbeløp shouldBe 1000
    }

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
