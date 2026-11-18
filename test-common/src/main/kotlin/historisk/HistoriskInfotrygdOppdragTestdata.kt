package no.nav.su.se.bakover.test.historisk

import behandling.revurdering.domain.Opphørsgrunn
import no.nav.su.se.bakover.common.domain.regelspesifisering.Regelspesifiseringer
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskBosituasjon
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskInfotrygdYtelseForMåned
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskStønadId
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskVedtakId
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.OriginalHistoriskInfotrygdYtelsestidslinje
import no.nav.su.se.bakover.domain.historisk.revurdering.GjeldendeHistoriskInfotrygdMånedsdata
import no.nav.su.se.bakover.domain.historisk.revurdering.GjeldendeHistoriskInfotrygdVedtaksdata
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdBeregning
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdFradragsgrunnlag
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdMånedskilde
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurdering
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurderingsbosituasjon
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurdertMånedsresultat
import no.nav.su.se.bakover.test.getOrFail
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

object HistoriskInfotrygdOppdragTestdata {
    private val år = 2018
    val januar = januar(år)
    val februar = februar(år)
    val periode: Periode = januar..februar
    val projeksjonId: UUID = UUID.randomUUID()
    val sakId: UUID = UUID.randomUUID()
    val stønadId = HistoriskStønadId(1)
    val vedtakId = HistoriskVedtakId(2)
    val gammeltBeløp = BigDecimal(10_000)
    val nyttBeløp = BigDecimal(12_000)
    val tidspunkt: Tidspunkt = Tidspunkt.create(Instant.parse("2026-11-13T10:00:00Z"))
    val saksbehandler = NavIdentBruker.Saksbehandler("S123456")

    fun original() = OriginalHistoriskInfotrygdYtelsestidslinje(
        projeksjonId = projeksjonId,
        periode = periode,
        måneder = periode.måneder().associateWith { måned ->
            HistoriskInfotrygdYtelseForMåned.Ytelse(
                måned = måned,
                stønadId = stønadId,
                vedtakId = vedtakId,
                oppdragId = null,
                bosituasjon = HistoriskBosituasjon.ENSLIG,
                sats = gammeltBeløp,
                fradrag = BigDecimal.ZERO,
                fradragskoder = emptyList(),
            )
        },
    )

    fun gjeldende(
        kilde: HistoriskInfotrygdMånedskilde = HistoriskInfotrygdMånedskilde.OriginalProjeksjon(projeksjonId),
        periode: Periode = HistoriskInfotrygdOppdragTestdata.periode,
    ) = GjeldendeHistoriskInfotrygdVedtaksdata(
        projeksjonId = projeksjonId,
        periode = periode,
        tidslinje = periode.måneder().associateWith { måned ->
            GjeldendeHistoriskInfotrygdMånedsdata.Ytelse(
                måned = måned,
                kilde = kilde,
                opprinneligStønadId = stønadId,
                opprinneligVedtakId = vedtakId,
                oppdragId = null,
                bosituasjon = HistoriskInfotrygdRevurderingsbosituasjon.ENSLIG,
                sats = gammeltBeløp,
                fradrag = BigDecimal.ZERO,
                fradragsgrunnlag = HistoriskInfotrygdFradragsgrunnlag.OriginaleKoder(emptyList()),
            )
        },
    )

    fun beregning(
        opphør: Boolean = false,
        beløp: BigDecimal = nyttBeløp,
        periode: Periode = HistoriskInfotrygdOppdragTestdata.periode,
    ) = HistoriskInfotrygdBeregning(
        månedsresultater = periode.måneder().associateWith { måned ->
            if (opphør) {
                HistoriskInfotrygdRevurdertMånedsresultat.Opphør(
                    måned = måned,
                    opprinneligStønadId = stønadId,
                    opprinneligVedtakId = vedtakId,
                    oppdragId = null,
                    bosituasjon = HistoriskInfotrygdRevurderingsbosituasjon.ENSLIG,
                    sats = beløp,
                    fradrag = emptyList(),
                    opphørsgrunn = Opphørsgrunn.MANGLENDE_DOKUMENTASJON,
                    manueltOpphør = true,
                    benyttetRegel = Regelspesifiseringer.REGEL_HISTORISK_INFOTRYGD_MANUELT_OPPHØR
                        .benyttRegelspesifisering("Test"),
                )
            } else {
                HistoriskInfotrygdRevurdertMånedsresultat.Ytelse(
                    måned = måned,
                    opprinneligStønadId = stønadId,
                    opprinneligVedtakId = vedtakId,
                    oppdragId = null,
                    bosituasjon = HistoriskInfotrygdRevurderingsbosituasjon.ENSLIG,
                    sats = beløp,
                    fradrag = emptyList(),
                    benyttetRegel = Regelspesifiseringer.REGEL_HISTORISK_INFOTRYGD_YTELSE
                        .benyttRegelspesifisering("Test"),
                )
            }
        },
    )

    fun revurdering(
        gjeldende: GjeldendeHistoriskInfotrygdVedtaksdata = gjeldende(),
        beregning: HistoriskInfotrygdBeregning = beregning(periode = gjeldende.periode),
    ): HistoriskInfotrygdRevurdering = HistoriskInfotrygdRevurdering.opprett(
        sakId = sakId,
        projeksjonId = projeksjonId,
        periode = gjeldende.periode,
        saksbehandler = saksbehandler,
        tidspunkt = tidspunkt,
        gjeldendeVedtaksdata = gjeldende,
    ).getOrFail()
        .oppdaterGrunnlag(beregning, saksbehandler, tidspunkt)
        .getOrFail()
}
