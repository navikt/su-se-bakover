package økonomi.infrastructure.kvittering.consumer

import arrow.core.Either
import arrow.core.getOrElse
import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.su.se.bakover.client.oppdrag.utbetaling.UtbetalingRequest.OppdragRequest
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.tid.Tidspunkt
import økonomi.domain.kvittering.Kvittering
import økonomi.domain.kvittering.Kvittering.Utbetalingsstatus
import økonomi.infrastructure.kvittering.consumer.UtbetalingKvitteringResponse.Alvorlighetsgrad.ALVORLIG_FEIL
import økonomi.infrastructure.kvittering.consumer.UtbetalingKvitteringResponse.Alvorlighetsgrad.OK
import økonomi.infrastructure.kvittering.consumer.UtbetalingKvitteringResponse.Alvorlighetsgrad.OK_MED_VARSEL
import økonomi.infrastructure.kvittering.consumer.UtbetalingKvitteringResponse.Alvorlighetsgrad.SQL_FEIL
import økonomi.infrastructure.kvittering.consumer.UtbetalingKvitteringResponse.Companion.toKvitteringResponse
import java.time.Clock

/**
 * https://confluence.adeo.no/display/OKSY/Returdata+fra+Oppdragssystemet+til+fagrutinen
 */
@JacksonXmlRootElement(localName = "oppdrag")
data class UtbetalingKvitteringResponse(
    val mmel: Mmel,
    @field:JacksonXmlProperty(localName = "oppdrag-110")
    val oppdragRequest: OppdragRequest,
) {
    data class Mmel(
        val systemId: String?,
        val kodeMelding: String?,
        val alvorlighetsgrad: Alvorlighetsgrad,
        val beskrMelding: String?,
        val sqlKode: String?,
        val sqlState: String?,
        val sqlMelding: String?,
        val mqCompletionKode: String?,
        val mqReasonKode: String?,
        val programId: String?,
        val sectionNavn: String?,
    )

    fun utbetalingsId(): UUID30 {
        return oppdragRequest.utbetalingsId()
    }

    enum class Alvorlighetsgrad(@JsonValue val value: String) {
        OK("00"),

        /** En varselmelding følger med */
        OK_MED_VARSEL("04"),

        /** Alvorlig feil som logges og stopper behandling av aktuelt tilfelle*/
        ALVORLIG_FEIL("08"),
        SQL_FEIL("12"),
        ;

        override fun toString() = value
    }

    fun toKvittering(originalKvittering: String, clock: Clock) = Kvittering(
        utbetalingsstatus = when (mmel.alvorlighetsgrad) {
            OK -> Utbetalingsstatus.OK
            OK_MED_VARSEL -> Utbetalingsstatus.OK_MED_VARSEL
            ALVORLIG_FEIL, SQL_FEIL -> Utbetalingsstatus.FEIL
        },
        originalKvittering = originalKvittering,
        mottattTidspunkt = Tidspunkt.now(clock),
    )

    fun utbetalingstatus(): Utbetalingsstatus {
        return when (mmel.alvorlighetsgrad) {
            OK -> Utbetalingsstatus.OK
            OK_MED_VARSEL -> Utbetalingsstatus.OK_MED_VARSEL
            ALVORLIG_FEIL, SQL_FEIL -> Utbetalingsstatus.FEIL
        }
    }

    companion object {
        internal fun String.toKvitteringResponse(xmlMapper: XmlMapper): UtbetalingKvitteringResponse = this
            .let {
                // Oppdrag sendte fram til feb/mar 2024 <oppdrag xmlns ...> og avsluttet med </Oppdrag>
                // Dette har de nå fikset, men vi tar høyde for gamle meldinger
                if (it.contains("</Oppdrag>")) {
                    it.replace("</Oppdrag>", "</oppdrag>")
                } else {
                    it
                }
            }
            .let {
                Either.catch {
                    xmlMapper.readValue<UtbetalingKvitteringResponse>(it)
                }.getOrElse {
                    // TODO metric og sikkerlogg
                    throw it
                }
            }
    }
}

fun kvitteringXmlTilSaksnummerOgUtbetalingId(
    xmlMapper: XmlMapper,
): (String) -> Triple<Saksnummer, UUID30, Utbetalingsstatus> = {
    it.toKvitteringResponse(xmlMapper).let {
        Triple(it.oppdragRequest.saksnummer(), it.oppdragRequest.utbetalingsId(), it.utbetalingstatus())
    }
}
