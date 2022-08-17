package no.nav.su.se.bakover.client.kabal

import com.fasterxml.jackson.annotation.JsonValue
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.klage.Hjemler
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Docs:
 *  https://confluence.adeo.no/pages/viewpage.action?pageId=441059973
 *  https://confluence.adeo.no/display/FIP/Kabaldata
 *  https://kabal-api.dev.intern.nav.no/swagger-ui/?urls.primaryName=external#/kabal-api-external/sendInnKlageV2UsingPOST
 *
 *
 * @param avsenderSaksbehandlerIdent Ident til saksbehandler
 * @param dvhReferanse Intern referanse som Kabal bruker når de leverer statistikk
 * @param fagsak Informasjon om saken/klagen i systemet som kaller på Kabal.
 * @param hjemler Liste med hjemler. Disse vises i Kabal.
 * @param innsendtTilNav Dato for når klagen ble innsendt.
 * @param mottattFoersteinstans Dato for når klagen registrerades på første instans
 * @param kilde Kode for kildesystemet. Brukes til filtrering på Kafka når vedtaket sendes fra Kabal.
 * @param kildeReferanse Intern referanse på klagen
 * @param klager Id til Klager.
 * @param tilknyttedeJournalposter Relevante journalposter til klagen. Disse vises i Kabal.
 * @param oversendtKaDato Kan settes dersom denne saken har blitt sendt til Gosys og derfor har fristen begynt å løpe.
 * @param innsynUrl Url tilbake til kildesystem for innsyn i sak
 * @param type Gyldige verdier er "KLAGE" i både prod og dev
 * @param ytelse Ytelsekode
 * */
internal data class KabalRequest(
    val avsenderEnhet: String = "4815",
    val avsenderSaksbehandlerIdent: String,
    val dvhReferanse: String,
    val fagsak: Fagsak,
    val hjemler: List<Hjemmel>,
    val innsendtTilNav: LocalDate,
    val mottattFoersteinstans: LocalDate,
    val kilde: String = "SUPSTONAD",
    val kildeReferanse: String,
    val klager: Klager,
    val tilknyttedeJournalposter: List<TilknyttedeJournalposter>,
    val kommentar: String? = null,
    val frist: LocalDate? = null,
    val sakenGjelder: SakenGjelder? = null,
    val oversendtKaDato: LocalDateTime? = null,
    val innsynUrl: String? = null,
    val type: String = "KLAGE",
    val ytelse: String = "SUP_UFF",
) {
    data class Klager(val id: PartId)
    data class SakenGjelder(val id: PartId, val skalMottaKopi: Boolean)

    data class TilknyttedeJournalposter(val journalpostId: JournalpostId, val type: Type) {
        enum class Type(private val verdi: String) {
            ANNET("ANNET"),
            BRUKERS_KLAGE("BRUKERS_KLAGE"),
            OPPRINNELIG_VEDTAK("OPPRINNELIG_VEDTAK"),
            OVERSENDELSESBREV("OVERSENDELSESBREV");

            override fun toString(): String = this.verdi
        }
    }

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
        LOV_OM_SUPPLERENDE_STØNAD_PARAGRAF_21("SUP_ST_L_21");

        override fun toString(): String = this.verdi

        companion object {
            fun Hjemler.toKabalHjemler(): List<Hjemmel> {
                return this.map {
                    when (it) {
                        no.nav.su.se.bakover.domain.klage.Hjemmel.SU_PARAGRAF_3 -> LOV_OM_SUPPLERENDE_STØNAD_PARAGRAF_3
                        no.nav.su.se.bakover.domain.klage.Hjemmel.SU_PARAGRAF_4 -> LOV_OM_SUPPLERENDE_STØNAD_PARAGRAF_4
                        no.nav.su.se.bakover.domain.klage.Hjemmel.SU_PARAGRAF_5 -> LOV_OM_SUPPLERENDE_STØNAD_PARAGRAF_5
                        no.nav.su.se.bakover.domain.klage.Hjemmel.SU_PARAGRAF_6 -> LOV_OM_SUPPLERENDE_STØNAD_PARAGRAF_6
                        no.nav.su.se.bakover.domain.klage.Hjemmel.SU_PARAGRAF_7 -> LOV_OM_SUPPLERENDE_STØNAD_PARAGRAF_7
                        no.nav.su.se.bakover.domain.klage.Hjemmel.SU_PARAGRAF_8 -> LOV_OM_SUPPLERENDE_STØNAD_PARAGRAF_8
                        no.nav.su.se.bakover.domain.klage.Hjemmel.SU_PARAGRAF_9 -> LOV_OM_SUPPLERENDE_STØNAD_PARAGRAF_9
                        no.nav.su.se.bakover.domain.klage.Hjemmel.SU_PARAGRAF_10 -> LOV_OM_SUPPLERENDE_STØNAD_PARAGRAF_10
                        no.nav.su.se.bakover.domain.klage.Hjemmel.SU_PARAGRAF_11 -> LOV_OM_SUPPLERENDE_STØNAD_PARAGRAF_11
                        no.nav.su.se.bakover.domain.klage.Hjemmel.SU_PARAGRAF_12 -> LOV_OM_SUPPLERENDE_STØNAD_PARAGRAF_12
                        no.nav.su.se.bakover.domain.klage.Hjemmel.SU_PARAGRAF_13 -> LOV_OM_SUPPLERENDE_STØNAD_PARAGRAF_13
                        no.nav.su.se.bakover.domain.klage.Hjemmel.SU_PARAGRAF_17 -> LOV_OM_SUPPLERENDE_STØNAD_PARAGRAF_17
                        no.nav.su.se.bakover.domain.klage.Hjemmel.SU_PARAGRAF_18 -> LOV_OM_SUPPLERENDE_STØNAD_PARAGRAF_18
                        no.nav.su.se.bakover.domain.klage.Hjemmel.SU_PARAGRAF_21 -> LOV_OM_SUPPLERENDE_STØNAD_PARAGRAF_21
                    }
                }
            }
        }
    }

    data class Fagsak(
        val fagsakId: String,
        val fagsystem: String = "SUPSTONAD",
    )

    data class PartId(
        val type: String = "PERSON",
        val verdi: String,
    )
}
