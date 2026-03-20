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
                    fradragstype = Fradragstype.Uføretrygd,
                    førRegulering = 1000,
                    etterRegulering = 1064,
                ),
            ),
            beløpEps = listOf(
                RegulertBeløp(
                    fradragstype = Fradragstype.Alderspensjon,
                    førRegulering = 2000,
                    etterRegulering = 2128,
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
                    fradragstype = Fradragstype.Uføretrygd,
                    førRegulering = 1000,
                    etterRegulering = 1064,
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
                    fradragstype = Fradragstype.Uføretrygd,
                    førRegulering = 1000,
                    etterRegulering = 1064,
                ),
                RegulertBeløp(
                    fradragstype = Fradragstype.Arbeidsavklaringspenger,
                    førRegulering = 2000,
                    etterRegulering = 2128,
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
    fun `utleder alle fradrag når det finnes for både bruker og eps samt har fradragstyper på er like på tvers`() {
        val eksisterende = nonEmptyListOf(
            lagFradragsgrunnlag(Fradragstype.Uføretrygd, 1000.0, FradragTilhører.BRUKER),
            lagFradragsgrunnlag(Fradragstype.OffentligPensjon, 4000.0, FradragTilhører.BRUKER),
            lagFradragsgrunnlag(Fradragstype.Alderspensjon, 2000.0, FradragTilhører.EPS),
            lagFradragsgrunnlag(Fradragstype.SupplerendeStønad, 5000.0, FradragTilhører.EPS),
            lagFradragsgrunnlag(Fradragstype.Kvalifiseringsstønad, 3000.0, FradragTilhører.BRUKER),
            lagFradragsgrunnlag(Fradragstype.Kvalifiseringsstønad, 3001.0, FradragTilhører.EPS),
        )

        val eksterntRegulerteBeløp = EksterntRegulerteBeløp(
            fnr = fnr,
            beløpBruker = listOf(
                RegulertBeløp(
                    fradragstype = Fradragstype.Uføretrygd,
                    førRegulering = 1000,
                    etterRegulering = 1064,
                ),
            ),
            beløpEps = listOf(
                RegulertBeløp(
                    fradragstype = Fradragstype.Alderspensjon,
                    førRegulering = 2000,
                    etterRegulering = 2128,
                ),
            ),
        )

        val resultat = utledReguleringstypeOgOppdaterFradrag(
            fradrag = eksisterende,
            eksterntRegulerteBeløp = eksterntRegulerteBeløp,
            omregningsfaktor = BigDecimal("1.064076"),
        )

        resultat.first shouldNotBe Reguleringstype.AUTOMATISK
        with(resultat.second) {
            size shouldBe 6
            single { it.fradragstype == Fradragstype.Uføretrygd }.månedsbeløp shouldBe 1064
            single { it.fradragstype == Fradragstype.Alderspensjon }.månedsbeløp shouldBe 2128
            single { it.fradragstype == Fradragstype.OffentligPensjon }.månedsbeløp shouldBe 4000
            single { it.fradragstype == Fradragstype.SupplerendeStønad }.månedsbeløp shouldBe 5000
            single { it.fradragstype == Fradragstype.Kvalifiseringsstønad && it.tilhører == FradragTilhører.BRUKER }.månedsbeløp shouldBe 3000
            single { it.fradragstype == Fradragstype.Kvalifiseringsstønad && it.tilhører == FradragTilhører.EPS }.månedsbeløp shouldBe 3001
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
                    fradragstype = Fradragstype.Uføretrygd,
                    førRegulering = 1000,
                    etterRegulering = 1064,
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
                    fradragstype = Fradragstype.Uføretrygd,
                    førRegulering = 1000,
                    etterRegulering = 1064,
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

        val problemBruker = reguleringstype.problemer.filterIsInstance<ÅrsakTilManuellRegulering.ManglerRegulertBeløpForFradrag>()
            .single { it.fradragTilhører == FradragTilhører.BRUKER }
        problemBruker.fradragskategori shouldBe Fradragstype.Kvalifiseringsstønad.kategori

        val problemEps = reguleringstype.problemer.filterIsInstance<ÅrsakTilManuellRegulering.ManglerRegulertBeløpForFradrag>()
            .single { it.fradragTilhører == FradragTilhører.EPS }
        problemEps.fradragskategori shouldBe Fradragstype.Kvalifiseringsstønad.kategori

        // Fradragsgrunnlag skal være delvis oppdatert - kun Uføretrygd
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
                    fradragstype = Fradragstype.Uføretrygd,
                    førRegulering = 900, // Avviker fra vårt beløp (1000)
                    etterRegulering = 958,
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
        årsak.fradragskategori shouldBe Fradragstype.Uføretrygd.kategori
        årsak.fradragTilhører shouldBe FradragTilhører.BRUKER
        årsak.vårtBeløpFørRegulering shouldBe BigDecimal("1000.00")
        årsak.eksternNettoBeløpFørRegulering shouldBe BigDecimal("900")

        // Fradragsgrunnlag skal ikke være oppdatert ved manuell regulering
        with(resultat.second) {
            size shouldBe 1
            single().månedsbeløp shouldBe 1000
        }
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
                    fradragstype = Fradragstype.Uføretrygd,
                    førRegulering = 1000, // Matcher vårt beløp
                    etterRegulering = 1075, // Avviker for mye fra forventet (1064.08, differanse > 10)
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
        val årsak = reguleringstype.problemer.single() as ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.DifferanseEtterRegulering
        årsak.fradragskategori shouldBe Fradragstype.Uføretrygd.kategori
        årsak.fradragTilhører shouldBe FradragTilhører.BRUKER
        årsak.vårtBeløpFørRegulering shouldBe BigDecimal("1000.00")
        årsak.forventetBeløpEtterRegulering shouldBe BigDecimal("1064.08")
        årsak.eksternNettoBeløpEtterRegulering shouldBe BigDecimal("1075")

        // Fradragsgrunnlag skal ikke være oppdatert ved manuell regulering
        with(resultat.second) {
            size shouldBe 1
            single().månedsbeløp shouldBe 1000
        }
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
