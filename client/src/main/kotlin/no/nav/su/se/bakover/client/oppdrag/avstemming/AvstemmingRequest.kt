package no.nav.su.se.bakover.client.oppdrag.avstemming

import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement
import java.math.BigDecimal

// Denne klassen skal brukes til å lage avstemming xml requesten vi skal sende til oppdrag

@JacksonXmlRootElement(localName = "avstemmingsdata")
data class AvstemmingStartRequest(
    val aksjon: Aksjonsdata
)

@JacksonXmlRootElement(localName = "avstemmingsdata")
data class AvstemmingStoppRequest(
    val aksjon: Aksjonsdata
)

@JacksonXmlRootElement(localName = "avstemmingsdata")
data class AvstemmingDataRequest(
    val aksjon: Aksjonsdata,
    val total: Totaldata?,
    val periode: Periodedata,
    val grunnlag: Grunnlagdata,
    val detalj: List<Detaljdata>
) {
    data class Totaldata(
        val totalAntall: Int,
        val totalBelop: BigDecimal,
        val fortegn: Fortegn?
    )

    /*
        Når avstemming gjelder fra og til (dato + time) (yyyyMMddHH)
     */
    data class Periodedata(
        val datoAvstemtFom: String,
        val datoAvstemtTom: String
    )

    /*
        Grunnlagsrecord id-130
        godkjent = alvorlighetsgrad 00
        varsel = alvorlighetsgrad 04
        avvist = alvorlighetsgrad 08
        mangler = meldinger det ikke er mottatt kvitteringer
     */
    data class Grunnlagdata(
        val godkjentAntall: Int,
        val godkjentBelop: BigDecimal,
        val godkjenttFortegn: Fortegn,
        val varselAntall: Int,
        val varselBelop: BigDecimal,
        val varselFortegn: Fortegn,
        val avvistAntall: Int,
        val avvistBelop: BigDecimal,
        val avvistFortegn: Fortegn,
        val manglerAntall: Int,
        val manglerBelop: BigDecimal,
        val manglerFortegn: Fortegn
    )

    data class Detaljdata(
        val detaljType: Detaljtype,
        /*
            Hvem detaljen gjelder. Kan innehold dnr, fnr, TSS nummer eller orgnr. Tilsvarer oppdragGjelder
         */
        val offnr: String,
        val avleverendeTransaksjonNokkel: String,
        val tidspunkt: String // TODO: Finne format på denne
    ) {
        enum class Detaljtype(@JsonValue val value: String) {
            GODKJENT_MED_VARSEL("VARS"),
            AVVIST("AVVI"),
            MANGLENDE_KVITTERING("MANG");

            override fun toString() = value
        }
    }

    enum class Fortegn(@JsonValue val value: String) {
        TILLEGG("T"),
        FRADRAG("F");

        override fun toString() = value
    }
}

data class Aksjonsdata(
    val aksjonType: AksjonType,
    val kildeType: KildeType,
    val avstemmingType: AvstemmingType,
    val mottakendeKomponentKode: String,
    val brukerId: String // Saksbehandler
) {
    enum class AksjonType(@JsonValue val value: String) {
        START("START"),
        DATA("DATA"),
        AVSLUTT("AVSL");

        override fun toString() = value
    }

    enum class KildeType(@JsonValue val value: String) {
        AVLEVERT("AVLEV"),
        MOTTATT("MOTT");

        override fun toString() = value
    }

    enum class AvstemmingType(@JsonValue val value: String) {
        GRENSESNITTAVSTEMMING("GRSN"),
        KONSISTENSAVSTEMMING("KONS"),
        PERIODEAVSTEMMING("PERI");

        override fun toString() = value
    }
}
