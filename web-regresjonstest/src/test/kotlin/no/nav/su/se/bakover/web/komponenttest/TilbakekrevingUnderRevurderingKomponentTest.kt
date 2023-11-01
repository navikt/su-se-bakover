package no.nav.su.se.bakover.web.komponenttest

import arrow.core.left
import dokument.domain.brev.HentDokumenterForIdType
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeTypeOf
import io.ktor.client.HttpClient
import no.nav.su.se.bakover.common.Beløp
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.MånedBeløp
import no.nav.su.se.bakover.common.Månedsbeløp
import no.nav.su.se.bakover.common.extensions.desember
import no.nav.su.se.bakover.common.extensions.februar
import no.nav.su.se.bakover.common.extensions.fixedClock
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.extensions.oktober
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.desember
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.AvventerKravgrunnlag
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.MottattKravgrunnlag
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.SendtTilbakekrevingsvedtak
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.TilbakekrevingsvedtakForsendelseFeil
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetRevurdering
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetSøknadsbehandling
import no.nav.su.se.bakover.hendelse.domain.JMSHendelseMetadata
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.applicationConfig
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.shouldBeType
import no.nav.su.se.bakover.web.TestClientsBuilder
import no.nav.su.se.bakover.web.kravgrunnlag.emulerViMottarKravgrunnlag
import no.nav.su.se.bakover.web.revurdering.attestering.sendTilAttestering
import no.nav.su.se.bakover.web.revurdering.avgjørTilbakekreving
import no.nav.su.se.bakover.web.revurdering.beregnOgSimuler
import no.nav.su.se.bakover.web.revurdering.brevvalg.velgIkkeSendBrev
import no.nav.su.se.bakover.web.revurdering.brevvalg.velgSendBrev
import no.nav.su.se.bakover.web.revurdering.fradrag.leggTilFradrag
import no.nav.su.se.bakover.web.revurdering.iverksett.iverksett
import no.nav.su.se.bakover.web.revurdering.opprett.opprettRevurdering
import no.nav.su.se.bakover.web.routes.revurdering.TilbakekrevingsbehandlingJson
import no.nav.su.se.bakover.web.services.tilbakekreving.genererKravgrunnlagFraSimulering
import no.nav.su.se.bakover.web.services.tilbakekreving.lagKravgrunnlagXml
import no.nav.su.se.bakover.web.søknadsbehandling.BehandlingJson
import no.nav.su.se.bakover.web.søknadsbehandling.RevurderingJson
import no.nav.su.se.bakover.web.søknadsbehandling.opprettInnvilgetSøknadsbehandling
import org.hamcrest.MatcherAssert
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.xmlunit.diff.DefaultNodeMatcher
import org.xmlunit.diff.ElementSelectors
import org.xmlunit.matchers.CompareMatcher
import tilbakekreving.domain.kravgrunnlag.RåttKravgrunnlag
import java.util.UUID

class TilbakekrevingUnderRevurderingKomponentTest {

    private val nodeMatcher = DefaultNodeMatcher().apply { ElementSelectors.byName }

    @Test
    fun `happy path full tilbakekreving`() {
        // Søknadsbehandling gir 20946 per måned for hele 2021.
        // Utbetaler januar, siden klokka starter på 1.februar.
        // Revurderer januar (nytt månedsbeløp 2638 siden vi har et fradrag på 18308)
        // Får feilutbetaling på 18308 og forventer et tilsvarende kravgrunnlag.
        val fraOgMedRevurdering = 1.januar(2021)
        val tilOgMedRevurdering = 31.januar(2021)
        withKomptestApplication(
            clock = TikkendeKlokke(1.februar(2021).fixedClock()),
        ) { appComponents ->
            val (sakid, revurderingId) = vedtakMedTilbakekrevingUnderRevurdering(
                avgjørelse = TilbakekrevingsbehandlingJson.TilbakekrevingsAvgjørelseJson.TILBAKEKREV,
                client = this.client,
                appComponents = appComponents,
                fraOgMedRevurdering = fraOgMedRevurdering.toString(),
                tilOgMedRevurdering = tilOgMedRevurdering.toString(),
            )

            val vedtak = appComponents.services.vedtakService.hentForRevurderingId(UUID.fromString(revurderingId))!!
                .shouldBeType<VedtakInnvilgetRevurdering>().let { vedtak ->
                    vedtak.simulering.harFeilutbetalinger() shouldBe true
                    vedtak.behandling.simulering.harFeilutbetalinger() shouldBe true
                    vedtak.behandling.tilbakekrevingsbehandling.shouldBeType<AvventerKravgrunnlag>().also {
                        it.avgjort.sakId.toString() shouldBe sakid
                        it.avgjort.revurderingId.toString() shouldBe revurderingId
                    }
                    vedtak.beregning.getMånedsberegninger().map {
                        it.måned to it.getSumYtelse()
                    } shouldBe listOf(januar(2021) to 2638)
                    vedtak.simulering.hentFeilutbetalteBeløp() shouldBe Månedsbeløp(
                        listOf(
                            MånedBeløp(
                                periode = januar(2021), beløp = Beløp(18308),
                            ),
                        ),
                    )
                    vedtak.simulering.hentTilUtbetaling() shouldBe Månedsbeløp(
                        emptyList(),
                    )
                    vedtak.simulering.hentTotalUtbetaling() shouldBe Månedsbeløp(
                        listOf(
                            MånedBeløp(
                                periode = januar(2021), beløp = Beløp(2638),
                            ),
                        ),
                    )
                    vedtak.simulering.hentUtbetalteBeløp() shouldBe Månedsbeløp(
                        listOf(
                            MånedBeløp(
                                periode = januar(2021), beløp = Beløp(20946),
                            ),
                        ),
                    )
                    vedtak
                }

            appComponents.databaseRepos.utbetaling.hentOversendtUtbetalingForUtbetalingId(vedtak.utbetalingId, null)!!
                .shouldBeType<Utbetaling.OversendtUtbetaling.MedKvittering>()

            appComponents.services.brev.hentDokumenterFor(HentDokumenterForIdType.HentDokumenterForVedtak(vedtak.id))
                .also {
                    it shouldBe emptyList()
                }

            appComponents.services.tilbakekrevingService.hentAvventerKravgrunnlag(UUID.fromString(sakid))
                .single() shouldBe vedtak.behandling.tilbakekrevingsbehandling
            appComponents.services.tilbakekrevingService.hentAvventerKravgrunnlag(vedtak.utbetalingId) shouldBe vedtak.behandling.tilbakekrevingsbehandling
            appComponents.emulerViMottarKravgrunnlag()

            appComponents.services.vedtakService.hentForRevurderingId(UUID.fromString(revurderingId))!!
                .shouldBeType<VedtakInnvilgetRevurdering>().behandling.tilbakekrevingsbehandling.shouldBeType<MottattKravgrunnlag>()

            appComponents.services.tilbakekrevingService.sendUteståendeTilbakekrevingsvedtak()

            appComponents.services.vedtakService.hentForRevurderingId(UUID.fromString(revurderingId))!!
                .shouldBeType<VedtakInnvilgetRevurdering>().behandling.also {
                    it.simulering.harFeilutbetalinger() shouldBe true
                }.tilbakekrevingsbehandling.shouldBeType<SendtTilbakekrevingsvedtak>()
                .also { tilbakekrevingsvedtak ->
                    val actualXml = tilbakekrevingsvedtak.tilbakekrevingsvedtakForsendelse.originalRequest()
                    val expected = """
<TilbakekrevingsvedtakRequest>
	<tilbakekrevingsvedtak>
		<kodeAksjon>8</kodeAksjon>
		<vedtakId>654321</vedtakId>
		<datoVedtakFagsystem/>
		<kodeHjemmel>SUL_13</kodeHjemmel>
		<renterBeregnes>N</renterBeregnes>
		<enhetAnsvarlig>8020</enhetAnsvarlig>
		<kontrollfelt>2021-02-01-01.02.52.000000</kontrollfelt>
		<saksbehId>K231B433</saksbehId>
		<tilbakekrevingsperiode>
			<periode>
				<fom>2021-01-01</fom>
				<tom>2021-01-31</tom>
			</periode>
			<renterBeregnes>N</renterBeregnes>
			<belopRenter>0</belopRenter>
			<tilbakekrevingsbelop>
				<kodeKlasse>KL_KODE_FEIL_INNT</kodeKlasse>
				<belopOpprUtbet>0</belopOpprUtbet>
				<belopNy>18308</belopNy>
				<belopTilbakekreves>0</belopTilbakekreves>
				<belopUinnkrevd>0</belopUinnkrevd>
				<belopSkatt/>
				<kodeResultat/>
				<kodeAarsak/>
				<kodeSkyld/>
			</tilbakekrevingsbelop>
			<tilbakekrevingsbelop>
				<kodeKlasse>SUUFORE</kodeKlasse>
				<belopOpprUtbet>20946</belopOpprUtbet>
				<belopNy>2638</belopNy>
				<belopTilbakekreves>18308</belopTilbakekreves>
				<belopUinnkrevd>0</belopUinnkrevd>
				<belopSkatt>9154</belopSkatt>
				<kodeResultat>FULL_TILBAKEKREV</kodeResultat>
				<kodeAarsak>ANNET</kodeAarsak>
				<kodeSkyld>BRUKER</kodeSkyld>
			</tilbakekrevingsbelop>
		</tilbakekrevingsperiode>
	</tilbakekrevingsvedtak>
</TilbakekrevingsvedtakRequest>
                    """.trimIndent()
                    MatcherAssert.assertThat(
                        actualXml,
                        CompareMatcher.isSimilarTo(expected).withNodeMatcher(nodeMatcher).ignoreWhitespace(),
                    )
                }
            @Suppress("UNCHECKED_CAST")
            appComponents.services.brev.hentDokumenterFor(HentDokumenterForIdType.HentDokumenterForVedtak(vedtak.id))
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
        val fraOgMedRevurdering = 1.januar(2021)
        val tilOgMedRevurdering = 31.januar(2021)
        withKomptestApplication(
            clock = TikkendeKlokke(1.februar(2021).fixedClock()),
        ) { appComponents ->
            val (sakid, revurderingId) = vedtakMedTilbakekrevingUnderRevurdering(
                avgjørelse = TilbakekrevingsbehandlingJson.TilbakekrevingsAvgjørelseJson.IKKE_TILBAKEKREV,
                client = this.client,
                appComponents = appComponents,
                fraOgMedRevurdering = fraOgMedRevurdering.toString(),
                tilOgMedRevurdering = tilOgMedRevurdering.toString(),
            )

            val vedtak = appComponents.services.vedtakService.hentForRevurderingId(UUID.fromString(revurderingId))!!
                .shouldBeType<VedtakInnvilgetRevurdering>().let { revurdering ->
                    revurdering.simulering.harFeilutbetalinger() shouldBe true
                    revurdering.behandling.simulering.harFeilutbetalinger() shouldBe true
                    revurdering.behandling.tilbakekrevingsbehandling.shouldBeType<AvventerKravgrunnlag>().also {
                        it.avgjort.sakId.toString() shouldBe sakid
                        it.avgjort.revurderingId.toString() shouldBe revurderingId
                    }
                    revurdering
                }

            appComponents.services.brev.hentDokumenterFor(HentDokumenterForIdType.HentDokumenterForVedtak(vedtak.id))
                .also {
                    it shouldBe emptyList()
                }

            appComponents.services.tilbakekrevingService.hentAvventerKravgrunnlag(UUID.fromString(sakid))
                .single() shouldBe vedtak.behandling.tilbakekrevingsbehandling
            appComponents.services.tilbakekrevingService.hentAvventerKravgrunnlag(vedtak.utbetalingId) shouldBe vedtak.behandling.tilbakekrevingsbehandling
            appComponents.emulerViMottarKravgrunnlag()

            appComponents.services.vedtakService.hentForRevurderingId(UUID.fromString(revurderingId))!!
                .shouldBeType<VedtakInnvilgetRevurdering>().behandling.tilbakekrevingsbehandling.shouldBeType<MottattKravgrunnlag>()

            appComponents.sendTilbakekrevingsvedtakTilØkonomi()

            appComponents.services.vedtakService.hentForRevurderingId(UUID.fromString(revurderingId))!!
                .shouldBeType<VedtakInnvilgetRevurdering>().behandling.tilbakekrevingsbehandling.shouldBeType<SendtTilbakekrevingsvedtak>()
                .also { tilbakekrevingsvedtak ->
                    val actualXml = tilbakekrevingsvedtak.tilbakekrevingsvedtakForsendelse.originalRequest()
                    val expected = """
<TilbakekrevingsvedtakRequest>
	<tilbakekrevingsvedtak>
		<kodeAksjon>8</kodeAksjon>
		<vedtakId>654321</vedtakId>
		<datoVedtakFagsystem/>
		<kodeHjemmel>SUL_13</kodeHjemmel>
		<renterBeregnes>N</renterBeregnes>
		<enhetAnsvarlig>8020</enhetAnsvarlig>
		<kontrollfelt>2021-02-01-01.02.52.000000</kontrollfelt>
		<saksbehId>K231B433</saksbehId>
		<tilbakekrevingsperiode>
			<periode>
				<fom>2021-01-01</fom>
				<tom>2021-01-31</tom>
			</periode>
			<renterBeregnes>N</renterBeregnes>
			<belopRenter>0</belopRenter>
			<tilbakekrevingsbelop>
				<kodeKlasse>KL_KODE_FEIL_INNT</kodeKlasse>
				<belopOpprUtbet>0</belopOpprUtbet>
				<belopNy>18308</belopNy>
				<belopTilbakekreves>0</belopTilbakekreves>
				<belopUinnkrevd>0</belopUinnkrevd>
				<belopSkatt/>
				<kodeResultat/>
				<kodeAarsak/>
				<kodeSkyld/>
			</tilbakekrevingsbelop>
			<tilbakekrevingsbelop>
				<kodeKlasse>SUUFORE</kodeKlasse>
				<belopOpprUtbet>20946</belopOpprUtbet>
				<belopNy>2638</belopNy>
				<belopTilbakekreves>0</belopTilbakekreves>
				<belopUinnkrevd>18308</belopUinnkrevd>
				<belopSkatt>0</belopSkatt>
				<kodeResultat>INGEN_TILBAKEKREV</kodeResultat>
				<kodeAarsak>ANNET</kodeAarsak>
				<kodeSkyld>IKKE_FORDELT</kodeSkyld>
			</tilbakekrevingsbelop>
		</tilbakekrevingsperiode>
	</tilbakekrevingsvedtak>
</TilbakekrevingsvedtakRequest>
                    """.trimIndent()
                    MatcherAssert.assertThat(
                        actualXml,
                        CompareMatcher.isSimilarTo(expected).withNodeMatcher(nodeMatcher).ignoreWhitespace(),
                    )
                }

            appComponents.services.brev.hentDokumenterFor(HentDokumenterForIdType.HentDokumenterForVedtak(vedtak.id))
                .also { dokumenter ->
                    dokumenter.single().also { brev ->
                        brev.tittel shouldBe "Vi har vurdert den supplerende stønaden din på nytt"
                    }
                }
        }
    }

    @Test
    fun `Knytter ikke kravgrunnlag til sak dersom revurdering avventer kravgrunnlag men utbetalingId er ulik`() {
        val fraOgMedRevurdering = 1.januar(2021)
        val tilOgMedRevurdering = 31.januar(2021)
        withKomptestApplication(
            clock = TikkendeKlokke(1.februar(2021).fixedClock()),
        ) { appComponents ->
            // Lager et annet vedtak på en annen sak vi ikke skal forholde oss til.
            val (_, _) = vedtakMedTilbakekrevingUnderRevurdering(
                avgjørelse = TilbakekrevingsbehandlingJson.TilbakekrevingsAvgjørelseJson.TILBAKEKREV,
                client = this.client,
                appComponents = appComponents,
                fraOgMedRevurdering = fraOgMedRevurdering.toString(),
                tilOgMedRevurdering = tilOgMedRevurdering.toString(),
            )
            val (sakId, revurderingId) = vedtakMedTilbakekrevingUnderRevurdering(
                avgjørelse = TilbakekrevingsbehandlingJson.TilbakekrevingsAvgjørelseJson.TILBAKEKREV,
                client = this.client,
                appComponents = appComponents,
                fraOgMedRevurdering = fraOgMedRevurdering.toString(),
                tilOgMedRevurdering = tilOgMedRevurdering.toString(),
            )
            val utbetalingIdForSøknadsbehandling =
                appComponents.databaseRepos.sak.hentSak(UUID.fromString(sakId))!!.vedtakListe.filterIsInstance<VedtakInnvilgetSøknadsbehandling>()
                    .single().utbetalingId

            appComponents.emulerViMottarKravgrunnlag(
                overstyrUtbetalingId = listOf(null, utbetalingIdForSøknadsbehandling),
            )
            appComponents.services.tilbakekrevingService.sendUteståendeTilbakekrevingsvedtak()

            val vedtak =
                appComponents.services.vedtakService.hentForRevurderingId(UUID.fromString(revurderingId))!!.also {
                    it.shouldBeType<VedtakInnvilgetRevurdering>().behandling.tilbakekrevingsbehandling.shouldBeType<AvventerKravgrunnlag>()
                }

            appComponents.services.brev.hentDokumenterFor(HentDokumenterForIdType.HentDokumenterForVedtak(vedtak.id))
                .also { dokumenter ->
                    dokumenter shouldBe emptyList()
                }
        }
    }

    @Test
    fun `kaster hvis det ikke er samsvar mellom beløpene i simuleringen og kravgrunnlaget`() {
        val fraOgMedRevurdering = 1.januar(2021)
        val tilOgMedRevurdering = 31.januar(2021)
        val clock = TikkendeKlokke(1.februar(2021).fixedClock())
        withKomptestApplication(
            clock = clock,
        ) { appComponents ->
            val (sakId, revurderingId) = vedtakMedTilbakekrevingUnderRevurdering(
                avgjørelse = TilbakekrevingsbehandlingJson.TilbakekrevingsAvgjørelseJson.TILBAKEKREV,
                client = this.client,
                appComponents = appComponents,
                fraOgMedRevurdering = fraOgMedRevurdering.toString(),
                tilOgMedRevurdering = tilOgMedRevurdering.toString(),
            )
            val sak = appComponents.databaseRepos.sak.hentSak(UUID.fromString(sakId))!!
            val kravgrunnlag = genererKravgrunnlagFraSimulering(
                saksnummer = sak.saksnummer,
                simulering = sak.revurderinger.single().simulering!!,
                utbetalingId = sak.vedtakListe.filterIsInstance<VedtakInnvilgetRevurdering>()
                    .single().utbetalingId,
                clock = clock,
            ).let {
                it.copy(
                    grunnlagsmåneder = it.grunnlagsmåneder.map {
                        it.copy(
                            ytelse = it.ytelse.copy(
                                beløpSkalTilbakekreves = 99,
                            ),
                        )
                    },
                )
            }
            appComponents.tilbakekrevingskomponenter.services.råttKravgrunnlagService.lagreRåttkravgrunnlagshendelse(
                råttKravgrunnlag = RåttKravgrunnlag(
                    lagKravgrunnlagXml(
                        kravgrunnlag,
                        sak.fnr.toString(),
                    ),
                ),
                meta = JMSHendelseMetadata.fromCorrelationId(CorrelationId.generate()),
            )

            appComponents.tilbakekrevingskomponenter.services.knyttKravgrunnlagTilSakOgUtbetalingKonsument.knyttKravgrunnlagTilSakOgUtbetaling(
                correlationId = CorrelationId.generate(),
            ).let {
                it.shouldBeLeft()
                it.value.message shouldBe "Ikke samsvar mellom perioder og beløp i simulering og kravgrunnlag for revurdering:$revurderingId. Simulering: Månedsbeløp(månedbeløp=[MånedBeløp(periode=2021-01, beløp=Beløp(value=18308))]), Kravgrunnlag: Månedsbeløp(månedbeløp=[MånedBeløp(periode=2021-01, beløp=Beløp(value=99))])"
                it.value.shouldBeTypeOf<IllegalStateException>()
            }

            appComponents.services.tilbakekrevingService.sendUteståendeTilbakekrevingsvedtak()
            val vedtak =
                appComponents.services.vedtakService.hentForRevurderingId(UUID.fromString(revurderingId))!!.also {
                    it.shouldBeType<VedtakInnvilgetRevurdering>().behandling.tilbakekrevingsbehandling.shouldBeType<AvventerKravgrunnlag>()
                }

            appComponents.services.brev.hentDokumenterFor(HentDokumenterForIdType.HentDokumenterForVedtak(vedtak.id))
                .also { dokumenter ->
                    dokumenter shouldBe emptyList()
                }
        }
    }

    @Test
    fun `kaster hvis det ikke er samsvar mellom periodene i simuleringen og kravgrunnlaget`() {
        val fraOgMedRevurdering = 1.januar(2021)
        val tilOgMedRevurdering = 31.januar(2021)
        val clock = TikkendeKlokke(1.februar(2021).fixedClock())
        withKomptestApplication(
            clock = clock,
        ) { appComponents ->
            val (sakId, revurderingId) = vedtakMedTilbakekrevingUnderRevurdering(
                avgjørelse = TilbakekrevingsbehandlingJson.TilbakekrevingsAvgjørelseJson.TILBAKEKREV,
                client = this.client,
                appComponents = appComponents,
                fraOgMedRevurdering = fraOgMedRevurdering.toString(),
                tilOgMedRevurdering = tilOgMedRevurdering.toString(),
            )
            val sak = appComponents.databaseRepos.sak.hentSak(UUID.fromString(sakId))!!
            val kravgrunnlag = genererKravgrunnlagFraSimulering(
                saksnummer = sak.saksnummer,
                simulering = sak.revurderinger.single().simulering!!,
                utbetalingId = sak.vedtakListe.filterIsInstance<VedtakInnvilgetRevurdering>()
                    .single().utbetalingId,
                clock = clock,
            ).let {
                it.copy(
                    grunnlagsmåneder = it.grunnlagsmåneder.map {
                        it.copy(
                            måned = desember(2021),
                        )
                    },
                )
            }
            appComponents.tilbakekrevingskomponenter.services.råttKravgrunnlagService.lagreRåttkravgrunnlagshendelse(
                råttKravgrunnlag = RåttKravgrunnlag(
                    lagKravgrunnlagXml(
                        kravgrunnlag,
                        sak.fnr.toString(),
                    ),
                ),
                meta = JMSHendelseMetadata.fromCorrelationId(CorrelationId.generate()),
            )

            appComponents.tilbakekrevingskomponenter.services.knyttKravgrunnlagTilSakOgUtbetalingKonsument.knyttKravgrunnlagTilSakOgUtbetaling(
                correlationId = CorrelationId.generate(),
            ).let {
                it.shouldBeLeft()
                it.value.message shouldBe "Ikke samsvar mellom perioder og beløp i simulering og kravgrunnlag for revurdering:$revurderingId. Simulering: Månedsbeløp(månedbeløp=[MånedBeløp(periode=2021-01, beløp=Beløp(value=18308))]), Kravgrunnlag: Månedsbeløp(månedbeløp=[MånedBeløp(periode=2021-12, beløp=Beløp(value=18308))])"
                it.value.shouldBeTypeOf<IllegalStateException>()
            }

            appComponents.services.tilbakekrevingService.sendUteståendeTilbakekrevingsvedtak()
            val vedtak =
                appComponents.services.vedtakService.hentForRevurderingId(UUID.fromString(revurderingId))!!.also {
                    it.shouldBeType<VedtakInnvilgetRevurdering>().behandling.tilbakekrevingsbehandling.shouldBeType<AvventerKravgrunnlag>()
                }

            appComponents.services.brev.hentDokumenterFor(HentDokumenterForIdType.HentDokumenterForVedtak(vedtak.id))
                .also { dokumenter ->
                    dokumenter shouldBe emptyList()
                }
        }
    }

    @Test
    fun `send tilbakekrevingsvedtak lagrer ingenting dersom kall til økonomi feiler`() {
        val clock = TikkendeKlokke(1.oktober(2021).fixedClock())
        withKomptestApplication(
            clock = clock,
            clientsBuilder = { databaseRepos, klokke ->
                TestClientsBuilder(
                    clock = klokke,
                    databaseRepos = databaseRepos,
                ).build(applicationConfig()).copy(
                    tilbakekrevingClient = mock {
                        on { sendTilbakekrevingsvedtak(any()) } doReturn TilbakekrevingsvedtakForsendelseFeil.left()
                    },
                )
            },
        ) { appComponents ->
            val (_, revurderingId) = vedtakMedTilbakekrevingUnderRevurdering(
                avgjørelse = TilbakekrevingsbehandlingJson.TilbakekrevingsAvgjørelseJson.TILBAKEKREV,
                client = this.client,
                appComponents = appComponents,
            )

            val vedtak = appComponents.services.vedtakService.hentForRevurderingId(UUID.fromString(revurderingId))!!
                .shouldBeType<VedtakInnvilgetRevurdering>().also {
                    it.behandling.tilbakekrevingsbehandling.shouldBeType<AvventerKravgrunnlag>()
                }
            appComponents.emulerViMottarKravgrunnlag()

            appComponents.services.vedtakService.hentForRevurderingId(UUID.fromString(revurderingId))!!
                .shouldBeType<VedtakInnvilgetRevurdering>().also {
                    it.behandling.tilbakekrevingsbehandling.shouldBeType<MottattKravgrunnlag>()
                }

            assertThrows<RuntimeException> {
                appComponents.sendTilbakekrevingsvedtakTilØkonomi()
            }.also {
                it.message shouldContain "Feil ved oversendelse av tilbakekrevingsvedtak for tilbakekrevingsbehandling"
            }

            appComponents.services.vedtakService.hentForRevurderingId(UUID.fromString(revurderingId))!!
                .shouldBeType<VedtakInnvilgetRevurdering>().also {
                    it.behandling.tilbakekrevingsbehandling.shouldBeType<MottattKravgrunnlag>()
                }

            appComponents.services.brev.hentDokumenterFor(HentDokumenterForIdType.HentDokumenterForVedtak(vedtak.id)) shouldBe emptyList()
        }
    }

    @Test
    fun `kan velge å ikke sende ut brev for tilbakekrevingsvedtak`() {
        val clock = TikkendeKlokke(1.oktober(2021).fixedClock())
        withKomptestApplication(
            clock = clock,
        ) { appComponents ->
            val (_, revurderingId) = vedtakMedTilbakekrevingUnderRevurdering(
                avgjørelse = TilbakekrevingsbehandlingJson.TilbakekrevingsAvgjørelseJson.TILBAKEKREV,
                brevvalg = { sakId, revurderingId ->
                    velgIkkeSendBrev(
                        sakId = sakId,
                        behandlingId = revurderingId,
                        client = this.client,
                    )
                },
                client = this.client,
                appComponents = appComponents,
            )

            val vedtak = appComponents.services.vedtakService.hentForRevurderingId(UUID.fromString(revurderingId))!!
                .shouldBeType<VedtakInnvilgetRevurdering>().also {
                    it.behandling.tilbakekrevingsbehandling.shouldBeType<AvventerKravgrunnlag>()
                }
            appComponents.emulerViMottarKravgrunnlag()

            appComponents.sendTilbakekrevingsvedtakTilØkonomi()

            appComponents.services.brev.hentDokumenterFor(HentDokumenterForIdType.HentDokumenterForVedtak(vedtak.id)) shouldBe emptyList()
            appComponents.services.brev.hentDokumenterFor(HentDokumenterForIdType.HentDokumenterForRevurdering(vedtak.behandling.id)) shouldBe emptyList()
            appComponents.services.vedtakService.hentForRevurderingId(UUID.fromString(revurderingId))!!
                .shouldBeType<VedtakInnvilgetRevurdering>().behandling.tilbakekrevingsbehandling.shouldBeType<SendtTilbakekrevingsvedtak>()
        }
    }

    private fun vedtakMedTilbakekrevingUnderRevurdering(
        avgjørelse: TilbakekrevingsbehandlingJson.TilbakekrevingsAvgjørelseJson,
        client: HttpClient,
        brevvalg: (sakId: String, revurderingId: String) -> String = { sakId, revurderingId ->
            velgSendBrev(
                sakId = sakId,
                behandlingId = revurderingId,
                client = client,
            )
        },
        appComponents: AppComponents,
        fraOgMedSøknadsbehandling: String = 1.januar(2021).toString(),
        tilOgMedSøknadsbehandling: String = 31.desember(2021).toString(),
        fraOgMedRevurdering: String = 1.januar(2021).toString(),
        tilOgMedRevurdering: String = 31.januar(2021).toString(),
    ): Pair<String, String> {
        return opprettInnvilgetSøknadsbehandling(
            fnr = Fnr.generer().toString(),
            fraOgMed = fraOgMedSøknadsbehandling,
            tilOgMed = tilOgMedSøknadsbehandling,
            client = client,
            appComponents = appComponents,
        ).let { søknadsbehandlingJson ->
            val sakId = BehandlingJson.hentSakId(søknadsbehandlingJson)

            val revurderingId = opprettRevurdering(
                sakId = sakId,
                fraOgMed = fraOgMedRevurdering,
                tilOgMed = tilOgMedRevurdering,
                client = client,
            ).let {
                RevurderingJson.hentRevurderingId(it)
            }
            leggTilFradrag(
                sakId = sakId,
                behandlingId = revurderingId,
                fraOgMed = fraOgMedRevurdering,
                tilOgMed = tilOgMedRevurdering,
                body = {
                    """
                        {
                          "fradrag": [
                            {
                              "periode": {
                                "fraOgMed": "$fraOgMedRevurdering",
                                "tilOgMed": "$tilOgMedRevurdering"
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
                client = client,
            )
            beregnOgSimuler(
                sakId = sakId,
                behandlingId = revurderingId,
                client = client,
            )
            brevvalg(
                sakId,
                revurderingId,
            )
            avgjørTilbakekreving(
                sakId = sakId,
                behandlingId = revurderingId,
                avgjørelse = { """{"avgjørelse":"$avgjørelse"}""" },
                client = client,
            )
            sendTilAttestering(
                sakId = sakId,
                behandlingId = revurderingId,
                client = client,
            )
            iverksett(
                sakId = sakId,
                behandlingId = revurderingId,
                client = client,
                appComponents = appComponents,
            )

            sakId to revurderingId
        }
    }
}
