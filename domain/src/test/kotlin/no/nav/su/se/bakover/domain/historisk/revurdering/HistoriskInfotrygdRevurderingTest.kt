package no.nav.su.se.bakover.domain.historisk.revurdering

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.regelspesifisering.Regelspesifiseringer
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskBosituasjon
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskStønadId
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskVedtakId
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

internal class HistoriskInfotrygdRevurderingTest {
    @Test
    fun `kan ikke sendes til attestering med delvis beregning`() {
        val opprettet = opprettet()
        val delvisBeregnet = opprettet.oppdaterGrunnlag(
            begrunnelse = "Nye opplysninger.",
            beregning = HistoriskInfotrygdBeregning(
                månedsresultater = linkedMapOf(januar(2020) to ytelse(januar(2020))),
                benyttetRegel = Regelspesifiseringer.REGEL_HISTORISK_INFOTRYGD_BEREGNING
                    .benyttRegelspesifisering("Test"),
            ),
            saksbehandler = saksbehandler,
            tidspunkt = tidspunkt.plusUnits(1),
        ).shouldBeRight()

        delvisBeregnet.sendTilAttestering(
            saksbehandler = saksbehandler,
            tidspunkt = tidspunkt.plusUnits(2),
        ).shouldBeLeft() shouldBe
            KunneIkkeSendeHistoriskInfotrygdRevurderingTilAttestering.BeregningDekkerIkkeHelePerioden
    }

    @Test
    fun `oppdager at vedtaket for en måned har endret seg`() {
        val opprettet = opprettet()
        val nyttVedtak = HistoriskInfotrygdRevurderingsvedtakId(UUID.randomUUID())
        val endretVedtaksdata = GjeldendeHistoriskInfotrygdVedtaksdata(
            projeksjonId = projeksjonId,
            periode = periode,
            tidslinje = linkedMapOf(
                januar(2020) to gammelYtelse(januar(2020)),
                februar(2020) to GjeldendeHistoriskInfotrygdMånedsdata.IngenYtelse(
                    måned = februar(2020),
                    kilde = HistoriskInfotrygdMånedskilde.Revurderingsvedtak(nyttVedtak),
                    opprinneligStønadId = HistoriskStønadId(1),
                    opprinneligVedtakId = HistoriskVedtakId(2),
                ),
            ),
        )

        opprettet.verifiserAtVedtakeneSomRevurderesIkkeHarForandretSeg(endretVedtaksdata)
            .shouldBeLeft() shouldBe
            KunneIkkeVerifisereHistoriskeVedtakSomRevurderes.DetHarKommetNyeOverlappendeVedtak
    }

    private fun opprettet() = HistoriskInfotrygdRevurdering.opprett(
        sakId = UUID.randomUUID(),
        projeksjonId = projeksjonId,
        periode = periode,
        saksbehandler = saksbehandler,
        tidspunkt = tidspunkt,
        gjeldendeVedtaksdata = GjeldendeHistoriskInfotrygdVedtaksdata(
            projeksjonId = projeksjonId,
            periode = periode,
            tidslinje = linkedMapOf(
                januar(2020) to gammelYtelse(januar(2020)),
                februar(2020) to gammelYtelse(februar(2020)),
            ),
        ),
    ).shouldBeRight()

    private fun gammelYtelse(måned: no.nav.su.se.bakover.common.tid.periode.Måned) =
        GjeldendeHistoriskInfotrygdMånedsdata.Ytelse(
            måned = måned,
            kilde = HistoriskInfotrygdMånedskilde.OriginalProjeksjon(projeksjonId),
            opprinneligStønadId = HistoriskStønadId(1),
            opprinneligVedtakId = HistoriskVedtakId(2),
            oppdragId = "oppdrag-1",
            bosituasjon = HistoriskBosituasjon.ENSLIG,
            sats = BigDecimal(10_000),
            fradrag = BigDecimal.ZERO,
            fradragsgrunnlag = HistoriskInfotrygdFradragsgrunnlag.OriginaleKoder(emptyList()),
        )

    private fun ytelse(måned: no.nav.su.se.bakover.common.tid.periode.Måned) =
        HistoriskInfotrygdRevurdertMånedsresultat.Ytelse(
            måned = måned,
            opprinneligStønadId = HistoriskStønadId(1),
            opprinneligVedtakId = HistoriskVedtakId(2),
            oppdragId = "oppdrag-1",
            bosituasjon = HistoriskBosituasjon.ENSLIG,
            sats = BigDecimal(10_000),
            fradrag = emptyList(),
        )

    private companion object {
        val projeksjonId: UUID = UUID.randomUUID()
        val periode: Periode = Periode.create(januar(2020).fraOgMed, februar(2020).tilOgMed)
        val tidspunkt: Tidspunkt = Tidspunkt.create(Instant.parse("2020-03-01T10:00:00Z"))
        val saksbehandler = NavIdentBruker.Saksbehandler("S123456")
    }
}
