package no.nav.su.se.bakover.domain.historisk.revurdering

import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.regelspesifisering.Regelspesifisering
import no.nav.su.se.bakover.common.domain.regelspesifisering.Regelspesifiseringer
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskBosituasjon
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskStønadId
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskVedtakId
import org.junit.jupiter.api.Test
import satser.domain.historisk.HistoriskInfotrygdSatskategori
import vilkår.inntekt.domain.grunnlag.FradragForMåned
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.math.BigDecimal
import java.math.MathContext
import java.util.UUID

internal class BeregnHistoriskInfotrygdRevurderingTest {
    @Test
    fun `beregner ytelse og opphør med komplett regeltre`() {
        val januar = januar(2020)
        val februar = februar(2020)
        val gjeldende = GjeldendeHistoriskInfotrygdVedtaksdata(
            projeksjonId = UUID.randomUUID(),
            periode = Periode.create(januar.fraOgMed, februar.tilOgMed),
            tidslinje = linkedMapOf(
                januar to gammelYtelse(januar),
                februar to gammelYtelse(februar),
            ),
        )
        val fradragSomGirBeløpUnderMinstegrensen = FradragForMåned(
            fradragstype = Fradragstype.Arbeidsinntekt,
            månedsbeløp = 15_900.0,
            måned = februar,
            tilhører = FradragTilhører.BRUKER,
        )

        val beregning = gjeldende.beregnRevurdering(
            listOf(
                HistoriskInfotrygdBeregningsgrunnlagForMåned(
                    måned = januar,
                    satskategori = HistoriskInfotrygdSatskategori.EN,
                    fradrag = emptyList(),
                ),
                HistoriskInfotrygdBeregningsgrunnlagForMåned(
                    måned = februar,
                    satskategori = HistoriskInfotrygdSatskategori.EN,
                    fradrag = listOf(fradragSomGirBeløpUnderMinstegrensen),
                ),
            ),
        ).shouldBeRight()

        val forventetMånedssats = BigDecimal(191_422).divide(BigDecimal(12), MathContext.DECIMAL128)
        (beregning.månedsresultater.getValue(januar) as HistoriskInfotrygdRevurdertMånedsresultat.Ytelse)
            .sats shouldBe forventetMånedssats
        beregning.månedsresultater.getValue(februar) shouldBe
            HistoriskInfotrygdRevurdertMånedsresultat.Opphør(
                måned = februar,
                opprinneligStønadId = STØNAD_ID,
                opprinneligVedtakId = VEDTAK_ID,
                oppdragId = OPPDRAG_ID,
                bosituasjon = HistoriskBosituasjon.ENSLIG,
                sats = forventetMånedssats,
                fradrag = listOf(fradragSomGirBeløpUnderMinstegrensen),
            )

        val hovedregel = beregning.benyttetRegel as Regelspesifisering.Beregning
        hovedregel.kode shouldBe Regelspesifiseringer.REGEL_HISTORISK_INFOTRYGD_BEREGNING.kode
        hovedregel.avhengigeRegler.size shouldBe 2
        hovedregel.avhengigeRegler.forEach { månedsregel ->
            månedsregel as Regelspesifisering.Beregning
            månedsregel.kode shouldBe Regelspesifiseringer.REGEL_HISTORISK_INFOTRYGD_MÅNEDSBEREGNING.kode
            månedsregel.avhengigeRegler.size shouldBe 3
            (månedsregel.avhengigeRegler[2] as Regelspesifisering.Beregning).kode shouldBe
                Regelspesifiseringer.REGEL_HISTORISK_INFOTRYGD_MINSTEGRENSE.kode
        }
    }

    private fun gammelYtelse(måned: no.nav.su.se.bakover.common.tid.periode.Måned) =
        GjeldendeHistoriskInfotrygdMånedsdata.Ytelse(
            måned = måned,
            kilde = HistoriskInfotrygdMånedskilde.OriginalProjeksjon(UUID.randomUUID()),
            opprinneligStønadId = stønadId,
            opprinneligVedtakId = vedtakId,
            oppdragId = oppdragId,
            bosituasjon = HistoriskBosituasjon.ENSLIG,
            sats = BigDecimal(10_000),
            fradrag = BigDecimal.ZERO,
            fradragsgrunnlag = HistoriskInfotrygdFradragsgrunnlag.OriginaleKoder(emptyList()),
        )

    private companion object {
        val STØNAD_ID = HistoriskStønadId(1)
        val VEDTAK_ID = HistoriskVedtakId(2)
        const val OPPDRAG_ID = "oppdrag-1"
    }
}
