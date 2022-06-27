package no.nav.su.se.bakover.web.komponenttest

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.server.testing.ApplicationTestBuilder
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.fixedClock
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.oktober
import no.nav.su.se.bakover.common.periode.Måned
import no.nav.su.se.bakover.common.periode.juni
import no.nav.su.se.bakover.common.periode.mai
import no.nav.su.se.bakover.common.periode.oktober
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.oppdrag.simulering.toYtelsekode
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.AvventerKravgrunnlag
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.MottattKravgrunnlag
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.SendtTilbakekrevingsvedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.shouldBeType
import no.nav.su.se.bakover.web.revurdering.attestering.sendTilAttestering
import no.nav.su.se.bakover.web.revurdering.avgjørTilbakekreving
import no.nav.su.se.bakover.web.revurdering.beregnOgSimuler
import no.nav.su.se.bakover.web.revurdering.forhåndsvarsel.leggTilIngenForhåndsvarsel
import no.nav.su.se.bakover.web.revurdering.fradrag.leggTilFradrag
import no.nav.su.se.bakover.web.revurdering.iverksett.iverksett
import no.nav.su.se.bakover.web.revurdering.opprett.opprettRevurdering
import no.nav.su.se.bakover.web.routes.revurdering.TilbakekrevingsbehandlingJson
import no.nav.su.se.bakover.web.services.tilbakekreving.TilbakekrevingsmeldingMapper
import no.nav.su.se.bakover.web.søknadsbehandling.BehandlingJson
import no.nav.su.se.bakover.web.søknadsbehandling.RevurderingJson
import no.nav.su.se.bakover.web.søknadsbehandling.opprettInnvilgetSøknadsbehandling
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.util.UUID

class Tilbakekreving {
    @Test
    fun `happy path full tilbakekreving`() {
        withKomptestApplication(
            clock = 1.oktober(2021).fixedClock(),
        ) { appComponents ->
            val (sakid, revurderingId) = vedtakMedTilbakekreving(avgjørelse = TilbakekrevingsbehandlingJson.TilbakekrevingsAvgjørelseJson.TILBAKEKREV)

            val vedtak = appComponents.services.vedtakService.hentForRevurderingId(UUID.fromString(revurderingId))!!
                .shouldBeType<VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRevurdering>().let { revurdering ->
                    revurdering.simulering.harFeilutbetalinger() shouldBe true
                    revurdering.behandling.simulering.harFeilutbetalinger() shouldBe true
                    revurdering.behandling.tilbakekrevingsbehandling.shouldBeType<AvventerKravgrunnlag>().also {
                        it.avgjort.sakId.toString() shouldBe sakid
                        it.avgjort.revurderingId.toString() shouldBe revurderingId
                    }
                    revurdering
                }

            appComponents.services.tilbakekrevingService.hentAvventerKravgrunnlag(UUID.fromString(sakid))
                .single() shouldBe vedtak.behandling.tilbakekrevingsbehandling
            appComponents.services.tilbakekrevingService.hentAvventerKravgrunnlag(vedtak.utbetalingId) shouldBe vedtak.behandling.tilbakekrevingsbehandling

            appComponents.consumers.tilbakekrevingConsumer.onMessage(
                lagKravgrunnlag(vedtak) {
                    lagPerioder(
                        mai(2021).until(oktober(2021)).map {
                            Feilutbetaling(
                                måned = it,
                                gammelUtbetaling = 21989,
                                nyUtbetaling = 3681,
                            )
                        },
                    )
                },
            )

            appComponents.services.vedtakService.hentForRevurderingId(UUID.fromString(revurderingId))!!
                .shouldBeType<VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRevurdering>().behandling.tilbakekrevingsbehandling.shouldBeType<MottattKravgrunnlag>()

            appComponents.services.tilbakekrevingService.sendTilbakekrevingsvedtak {
                TilbakekrevingsmeldingMapper.toKravgrunnlg(it).getOrFail()
            }

            appComponents.services.vedtakService.hentForRevurderingId(UUID.fromString(revurderingId))!!
                .shouldBeType<VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRevurdering>().behandling.tilbakekrevingsbehandling.shouldBeType<SendtTilbakekrevingsvedtak>()
                .also { tilbakekrevingsvedtak ->
                    tilbakekrevingsvedtak.tilbakekrevingsvedtakForsendelse.originalRequest() shouldContain """
                        <tilbakekrevingsbelop>
                            <kodeKlasse>SUUFORE</kodeKlasse>
                            <belopOpprUtbet>21989</belopOpprUtbet>
                            <belopNy>3681</belopNy>
                            <belopTilbakekreves>18308</belopTilbakekreves>
                            <belopUinnkrevd>0</belopUinnkrevd>
                            <belopSkatt>4729.00</belopSkatt>
                            <kodeResultat>FULL_TILBAKEKREV</kodeResultat>
                            <kodeAarsak>ANNET</kodeAarsak>
                            <kodeSkyld>BRUKER</kodeSkyld>
                        </tilbakekrevingsbelop>
                    """.replace("\n", "").filterNot { it.isWhitespace() }
                }
        }
    }

    @Test
    fun `happy path ingen tilbakekreving`() {
        withKomptestApplication(
            clock = 1.oktober(2021).fixedClock(),
        ) { appComponents ->
            val (sakid, revurderingId) = vedtakMedTilbakekreving(avgjørelse = TilbakekrevingsbehandlingJson.TilbakekrevingsAvgjørelseJson.IKKE_TILBAKEKREV)

            val vedtak = appComponents.services.vedtakService.hentForRevurderingId(UUID.fromString(revurderingId))!!
                .shouldBeType<VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRevurdering>().let { revurdering ->
                    revurdering.simulering.harFeilutbetalinger() shouldBe true
                    revurdering.behandling.simulering.harFeilutbetalinger() shouldBe true
                    revurdering.behandling.tilbakekrevingsbehandling.shouldBeType<AvventerKravgrunnlag>().also {
                        it.avgjort.sakId.toString() shouldBe sakid
                        it.avgjort.revurderingId.toString() shouldBe revurderingId
                    }
                    revurdering
                }

            appComponents.services.tilbakekrevingService.hentAvventerKravgrunnlag(UUID.fromString(sakid))
                .single() shouldBe vedtak.behandling.tilbakekrevingsbehandling
            appComponents.services.tilbakekrevingService.hentAvventerKravgrunnlag(vedtak.utbetalingId) shouldBe vedtak.behandling.tilbakekrevingsbehandling

            appComponents.consumers.tilbakekrevingConsumer.onMessage(
                lagKravgrunnlag(vedtak) {
                    lagPerioder(
                        mai(2021).until(oktober(2021)).map {
                            Feilutbetaling(
                                måned = it,
                                gammelUtbetaling = 21989,
                                nyUtbetaling = 3681,
                            )
                        },
                    )
                },
            )

            appComponents.services.vedtakService.hentForRevurderingId(UUID.fromString(revurderingId))!!
                .shouldBeType<VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRevurdering>().behandling.tilbakekrevingsbehandling.shouldBeType<MottattKravgrunnlag>()

            appComponents.services.tilbakekrevingService.sendTilbakekrevingsvedtak {
                TilbakekrevingsmeldingMapper.toKravgrunnlg(it).getOrFail()
            }

            appComponents.services.vedtakService.hentForRevurderingId(UUID.fromString(revurderingId))!!
                .shouldBeType<VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRevurdering>().behandling.tilbakekrevingsbehandling.shouldBeType<SendtTilbakekrevingsvedtak>()
                .also { tilbakekrevingsvedtak ->
                    tilbakekrevingsvedtak.tilbakekrevingsvedtakForsendelse.originalRequest() shouldContain """
                        <tilbakekrevingsbelop>
                            <kodeKlasse>SUUFORE</kodeKlasse>
                            <belopOpprUtbet>21989</belopOpprUtbet>
                            <belopNy>3681</belopNy>
                            <belopTilbakekreves>0</belopTilbakekreves>
                            <belopUinnkrevd>18308</belopUinnkrevd>
                            <belopSkatt>0</belopSkatt>
                            <kodeResultat>INGEN_TILBAKEKREV</kodeResultat>
                            <kodeAarsak>ANNET</kodeAarsak>
                            <kodeSkyld>IKKE_FORDELT</kodeSkyld>
                        </tilbakekrevingsbelop>
                    """.replace("\n", "").filterNot { it.isWhitespace() }
                }
        }
    }

    @Test
    fun `kaster hvis vi ikke finner tilbakekrevingsbehandling for kravgrunnlag`() {
        withKomptestApplication(
            clock = 1.oktober(2021).fixedClock(),
        ) { appComponents ->
            val (sakid, revurderingId) = vedtakMedTilbakekreving(avgjørelse = TilbakekrevingsbehandlingJson.TilbakekrevingsAvgjørelseJson.TILBAKEKREV)

            val vedtak =
                appComponents.services.vedtakService.hentForRevurderingId(UUID.fromString(revurderingId))!! as VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRevurdering

            assertThrows<IllegalStateException> {
                appComponents.consumers.tilbakekrevingConsumer.onMessage(
                    // tuller litt med utbetalingsid
                    lagKravgrunnlag(vedtak.copy(utbetalingId = UUID30.randomUUID())) {
                        lagPerioder(
                            mai(2021).until(oktober(2021)).map {
                                Feilutbetaling(
                                    måned = it,
                                    gammelUtbetaling = 21989,
                                    nyUtbetaling = 3681,
                                )
                            },
                        )
                    },
                )
            }.also {
                it.message shouldContain "Forventet å finne 1 tilbakekrevingsbehandling som avventer kravgrunnlag for utbetalingId:"
            }
        }
    }

    @Test
    fun `kaster hvis det ikke er samsvar mellom beløpene i simuleringen og kravgrunnlaget`() {
        withKomptestApplication(
            clock = 1.oktober(2021).fixedClock(),
        ) { appComponents ->
            val (sakid, revurderingId) = vedtakMedTilbakekreving(avgjørelse = TilbakekrevingsbehandlingJson.TilbakekrevingsAvgjørelseJson.TILBAKEKREV)

            val vedtak =
                appComponents.services.vedtakService.hentForRevurderingId(UUID.fromString(revurderingId))!! as VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRevurdering

            assertThrows<IllegalStateException> {
                appComponents.consumers.tilbakekrevingConsumer.onMessage(
                    lagKravgrunnlag(vedtak) {
                        lagPerioder(
                            mai(2021).until(oktober(2021)).map {
                                Feilutbetaling(
                                    måned = it,
                                    gammelUtbetaling = 17500,
                                    nyUtbetaling = 15,
                                )
                            },
                        )
                    },
                )
            }.also {
                it.message shouldContain "Ikke samsvar mellom perioder og beløp i simulering og kravgrunnlag for revurdering:"
            }
        }
    }

    @Test
    fun `kaster hvis det ikke er samsvar mellom periodene i simuleringen og kravgrunnlaget`() {
        withKomptestApplication(
            clock = 1.oktober(2021).fixedClock(),
        ) { appComponents ->
            val (sakid, revurderingId) = vedtakMedTilbakekreving(avgjørelse = TilbakekrevingsbehandlingJson.TilbakekrevingsAvgjørelseJson.TILBAKEKREV)

            val vedtak =
                appComponents.services.vedtakService.hentForRevurderingId(UUID.fromString(revurderingId))!! as VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRevurdering

            assertThrows<IllegalStateException> {
                appComponents.consumers.tilbakekrevingConsumer.onMessage(
                    lagKravgrunnlag(vedtak) {
                        lagPerioder(
                            mai(2021).until(juni(2021)).map {
                                Feilutbetaling(
                                    måned = it,
                                    gammelUtbetaling = 21989,
                                    nyUtbetaling = 3681,
                                )
                            },
                        )
                    },
                )
            }.also {
                it.message shouldContain "Ikke samsvar mellom perioder og beløp i simulering og kravgrunnlag for revurdering:"
            }
        }
    }

    private fun ApplicationTestBuilder.vedtakMedTilbakekreving(
        avgjørelse: TilbakekrevingsbehandlingJson.TilbakekrevingsAvgjørelseJson,
    ): Pair<String, String> {
        return opprettInnvilgetSøknadsbehandling(
            fnr = Fnr.generer().toString(),
            fraOgMed = 1.januar(2021).toString(),
            tilOgMed = 31.desember(2021).toString(),
        ).let { søknadsbehandlingJson ->
            val sakId = BehandlingJson.hentSakId(søknadsbehandlingJson)

            val revurderingId = opprettRevurdering(
                sakId = sakId,
                fraOgMed = 1.mai(2021).toString(),
            ).let {
                RevurderingJson.hentRevurderingId(it)
            }
            leggTilFradrag(
                sakId = sakId,
                behandlingId = revurderingId,
                fraOgMed = 1.mai(2021).toString(),
                tilOgMed = 31.desember(2021).toString(),
                body = {
                    """
                        {
                          "fradrag": [
                            {
                              "periode": {
                                "fraOgMed": "${1.mai(2021)}",
                                "tilOgMed": "${31.desember(2021)}"
                              },
                              "type": "Arbeidsinntekt",
                              "beløp": 18308.0,
                              "utenlandskInntekt": null,
                              "tilhører": "BRUKER"
                            }
                          ]                    
                        }
                        """
                },
            )
            beregnOgSimuler(
                sakId = sakId,
                behandlingId = revurderingId,
            )
            avgjørTilbakekreving(
                sakId = sakId,
                behandlingId = revurderingId,
                avgjørelse = { """{"avgjørelse":"$avgjørelse"}""" },
            )
            leggTilIngenForhåndsvarsel(
                sakId = sakId,
                behandlingId = revurderingId,
            )
            sendTilAttestering(
                sakId = sakId,
                behandlingId = revurderingId,
            )
            iverksett(
                sakId = sakId,
                behandlingId = revurderingId,
            )

            sakId to revurderingId
        }
    }

    private fun lagKravgrunnlag(
        vedtak: VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRevurdering,
        lagPerioder: () -> String,
    ): String {
        return """
        <urn:detaljertKravgrunnlagMelding xmlns:mmel="urn:no:nav:tilbakekreving:typer:v1" xmlns:urn="urn:no:nav:tilbakekreving:kravgrunnlag:detalj:v1">
            <urn:detaljertKravgrunnlag>
                <urn:kravgrunnlagId>298606</urn:kravgrunnlagId>
                <urn:vedtakId>436206</urn:vedtakId>
                <urn:kodeStatusKrav>NY</urn:kodeStatusKrav>
                <urn:kodeFagomraade>${vedtak.behandling.sakstype.toYtelsekode()}</urn:kodeFagomraade>
                <urn:fagsystemId>${vedtak.behandling.saksnummer}</urn:fagsystemId>
                <urn:vedtakIdOmgjort>0</urn:vedtakIdOmgjort>
                <urn:vedtakGjelderId>${vedtak.behandling.fnr}</urn:vedtakGjelderId>
                <urn:typeGjelderId>PERSON</urn:typeGjelderId>
                <urn:utbetalesTilId>${vedtak.behandling.fnr}</urn:utbetalesTilId>
                <urn:typeUtbetId>PERSON</urn:typeUtbetId>
                <urn:enhetAnsvarlig>8020</urn:enhetAnsvarlig>
                <urn:enhetBosted>8020</urn:enhetBosted>
                <urn:enhetBehandl>8020</urn:enhetBehandl>
                <urn:kontrollfelt>2022-02-07-18.39.47.693011</urn:kontrollfelt>
                <urn:saksbehId>K231B433</urn:saksbehId>
                <urn:referanse>${vedtak.utbetalingId}</urn:referanse>
                ${lagPerioder()}
            </urn:detaljertKravgrunnlag>
        </urn:detaljertKravgrunnlagMelding><?xml version="1.0" encoding="utf-8"?>
    """
    }

    private fun lagPerioder(feilutbetalinger: List<Feilutbetaling>): String {
        return StringBuffer().apply {
            feilutbetalinger.forEach {
                append(
                    """
                <urn:tilbakekrevingsPeriode>
                    <urn:periode>
                        <mmel:fom>${it.måned.fraOgMed}</mmel:fom>
                        <mmel:tom>${it.måned.tilOgMed}</mmel:tom>
                    </urn:periode>
                    <urn:belopSkattMnd>${it.skattMnd}</urn:belopSkattMnd>
                    <urn:tilbakekrevingsBelop>
                        <urn:kodeKlasse>KL_KODE_FEIL_INNT</urn:kodeKlasse>
                        <urn:typeKlasse>FEIL</urn:typeKlasse>
                        <urn:belopOpprUtbet>0.00</urn:belopOpprUtbet>
                        <urn:belopNy>${it.feilutbetalt()}</urn:belopNy>
                        <urn:belopTilbakekreves>0.00</urn:belopTilbakekreves>
                        <urn:belopUinnkrevd>0.00</urn:belopUinnkrevd>
                        <urn:skattProsent>0.0000</urn:skattProsent>
                    </urn:tilbakekrevingsBelop>
                    <urn:tilbakekrevingsBelop>
                        <urn:kodeKlasse>SUUFORE</urn:kodeKlasse>
                        <urn:typeKlasse>YTEL</urn:typeKlasse>
                        <urn:belopOpprUtbet>${it.gammelUtbetaling}</urn:belopOpprUtbet>
                        <urn:belopNy>${it.nyUtbetaling}</urn:belopNy>
                        <urn:belopTilbakekreves>${it.feilutbetalt()}</urn:belopTilbakekreves>
                        <urn:belopUinnkrevd>0.00</urn:belopUinnkrevd>
                        <urn:skattProsent>${it.skattProsent}</urn:skattProsent>
                    </urn:tilbakekrevingsBelop>
            </urn:tilbakekrevingsPeriode>
                    """.trimIndent(),
                )
            }
        }.toString()
    }

    private data class Feilutbetaling(
        val måned: Måned,
        var gammelUtbetaling: Int,
        val nyUtbetaling: Int,
        val skattMnd: BigDecimal = BigDecimal("4729.00"),
        val skattProsent: BigDecimal = BigDecimal("43.9983"),
    ) {
        fun feilutbetalt() = gammelUtbetaling - nyUtbetaling
    }
}
