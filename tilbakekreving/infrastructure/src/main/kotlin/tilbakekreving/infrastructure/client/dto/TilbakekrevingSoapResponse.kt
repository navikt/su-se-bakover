package tilbakekreving.infrastructure.client.dto

import arrow.core.Either
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.su.se.bakover.common.infrastructure.xml.xmlMapper
import no.nav.su.se.bakover.common.sikkerLogg
import org.slf4j.LoggerFactory

private val log =
    LoggerFactory.getLogger("tilbakekreving.infrastructure.client.dto.deserializeTilbakekrevingsvedtakResponse")

internal fun String.deserializeTilbakekrevingsvedtakResponse(
    soapRequest: String,
): Either<KlarteIkkeDeserialisereTilbakekrevingsresponsen, Tilbakekrevingsresponse> {
    return Either.catch {
        xmlMapper.readValue<Envelope>(this).body.tilbakekrevingsvedtakResponse
    }.mapLeft {
        log.error(
            "Feil ved sending av tilbakekrevingsvedtak: Klarte ikke deserialisere responsen fra Tilbakekrevingsmodulen (OS). Vi antar at vi fikk en positiv respons, men dette må følges opp manuelt. Se sikkerlogg for mer kontekst.",
            RuntimeException("Trigger stacktrace"),
        )
        sikkerLogg.error(
            "Feil ved sending av tilbakekrevingsvedtak: Klarte ikke deserialisere responsen fra Tilbakekrevingsmodulen (OS). Vi antar at vi fikk en positiv respons, men dette må følges opp manuelt. Respons: $this, Request: $soapRequest.",
            it,
        )
        KlarteIkkeDeserialisereTilbakekrevingsresponsen
    }
}

object KlarteIkkeDeserialisereTilbakekrevingsresponsen

@JacksonXmlRootElement(localName = "Envelope", namespace = "http://schemas.xmlsoap.org/soap/envelope/")
internal data class Envelope(
    @JacksonXmlProperty(localName = "Body")
    val body: Body,
)

internal data class Body(
    val tilbakekrevingsvedtakResponse: Tilbakekrevingsresponse,
)

internal data class Tilbakekrevingsresponse(
    val mmel: Mmel,
    val tilbakekrevingsvedtak: Tilbakekrevingsvedtak,
)

internal data class Mmel(
    val systemId: String? = null,
    val kodeMelding: String? = null,
    // 00, 04, 08, 12
    val alvorlighetsgrad: String? = null,
    val beskrMelding: String? = null,
    val sqlKode: String? = null,
    val sqlState: String? = null,
    val sqlMelding: String? = null,
    val mqCompletionKode: String? = null,
    val mqReasonKode: String? = null,
    val programId: String? = null,
    val sectionNavn: String? = null,
)

internal data class Tilbakekrevingsvedtak(
    /**
     * 1 - 441 - Kode-aksjon - X(01) - Krav - Aksjonskode:
     * 7 - midlertidig lagring
     * 8 - fatte vedtak
     * Aksjonskode 7 vil ikke kunne benyttes i fase 1.
     */
    val kodeAksjon: String? = null,
    /**
     * integer
     * 2 - 441 - Vedtak-id - 9(10) - Krav - Identifikasjon av tilbakekrevingsvedtaket
     * */
    val vedtakId: String? = null,
    // 3 - 441 - Dato-vedtak-fagsystem - X(08) - Valgfritt - Vedtaksdato på fagsystems vedtak. Omfeltet ikke er utfylt, legger TIlbakekrevingskomponenten inn dagens dato
    val datoVedtakFagsystem: String? = null,
    // 4 - 441 - Kode-hjemmel - X(20) - Krav - Lovhjemmel om tilbakekrevingsvedtaket
    val kodeHjemmel: String? = null,
    // 5 - 441 - Renter-beregnes - X(01) - Betinget krav - 'J' Dersom det skal beregnes renter på kravet
    val renterBeregnes: String? = null,
    // 6 - 441 - Enhet-ansvarlig - X(13) - Krav - Ansvarlig enhet
    val enhetAnsvarlig: String? = null,
    // 7 - 441 - Kontrollfelt - X(26) - Krav - Brukes ved innsending av tilbakekrevingsvedtak for å kontrollere at kravgrunnlaget ikke er blitt endret i mellomtiden
    val kontrollfelt: String? = null,
    // 8 - 441 - Saksbeh-id - X(08) - Krav - Saksbehandler
    val saksbehId: String? = null,
    // Liste over 442 - Tilbakekrevingsperiode
    val tilbakekrevingsperiode: List<Tilbakekrevingsperiode> = emptyList(),
)

internal data class Tilbakekrevingsperiode(
    val periode: Periode? = null,
    // 3 - 442 - Renter-beregnes - X(01) - Valgfritt - 'J' dersom det skal beregnes retner på kravet (nytt felt)
    val renterBeregnes: String? = null,
    /**
     * decimal (totalDigits 11, fractionDigits 2)
     * 4 - 442 - Belop-renter - Evt. beregnede renter i fagrutinen (nytt felt)
     */
    val belopRenter: String? = null,
    // Liste over 443 - Tilbakekrevingsbeløp
    val tilbakekrevingsbelop: List<Tilbakekrevingsbelop> = emptyList(),
)

internal data class Periode(
    // 1 - Dato-periode-fom - 442 - X(08) - Krav - Tilbakekrevingsperioder delt opp slik at ingen spenner over månedsskifter
    val fom: String? = null,
    // 2 - Dato-periode-tom - 442 - X(08) - Krav - Tilbakekrevingsperioder delt opp slik at ingen spenner over månedsskifter
    val tom: String? = null,
)

internal data class Tilbakekrevingsbelop(
    /**
     * min len 1, max 20
     * 1 - 443 - Kode-klasse - X(20) - Krav - Klassifisering av stønad, skatt, trekk etc. Det må minimum sendes med klassekoder for feilutbetaling og de ytelsesklassekoder som er feilutbetalt.
     */
    val kodeKlasse: String? = null,
    /**
     * decimal (totalDigits 11, fractionDigits 2)
     * 3 - 443 - Belop-oppr-utbet - 9(8)V99 - Krav - Egen betydning for KL_KODE_FEIL_INNT. Vil alltid være 0. Vi asserter på dette når vi tolker kravgrunnlaget fra oppdrag.
     */
    val belopOpprUtbet: String? = null,
    /**
     * decimal (totalDigits 11, fractionDigits 2)
     * 5 - 443 - Belop-ny - 9(8)V99 - Krav - Egen betydning for KL_KODE_FEIL_INNT. Vil være det feilutbetalte beløpet for perioden. Vi asserter på dette når vi tolker kravgrunnlaget fra oppdrag.
     * TODO jah: Vi tar ikke med det feilutbetalte beløpet fra kravgrunnlaget, men det vil være det høyeste av disse. Bør vi legge det til?
     */
    val belopNy: String? = null,
    /**
     * decimal (totalDigits 11, fractionDigits 2)
     * 7 - 443 - Belop-tilbakekreves - 9(8)V99 - Krav - Krav - Egen betydning for KL_KODE_FEIL_INNT. Vil alltid være 0. Vi asserter på dette når vi tolker kravgrunnlaget fra oppdrag.
     */
    val belopTilbakekreves: String? = null,

    /**
     * decimal (totalDigits 11, fractionDigits 2)
     * 9 - 443 - Belop-uinnkrevd - 9(8)V99 - Valgfritt - Krav - Egen betydning for KL_KODE_FEIL_INNT. Vil alltid være 0. Vi asserter på dette når vi tolker kravgrunnlaget fra oppdrag.
     * TODO jah: Verifiser om dette er valgfritt eller krav?
     */
    val belopUinnkrevd: String? = null,
    /**
     * decimal (totalDigits 7, fractionDigits 4)
     * 11 - 443 - Belop-skatt - 9(8)V99 - Valgfritt - Skattebeløp, som skal redusere beløp til innkreving.
     */
    val belopSkatt: String? = null,
    val kodeResultat: String? = null,
    val kodeAarsak: String? = null,
    val kodeSkyld: String? = null,
)
