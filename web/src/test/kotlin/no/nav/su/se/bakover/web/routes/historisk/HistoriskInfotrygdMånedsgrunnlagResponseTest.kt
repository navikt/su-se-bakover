package no.nav.su.se.bakover.web.routes.historisk

import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.desember
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskStønadId
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskVedtakId
import no.nav.su.se.bakover.domain.historisk.revurdering.GjeldendeHistoriskInfotrygdMånedsdata
import no.nav.su.se.bakover.domain.historisk.revurdering.GjeldendeHistoriskInfotrygdVedtaksdata
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdBeregning
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdBeregningsgrunnlagForMåned
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdFradragsgrunnlag
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdMånedskilde
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurdering
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurderingId
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurderingStatus
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurderingsbosituasjon
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurderingsvedtakId
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdVedtaksbrevvalg
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskVedtakSomRevurderes
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskeVedtakSomRevurderesMånedsvis
import no.nav.su.se.bakover.domain.historisk.revurdering.beregnRevurdering
import no.nav.su.se.bakover.service.historisk.revurdering.HistoriskInfotrygdRevurderingService.HistoriskInfotrygdMånedsgrunnlag
import no.nav.su.se.bakover.service.historisk.revurdering.HistoriskInfotrygdRevurderingService.HistoriskInfotrygdMånedsgrunnlagForMåned
import org.junit.jupiter.api.Test
import vilkår.inntekt.domain.grunnlag.FradragForMåned
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype
import vilkår.inntekt.domain.grunnlag.UtenlandskInntekt
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

internal class HistoriskInfotrygdMånedsgrunnlagResponseTest {
    @Test
    fun `returnerer Infotrygd-koder og strukturerte SU-appfradrag sammen med riktig kilde`() {
        val måneder = listOf(
            ytelse(),
            ytelse().copy(
                måned = januar,
                kilde = HistoriskInfotrygdMånedskilde.Revurderingsvedtak(tidligereVedtakId),
                fradragsgrunnlag = HistoriskInfotrygdFradragsgrunnlag.RevurderteFradrag(tidligereFradrag),
            ),
        )

        val response = HistoriskInfotrygdMånedsgrunnlag(revurdering(), måneder).toResponse()

        response.revurderingId shouldBe revurderingId.value
        response.lagretBeregningINåværendeRevurdering shouldBe null
        response.vedtaksgrunnlagSomRevurderes shouldBe listOf(
            forventetYtelse(),
            forventetYtelse().copy(
                måned = januar.toString(),
                kilde = HistoriskInfotrygdYtelseskildeResponse.Suapp(
                    vedtakId = tidligereVedtakId.value,
                    fradrag = listOf(
                        HistoriskInfotrygdFradragForMånedResponse(
                            type = Fradragstype.Arbeidsinntekt.kategori.name,
                            beskrivelse = null,
                            månedsbeløp = fradragsbeløp,
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),
                ),
            ),
        )
        deserialize<HistoriskInfotrygdMånedsgrunnlagResponse>(serialize(response)) shouldBe response
    }

    @Test
    fun `beholder beskrivelse tilhørighet og utenlandsk inntekt fra tidligere SU-appvedtak`() {
        val beskrivelse = "Annen inntekt"
        val utenlandskInntekt = UtenlandskInntekt.create(
            beløpIUtenlandskValuta = 100,
            valuta = "SEK",
            kurs = 1.0,
        )
        val fradrag = FradragForMåned(
            fradragstype = Fradragstype.Annet(beskrivelse),
            månedsbeløp = fradragsbeløp,
            måned = januar,
            tilhører = FradragTilhører.EPS,
            utenlandskInntekt = utenlandskInntekt,
        )
        val måned = ytelse().copy(
            måned = januar,
            kilde = HistoriskInfotrygdMånedskilde.Revurderingsvedtak(tidligereVedtakId),
            fradragsgrunnlag = HistoriskInfotrygdFradragsgrunnlag.RevurderteFradrag(listOf(fradrag)),
        )

        val response = HistoriskInfotrygdMånedsgrunnlag(revurdering(), listOf(måned)).toResponse()

        response.vedtaksgrunnlagSomRevurderes.single() shouldBe forventetYtelse().copy(
            måned = januar.toString(),
            kilde = HistoriskInfotrygdYtelseskildeResponse.Suapp(
                vedtakId = tidligereVedtakId.value,
                fradrag = listOf(
                    HistoriskInfotrygdFradragForMånedResponse(
                        type = fradrag.fradragstype.kategori.name,
                        beskrivelse = beskrivelse,
                        månedsbeløp = fradragsbeløp,
                        utenlandskInntekt = HistoriskInfotrygdUtenlandskInntektRequest(
                            beløpIUtenlandskValuta = utenlandskInntekt.beløpIUtenlandskValuta,
                            valuta = utenlandskInntekt.valuta,
                            kurs = utenlandskInntekt.kurs,
                        ),
                        tilhører = FradragTilhører.EPS,
                    ),
                ),
            ),
        )
        deserialize<HistoriskInfotrygdMånedsgrunnlagResponse>(serialize(response)) shouldBe response
    }

    @Test
    fun `skiller ingen ytelse fra ytelse med null kroner i fradrag`() {
        val måneder = listOf(
            HistoriskInfotrygdMånedsgrunnlagForMåned.IngenYtelse(
                måned = desember,
                opprinneligStønadId = stønadId.value,
                opprinneligVedtakId = opprinneligVedtakId.value,
                oppdragId = oppdragId,
                kilde = HistoriskInfotrygdMånedskilde.OriginalProjeksjon(projeksjonId),
            ),
            ytelse().copy(
                måned = januar,
                samletFradrag = BigDecimal.ZERO,
                ytelsesbeløp = sats,
                fradragsgrunnlag = HistoriskInfotrygdFradragsgrunnlag.OriginaleKoder(emptyList()),
            ),
        )

        val response = HistoriskInfotrygdMånedsgrunnlag(revurdering(), måneder).toResponse()

        response.vedtaksgrunnlagSomRevurderes shouldBe listOf(
            HistoriskInfotrygdMånedsgrunnlagForMånedResponse.IngenYtelse(
                måned = desember.toString(),
                opprinneligStønadId = stønadId.value,
                opprinneligVedtakId = opprinneligVedtakId.value,
                oppdragId = oppdragId,
                kilde = HistoriskInfotrygdVedtakskildeResponse.Infotrygd(projeksjonId),
            ),
            forventetYtelse().copy(
                måned = januar.toString(),
                samletFradrag = BigDecimal.ZERO,
                ytelsesbeløp = sats,
                kilde = HistoriskInfotrygdYtelseskildeResponse.Infotrygd(projeksjonId, emptyList()),
            ),
        )
        deserialize<HistoriskInfotrygdMånedsgrunnlagResponse>(serialize(response)) shouldBe response
    }

    @Test
    fun `beholder referansen til et tidligere SU-appvedtak ved ingen ytelse`() {
        val måned = HistoriskInfotrygdMånedsgrunnlagForMåned.IngenYtelse(
            måned = januar,
            opprinneligStønadId = stønadId.value,
            opprinneligVedtakId = opprinneligVedtakId.value,
            oppdragId = oppdragId,
            kilde = HistoriskInfotrygdMånedskilde.Revurderingsvedtak(tidligereVedtakId),
        )

        val response = HistoriskInfotrygdMånedsgrunnlag(revurdering(), listOf(måned)).toResponse()

        response.vedtaksgrunnlagSomRevurderes.single() shouldBe
            HistoriskInfotrygdMånedsgrunnlagForMånedResponse.IngenYtelse(
                måned = januar.toString(),
                opprinneligStønadId = stønadId.value,
                opprinneligVedtakId = opprinneligVedtakId.value,
                oppdragId = oppdragId,
                kilde = HistoriskInfotrygdVedtakskildeResponse.Suapp(tidligereVedtakId.value),
            )
        deserialize<HistoriskInfotrygdMånedsgrunnlagResponse>(serialize(response)) shouldBe response
    }

    @Test
    fun `lagret beregning på nåværende revurdering erstatter ikke tidligere fradragsgrunnlag`() {
        val måneder = listOf(ytelse(), ytelse().copy(måned = januar))
        val gjeldende = GjeldendeHistoriskInfotrygdVedtaksdata(
            projeksjonId = projeksjonId,
            periode = periode,
            tidslinje = periode.måneder().associateWith { måned ->
                GjeldendeHistoriskInfotrygdMånedsdata.Ytelse(
                    måned = måned,
                    kilde = HistoriskInfotrygdMånedskilde.OriginalProjeksjon(projeksjonId),
                    opprinneligStønadId = stønadId,
                    opprinneligVedtakId = opprinneligVedtakId,
                    oppdragId = oppdragId,
                    bosituasjon = bosituasjon,
                    sats = sats,
                    fradrag = samletFradrag,
                    fradragsgrunnlag = HistoriskInfotrygdFradragsgrunnlag.OriginaleKoder(fradragskoder),
                )
            },
        )
        val beregning = gjeldende.beregnRevurdering(
            grunnlag = periode.måneder().map { måned ->
                HistoriskInfotrygdBeregningsgrunnlagForMåned(
                    måned = måned,
                    bosituasjon = bosituasjon,
                    fradrag = emptyList(),
                )
            },
        ).shouldBeRight()

        val response = HistoriskInfotrygdMånedsgrunnlag(revurdering(beregning), måneder).toResponse()

        response.vedtaksgrunnlagSomRevurderes shouldBe
            listOf(forventetYtelse(), forventetYtelse().copy(måned = januar.toString()))
        val lagret = requireNotNull(response.lagretBeregningINåværendeRevurdering)
        lagret.måneder.map { it.måned } shouldBe listOf(desember.toString(), januar.toString())
        lagret.måneder.forEach {
            it.fradrag shouldBe emptyList()
            it.sumFradrag shouldBe BigDecimal.ZERO
            it.gammeltBeløp shouldBe ytelsesbeløp
            it.differanse shouldBe it.nyttBeløp - ytelsesbeløp
        }
        deserialize<HistoriskInfotrygdMånedsgrunnlagResponse>(serialize(response)) shouldBe response
    }

    private fun ytelse() = HistoriskInfotrygdMånedsgrunnlagForMåned.Ytelse(
        måned = desember,
        stønadsstart = desember.fraOgMed,
        opprinneligStønadId = stønadId.value,
        opprinneligVedtakId = opprinneligVedtakId.value,
        oppdragId = oppdragId,
        kilde = HistoriskInfotrygdMånedskilde.OriginalProjeksjon(projeksjonId),
        sats = sats,
        samletFradrag = samletFradrag,
        fradragsgrunnlag = HistoriskInfotrygdFradragsgrunnlag.OriginaleKoder(fradragskoder),
        ytelsesbeløp = ytelsesbeløp,
        foreslåttBosituasjon = bosituasjon,
        foreslåttSatskategori = bosituasjon.historiskSatskategori()?.name,
        kreverKontrollAvHistoriskForsørgingstillegg = false,
    )

    private fun forventetYtelse() = HistoriskInfotrygdMånedsgrunnlagForMånedResponse.Ytelse(
        måned = desember.toString(),
        stønadsstart = desember.fraOgMed,
        opprinneligStønadId = stønadId.value,
        opprinneligVedtakId = opprinneligVedtakId.value,
        oppdragId = oppdragId,
        kilde = HistoriskInfotrygdYtelseskildeResponse.Infotrygd(projeksjonId, fradragskoder),
        sats = sats,
        samletFradrag = samletFradrag,
        ytelsesbeløp = ytelsesbeløp,
        foreslåttBosituasjon = bosituasjon,
        foreslåttSatskategori = bosituasjon.historiskSatskategori()?.name,
        kreverKontrollAvHistoriskForsørgingstillegg = false,
    )

    private fun revurdering(beregning: HistoriskInfotrygdBeregning? = null) = HistoriskInfotrygdRevurdering(
        id = revurderingId,
        sakId = sakId,
        projeksjonId = projeksjonId,
        periode = periode,
        status = if (beregning == null) {
            HistoriskInfotrygdRevurderingStatus.OPPRETTET
        } else {
            HistoriskInfotrygdRevurderingStatus.BEREGNET
        },
        saksbehandler = NavIdentBruker.Saksbehandler("S123456"),
        opprettet = tidspunkt,
        oppdatert = tidspunkt,
        avslutningsbegrunnelse = null,
        vedtaksbrevvalg = HistoriskInfotrygdVedtaksbrevvalg.IKKE_VALGT,
        vedtaksbrevFritekst = null,
        vedtakSomRevurderesMånedsvis = HistoriskeVedtakSomRevurderesMånedsvis(
            periode.måneder().associateWith {
                HistoriskVedtakSomRevurderes.OriginaltInfotrygdVedtak(opprinneligVedtakId)
            },
        ),
        beregning = beregning,
        attesteringer = emptyList(),
    )

    private companion object {
        val desember = desember(2019)
        val januar = januar(2020)
        val periode = Periode.create(desember.fraOgMed, januar.tilOgMed)
        val projeksjonId: UUID = UUID.randomUUID()
        val revurderingId = HistoriskInfotrygdRevurderingId.generer()
        val sakId: UUID = UUID.randomUUID()
        val tidligereVedtakId = HistoriskInfotrygdRevurderingsvedtakId(UUID.randomUUID())
        val stønadId = HistoriskStønadId(1)
        val opprinneligVedtakId = HistoriskVedtakId(2)
        val oppdragId = "oppdrag-1"
        val bosituasjon = HistoriskInfotrygdRevurderingsbosituasjon.ENSLIG
        val sats = BigDecimal(10_000)
        val fradragsbeløp = 1_000.0
        val samletFradrag = BigDecimal.valueOf(fradragsbeløp)
        val ytelsesbeløp = BigDecimal(9_000)
        val fradragskoder = listOf("ARBM")
        val tidligereFradrag = listOf(
            FradragForMåned(
                fradragstype = Fradragstype.Arbeidsinntekt,
                månedsbeløp = fradragsbeløp,
                måned = januar,
                tilhører = FradragTilhører.BRUKER,
            ),
        )
        val tidspunkt = Tidspunkt.create(Instant.parse("2026-10-29T10:00:00Z"))
    }
}
