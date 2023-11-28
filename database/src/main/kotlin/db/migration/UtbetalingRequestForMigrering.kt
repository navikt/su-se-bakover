package db.migration

import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement

/**
 * Forenkling av [no.nav.su.se.bakover.client.oppdrag.utbetaling.UtbetalingRequest] i klient.
 * Fjernet alt vi ikke bruker.
 * Slettes når migreringen er ferdig.
 */
@JacksonXmlRootElement(localName = "Oppdrag")
data class UtbetalingRequestForMigrering(
    @field:JacksonXmlProperty(localName = "oppdrag-110")
    val oppdragRequest: OppdragRequest,
) {
    @JsonPropertyOrder(
        "oppdragslinjer",
    )
    data class OppdragRequest(
        @field:JacksonXmlProperty(localName = "oppdrags-linje-150")
        val oppdragslinjer: List<Oppdragslinje>,
    )

    /**
     * Rekke følge må samsvare med prop-order for [no.trygdeetaten.skjema.oppdrag.OppdragsLinje150]
     */
    @JsonPropertyOrder(
        "kodeEndringLinje",
        "kodeStatusLinje",
        "datoStatusFom",
        "delytelseId",
        "kodeKlassifik",
        "datoVedtakFom",
        "datoVedtakTom",
        "satser/domain",
        "fradragTillegg",
        "typeSats",
        "brukKjoreplan",
        "saksbehId",
        "utbetalesTilId",
        "utbetalingId",
        "refFagsystemId",
        "refDelytelseId",
        "grad",
        "attestant",
    )
    data class Oppdragslinje(
        val kodeEndringLinje: KodeEndringLinje,
        val kodeStatusLinje: KodeStatusLinje?,
        val datoStatusFom: String?,
        /** Makslengde 30 */
        val delytelseId: String,
        /** [1,50] tegn */
        val kodeKlassifik: String,
        val datoVedtakFom: String,
        val datoVedtakTom: String,
        /**
         * <xsd:totalDigits value="13"/>
         * <xsd:fractionDigits value="2"/>
         * xsd:decimal
         * */
        val sats: String,
        val fradragTillegg: FradragTillegg,
        val typeSats: TypeSats,
        /** Lengde 1 tegn */
        val brukKjoreplan: Kjøreplan,
        /** saksbehandlerId - Makslengde 8 tegn */
        val saksbehId: String,
        /** Fødselsnummer eller Organisasjonsnummer [9,11] tegn */
        val utbetalesTilId: String,
        /** [0,30] tegn - en referanse til hvilken utbetaling-id (vår) utbetalingslinjen er koblet til */
        @field:JacksonXmlProperty(localName = "henvisning")
        val utbetalingId: String?,
        /** Makslengde 30 tegn */
        val refDelytelseId: String?,
        val refFagsystemId: String?,
        @field:JacksonXmlProperty(localName = "grad-170")
        val grad: Grad?,
        @field:JacksonXmlProperty(localName = "attestant-180")
        val attestant: List<Attestant>,
    ) {
        enum class KodeEndringLinje(@JsonValue val value: String) {
            NY("NY"),
            ENDRING("ENDR"),
            ;

            override fun toString() = value
        }

        enum class KodeStatusLinje(@JsonValue val value: String) {
            /**
             * Ny oppdragslinje som ikke tidligere er oversendt
             */
            NY("NY"),

            /**
             * Opphør/annullering av tidligere oversendte oppdragslinjer.
             */
            OPPHØR("OPPH"),

            /**
             * Hviler utbetalingene inntil nye instruks blir gitt (f.eks reaktivering eller erstattes av nye linjer).
             * Fører til at OS ikke inkluderer aktuelle linjer ved beregning av ytelsen.
             */
            HVIL("HVIL"),

            /**
             * Reaktivering oppdragsliner med status OPPH/HVIL.
             */
            REAKTIVER("REAK"),
            ;

            override fun toString() = value
        }

        enum class FradragTillegg(@JsonValue val value: String) {
            @Suppress("unused")
            FRADRAG("F"),
            TILLEGG("T"),
            ;

            override fun toString() = value
        }

        @Suppress("unused")
        enum class TypeSats(@JsonValue val value: String) {
            DAG("DAG"),
            UKE("UKE"),

            /** sic */
            FJORTEN_DB("14DB"),
            MND("MND"),
            AAR("AAR"),
            ENGANGSUTBETALING("ENG"),
            AKTO("AKTO"),
            ;

            override fun toString() = value
        }

        data class Grad(
            val typeGrad: TypeGrad,
            val grad: Int,
        )

        enum class TypeGrad(@JsonValue val value: String) {
            UFOR("UFOR"),
        }

        data class Attestant(
            /** [1,8] tegn */
            val attestantId: String,
        )

        /**
         * Fra doc: Bruk-kjoreplan gjør det mulig å velge om delytelsen skal beregnes/utbetales i henhold til kjøreplanen eller om dette skal skje idag.
         * Verdien 'N' medfører at beregningen kjøres idag. Beregningen vil bare gjelde beregningsperioder som allerede er forfalt.
         *
         * - JA: Dersom man ønsker å utbetale ved neste oppsatte kjøreplan (typisk rundt den 20. for Supplerende Stønad). Et eksempel når dette ønskes er i forbindelse med etterbetaling av regulering.
         * - NEI: Dersom man ønsker å utbetale etterbetalinger snarest. Dette er Supplerende Stønad sin default.
         */
        enum class Kjøreplan(@JsonValue val value: String) {
            JA("J"),
            NEI("N"),
            ;

            override fun toString() = value
        }
    }
}
