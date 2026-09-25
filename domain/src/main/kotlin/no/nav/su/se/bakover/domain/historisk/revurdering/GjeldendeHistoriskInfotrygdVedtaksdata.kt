package no.nav.su.se.bakover.domain.historisk.revurdering

import behandling.revurdering.domain.Opphørsgrunn
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskBosituasjon
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskInfotrygdYtelseForMåned
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskStønadId
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskVedtakId
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.OriginalHistoriskInfotrygdYtelsestidslinje
import vilkår.inntekt.domain.grunnlag.FradragForMåned
import java.math.BigDecimal
import java.util.UUID

@JvmInline
value class HistoriskInfotrygdRevurderingsvedtakId(val value: UUID)

/**
 * Gjeldende vedtaksdata for den separate kanalen for revurdering av Infotrygd-vedtak.
 *
 * Dataene bygges fra én fastlåst originalprojeksjon og absolutte månedsresultater fra senere iverksatte
 * historiske revurderingsvedtak. De skal ikke blandes med ordinær GjeldendeVedtaksdata.
 */
data class GjeldendeHistoriskInfotrygdVedtaksdata(
    val projeksjonId: UUID,
    val periode: Periode,
    val tidslinje: Map<Måned, GjeldendeHistoriskInfotrygdMånedsdata>,
) {
    init {
        require(periode.måneder() == tidslinje.keys.toList()) {
            "Gjeldende historisk vedtaksdata må inneholde alle månedene i perioden i stigende rekkefølge"
        }
    }

    fun forMåned(måned: Måned): GjeldendeHistoriskInfotrygdMånedsdata? = tidslinje[måned]

    companion object {
        fun bygg(
            original: OriginalHistoriskInfotrygdYtelsestidslinje,
            effekter: List<HistoriskInfotrygdRevurderingseffekt>,
        ): GjeldendeHistoriskInfotrygdVedtaksdata {
            val måneder = original.måneder.mapValuesTo(linkedMapOf()) { (_, resultat) ->
                resultat.tilGjeldende(original.projeksjonId)
            }

            effekter
                .sortedWith(compareBy({ it.iverksatt }, { it.vedtakId.value.toString() }))
                .forEach { effekt ->
                    effekt.månedsresultater.forEach { (måned, resultat) ->
                        require(måned in måneder) {
                            "Revurderingsvedtak ${effekt.vedtakId.value} har effekt utenfor originalperioden: $måned"
                        }
                        måneder[måned] = resultat.tilGjeldende(effekt.vedtakId)
                    }
                }

            return GjeldendeHistoriskInfotrygdVedtaksdata(
                projeksjonId = original.projeksjonId,
                periode = original.periode,
                tidslinje = måneder,
            )
        }
    }
}

data class HistoriskInfotrygdRevurderingseffekt(
    val vedtakId: HistoriskInfotrygdRevurderingsvedtakId,
    val iverksatt: Tidspunkt,
    val månedsresultater: Map<Måned, HistoriskInfotrygdRevurdertMånedsresultat>,
) {
    init {
        require(månedsresultater.isNotEmpty()) {
            "Et historisk revurderingsvedtak må ha effekt for minst én måned"
        }
        require(månedsresultater.keys.toList() == månedsresultater.keys.sorted()) {
            "Månedsresultatene må ligge i stigende rekkefølge"
        }
    }
}

sealed interface HistoriskInfotrygdRevurdertMånedsresultat {
    val måned: Måned
    val opprinneligStønadId: HistoriskStønadId
    val opprinneligVedtakId: HistoriskVedtakId
    val oppdragId: String?

    data class Ytelse(
        override val måned: Måned,
        override val opprinneligStønadId: HistoriskStønadId,
        override val opprinneligVedtakId: HistoriskVedtakId,
        override val oppdragId: String?,
        val bosituasjon: HistoriskBosituasjon,
        val sats: BigDecimal,
        val fradrag: List<FradragForMåned>,
        val gjeninnvilgelsesbegrunnelse: String? = null,
    ) : HistoriskInfotrygdRevurdertMånedsresultat {
        val sumFradrag: BigDecimal = fradrag.sumOf { BigDecimal.valueOf(it.månedsbeløp) }

        init {
            require(sats.signum() >= 0) { "Sats kan ikke være negativ" }
            require(fradrag.all { it.måned == måned }) {
                "Alle fradrag må tilhøre måneden som revurderes"
            }
            require(sumFradrag <= sats) { "Fradrag kan ikke være større enn sats" }
        }

        val beløp: BigDecimal = sats - sumFradrag
    }

    data class Opphør(
        override val måned: Måned,
        override val opprinneligStønadId: HistoriskStønadId,
        override val opprinneligVedtakId: HistoriskVedtakId,
        override val oppdragId: String?,
        val bosituasjon: HistoriskBosituasjon,
        val sats: BigDecimal,
        val fradrag: List<FradragForMåned>,
        val opphørsgrunn: Opphørsgrunn = Opphørsgrunn.FOR_HØY_INNTEKT,
        val begrunnelse: String? = null,
    ) : HistoriskInfotrygdRevurdertMånedsresultat {
        init {
            require(sats.signum() >= 0) { "Sats kan ikke være negativ" }
            require(fradrag.all { it.måned == måned }) {
                "Alle fradrag må tilhøre måneden som revurderes"
            }
        }
    }
}

sealed interface GjeldendeHistoriskInfotrygdMånedsdata {
    val måned: Måned
    val kilde: HistoriskInfotrygdMånedskilde

    data class Ytelse(
        override val måned: Måned,
        override val kilde: HistoriskInfotrygdMånedskilde,
        val opprinneligStønadId: HistoriskStønadId,
        val opprinneligVedtakId: HistoriskVedtakId,
        val oppdragId: String?,
        val bosituasjon: HistoriskBosituasjon?,
        val sats: BigDecimal,
        val fradrag: BigDecimal,
        val fradragsgrunnlag: HistoriskInfotrygdFradragsgrunnlag,
    ) : GjeldendeHistoriskInfotrygdMånedsdata {
        val beløp: BigDecimal = sats - fradrag
    }

    data class IngenYtelse(
        override val måned: Måned,
        override val kilde: HistoriskInfotrygdMånedskilde,
        val opprinneligStønadId: HistoriskStønadId? = null,
        val opprinneligVedtakId: HistoriskVedtakId? = null,
        val oppdragId: String? = null,
    ) : GjeldendeHistoriskInfotrygdMånedsdata
}

sealed interface HistoriskInfotrygdMånedskilde {
    data class OriginalProjeksjon(val projeksjonId: UUID) : HistoriskInfotrygdMånedskilde
    data class Revurderingsvedtak(
        val vedtakId: HistoriskInfotrygdRevurderingsvedtakId,
    ) : HistoriskInfotrygdMånedskilde
}

sealed interface HistoriskInfotrygdFradragsgrunnlag {
    data class OriginaleKoder(val koder: List<String>) : HistoriskInfotrygdFradragsgrunnlag
    data class RevurderteFradrag(val fradrag: List<FradragForMåned>) : HistoriskInfotrygdFradragsgrunnlag
}

private fun HistoriskInfotrygdYtelseForMåned.tilGjeldende(
    projeksjonId: UUID,
): GjeldendeHistoriskInfotrygdMånedsdata = when (this) {
    is HistoriskInfotrygdYtelseForMåned.Ytelse -> GjeldendeHistoriskInfotrygdMånedsdata.Ytelse(
        måned = måned,
        kilde = HistoriskInfotrygdMånedskilde.OriginalProjeksjon(projeksjonId),
        opprinneligStønadId = stønadId,
        opprinneligVedtakId = vedtakId,
        oppdragId = oppdragId,
        bosituasjon = bosituasjon,
        sats = sats,
        fradrag = fradrag,
        fradragsgrunnlag = HistoriskInfotrygdFradragsgrunnlag.OriginaleKoder(fradragskoder),
    )

    is HistoriskInfotrygdYtelseForMåned.IngenYtelse -> GjeldendeHistoriskInfotrygdMånedsdata.IngenYtelse(
        måned = måned,
        kilde = HistoriskInfotrygdMånedskilde.OriginalProjeksjon(projeksjonId),
        opprinneligStønadId = stønadId,
        opprinneligVedtakId = vedtakId,
        oppdragId = oppdragId,
    )
}

private fun HistoriskInfotrygdRevurdertMånedsresultat.tilGjeldende(
    vedtakId: HistoriskInfotrygdRevurderingsvedtakId,
): GjeldendeHistoriskInfotrygdMånedsdata = when (this) {
    is HistoriskInfotrygdRevurdertMånedsresultat.Ytelse -> GjeldendeHistoriskInfotrygdMånedsdata.Ytelse(
        måned = måned,
        kilde = HistoriskInfotrygdMånedskilde.Revurderingsvedtak(vedtakId),
        opprinneligStønadId = opprinneligStønadId,
        opprinneligVedtakId = opprinneligVedtakId,
        oppdragId = oppdragId,
        bosituasjon = bosituasjon,
        sats = sats,
        fradrag = sumFradrag,
        fradragsgrunnlag = HistoriskInfotrygdFradragsgrunnlag.RevurderteFradrag(fradrag),
    )

    is HistoriskInfotrygdRevurdertMånedsresultat.Opphør -> GjeldendeHistoriskInfotrygdMånedsdata.IngenYtelse(
        måned = måned,
        kilde = HistoriskInfotrygdMånedskilde.Revurderingsvedtak(vedtakId),
        opprinneligStønadId = opprinneligStønadId,
        opprinneligVedtakId = opprinneligVedtakId,
        oppdragId = oppdragId,
    )
}
