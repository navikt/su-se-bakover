package no.nav.su.se.bakover.client.oppdrag.simulering

import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.su.se.bakover.common.infrastructure.xml.xmlMapper

internal fun String.deserializeSimulerBeregningResponse(): SimulerBeregningResponse.Beregning? {
    return xmlMapper.readValue<Envelope>(this).body.simulerBeregningResponse.response?.simulering
}

@JacksonXmlRootElement(localName = "Envelope", namespace = "http://schemas.xmlsoap.org/soap/envelope/")
data class Envelope(
    @JacksonXmlProperty(localName = "Body")
    val body: Body,
)

data class Body(
    val simulerBeregningResponse: SimulerBeregningResponse,
)

/**
 * Fritt kopiert fra https://github.com/navikt/tjenestespesifikasjoner/blob/master/nav-system-os-simuler-fp-service-tjenestespesifikasjon/src/main/wsdl/no/nav/system/os/tjenester/simulerFpService/simulerFpServiceServiceTypes.xsd
 */
data class SimulerBeregningResponse(
    val response: Response? = null,
) {
    @JsonPropertyOrder(
        "simulering",
        "infomelding",
    )
    data class Response(
        val simulering: Beregning? = null,
        val infomelding: Infomelding? = null,
    )

    data class Infomelding(
        val beskrMelding: String,
    )

    @JsonPropertyOrder(
        "gjelderId",
        "gjelderNavn",
        "datoBeregnet",
        "kodeFaggruppe",
        "belop",
        "beregningsPeriode",
    )
    data class Beregning(
        val gjelderId: String,
        val gjelderNavn: String,
        val datoBeregnet: String,
        val kodeFaggruppe: String,
        // BigDecimal
        val belop: String,
        val beregningsPeriode: List<BeregningsPeriode> = emptyList(),
    )

    @JsonPropertyOrder(
        "periodeFom",
        "periodeTom",
        "beregningStoppnivaa",
    )
    data class BeregningsPeriode(
        val periodeFom: String,
        val periodeTom: String,
        val beregningStoppnivaa: List<BeregningStoppnivaa>,
    )

    @JsonPropertyOrder(
        "kodeFagomraade",
        "stoppNivaaId",
        "behandlendeEnhet",
        "oppdragsId",
        "fagsystemId",
        "kid",
        "utbetalesTilId",
        "utbetalesTilNavn",
        "bilagsType",
        "forfall",
        "feilkonto",
        "beregningStoppnivaaDetaljer",
    )
    data class BeregningStoppnivaa(
        val kodeFagomraade: String,
        // BigInteger
        val stoppNivaaId: String,
        val behandlendeEnhet: String,
        // Long
        val oppdragsId: String,
        val fagsystemId: String,
        val kid: String? = null,
        val utbetalesTilId: String,
        val utbetalesTilNavn: String,
        val bilagsType: String,
        val forfall: String,
        // Boolean
        val feilkonto: String,
        val beregningStoppnivaaDetaljer: List<BeregningStoppnivaaDetaljer>,
    )

    @JsonPropertyOrder(
        "faktiskFom",
        "faktiskTom",
        "kontoStreng",
        "behandlingskode",
        "belop",
        "trekkVedtakId",
        "stonadId",
        "korrigering",
        "tilbakeforing",
        "linjeId",
        "sats",
        "typeSats",
        "antallSats",
        "saksbehId",
        "uforeGrad",
        "kravhaverId",
        "delytelseId",
        "bostedsenhet",
        "skykldnerId",
        "klassekode",
        "klasseKodeBeskrivelse",
        "typeKlasse",
        "typeKlasseBeskrivelse",
        "refunderesOrgNr",
    )
    data class BeregningStoppnivaaDetaljer(
        val faktiskFom: String,
        val faktiskTom: String,
        val kontoStreng: String,
        val behandlingskode: String,
        // BigDecimal
        val belop: String,
        // Long
        val trekkVedtakId: String,
        // Long
        val stonadId: String? = null,
        // Long
        val korrigering: String? = null,
        // Boolean
        val tilbakeforing: String,
        // BigInteger
        val linjeId: String,
        // BigDecimal
        val sats: String,
        val typeSats: String,
        // BigDecimal
        val antallSats: String,
        val saksbehId: String,
        // BigInteger
        val uforeGrad: String,
        val kravhaverId: String? = null,
        val delytelseId: String? = null,
        val bostedsenhet: String,
        val skykldnerId: String? = null,
        val klassekode: String,
        val klasseKodeBeskrivelse: String,
        val typeKlasse: String,
        val typeKlasseBeskrivelse: String,
        val refunderesOrgNr: String? = null,
    )
}
