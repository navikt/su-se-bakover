package no.nav.su.se.bakover.web.services.klage.klageinstans

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.json.JSONObject
import java.time.Clock
import java.util.UUID

internal object KlageinstanshendelseMapper {
    fun map(
        message: ConsumerRecord<String, String>,
        topic: String,
        clock: Clock,
    ): Either<KunneIkkeMappeKlageinstanshendelse, no.nav.su.se.bakover.domain.klage.UprosessertKlageinstanshendelse> {
        val key = message.key()
        val value = message.value()

        val kilde = Either.catch { JSONObject(value).getString("kilde") }
            .getOrElse { return KunneIkkeMappeKlageinstanshendelse.FantIkkeKilde.left() }

        if (kilde != "SUPSTONAD") return KunneIkkeMappeKlageinstanshendelse.IkkeAktuellOpplysningstype(kilde).left()

        val eventId = Either.catch { JSONObject(value).getString("eventId") }
            .getOrElse { return KunneIkkeMappeKlageinstanshendelse.FantIkkeEventId.left() }

        return no.nav.su.se.bakover.domain.klage.UprosessertKlageinstanshendelse(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(clock),
            metadata = no.nav.su.se.bakover.domain.klage.UprosessertKlageinstanshendelse.Metadata(
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
    object FantIkkeKilde : KunneIkkeMappeKlageinstanshendelse
    object FantIkkeEventId : KunneIkkeMappeKlageinstanshendelse
}
