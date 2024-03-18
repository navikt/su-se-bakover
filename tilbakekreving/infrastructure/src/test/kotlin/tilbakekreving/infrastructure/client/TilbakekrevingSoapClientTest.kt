package tilbakekreving.infrastructure.client

import arrow.core.left
import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.WireMock
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.test.attestant
import no.nav.su.se.bakover.test.auth.FakeSamlTokenProvider
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.tilbakekreving.tilbakekrevingSoapResponseConversionError
import no.nav.su.se.bakover.test.tilbakekreving.tilbakekrevingSoapResponseOk
import no.nav.su.se.bakover.test.tilbakekreving.tilbakekrevingSoapResponseVedtakIdFinnesIkke
import no.nav.su.se.bakover.test.vurderingerMedKrav
import no.nav.su.se.bakover.test.wiremock.startedWireMockServerWithCorrelationId
import org.junit.jupiter.api.Test
import tilbakekreving.domain.kravgrunnlag.rått.RåTilbakekrevingsvedtakForsendelse
import tilbakekreving.domain.vedtak.KunneIkkeSendeTilbakekrevingsvedtak

internal class TilbakekrevingSoapClientTest {
    @Test
    fun `conversion error`() {
        startedWireMockServerWithCorrelationId {
            stubFor(
                wiremockBuilder("/a").willReturn(
                    WireMock.jsonResponse(tilbakekrevingSoapResponseConversionError(), 500),
                ),
            )
            TilbakekrevingSoapClient(
                baseUrl = "${this.baseUrl()}/a",
                samlTokenProvider = FakeSamlTokenProvider(),
                clock = fixedClock,
            ).sendTilbakekrevingsvedtak(
                vurderingerMedKrav = vurderingerMedKrav(),
                attestertAv = attestant,
            ) shouldBe KunneIkkeSendeTilbakekrevingsvedtak.FeilStatusFraOppdrag.left()
        }
    }

    @Test
    fun `vedtak id finnes ikke`() {
        startedWireMockServerWithCorrelationId {
            stubFor(
                wiremockBuilder("/b").willReturn(
                    WireMock.okXml(tilbakekrevingSoapResponseVedtakIdFinnesIkke()),
                ),
            )
            TilbakekrevingSoapClient(
                baseUrl = "${this.baseUrl()}/b",
                samlTokenProvider = FakeSamlTokenProvider(),
                clock = fixedClock,
            ).sendTilbakekrevingsvedtak(
                vurderingerMedKrav = vurderingerMedKrav(),
                attestertAv = attestant,
            ) shouldBe KunneIkkeSendeTilbakekrevingsvedtak.AlvorlighetsgradFeil.left()
        }
    }

    @Test
    fun `happy case`() {
        val responseXml = tilbakekrevingSoapResponseOk()
        startedWireMockServerWithCorrelationId {
            stubFor(
                wiremockBuilder("/c").willReturn(
                    WireMock.okXml(responseXml),
                ),
            )
            TilbakekrevingSoapClient(
                baseUrl = "${this.baseUrl()}/c",
                samlTokenProvider = FakeSamlTokenProvider(),
                clock = fixedClock,
            ).sendTilbakekrevingsvedtak(
                vurderingerMedKrav = vurderingerMedKrav(),
                attestertAv = attestant,
            ).getOrFail().shouldBeEqualToIgnoringFields(
                RåTilbakekrevingsvedtakForsendelse(
                    requestXml = "ignore-me",
                    responseXml = responseXml,
                    tidspunkt = fixedTidspunkt,
                ),
                RåTilbakekrevingsvedtakForsendelse::requestXml,
            )
        }
    }
}

private fun wiremockBuilder(testUrl: String): MappingBuilder =
    WireMock.post(WireMock.urlPathEqualTo(testUrl)).withHeader(
        "SOAPAction",
        WireMock.equalTo("http://okonomi.nav.no/tilbakekrevingService/TilbakekrevingPortType/tilbakekrevingsvedtakRequest"),
    )
