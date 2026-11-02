package no.nav.su.se.bakover.domain.historisk.revurdering.brev

import behandling.revurdering.domain.Opphørsgrunn
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.regelspesifisering.Regelspesifiseringer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.domain.brev.beregning.toMånedsfradragPerType
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskStønadId
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskVedtakId
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdBeregning
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurdering
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurderingId
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurderingStatus
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurderingsbosituasjon
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurdertMånedsresultat
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdVedtaksbrevvalg
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskVedtakSomRevurderes
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskeVedtakSomRevurderesMånedsvis
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.sakinfo
import org.junit.jupiter.api.Test
import satser.domain.supplerendestønad.SatsFactoryForSupplerendeStønad
import vilkår.inntekt.domain.grunnlag.FradragForMåned
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

internal class HistoriskInfotrygdRevurderingBrevTest {
    @Test
    fun `brev viser samme fribeloep og anvendte EPS-fradrag som beregningen`() {
        val ordinærSats = sats.toInt()
        val inntektUnderFribeløp = 15_000.0
        val lavInntekt = 1_000
        val sosialstønad = 100
        val tilfeller = listOf(
            Tilfelle(HistoriskInfotrygdRevurderingsbosituasjon.EPS_OVER_67, 16_000.0, 0.0, 841, ordinærSats, true, false),
            Tilfelle(HistoriskInfotrygdRevurderingsbosituasjon.EPS_OVER_67, inntektUnderFribeløp, 0.0, 0, ordinærSats, false, true),
            Tilfelle(HistoriskInfotrygdRevurderingsbosituasjon.EPS_OVER_67, ordinærSats.toDouble(), 0.0, 0, ordinærSats, false, true),
            Tilfelle(
                HistoriskInfotrygdRevurderingsbosituasjon.EPS_OVER_67,
                inntektUnderFribeløp,
                sosialstønad.toDouble(),
                sosialstønad,
                ordinærSats,
                true,
                false,
            ),
            Tilfelle(HistoriskInfotrygdRevurderingsbosituasjon.EPS_UNDER_67, lavInntekt.toDouble(), 0.0, lavInntekt, 0, true, false),
            Tilfelle(HistoriskInfotrygdRevurderingsbosituasjon.ENSLIG, lavInntekt.toDouble(), 0.0, 0, 0, false, false),
            Tilfelle(HistoriskInfotrygdRevurderingsbosituasjon.ENSLIG_MED_BOFELLESSKAP, lavInntekt.toDouble(), 0.0, 0, 0, false, false),
        )
        tilfeller.forEach { tilfelle ->
            val epsFradrag = buildList {
                add(fradrag(Fradragstype.Arbeidsinntekt, tilfelle.inntekt))
                if (tilfelle.sosialstønad != 0.0) add(fradrag(Fradragstype.Sosialstønad, tilfelle.sosialstønad))
            }
            val ytelse = HistoriskInfotrygdRevurdertMånedsresultat.Ytelse(
                måned = måned2020,
                opprinneligStønadId = stønadId,
                opprinneligVedtakId = vedtakId,
                oppdragId = "oppdrag-1",
                bosituasjon = tilfelle.bosituasjon,
                sats = sats,
                fradrag = epsFradrag,
                benyttetRegel = Regelspesifiseringer.REGEL_HISTORISK_INFOTRYGD_YTELSE.benyttRegelspesifisering("Test"),
            )
            ytelse.sumFradrag.compareTo(BigDecimal(tilfelle.forventetFradrag)) shouldBe 0
            val opphør = HistoriskInfotrygdRevurdertMånedsresultat.Opphør(
                måned = måned2020,
                opprinneligStønadId = stønadId,
                opprinneligVedtakId = vedtakId,
                oppdragId = ytelse.oppdragId,
                bosituasjon = tilfelle.bosituasjon,
                sats = sats,
                fradrag = epsFradrag,
                opphørsgrunn = Opphørsgrunn.FORMUE,
                manueltOpphør = true,
                benyttetRegel = Regelspesifiseringer.REGEL_HISTORISK_INFOTRYGD_MANUELT_OPPHØR.benyttRegelspesifisering("Test"),
            )
            listOf(ytelse, opphør).forEach { resultat ->
                val brev = revurdering(resultat).lagVedtaksbrevkommando(sak).shouldBeRight()
                val periode = brev.beregningsperioder.single()
                periode.epsFribeløp shouldBe tilfelle.fribeløp
                periode.ytelsePerMåned shouldBe if (resultat is HistoriskInfotrygdRevurdertMånedsresultat.Ytelse) {
                    sats.toInt() - tilfelle.forventetFradrag
                } else {
                    0
                }
                periode.fradrag.eps.fradrag shouldBe if (tilfelle.visFradrag) {
                    epsFradrag.toMånedsfradragPerType()
                } else {
                    emptyList()
                }
                periode.fradrag.eps.harFradragMedSumSomErLavereEnnFribeløp shouldBe tilfelle.underFribeløp
            }
        }
    }

    @Test
    fun `brev viser bosituasjon og separate ordinære satser for EPS ufør flyktning`() {
        val måned2021 = januar(2021)
        val satser = SatsFactoryForSupplerendeStønad().gjeldende(måned2021.fraOgMed)
        val ordinærAlderssats = satser.ordinærAlder(måned2021).satsForMåned
        val ordinærUføresats = satser.ordinærUføre(måned2021).satsForMåned
        val epsFradrag = listOf(
            fradrag(Fradragstype.Arbeidsinntekt, ordinærUføresats.toDouble() + 75.0, måned2021),
            fradrag(Fradragstype.Sosialstønad, 30.0, måned2021),
        )
        val resultat = HistoriskInfotrygdRevurdertMånedsresultat.Ytelse(
            måned = måned2021,
            opprinneligStønadId = stønadId,
            opprinneligVedtakId = vedtakId,
            oppdragId = "oppdrag-1",
            bosituasjon = HistoriskInfotrygdRevurderingsbosituasjon.EPS_UNDER_67_UFØR_FLYKTNING,
            sats = ordinærAlderssats,
            epsFribeløp = ordinærUføresats,
            fradrag = epsFradrag,
            benyttetRegel = Regelspesifiseringer.REGEL_HISTORISK_INFOTRYGD_YTELSE.benyttRegelspesifisering("Test"),
        )

        val brev = revurdering(resultat, måned2021).lagVedtaksbrevkommando(sak).shouldBeRight()
        val periode = brev.beregningsperioder.single()

        periode.sats shouldBe "ektefelle under 67 år som er ufør flyktning"
        periode.satsbeløpPerMåned shouldBe ordinærAlderssats.setScale(0, RoundingMode.HALF_UP).toInt()
        periode.epsFribeløp shouldBe ordinærUføresats.setScale(0, RoundingMode.HALF_UP).toInt()
        val forventetYtelse = (ordinærAlderssats - BigDecimal(105)).setScale(0, RoundingMode.HALF_UP).toInt()
        periode.ytelsePerMåned shouldBe forventetYtelse
        periode.fradrag.eps.fradrag shouldBe epsFradrag.toMånedsfradragPerType()
        periode.fradrag.eps.harFradragMedSumSomErLavereEnnFribeløp shouldBe false
    }

    private fun fradrag(
        type: Fradragstype,
        beløp: Double,
        måned: Måned = måned2020,
    ) = FradragForMåned(
        fradragstype = type,
        månedsbeløp = beløp,
        måned = måned,
        tilhører = FradragTilhører.EPS,
    )

    private fun revurdering(
        resultat: HistoriskInfotrygdRevurdertMånedsresultat,
        måned: Måned = måned2020,
    ) = HistoriskInfotrygdRevurdering(
        id = HistoriskInfotrygdRevurderingId.generer(),
        sakId = sak.sakId,
        projeksjonId = UUID.randomUUID(),
        periode = måned,
        status = HistoriskInfotrygdRevurderingStatus.BEREGNET,
        saksbehandler = NavIdentBruker.Saksbehandler("S123456"),
        opprettet = fixedTidspunkt,
        oppdatert = fixedTidspunkt,
        avslutningsbegrunnelse = null,
        vedtaksbrevvalg = HistoriskInfotrygdVedtaksbrevvalg.SEND,
        vedtaksbrevFritekst = "Vedtaksbrev",
        vedtakSomRevurderesMånedsvis = HistoriskeVedtakSomRevurderesMånedsvis(
            mapOf(måned to HistoriskVedtakSomRevurderes.OriginaltInfotrygdVedtak(vedtakId)),
        ),
        beregning = HistoriskInfotrygdBeregning(
            månedsresultater = mapOf(måned to resultat),
        ),
        attesteringer = emptyList(),
    )

    private data class Tilfelle(
        val bosituasjon: HistoriskInfotrygdRevurderingsbosituasjon,
        val inntekt: Double,
        val sosialstønad: Double,
        val forventetFradrag: Int,
        val fribeløp: Int,
        val visFradrag: Boolean,
        val underFribeløp: Boolean,
    )

    private companion object {
        val måned2020 = januar(2020)
        val sats = BigDecimal(15_159)
        val stønadId = HistoriskStønadId(1)
        val vedtakId = HistoriskVedtakId(2)
        val sak = sakinfo.copy(type = Sakstype.ALDER)
    }
}
