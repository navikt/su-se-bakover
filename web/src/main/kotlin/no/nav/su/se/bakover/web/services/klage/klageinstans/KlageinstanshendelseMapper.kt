package no.nav.su.se.bakover.web.services.klage.klageinstans

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import behandling.klage.domain.UprosessertKlageinstanshendelse
import no.nav.su.se.bakover.common.tid.Tidspunkt
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.json.JSONObject
import java.time.Clock
import java.util.UUID

internal data object KlageinstanshendelseMapper {
    fun map(
        message: ConsumerRecord<String, String>,
        topic: String,
        clock: Clock,
    ): Either<KunneIkkeMappeKlageinstanshendelse, UprosessertKlageinstanshendelse> {
        val key = message.key()
        val value = message.value()

        val kilde = Either.catch { JSONObject(value).getString("kilde") }
            .getOrElse { return KunneIkkeMappeKlageinstanshendelse.FantIkkeKilde.left() }

        if (kilde != "SUPSTONAD") return KunneIkkeMappeKlageinstanshendelse.IkkeAktuellOpplysningstype(kilde).left()

        val eventId = Either.catch { JSONObject(value).getString("eventId") }
            .getOrElse { return KunneIkkeMappeKlageinstanshendelse.FantIkkeEventId.left() }

        return UprosessertKlageinstanshendelse(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(clock),
            metadata = UprosessertKlageinstanshendelse.Metadata(
                topic = topic,
                hendelseId = eventId,
                offset = message.offset(),
                partisjon = message.partition(),
                key = key,
                value = value,
            ),
        ).right()
    }
}

sealed interface KunneIkkeMappeKlageinstanshendelse {
    data class IkkeAktuellOpplysningstype(val kilde: String) : KunneIkkeMappeKlageinstanshendelse
    data object FantIkkeKilde : KunneIkkeMappeKlageinstanshendelse
    data object FantIkkeEventId : KunneIkkeMappeKlageinstanshendelse
}
