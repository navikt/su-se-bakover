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
data class AvstemmingStartRequest(
    val aksjon: Aksjonsdata,
)

@JacksonXmlRootElement(localName = "avstemmingsdata")
data class AvstemmingStoppRequest(
    val aksjon: Aksjonsdata,
)

sealed class AvstemmingDataRequest {
    abstract val aksjon: Aksjonsdata

    fun startXml(): String {
        return XmlMapper.writeValueAsString(AvstemmingStartRequest(aksjon.start()))
    }

    abstract fun dataXml(): String

    fun avsluttXml(): String {
        return XmlMapper.writeValueAsString(AvstemmingStoppRequest(aksjon.avslutt()))
    }
}

@JacksonXmlRootElement(localName = "avstemmingsdata")
data class GrensesnittsavstemmingRequest(
    override val aksjon: Aksjonsdata.Grensesnittsavstemming,
    val total: Totaldata,
    val periode: Periodedata,
    val grunnlag: Grunnlagdata,
    val detalj: List<Detaljdata>,
) : AvstemmingDataRequest() {

    override fun dataXml(): String {
        return XmlMapper.writeValueAsString(this)
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
        val tidspunkt: String, // TODO: Finne format på denne
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
data class Totaldata(
    val totalAntall: Int,
    val totalBelop: BigDecimal,
    val fortegn: Fortegn?,
)

/**
 * ID 150
 */
data class Periodedata(
    val datoAvstemtFom: String,
    val datoAvstemtTom: String,
)

enum class Fortegn(@JsonValue val value: String) {
    TILLEGG("T"),
    FRADRAG("F");

    override fun toString() = value
}

@JacksonXmlRootElement(localName = "avstemmingsdata")
data class KonsistensavstemmingDataRequest(
    override val aksjon: Aksjonsdata.Konsistensavstemming,
    // TODO OPPDRAGS+LINJER
    val total: Totaldata,
) : AvstemmingDataRequest() {

    override fun dataXml(): String {
        return XmlMapper.writeValueAsString(this)
    }
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

    @JacksonXmlProperty
    @Suppress("unused")
    protected val underkomponentKode: String = OppdragDefaults.KODE_FAGOMRÅDE

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

    /**
     * ID 110 START
     */
    abstract fun start(): Aksjonsdata

    /**
     * ID 110 AVSLUTT
     */
    abstract fun avslutt(): Aksjonsdata

    data class Grensesnittsavstemming(
        override val aksjonType: AksjonType = AksjonType.DATA,
        override val avleverendeAvstemmingId: String,
        val nokkelFom: String,
        val nokkelTom: String,
    ) : Aksjonsdata() {
        @JacksonXmlProperty
        override val avstemmingType: AvstemmingType = AvstemmingType.GRENSESNITTAVSTEMMING

        override fun start(): Grensesnittsavstemming {
            return copy(aksjonType = AksjonType.START)
        }

        override fun avslutt(): Grensesnittsavstemming {
            return copy(aksjonType = AksjonType.AVSLUTT)
        }
    }

    data class Konsistensavstemming(
        override val aksjonType: AksjonType = AksjonType.DATA,
        override val avleverendeAvstemmingId: String,
        val tidspunktAvstemmingTom: String,
    ) : Aksjonsdata() {
        @JacksonXmlProperty
        override val avstemmingType: AvstemmingType = AvstemmingType.KONSISTENSAVSTEMMING

        override fun start(): Konsistensavstemming {
            return copy(aksjonType = AksjonType.START)
        }

        override fun avslutt(): Konsistensavstemming {
            return copy(aksjonType = AksjonType.AVSLUTT)
        }
    }
}
