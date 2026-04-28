package no.nav.su.se.bakover.domain.regulering

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.test.nyÅrsakBrukerManglerSupplement
import no.nav.su.se.bakover.test.nyÅrsakDelvisOpphør
import no.nav.su.se.bakover.test.nyÅrsakDifferanseEtterRegulering
import no.nav.su.se.bakover.test.nyÅrsakDifferanseFørRegulering
import no.nav.su.se.bakover.test.nyÅrsakFantIkkeVedtakForApril
import no.nav.su.se.bakover.test.nyÅrsakFinnesFlerePerioderAvFradrag
import no.nav.su.se.bakover.test.nyÅrsakFradragErUtenlandsinntekt
import no.nav.su.se.bakover.test.nyÅrsakSupplementHarFlereVedtaksperioderForFradrag
import no.nav.su.se.bakover.test.nyÅrsakSupplementInneholderIkkeFradraget
import no.nav.su.se.bakover.test.nyÅrsakVedtakstidslinjeErIkkeSammenhengende
import no.nav.su.se.bakover.test.nyÅrsakYtelseErMidlertidigStanset
import org.junit.jupiter.api.Test
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.math.BigDecimal

class ÅrsakTilManuellReguleringTest {

    @Test
    fun `differanse i mismatch`() {
        val mistmatch = ÅrsakTilManuellRegulering.Historisk.FradragMåHåndteresManuelt.DifferanseFørRegulering(
            fradragskategori = Fradragstype.Kategori.Dagpenger,
            fradragTilhører = FradragTilhører.BRUKER,
            begrunnelse = "sed",
            eksternNettoBeløpFørRegulering = BigDecimal(100),
            eksternBruttoBeløpFørRegulering = BigDecimal(100),
            vårtBeløpFørRegulering = BigDecimal(40),
        )

        mistmatch.differanse shouldBe BigDecimal(60)
    }

    @Test
    fun `differanse i beløp er større en forventet`() {
        val forv = ÅrsakTilManuellRegulering.Historisk.FradragMåHåndteresManuelt.DifferanseEtterRegulering(
            fradragskategori = Fradragstype.Kategori.Dagpenger,
            fradragTilhører = FradragTilhører.BRUKER,
            begrunnelse = "sed",
            eksternNettoBeløpEtterRegulering = BigDecimal(100),
            eksternBruttoBeløpEtterRegulering = BigDecimal(100),
            vårtBeløpFørRegulering = BigDecimal(40),
            forventetBeløpEtterRegulering = BigDecimal(60),
        )
        forv.differanse shouldBe BigDecimal(40)
    }

    @Test
    fun `hvert av årsakene har riktig kategori`() {
        nyÅrsakBrukerManglerSupplement().kategori shouldBe ÅrsakTilManuellReguleringKategori.BrukerManglerSupplement
        nyÅrsakSupplementInneholderIkkeFradraget().kategori shouldBe ÅrsakTilManuellReguleringKategori.SupplementInneholderIkkeFradraget
        nyÅrsakFinnesFlerePerioderAvFradrag().kategori shouldBe ÅrsakTilManuellReguleringKategori.FinnesFlerePerioderAvFradrag
        nyÅrsakFradragErUtenlandsinntekt().kategori shouldBe ÅrsakTilManuellReguleringKategori.FradragErUtenlandsinntekt
        nyÅrsakSupplementHarFlereVedtaksperioderForFradrag().kategori shouldBe ÅrsakTilManuellReguleringKategori.SupplementHarFlereVedtaksperioderForFradrag
        nyÅrsakDifferanseFørRegulering().kategori shouldBe ÅrsakTilManuellReguleringKategori.DifferanseFørRegulering
        nyÅrsakDifferanseEtterRegulering().kategori shouldBe ÅrsakTilManuellReguleringKategori.DifferanseEtterRegulering
        nyÅrsakFantIkkeVedtakForApril().kategori shouldBe ÅrsakTilManuellReguleringKategori.FantIkkeVedtakForApril
        nyÅrsakYtelseErMidlertidigStanset().kategori shouldBe ÅrsakTilManuellReguleringKategori.YtelseErMidlertidigStanset
        nyÅrsakVedtakstidslinjeErIkkeSammenhengende().kategori shouldBe ÅrsakTilManuellReguleringKategori.VedtakstidslinjeErIkkeSammenhengende
        nyÅrsakDelvisOpphør().kategori shouldBe ÅrsakTilManuellReguleringKategori.DelvisOpphør

        ÅrsakTilManuellRegulering.Historisk.ForventetInntektErStørreEnn0().kategori shouldBe ÅrsakTilManuellReguleringKategori.ForventetInntektErStørreEnn0
        ÅrsakTilManuellRegulering.Historisk.YtelseErMidlertidigStanset.kategori shouldBe ÅrsakTilManuellReguleringKategori.YtelseErMidlertidigStanset
        ÅrsakTilManuellRegulering.Historisk.UtbetalingFeilet.kategori shouldBe ÅrsakTilManuellReguleringKategori.UtbetalingFeilet
    }
}
