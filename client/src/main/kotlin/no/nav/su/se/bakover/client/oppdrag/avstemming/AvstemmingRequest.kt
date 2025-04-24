package no.nav.su.se.bakover.client.oppdrag.avstemming

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement
import no.nav.su.se.bakover.client.oppdrag.OppdragDefaults
import no.nav.su.se.bakover.common.infrastructure.xml.xmlMapper
import java.math.BigDecimal

/**
 * https://github.com/navikt/tjenestespesifikasjoner/blob/master/avstemming-v1-tjenestespesifikasjon/src/main/wsdl/no/nav/virksomhet/tjenester/avstemming/meldinger/meldinger.xsd
 */
@JacksonXmlRootElement(localName = "avstemmingsdata")
internal data class AvstemmingStartRequest(
    val aksjon: Aksjonsdata,
)

@JacksonXmlRootElement(localName = "avstemmingsdata")
internal data class AvstemmingStoppRequest(
    val aksjon: Aksjonsdata,
)

@JacksonXmlRootElement(localName = "avstemmingsdata")
@JsonPropertyOrder(
    "aksjon",
    "total",
    "periode",
    "grunnlag",
    "detalj",
)
internal data class GrensesnittsavstemmingData(
    val aksjon: Grensesnittsavstemming,
    val total: Totaldata,
    val periode: Periodedata,
    val grunnlag: Grunnlagdata,
    val detalj: List<Detaljdata>,
) {

    fun startXml(): String {
        return xmlMapper.writeValueAsString(AvstemmingStartRequest(aksjon.start()))
    }

    fun dataXml(): String {
        return xmlMapper.writeValueAsString(this)
    }

    fun avsluttXml(): String {
        return xmlMapper.writeValueAsString(AvstemmingStoppRequest(aksjon.avslutt()))
    }

    /**
     * ID 130
     * godkjent = alvorlighetsgrad 00
     * varsel = alvorlighetsgrad 04
     * avvist = alvorlighetsgrad 08
     * mangler = meldinger det ikke er mottatt kvitteringer
     */
    @JsonPropertyOrder(
        "godkjentAntall",
        "godkjentBelop",
        "godkjentFortegn",
        "varselAntall",
        "varselBelop",
        "varselFortegn",
        "avvistAntall",
        "avvistBelop",
        "avvistFortegn",
        "manglerAntall",
        "manglerBelop",
        "manglerFortegn",
    )
    data class Grunnlagdata(
        val godkjentAntall: Int,
        val godkjentBelop: BigDecimal,
        val godkjentFortegn: Fortegn,
        val varselAntall: Int,
        val varselBelop: BigDecimal,
        val varselFortegn: Fortegn,
        val avvistAntall: Int,
        val avvistBelop: BigDecimal,
        val avvistFortegn: Fortegn,
        val manglerAntall: Int,
        val manglerBelop: BigDecimal,
        val manglerFortegn: Fortegn,
    ) {
        @JsonIgnore
        fun totaltAntall() = godkjentAntall + varselAntall + avvistAntall + manglerAntall
    }

    /**
     * ID 140
     */
    @JsonPropertyOrder(
        "detaljType",
        "offnr",
        "avleverendeTransaksjonNokkel",
        "tidspunkt",
    )
    data class Detaljdata(
        val detaljType: Detaljtype,
        /*
            Hvem detaljen gjelder. Kan innehold dnr, fnr, TSS nummer eller orgnr. Tilsvarer oppdragGjelder
         */
        val offnr: String,
        val avleverendeTransaksjonNokkel: String,
        val tidspunkt: String,
    ) {
        enum class Detaljtype(@JsonValue val value: String) {
            GODKJENT_MED_VARSEL("VARS"),
            AVVIST("AVVI"),
            MANGLENDE_KVITTERING("MANG"),
            ;

            override fun toString() = value
        }
    }
}

/**
 * ID 120
 */
@JsonPropertyOrder(
    "totalAntall",
    "totalBelop",
    "fortegn",
)
internal data class Totaldata(
    val totalAntall: Int,
    val totalBelop: BigDecimal,
    val fortegn: Fortegn?,
)

/**
 * ID 150
 */
@JsonPropertyOrder(
    "datoAvstemtFom",
    "datoAvstemtTom",
)
internal data class Periodedata(
    val datoAvstemtFom: String,
    val datoAvstemtTom: String,
)

internal enum class Fortegn(@JsonValue val value: String) {
    TILLEGG("T"),
    FRADRAG("F"),
    ;

    override fun toString() = value
}

@JacksonXmlRootElement(localName = "sendAsynkronKonsistensavstemmingsdata")
internal data class SendAsynkronKonsistensavstemmingsdata(
    val request: SendKonsistensavstemmingRequest,
)

internal data class SendKonsistensavstemmingRequest(
    val konsistensavstemmingsdata: KonsistensavstemmingData,
)

@JacksonXmlRootElement(localName = "konsistensavstemmingdata")
@JsonPropertyOrder(
    "aksjonsdata",
    "oppdragsdataListe",
    "totaldata",
)
internal data class KonsistensavstemmingData(
    val aksjonsdata: Konsistensavstemming,
    val oppdragsdataListe: List<Oppdragsdata>? = null,
    val totaldata: Totaldata? = null,
) {

    @JsonPropertyOrder(
        "fagomradeKode",
        "fagsystemId",
        "utbetalingsfrekvens",
        "oppdragGjelderId",
        "oppdragGjelderFom",
        "saksbehandlerId",
        "oppdragsenhetListe",
        "oppdragslinjeListe",
    )
    data class Oppdragsdata(
        val fagomradeKode: String,
        val fagsystemId: String,
        val utbetalingsfrekvens: String,
        val oppdragGjelderId: String,
        val oppdragGjelderFom: String,
        val saksbehandlerId: String,
        val oppdragsenhetListe: List<Enhet>,
        val oppdragslinjeListe: List<Oppdragslinje>,
    )

    @JsonPropertyOrder(
        "enhetType",
        "enhet",
        "enhetFom",
    )
    data class Enhet(
        val enhetType: String,
        val enhet: String,
        val enhetFom: String,
    )

    @JsonPropertyOrder(
        "fom",
        "tom",
    )
    data class VedtakPeriode(
        val fom: String,
        val tom: String,
    )

    @JsonPropertyOrder(
        "delytelseId",
        "klassifikasjonKode",
        "vedtakPeriode",
        "sats",
        "satstypeKode",
        "fradragTillegg",
        "brukKjoreplan",
        "utbetalesTilId",
        "attestantListe",
    )
    data class Oppdragslinje(
        val delytelseId: String,
        val klassifikasjonKode: String,
        val vedtakPeriode: VedtakPeriode,
        val sats: BigDecimal,
        val satstypeKode: String,
        val fradragTillegg: String,
        val brukKjoreplan: String,
        val utbetalesTilId: String,
        val attestantListe: List<Attestant>,
    )

    @JsonPropertyOrder(
        "attestantId",
    )
    data class Attestant(
        val attestantId: String,
    )
}

internal sealed interface Aksjonsdata {
    @get:JacksonXmlProperty
    @Suppress("unused")
    val kildeType: KildeType get() = KildeType.AVLEVERENDE

    @get:JacksonXmlProperty
    @Suppress("unused")
    val mottakendeKomponentKode: String get() = "OS"

    @get:JacksonXmlProperty
    @Suppress("unused")
    val brukerId: String get() = OppdragDefaults.SAKSBEHANDLER_ID

    @get:JacksonXmlProperty
    @Suppress("unused")
    val avleverendeKomponentKode: String get() = OppdragDefaults.KODE_KOMPONENT

    val underkomponentKode: String

    val aksjonType: AksjonType
    val avstemmingType: AvstemmingType
    val avleverendeAvstemmingId: String
}

internal enum class AksjonType(@JsonValue val value: String) {
    START("START"),
    DATA("DATA"),
    AVSLUTT("AVSL"),
    ;

    override fun toString() = value
}

internal enum class KildeType(@JsonValue val value: String) {
    AVLEVERENDE("AVLEV"),
    MOTTAKENDE("MOTT"),
    ;

    override fun toString() = value
}

internal enum class AvstemmingType(@JsonValue val value: String) {
    GRENSESNITTAVSTEMMING("GRSN"),
    KONSISTENSAVSTEMMING("KONS"),
    PERIODEAVSTEMMING("PERI"),
    ;

    override fun toString() = value
}

@JsonPropertyOrder(
    "aksjonType",
    "kildeType",
    "avstemmingType",
    "avleverendeKomponentKode",
    "mottakendeKomponentKode",
    "underkomponentKode",
    "nokkelFom",
    "nokkelTom",
    "avleverendeAvstemmingId",
    "brukerId",
)
internal data class Grensesnittsavstemming(
    override val underkomponentKode: String,
    override val aksjonType: AksjonType = AksjonType.DATA,
    override val avleverendeAvstemmingId: String,
    val nokkelFom: String,
    val nokkelTom: String,
) : Aksjonsdata {
    @field:JacksonXmlProperty
    override val avstemmingType: AvstemmingType = AvstemmingType.GRENSESNITTAVSTEMMING

    fun start(): Grensesnittsavstemming {
        return copy(aksjonType = AksjonType.START)
    }

    fun avslutt(): Grensesnittsavstemming {
        return copy(aksjonType = AksjonType.AVSLUTT)
    }
}

@JsonPropertyOrder(
    "aksjonType",
    "kildeType",
    "avstemmingType",
    "avleverendeKomponentKode",
    "mottakendeKomponentKode",
    "underkomponentKode",
    "tidspunktAvstemmingTom",
    "avleverendeAvstemmingId",
    "brukerId",
)
internal data class Konsistensavstemming(
    override val underkomponentKode: String,
    override val aksjonType: AksjonType = AksjonType.DATA,
    override val avleverendeAvstemmingId: String,
    val tidspunktAvstemmingTom: String,
) : Aksjonsdata {
    @field:JacksonXmlProperty
    override val avstemmingType: AvstemmingType = AvstemmingType.KONSISTENSAVSTEMMING

    fun start(): KonsistensavstemmingData {
        return KonsistensavstemmingData(
            aksjonsdata = copy(aksjonType = AksjonType.START),
        )
    }

    fun avslutt(): KonsistensavstemmingData {
        return KonsistensavstemmingData(
            aksjonsdata = copy(aksjonType = AksjonType.AVSLUTT),
        )
    }
}
