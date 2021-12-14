package no.nav.su.se.bakover.web.services.klage

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.json.JSONObject
import java.time.Clock
import java.util.UUID

internal object KlagevedtakMapper {
    fun map(
        message: ConsumerRecord<String, String>,
        clock: Clock,
    ): Either<KunneIkkeMappeKlagevedtak, no.nav.su.se.bakover.domain.klage.UprosessertFattetKlagevedtak> {
        val key = message.key()
        val value = message.value()

        val kilde = Either.catch { JSONObject(value).getString("kilde") }
            .getOrHandle { return KunneIkkeMappeKlagevedtak.FantIkkeKilde.left() }

        if (kilde != "SUPSTONAD") return KunneIkkeMappeKlagevedtak.IkkeAktuellOpplysningstype(kilde).left()

        val eventId = Either.catch { JSONObject(value).getString("eventId") }
            .getOrHandle { return KunneIkkeMappeKlagevedtak.FantIkkeEventId.left() }

        return no.nav.su.se.bakover.domain.klage.UprosessertFattetKlagevedtak(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(clock),
            metadata = no.nav.su.se.bakover.domain.klage.UprosessertFattetKlagevedtak.Metadata(
                hendelseId = eventId,
                offset = message.offset(),
                partisjon = message.partition(),
                key = key,
                value = value,
            ),
        ).right()
    }
}

sealed class KunneIkkeMappeKlagevedtak {
    data class IkkeAktuellOpplysningstype(val kilde: String) : KunneIkkeMappeKlagevedtak()
    object FantIkkeKilde : KunneIkkeMappeKlagevedtak()
    object FantIkkeEventId : KunneIkkeMappeKlagevedtak()
}
