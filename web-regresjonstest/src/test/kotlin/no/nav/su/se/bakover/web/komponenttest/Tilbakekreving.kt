package no.nav.su.se.bakover.web.komponenttest

import arrow.core.left
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.server.testing.ApplicationTestBuilder
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.fixedClock
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.oktober
import no.nav.su.se.bakover.common.periode.juni
import no.nav.su.se.bakover.common.periode.mai
import no.nav.su.se.bakover.common.periode.oktober
import no.nav.su.se.bakover.common.trimWhitespace
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.AvventerKravgrunnlag
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.MottattKravgrunnlag
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.SendtTilbakekrevingsvedtak
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.TilbakekrevingsvedtakForsendelseFeil
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.service.brev.HentDokumenterForIdType
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.shouldBeType
import no.nav.su.se.bakover.web.SharedRegressionTestData
import no.nav.su.se.bakover.web.TestClientsBuilder
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
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.UUID

class Tilbakekreving {
    @Test
    fun `happy path full tilbakekreving`() {
        withKomptestApplication(
            clock = TikkendeKlokke(1.oktober(2021).fixedClock()),
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

            appComponents.mottaKvitteringForUtbetalingFraØkonomi(vedtak.utbetalingId)

            appComponents.services.utbetaling.hentUtbetaling(vedtak.utbetalingId).getOrFail()
                .shouldBeType<Utbetaling.OversendtUtbetaling.MedKvittering>()

            appComponents.services.brev.hentDokumenterFor(HentDokumenterForIdType.Vedtak(vedtak.id)).also {
                it shouldBe emptyList()
            }

            appComponents.services.tilbakekrevingService.hentAvventerKravgrunnlag(UUID.fromString(sakid))
                .single() shouldBe vedtak.behandling.tilbakekrevingsbehandling
            appComponents.services.tilbakekrevingService.hentAvventerKravgrunnlag(vedtak.utbetalingId) shouldBe vedtak.behandling.tilbakekrevingsbehandling

            appComponents.consumers.tilbakekrevingConsumer.onMessage(
                lagKravgrunnlag(vedtak) {
                    lagKravgrunnlagPerioder(
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
                    """.replace("\n", "").trimWhitespace()
                }

            @Suppress("UNCHECKED_CAST")
            appComponents.services.brev.hentDokumenterFor(HentDokumenterForIdType.Vedtak(vedtak.id))
                .also { dokumenter ->
                    dokumenter.single().also { brev ->
                        (
                            JSONObject(brev.generertDokumentJson).getJSONArray("tilbakekreving")
                                .map { it } as List<JSONObject>
                            )
                            .map { it.getString("beløp") }
                            .all { it == "13 579" } // 18308 - 4729 = 13579
                        brev.tittel shouldBe "Vi har vurdert den supplerende stønaden din på nytt og vil kreve tilbake penger"
                    }
                }
        }
    }

    @Test
    fun `happy path ingen tilbakekreving`() {
        withKomptestApplication(
            clock = TikkendeKlokke(1.oktober(2021).fixedClock()),
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

            appComponents.mottaKvitteringForUtbetalingFraØkonomi(vedtak.utbetalingId)

            appComponents.services.brev.hentDokumenterFor(HentDokumenterForIdType.Vedtak(vedtak.id)).also {
                it shouldBe emptyList()
            }

            appComponents.services.tilbakekrevingService.hentAvventerKravgrunnlag(UUID.fromString(sakid))
                .single() shouldBe vedtak.behandling.tilbakekrevingsbehandling
            appComponents.services.tilbakekrevingService.hentAvventerKravgrunnlag(vedtak.utbetalingId) shouldBe vedtak.behandling.tilbakekrevingsbehandling

            appComponents.consumers.tilbakekrevingConsumer.onMessage(
                lagKravgrunnlag(vedtak) {
                    lagKravgrunnlagPerioder(
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

            appComponents.sendTilbakekrevingsvedtakTilØkonomi()

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
                    """.replace("\n", "").trimWhitespace()
                }

            @Suppress("UNCHECKED_CAST")
            appComponents.services.brev.hentDokumenterFor(HentDokumenterForIdType.Vedtak(vedtak.id))
                .also { dokumenter ->
                    dokumenter.single().also { brev ->
                        brev.tittel shouldBe "Vi har vurdert den supplerende stønaden din på nytt"
                    }
                }
        }
    }

    @Test
    fun `kaster hvis vi ikke finner tilbakekrevingsbehandling for kravgrunnlag`() {
        withKomptestApplication(
            clock = 1.oktober(2021).fixedClock(),
        ) { appComponents ->
            val (_, revurderingId) = vedtakMedTilbakekreving(avgjørelse = TilbakekrevingsbehandlingJson.TilbakekrevingsAvgjørelseJson.TILBAKEKREV)

            val vedtak =
                appComponents.services.vedtakService.hentForRevurderingId(UUID.fromString(revurderingId))!! as VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRevurdering

            assertThrows<IllegalStateException> {
                appComponents.consumers.tilbakekrevingConsumer.onMessage(
                    // tuller litt med utbetalingsid
                    lagKravgrunnlag(vedtak.copy(utbetalingId = UUID30.randomUUID())) {
                        lagKravgrunnlagPerioder(
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
            val (_, revurderingId) = vedtakMedTilbakekreving(avgjørelse = TilbakekrevingsbehandlingJson.TilbakekrevingsAvgjørelseJson.TILBAKEKREV)

            val vedtak =
                appComponents.services.vedtakService.hentForRevurderingId(UUID.fromString(revurderingId))!! as VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRevurdering

            assertThrows<IllegalStateException> {
                appComponents.consumers.tilbakekrevingConsumer.onMessage(
                    lagKravgrunnlag(vedtak) {
                        lagKravgrunnlagPerioder(
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
            val (_, revurderingId) = vedtakMedTilbakekreving(avgjørelse = TilbakekrevingsbehandlingJson.TilbakekrevingsAvgjørelseJson.TILBAKEKREV)

            val vedtak =
                appComponents.services.vedtakService.hentForRevurderingId(UUID.fromString(revurderingId))!! as VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRevurdering

            assertThrows<IllegalStateException> {
                appComponents.consumers.tilbakekrevingConsumer.onMessage(
                    lagKravgrunnlag(vedtak) {
                        lagKravgrunnlagPerioder(
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

    @Test
    fun `send tilbakekrevingsvedtak lagrer ingenting dersom kall til økonomi feiler`() {
        val clock = 1.oktober(2021).fixedClock()
        withKomptestApplication(
            clock = clock,
            clients = {
                TestClientsBuilder(
                    clock = clock,
                    databaseRepos = it,
                ).build(SharedRegressionTestData.applicationConfig).copy(
                    tilbakekrevingClient = mock {
                        on { sendTilbakekrevingsvedtak(any()) } doReturn TilbakekrevingsvedtakForsendelseFeil.left()
                    },
                )
            },
        ) { appComponents ->
            val (_, revurderingId) = vedtakMedTilbakekreving(avgjørelse = TilbakekrevingsbehandlingJson.TilbakekrevingsAvgjørelseJson.TILBAKEKREV)

            val vedtak = appComponents.services.vedtakService.hentForRevurderingId(UUID.fromString(revurderingId))!!
                .shouldBeType<VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRevurdering>().also {
                    it.behandling.tilbakekrevingsbehandling.shouldBeType<AvventerKravgrunnlag>()
                }

            appComponents.consumers.tilbakekrevingConsumer.onMessage(
                lagKravgrunnlag(vedtak) {
                    lagKravgrunnlagPerioder(
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
                .shouldBeType<VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRevurdering>().also {
                    it.behandling.tilbakekrevingsbehandling.shouldBeType<MottattKravgrunnlag>()
                }

            assertThrows<RuntimeException> {
                appComponents.sendTilbakekrevingsvedtakTilØkonomi()
            }.also {
                it.message shouldContain "Feil ved oversendelse av tilbakekrevingsvedtak for tilbakekrevingsbehandling"
            }

            appComponents.services.vedtakService.hentForRevurderingId(UUID.fromString(revurderingId))!!
                .shouldBeType<VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRevurdering>().also {
                    it.behandling.tilbakekrevingsbehandling.shouldBeType<MottattKravgrunnlag>()
                }

            appComponents.services.brev.hentDokumenterFor(HentDokumenterForIdType.Vedtak(vedtak.id)) shouldBe emptyList()
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
}
