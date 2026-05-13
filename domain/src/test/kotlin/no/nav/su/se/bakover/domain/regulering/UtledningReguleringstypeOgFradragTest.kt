package no.nav.su.se.bakover.domain.regulering

import arrow.core.nonEmptyListOf
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.august
import no.nav.su.se.bakover.common.tid.periode.desember
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.common.tid.periode.september
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.getOrFail
import org.junit.jupiter.api.Test
import vilkår.inntekt.domain.grunnlag.FradragForPeriode
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragsgrunnlag
import vilkår.inntekt.domain.grunnlag.Fradragstype
import vilkår.inntekt.domain.grunnlag.UtenlandskInntekt
import java.math.BigDecimal

class UtledningReguleringstypeOgFradragTest {

    @Test
    fun `oppdaterer fradrag som har ekstern regulering tilgjengelig`() {
        val eksisterende = nonEmptyListOf(
            lagFradragsgrunnlag(Fradragstype.Uføretrygd, 1000.0, FradragTilhører.BRUKER),
            lagFradragsgrunnlag(Fradragstype.Alderspensjon, 2000.0, FradragTilhører.EPS),
        )

        val eksterntRegulerteBeløp = EksterntRegulerteBeløp(
            brukerFnr = fnr,
            beløpBruker = listOf(
                RegulertBeløp(
                    fnr = fnr,
                    fradragstype = EksterntBeløpSomFradragstype.Uføretrygd,
                    førRegulering = BigDecimal(1000),
                    etterRegulering = BigDecimal(1064),
                ),
            ),
            beløpEps = listOf(
                RegulertBeløp(
                    fnr = fnr,
                    fradragstype = EksterntBeløpSomFradragstype.Alderspensjon,
                    førRegulering = BigDecimal(2000),
                    etterRegulering = BigDecimal(2128),
                ),
            ),
        )

        val resultat = utledReguleringstypeOgOppdaterFradrag(
            fradrag = eksisterende,
            eksterntRegulerteBeløp = eksterntRegulerteBeløp,
        ).getOrFail()

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
            brukerFnr = fnr,
            beløpBruker = listOf(
                RegulertBeløp(
                    fnr = fnr,
                    fradragstype = EksterntBeløpSomFradragstype.Uføretrygd,
                    førRegulering = BigDecimal(1000),
                    etterRegulering = BigDecimal(1064),
                ),
            ),
            beløpEps = emptyList(),
        )

        val resultat = utledReguleringstypeOgOppdaterFradrag(
            fradrag = eksisterende,
            eksterntRegulerteBeløp = eksterntRegulerteBeløp,
        ).getOrFail()

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
            brukerFnr = fnr,
            beløpBruker = listOf(
                RegulertBeløp(
                    fnr = fnr,
                    fradragstype = EksterntBeløpSomFradragstype.Uføretrygd,
                    førRegulering = BigDecimal(1000),
                    etterRegulering = BigDecimal(1064),
                ),
                RegulertBeløp(
                    fnr = fnr,
                    fradragstype = EksterntBeløpSomFradragstype.Arbeidsavklaringspenger,
                    førRegulering = BigDecimal(2000),
                    etterRegulering = BigDecimal(2128),
                ),
            ),
            beløpEps = emptyList(),
        )

        val resultat = utledReguleringstypeOgOppdaterFradrag(
            fradrag = eksisterende,
            eksterntRegulerteBeløp = eksterntRegulerteBeløp,
        ).getOrFail()

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
            brukerFnr = fnr,
            beløpBruker = listOf(
                RegulertBeløp(
                    fnr = fnr,
                    fradragstype = EksterntBeløpSomFradragstype.Uføretrygd,
                    førRegulering = BigDecimal(1000),
                    etterRegulering = BigDecimal(1064),
                ),
            ),
            beløpEps = listOf(
                RegulertBeløp(
                    fnr = fnr,
                    fradragstype = EksterntBeløpSomFradragstype.Alderspensjon,
                    førRegulering = BigDecimal(2000),
                    etterRegulering = BigDecimal(2128),
                ),
            ),
        )

        val resultat = utledReguleringstypeOgOppdaterFradrag(
            fradrag = eksisterende,
            eksterntRegulerteBeløp = eksterntRegulerteBeløp,
        ).getOrFail()

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
            brukerFnr = fnr,
            beløpBruker = listOf(
                RegulertBeløp(
                    fnr = fnr,
                    fradragstype = EksterntBeløpSomFradragstype.Uføretrygd,
                    førRegulering = BigDecimal(1000),
                    etterRegulering = BigDecimal(1064),
                ),
            ),
            beløpEps = emptyList(),
        )

        val resultat = utledReguleringstypeOgOppdaterFradrag(
            fradrag = eksisterende,
            eksterntRegulerteBeløp = eksterntRegulerteBeløp,
        ).getOrFail()

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
            brukerFnr = fnr,
            beløpBruker = listOf(
                RegulertBeløp(
                    fnr = fnr,
                    fradragstype = EksterntBeløpSomFradragstype.Uføretrygd,
                    førRegulering = BigDecimal(1000),
                    etterRegulering = BigDecimal(1064),
                ),
            ),
            beløpEps = emptyList(),
        )

        val resultat = utledReguleringstypeOgOppdaterFradrag(
            fradrag = eksisterende,
            eksterntRegulerteBeløp = eksterntRegulerteBeløp,
        ).getOrFail()

        val reguleringstype = resultat.first as Reguleringstype.MANUELL
        reguleringstype.problemer.size shouldBe 2

        val problemBruker =
            reguleringstype.problemer.filterIsInstance<ÅrsakTilManuellRegulering.ManglerRegulertBeløpForFradrag>()
                .single { it.fradragTilhører == FradragTilhører.BRUKER }
        problemBruker.fradragskategori shouldBe Fradragstype.Kvalifiseringsstønad.kategori

        val problemEps =
            reguleringstype.problemer.filterIsInstance<ÅrsakTilManuellRegulering.ManglerRegulertBeløpForFradrag>()
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
            brukerFnr = fnr,
            beløpBruker = listOf(
                RegulertBeløp(
                    fnr = fnr,
                    fradragstype = EksterntBeløpSomFradragstype.Uføretrygd,
                    førRegulering = BigDecimal("900.00"), // Avviker fra vårt beløp (1000)
                    etterRegulering = BigDecimal("958.00"),
                ),
            ),
            beløpEps = emptyList(),
        )

        val resultat = utledReguleringstypeOgOppdaterFradrag(
            fradrag = eksisterende,
            eksterntRegulerteBeløp = eksterntRegulerteBeløp,
        ).shouldBeLeft()

        resultat.årsak shouldBe ÅrsakRevurdering.Årsak.DIFFERANSE_MED_EKSTERNE_BELØP
        resultat.diffBeløp.size shouldBe 1
        with(resultat.diffBeløp.first() as ÅrsakRevurdering.BeløperMedDiff.Fradrag) {
            fradragstype shouldBe Fradragstype.Uføretrygd
            tilhører shouldBe FradragTilhører.BRUKER
            eksisterendeBeløp shouldBe BigDecimal("1000.00")
            nyttBeløp shouldBe BigDecimal("900.00")
        }
    }

    @Test
    fun `utleder automatisk regulering når fradragsgrunnlag ikke har grunnbeløp`() {
        val eksisterende = nonEmptyListOf(
            lagFradragsgrunnlag(Fradragstype.Kapitalinntekt, 1000.0, FradragTilhører.BRUKER),
        )

        val eksterntRegulerteBeløp = EksterntRegulerteBeløp(
            brukerFnr = fnr,
            beløpBruker = emptyList(),
            beløpEps = emptyList(),
        )

        val resultat = utledReguleringstypeOgOppdaterFradrag(
            fradrag = eksisterende,
            eksterntRegulerteBeløp = eksterntRegulerteBeløp,
        ).getOrFail()

        resultat.first shouldBe Reguleringstype.AUTOMATISK
        with(resultat.second) {
            size shouldBe 1
            single { it.fradragstype == Fradragstype.Kapitalinntekt }.månedsbeløp shouldBe 1000.0
        }
    }

    @Test
    fun `utleder automatisk regulering når fradragsgrunnlag har utenlandskInntekt`() {
        val eksisterende = nonEmptyListOf(
            Fradragsgrunnlag.create(
                opprettet = Tidspunkt.now(fixedClock),
                fradrag = FradragForPeriode(
                    fradragstype = Fradragstype.Alderspensjon,
                    månedsbeløp = 2000.0,
                    periode = mai(2026)..desember(2026),
                    utenlandskInntekt = UtenlandskInntekt.create(
                        beløpIUtenlandskValuta = 500,
                        valuta = "EUR",
                        kurs = 11.0,
                    ),
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
        )

        val eksterntRegulerteBeløp = EksterntRegulerteBeløp(
            brukerFnr = fnr,
            beløpBruker = emptyList(),
            beløpEps = emptyList(),
        )

        val resultat = utledReguleringstypeOgOppdaterFradrag(
            fradrag = eksisterende,
            eksterntRegulerteBeløp = eksterntRegulerteBeløp,
        ).getOrFail()

        resultat.first shouldBe Reguleringstype.AUTOMATISK
        with(resultat.second) {
            size shouldBe 1
            single { it.fradragstype == Fradragstype.Alderspensjon }.månedsbeløp shouldBe 2000.0
        }
    }

    @Test
    fun `utleder manuell regulering når samme fradragstype har ulike beløp i forskjellige perioder`() {
        val eksisterende = nonEmptyListOf(
            lagFradragsgrunnlag(Fradragstype.Uføretrygd, 1000.0, FradragTilhører.BRUKER, mai(2026)..august(2026)),
            lagFradragsgrunnlag(Fradragstype.Uføretrygd, 1500.0, FradragTilhører.BRUKER, september(2026)..desember(2026)),
        )

        val eksterntRegulerteBeløp = EksterntRegulerteBeløp(
            brukerFnr = fnr,
            beløpBruker = listOf(
                RegulertBeløp(
                    fnr = fnr,
                    fradragstype = EksterntBeløpSomFradragstype.Uføretrygd,
                    førRegulering = BigDecimal(1000),
                    etterRegulering = BigDecimal(1064),
                ),
            ),
            beløpEps = emptyList(),
        )

        val resultat = utledReguleringstypeOgOppdaterFradrag(
            fradrag = eksisterende,
            eksterntRegulerteBeløp = eksterntRegulerteBeløp,
        ).getOrFail()

        resultat.first shouldNotBe Reguleringstype.AUTOMATISK
        val reguleringstype = resultat.first as Reguleringstype.MANUELL
        reguleringstype.problemer.any {
            it is ÅrsakTilManuellRegulering.EtAutomatiskFradragHarFremtidigPeriode
        } shouldBe true
    }

    companion object {

        fun lagFradragsgrunnlag(
            fradragstypeBruker: Fradragstype,
            månedsbeløp: Double = 1000.0,
            tilhører: FradragTilhører,
            periode: Periode = mai(2026)..desember(2026),
        ) = Fradragsgrunnlag.create(
            opprettet = Tidspunkt.now(fixedClock),
            fradrag = FradragForPeriode(
                fradragstype = fradragstypeBruker,
                månedsbeløp = månedsbeløp,
                periode = periode,
                utenlandskInntekt = null,
                tilhører = tilhører,
            ),
        )
    }
}
