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
            beregning = HistoriskInfotrygdBeregning(
                månedsresultater = linkedMapOf(januar to ytelse(januar)),
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
    fun `beregning og attestering trenger ingen bekreftelse av historisk forsørgingstillegg`() {
        val beregning = HistoriskInfotrygdBeregning(
            månedsresultater = linkedMapOf(
                januar to ytelse(januar),
                februar to ytelse(februar),
            ),
        )
        val beregnet = opprettet().oppdaterGrunnlag(
            beregning = beregning,
            saksbehandler = saksbehandler,
            tidspunkt = tidspunkt.plusUnits(1),
        ).shouldBeRight()
        beregnet.beregning shouldBe beregning
        val medBrevvalg = beregnet.oppdaterVedtaksbrev(
            valg = HistoriskInfotrygdVedtaksbrevvalg.IKKE_SEND,
            fritekst = null,
            saksbehandler = saksbehandler,
            tidspunkt = tidspunkt.plusUnits(2),
        ).shouldBeRight()
        val forhåndsvarselVurdert = tidspunkt.plusUnits(3)
        val klarTilAttestering = medBrevvalg.velgÅIkkeSendeForhåndsvarsel(
            saksbehandler = saksbehandler,
            tidspunkt = forhåndsvarselVurdert,
        ).shouldBeRight()
        klarTilAttestering.forhåndsvarsel shouldBe HistoriskInfotrygdForhåndsvarsel.IkkeSendt(
            vurdertAv = saksbehandler,
            vurdert = forhåndsvarselVurdert,
            utdatert = false,
        )

        klarTilAttestering.sendTilAttestering(
            saksbehandler = saksbehandler,
            tidspunkt = tidspunkt.plusUnits(4),
        ).shouldBeRight().status shouldBe HistoriskInfotrygdRevurderingStatus.TIL_ATTESTERING
    }

    @Test
    fun `kan ikke velge bort forhåndsvarsel før beregning`() {
        opprettet().velgÅIkkeSendeForhåndsvarsel(
            saksbehandler = saksbehandler,
            tidspunkt = tidspunkt,
        ).shouldBeLeft() shouldBe KunneIkkeOppdatereHistoriskInfotrygdForhåndsvarsel.ManglerBeregning
    }

    @Test
    fun `oppdager at vedtaket for en måned har endret seg`() {
        val opprettet = opprettet()
        val nyttVedtak = HistoriskInfotrygdRevurderingsvedtakId(UUID.randomUUID())
        val endretVedtaksdata = GjeldendeHistoriskInfotrygdVedtaksdata(
            projeksjonId = projeksjonId,
            periode = periode,
            tidslinje = linkedMapOf(
                januar to gammelYtelse(januar),
                februar to GjeldendeHistoriskInfotrygdMånedsdata.IngenYtelse(
                    måned = februar,
                    kilde = HistoriskInfotrygdMånedskilde.Revurderingsvedtak(nyttVedtak),
                    opprinneligStønadId = stønadId,
                    opprinneligVedtakId = vedtakId,
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
                januar to gammelYtelse(januar),
                februar to gammelYtelse(februar),
            ),
        ),
    ).shouldBeRight()

    private fun gammelYtelse(måned: no.nav.su.se.bakover.common.tid.periode.Måned) =
        GjeldendeHistoriskInfotrygdMånedsdata.Ytelse(
            måned = måned,
            kilde = HistoriskInfotrygdMånedskilde.OriginalProjeksjon(projeksjonId),
            opprinneligStønadId = stønadId,
            opprinneligVedtakId = vedtakId,
            oppdragId = oppdragId,
            bosituasjon = HistoriskBosituasjon.ENSLIG,
            sats = sats,
            fradrag = BigDecimal.ZERO,
            fradragsgrunnlag = HistoriskInfotrygdFradragsgrunnlag.OriginaleKoder(emptyList()),
        )

    private fun ytelse(måned: no.nav.su.se.bakover.common.tid.periode.Måned) =
        HistoriskInfotrygdRevurdertMånedsresultat.Ytelse(
            måned = måned,
            opprinneligStønadId = stønadId,
            opprinneligVedtakId = vedtakId,
            oppdragId = oppdragId,
            bosituasjon = HistoriskBosituasjon.ENSLIG,
            sats = sats,
            fradrag = emptyList(),
            benyttetRegel = beregningsregel,
        )

    private companion object {
        val projeksjonId: UUID = UUID.randomUUID()
        val år = 2020
        val januar = januar(år)
        val februar = februar(år)
        val periode: Periode = Periode.create(januar.fraOgMed, februar.tilOgMed)
        val stønadId = HistoriskStønadId(1)
        val vedtakId = HistoriskVedtakId(2)
        val oppdragId = "oppdrag-1"
        val sats = BigDecimal(10_000)
        val beregningsregel = Regelspesifiseringer.REGEL_HISTORISK_INFOTRYGD_YTELSE
            .benyttRegelspesifisering("Test")
        val tidspunkt: Tidspunkt = Tidspunkt.create(Instant.parse("2020-03-01T10:00:00Z"))
        val saksbehandler = NavIdentBruker.Saksbehandler("S123456")
    }
}
