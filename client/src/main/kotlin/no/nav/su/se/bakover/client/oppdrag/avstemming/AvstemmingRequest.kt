package no.nav.su.se.bakover.client.oppdrag.avstemming

import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement
import no.nav.su.se.bakover.client.oppdrag.OppdragDefaults
import no.nav.su.se.bakover.client.oppdrag.XmlMapper
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
internal data class GrensesnittsavstemmingData(
    val aksjon: Aksjonsdata.Grensesnittsavstemming,
    val total: Totaldata,
    val periode: Periodedata,
    val grunnlag: Grunnlagdata,
    val detalj: List<Detaljdata>,
) {

    fun startXml(): String {
        return XmlMapper.writeValueAsString(AvstemmingStartRequest(aksjon.start()))
    }

    fun dataXml(): String {
        return XmlMapper.writeValueAsString(this)
    }

    fun avsluttXml(): String {
        return XmlMapper.writeValueAsString(AvstemmingStoppRequest(aksjon.avslutt()))
    }

    /**
     * ID 130
     * godkjent = alvorlighetsgrad 00
     * varsel = alvorlighetsgrad 04
     * avvist = alvorlighetsgrad 08
     * mangler = meldinger det ikke er mottatt kvitteringer
     */
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
        fun totaltAntall() = godkjentAntall + varselAntall + avvistAntall + manglerAntall
    }

    /**
     * ID 140
     */
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
            MANGLENDE_KVITTERING("MANG");

            override fun toString() = value
        }
    }
}

/**
 * ID 120
 */
internal data class Totaldata(
    val totalAntall: Int,
    val totalBelop: BigDecimal,
    val fortegn: Fortegn?,
)

/**
 * ID 150
 */
internal data class Periodedata(
    val datoAvstemtFom: String,
    val datoAvstemtTom: String,
)

internal enum class Fortegn(@JsonValue val value: String) {
    TILLEGG("T"),
    FRADRAG("F");

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
internal data class KonsistensavstemmingData(
    val aksjonsdata: Aksjonsdata.Konsistensavstemming,
    val oppdragsdataListe: List<Oppdragsdata>? = null,
    val totaldata: Totaldata? = null,
) {

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

    data class Enhet(
        val enhetType: String,
        val enhet: String,
        val enhetFom: String,
    )

    data class VedtakPeriode(
        val fom: String,
        val tom: String,
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

    data class Attestant(
        val attestantId: String,
    )
}

sealed class Aksjonsdata {
    @JacksonXmlProperty
    @Suppress("unused")
    protected val kildeType: KildeType = KildeType.AVLEVERENDE

    @JacksonXmlProperty
    @Suppress("unused")
    protected val mottakendeKomponentKode: String = "OS"

    @JacksonXmlProperty
    @Suppress("unused")
    protected val brukerId: String = OppdragDefaults.SAKSBEHANDLER_ID

    @JacksonXmlProperty
    @Suppress("unused")
    protected val avleverendeKomponentKode: String = OppdragDefaults.KODE_KOMPONENT

    abstract val underkomponentKode: String

    abstract val aksjonType: AksjonType
    abstract val avstemmingType: AvstemmingType
    abstract val avleverendeAvstemmingId: String

    enum class AksjonType(@JsonValue val value: String) {
        START("START"),
        DATA("DATA"),
        AVSLUTT("AVSL");

        override fun toString() = value
    }

    enum class KildeType(@JsonValue val value: String) {
        AVLEVERENDE("AVLEV"),
        MOTTAKENDE("MOTT");

        override fun toString() = value
    }

    enum class AvstemmingType(@JsonValue val value: String) {
        GRENSESNITTAVSTEMMING("GRSN"),
        KONSISTENSAVSTEMMING("KONS"),
        PERIODEAVSTEMMING("PERI");

        override fun toString() = value
    }

    internal data class Grensesnittsavstemming(
        override val underkomponentKode: String,
        override val aksjonType: AksjonType = AksjonType.DATA,
        override val avleverendeAvstemmingId: String,
        val nokkelFom: String,
        val nokkelTom: String,
    ) : Aksjonsdata() {
        @JacksonXmlProperty
        override val avstemmingType: AvstemmingType = AvstemmingType.GRENSESNITTAVSTEMMING

        fun start(): Grensesnittsavstemming {
            return copy(aksjonType = AksjonType.START)
        }

        fun avslutt(): Grensesnittsavstemming {
            return copy(aksjonType = AksjonType.AVSLUTT)
        }
    }

    internal data class Konsistensavstemming(
        override val underkomponentKode: String,
        override val aksjonType: AksjonType = AksjonType.DATA,
        override val avleverendeAvstemmingId: String,
        val tidspunktAvstemmingTom: String,
    ) : Aksjonsdata() {
        @JacksonXmlProperty
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
}
