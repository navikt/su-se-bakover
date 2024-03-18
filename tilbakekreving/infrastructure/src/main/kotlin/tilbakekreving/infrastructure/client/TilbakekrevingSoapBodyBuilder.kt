@file:Suppress("HttpUrlsUsage")

package tilbakekreving.infrastructure.client

import arrow.core.Either
import no.nav.su.se.bakover.common.domain.extensions.toStringWithDecimals
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.sikkerLogg
import org.slf4j.LoggerFactory
import tilbakekreving.domain.vedtak.KunneIkkeSendeTilbakekrevingsvedtak
import tilbakekreving.domain.vurdering.PeriodevurderingMedKrav
import tilbakekreving.domain.vurdering.VurderingerMedKrav
import tilbakekreving.infrastructure.client.dto.AksjonsKode
import tilbakekreving.infrastructure.client.dto.Skyld
import tilbakekreving.infrastructure.client.dto.TilbakekrevingsHjemmel
import tilbakekreving.infrastructure.client.dto.Tilbakekrevingsresultat
import tilbakekreving.infrastructure.client.dto.TilbakekrevingsÅrsak
import kotlin.math.max

private val log = LoggerFactory.getLogger("tilbakekreving.infrastructure.client.buildTilbakekrevingSoapRequest")

/**
 * Se: https://confluence.adeo.no/display/OKSY/Detaljer+om+de+enkelte+ID-koder
 * Se: https://github.com/navikt/tjenestespesifikasjoner/blob/master/tilbakekreving-v1-tjenestespesifikasjon/src/main/wsdl/no/nav/tilbakekreving/tilbakekreving-v1-tjenestespesifikasjon.wsdl
 */
internal fun buildTilbakekrevingSoapRequest(
    vurderingerMedKrav: VurderingerMedKrav,
    attestertAv: NavIdentBruker.Attestant,
): Either<KunneIkkeSendeTilbakekrevingsvedtak, String> {
    return Either.catch {
        // TODO jah: Vurder om vi skal legge til datoVedtakFagsystem istedenfor å få dagens dato.
        """
<ns4:tilbakekrevingsvedtakRequest xmlns:ns2="urn:no:nav:tilbakekreving:typer:v1"
                                  xmlns:ns4="http://okonomi.nav.no/tilbakekrevingService/"
                                  xmlns:ns3="urn:no:nav:tilbakekreving:tilbakekrevingsvedtak:vedtak:v1">
  <tilbakekrevingsvedtak>
    <ns3:kodeAksjon>${AksjonsKode.FATT_VEDTAK.nummer}</ns3:kodeAksjon>
    <ns3:vedtakId>${vurderingerMedKrav.eksternVedtakId}</ns3:vedtakId>
    <ns3:kodeHjemmel>${TilbakekrevingsHjemmel.T}</ns3:kodeHjemmel>
    <ns3:renterBeregnes>N</ns3:renterBeregnes>
    <ns3:enhetAnsvarlig>8020</ns3:enhetAnsvarlig>
    <ns3:kontrollfelt>${vurderingerMedKrav.eksternKontrollfelt}</ns3:kontrollfelt>
    <ns3:saksbehId>$attestertAv</ns3:saksbehId>
    ${
            vurderingerMedKrav.perioder.joinToString(separator = "\n") { periode ->
                """
      <ns3:tilbakekrevingsperiode>
        <ns3:periode>
          <ns2:fom>${periode.periode.fraOgMed}</ns2:fom>
          <ns2:tom>${periode.periode.tilOgMed}</ns2:tom>
        </ns3:periode>
        <ns3:renterBeregnes>N</ns3:renterBeregnes>
        <ns3:belopRenter>0.00</ns3:belopRenter>
        <ns3:tilbakekrevingsbelop>
          <ns3:kodeKlasse>SUUFORE</ns3:kodeKlasse>
          <ns3:belopOpprUtbet>${periode.bruttoTidligereUtbetalt.toStringWithDecimals(2)}</ns3:belopOpprUtbet>
          <ns3:belopNy>${periode.bruttoNyUtbetaling.toStringWithDecimals(2)}</ns3:belopNy>
          <ns3:belopTilbakekreves>${periode.bruttoSkalTilbakekreve.toStringWithDecimals(2)}</ns3:belopTilbakekreves>
          <ns3:belopUinnkrevd>${periode.bruttoSkalIkkeTilbakekreve.toStringWithDecimals(2)}</ns3:belopUinnkrevd>
          <ns3:belopSkatt>${periode.skattSomGårTilReduksjon.toStringWithDecimals(2)}</ns3:belopSkatt>
          <ns3:kodeResultat>${
                    when (periode) {
                        is PeriodevurderingMedKrav.SkalIkkeTilbakekreve -> Tilbakekrevingsresultat.INGEN_TILBAKEKREV.toString()
                        is PeriodevurderingMedKrav.SkalTilbakekreve -> Tilbakekrevingsresultat.FULL_TILBAKEKREV.toString()
                    }
                }</ns3:kodeResultat>
          <ns3:kodeAarsak>${TilbakekrevingsÅrsak.ANNET}</ns3:kodeAarsak>
          <ns3:kodeSkyld>${
                    when (periode) {
                        is PeriodevurderingMedKrav.SkalIkkeTilbakekreve -> Skyld.IKKE_FORDELT.toString()
                        is PeriodevurderingMedKrav.SkalTilbakekreve -> Skyld.BRUKER.toString()
                    }
                }</ns3:kodeSkyld>
        </ns3:tilbakekrevingsbelop>
        <ns3:tilbakekrevingsbelop>
          <ns3:kodeKlasse>KL_KODE_FEIL_INNT</ns3:kodeKlasse>
          <ns3:belopOpprUtbet>0.00</ns3:belopOpprUtbet>
          <ns3:belopNy>${
                    max(
                        periode.bruttoSkalTilbakekreve,
                        periode.bruttoSkalIkkeTilbakekreve,
                    ).toStringWithDecimals(2)
                }</ns3:belopNy>
          <ns3:belopTilbakekreves>0.00</ns3:belopTilbakekreves>
          <ns3:belopUinnkrevd>0.00</ns3:belopUinnkrevd>
        </ns3:tilbakekrevingsbelop>
      </ns3:tilbakekrevingsperiode>"""
            }
        }
  </tilbakekrevingsvedtak>
</ns4:tilbakekrevingsvedtakRequest>
        """.trimIndent()
    }.mapLeft {
        log.error(
            "Feil ved sending av tilbakekrevingsvedtak: Klarte ikke serialisere requesten som vi skulle sende til Tilbakekrevingsmodulen (OS). Se sikkerlogg for mer kontekst.",
            RuntimeException("Trigger stacktrace"),
        )
        sikkerLogg.error(
            "Feil ved sending av tilbakekrevingsvedtak: Klarte ikke serialisere requesten som vi skulle sende til Tilbakekrevingsmodulen (OS). vurderingerMedKrav: $vurderingerMedKrav, attestertAv: $attestertAv",
            it,
        )
        KunneIkkeSendeTilbakekrevingsvedtak.KlarteIkkeSerialisereRequest
    }
}
