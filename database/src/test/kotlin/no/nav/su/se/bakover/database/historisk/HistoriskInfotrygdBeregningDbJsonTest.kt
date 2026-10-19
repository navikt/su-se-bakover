package no.nav.su.se.bakover.database.historisk

import behandling.revurdering.domain.Opphørsgrunn
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.domain.regelspesifisering.Regelspesifiseringer
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskBosituasjon
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskStønadId
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskVedtakId
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdBeregning
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurdertMånedsresultat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.math.BigDecimal

internal class HistoriskInfotrygdBeregningDbJsonTest {
    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `lagrer regeltreet på hvert månedsresultat uten samleregel`(opphør: Boolean) {
        val beregning = beregning(opphør)
        val json = HistoriskInfotrygdBeregningDbJson.fromDomain(beregning).serialize()
        val lagret = deserialize<HistoriskInfotrygdBeregningDbJson>(json)

        lagret.månedsresultater.map { it.benyttetRegel } shouldBe
            beregning.månedsresultater.values.map { it.benyttetRegel }
        HistoriskInfotrygdBeregningDbJson.deserialize(json) shouldBe beregning
    }

    private fun beregning(opphør: Boolean) = HistoriskInfotrygdBeregning(
        månedsresultater = linkedMapOf(
            januar to resultat(januar, BigDecimal(10_000), opphør),
            februar to resultat(februar, BigDecimal(12_000), opphør),
        ),
    )

    private fun resultat(
        måned: Måned,
        sats: BigDecimal,
        opphør: Boolean,
    ): HistoriskInfotrygdRevurdertMånedsresultat {
        val månedsregel = Regelspesifiseringer.REGEL_HISTORISK_INFOTRYGD_MÅNEDSBEREGNING.benyttRegelspesifisering(
            verdi = sats.toPlainString(),
            avhengigeRegler = emptyList(),
        )
        return if (opphør) {
            HistoriskInfotrygdRevurdertMånedsresultat.Opphør(
                måned = måned,
                opprinneligStønadId = stønadId,
                opprinneligVedtakId = vedtakId,
                oppdragId = oppdragId,
                bosituasjon = HistoriskBosituasjon.ENSLIG,
                sats = sats,
                fradrag = emptyList(),
                opphørsgrunn = Opphørsgrunn.FORMUE,
                manueltOpphør = true,
                benyttetRegel = Regelspesifiseringer.REGEL_HISTORISK_INFOTRYGD_MANUELT_OPPHØR.benyttRegelspesifisering(
                    verdi = BigDecimal.ZERO.toPlainString(),
                    avhengigeRegler = listOf(månedsregel),
                ),
            )
        } else {
            HistoriskInfotrygdRevurdertMånedsresultat.Ytelse(
                måned = måned,
                opprinneligStønadId = stønadId,
                opprinneligVedtakId = vedtakId,
                oppdragId = oppdragId,
                bosituasjon = HistoriskBosituasjon.ENSLIG,
                sats = sats,
                fradrag = emptyList(),
                benyttetRegel = Regelspesifiseringer.REGEL_HISTORISK_INFOTRYGD_YTELSE.benyttRegelspesifisering(
                    verdi = sats.toPlainString(),
                    avhengigeRegler = listOf(månedsregel),
                ),
            )
        }
    }

    private companion object {
        val år = 2020
        val januar = januar(år)
        val februar = februar(år)
        val stønadId = HistoriskStønadId(1)
        val vedtakId = HistoriskVedtakId(2)
        val oppdragId = "oppdrag-1"
    }
}
