@file:Suppress("unused")

package no.nav.su.se.bakover.client.kabal

import behandling.klage.domain.Klagehjemler
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonValue
import no.nav.su.se.bakover.common.journal.JournalpostId
import java.time.LocalDate

/**
 * Docs:
 *  https://confluence.adeo.no/pages/viewpage.action?pageId=441059973
 *  https://confluence.adeo.no/display/FIP/Kabaldata
 *  https://kabal-api.dev.intern.nav.no/swagger-ui/index.html?urls.primaryName=external
 *
 * @param klager Fødselsnummer til klager.
 * @param fagsak Fagsak brukt tl journalføring. Dersom denne er tom, journalfører vi på en generell sak.
 * @param kildeReferanse Id som er intern for kildesytemet (f.eks. K9) så vedtak fra oss knyttes riktig i kilde. Her bruker vi vår interne/eksterne klageid (uuid).
 * @param dvhReferanse Id som rapporteres på til DVH, bruker kildeReferanse dersom denne ikke er satt. Her bruker vi vår interne/eksterne klageid (uuid).
 * @param hjemler Enum. Liste med hjemler. Disse vises i Kabal.
 * @param tilknyttedeJournalposter Relevante journalposter til klagen. Denne kan være tom. Disse vises i Kabal.
 * @param brukersHenvendelseMottattNavDato Dato for når klagen ble mottatt NAV.
 * @param innsendtTilNav Dato for når klagen ble innsendt. F.eks. posteringstidspunktet. Denne informasjonen registerer ikke SU (atm).
 * @param kommentar Fritekstfelt til klage der vi legger inn begrunnnelsen av formkravene samt klagenotatet til saksbehandler.
 * */
internal data class KabalRequest(
    val klager: Klager,
    val fagsak: Fagsak,
    val kildeReferanse: String,
    val dvhReferanse: String,
    val hjemler: List<Hjemmel>,
    val tilknyttedeJournalposter: List<TilknyttedeJournalposter>,
    val brukersHenvendelseMottattNavDato: LocalDate,
    val innsendtTilNav: LocalDate,
    val kommentar: String,
) {
    /** Enum. Gyldige verdier er [KLAGE,ANKE]. */
    @JsonInclude
    val type: String = "KLAGE"

    /** Id på enheten som behandlet vedtaket som denne henvendelsen gjelder. */
    @JsonInclude
    val forrigeBehandlendeEnhet: String = "4815"

    /** Enum. Kode for kildesystemet. Brukes til filtrering på Kafka når vedtaket sendes fra Kabal. */
    @JsonInclude
    val kilde: String = "SUPSTONAD"

    /** Enum. Ytelseskode */
    @JsonInclude
    val ytelse: String = "SUP_UFF"

    data class Klager(val id: PartId)

    data class TilknyttedeJournalposter(val type: Type, val journalpostId: JournalpostId) {
        enum class Type(private val verdi: String) {
            // TODO jah: Her kan vi legge til BRUKERS_SOEKNAD
            BRUKERS_KLAGE("BRUKERS_KLAGE"),
            OPPRINNELIG_VEDTAK("OPPRINNELIG_VEDTAK"),
            OVERSENDELSESBREV("OVERSENDELSESBREV"),
            ;

            override fun toString(): String = this.verdi
        }
    }

    /**
     * Dersom vi ønsker flere tilgjengelige hjemler, kan det bestilles fra Slack, #su-kabal-integrasjon eller #team-digital-klage
     */
    enum class Hjemmel(@JsonValue private val verdi: String) {
        LOV_OM_SUPPLERENDE_STØNAD_PARAGRAF_3("SUP_ST_L_3"),
        LOV_OM_SUPPLERENDE_STØNAD_PARAGRAF_4("SUP_ST_L_4"),
        LOV_OM_SUPPLERENDE_STØNAD_PARAGRAF_5("SUP_ST_L_5"),
        LOV_OM_SUPPLERENDE_STØNAD_PARAGRAF_6("SUP_ST_L_6"),
        LOV_OM_SUPPLERENDE_STØNAD_PARAGRAF_7("SUP_ST_L_7"),
        LOV_OM_SUPPLERENDE_STØNAD_PARAGRAF_8("SUP_ST_L_8"),
        LOV_OM_SUPPLERENDE_STØNAD_PARAGRAF_9("SUP_ST_L_9"),
        LOV_OM_SUPPLERENDE_STØNAD_PARAGRAF_10("SUP_ST_L_10"),
        LOV_OM_SUPPLERENDE_STØNAD_PARAGRAF_11("SUP_ST_L_11"),
        LOV_OM_SUPPLERENDE_STØNAD_PARAGRAF_12("SUP_ST_L_12"),
        LOV_OM_SUPPLERENDE_STØNAD_PARAGRAF_13("SUP_ST_L_13"),
        LOV_OM_SUPPLERENDE_STØNAD_PARAGRAF_17("SUP_ST_L_17"),
        LOV_OM_SUPPLERENDE_STØNAD_PARAGRAF_18("SUP_ST_L_18"),
        LOV_OM_SUPPLERENDE_STØNAD_PARAGRAF_21("SUP_ST_L_21"),
        LOV_OM_SUPPLERENDE_STØNAD_PARAGRAF_22("SUP_ST_L_22"),
        LOV_OM_BEHANDLINGSMÅTEN_I_FORVALTNINGSSAKER_PARAGRAF_12("FVL_12"),
        LOV_OM_BEHANDLINGSMÅTEN_I_FORVALTNINGSSAKER_PARAGRAF_28("FVL_28"),
        LOV_OM_BEHANDLINGSMÅTEN_I_FORVALTNINGSSAKER_PARAGRAF_29("FVL_29"),
        LOV_OM_BEHANDLINGSMÅTEN_I_FORVALTNINGSSAKER_PARAGRAF_31("FVL_31"),
        LOV_OM_BEHANDLINGSMÅTEN_I_FORVALTNINGSSAKER_PARAGRAF_32("FVL_32"),
        ;

        override fun toString(): String = this.verdi

        companion object {
            fun Klagehjemler.toKabalHjemler(): List<Hjemmel> {
                return this.map {
                    when (it) {
                        behandling.klage.domain.Hjemmel.SU_PARAGRAF_3 -> LOV_OM_SUPPLERENDE_STØNAD_PARAGRAF_3
                        behandling.klage.domain.Hjemmel.SU_PARAGRAF_4 -> LOV_OM_SUPPLERENDE_STØNAD_PARAGRAF_4
                        behandling.klage.domain.Hjemmel.SU_PARAGRAF_5 -> LOV_OM_SUPPLERENDE_STØNAD_PARAGRAF_5
                        behandling.klage.domain.Hjemmel.SU_PARAGRAF_6 -> LOV_OM_SUPPLERENDE_STØNAD_PARAGRAF_6
                        behandling.klage.domain.Hjemmel.SU_PARAGRAF_7 -> LOV_OM_SUPPLERENDE_STØNAD_PARAGRAF_7
                        behandling.klage.domain.Hjemmel.SU_PARAGRAF_8 -> LOV_OM_SUPPLERENDE_STØNAD_PARAGRAF_8
                        behandling.klage.domain.Hjemmel.SU_PARAGRAF_9 -> LOV_OM_SUPPLERENDE_STØNAD_PARAGRAF_9
                        behandling.klage.domain.Hjemmel.SU_PARAGRAF_10 -> LOV_OM_SUPPLERENDE_STØNAD_PARAGRAF_10
                        behandling.klage.domain.Hjemmel.SU_PARAGRAF_11 -> LOV_OM_SUPPLERENDE_STØNAD_PARAGRAF_11
                        behandling.klage.domain.Hjemmel.SU_PARAGRAF_12 -> LOV_OM_SUPPLERENDE_STØNAD_PARAGRAF_12
                        behandling.klage.domain.Hjemmel.SU_PARAGRAF_13 -> LOV_OM_SUPPLERENDE_STØNAD_PARAGRAF_13
                        behandling.klage.domain.Hjemmel.SU_PARAGRAF_17 -> LOV_OM_SUPPLERENDE_STØNAD_PARAGRAF_17
                        behandling.klage.domain.Hjemmel.SU_PARAGRAF_18 -> LOV_OM_SUPPLERENDE_STØNAD_PARAGRAF_18
                        behandling.klage.domain.Hjemmel.SU_PARAGRAF_21 -> LOV_OM_SUPPLERENDE_STØNAD_PARAGRAF_21
                        behandling.klage.domain.Hjemmel.SU_PARAGRAF_22 -> LOV_OM_SUPPLERENDE_STØNAD_PARAGRAF_22
                        behandling.klage.domain.Hjemmel.FVL_PARAGRAF_12 -> LOV_OM_BEHANDLINGSMÅTEN_I_FORVALTNINGSSAKER_PARAGRAF_12
                        behandling.klage.domain.Hjemmel.FVL_PARAGRAF_28 -> LOV_OM_BEHANDLINGSMÅTEN_I_FORVALTNINGSSAKER_PARAGRAF_28
                        behandling.klage.domain.Hjemmel.FVL_PARAGRAF_29 -> LOV_OM_BEHANDLINGSMÅTEN_I_FORVALTNINGSSAKER_PARAGRAF_29
                        behandling.klage.domain.Hjemmel.FVL_PARAGRAF_31 -> LOV_OM_BEHANDLINGSMÅTEN_I_FORVALTNINGSSAKER_PARAGRAF_31
                        behandling.klage.domain.Hjemmel.FVL_PARAGRAF_32 -> LOV_OM_BEHANDLINGSMÅTEN_I_FORVALTNINGSSAKER_PARAGRAF_32
                    }
                }
            }
        }
    }

    /**
     * @param fagsakId Her bruker vi saksnummer, det samme som vi bruker til journalføring og oppgaver.
     */
    data class Fagsak(
        val fagsakId: String,
    ) {
        /** Enum. */
        @JsonInclude
        val fagsystem: String = "SUPSTONAD"
    }

    /**
     * @param verdi Fødselsnummer
     */
    data class PartId(
        val verdi: String,
    ) {
        /** Enum. [PERSON,VIRKSOMHET] */
        @JsonInclude
        val type: String = "PERSON"
    }
}
