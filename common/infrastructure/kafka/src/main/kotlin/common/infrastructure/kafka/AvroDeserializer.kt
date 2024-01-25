package common.infrastructure.kafka

import arrow.core.Either
import arrow.core.getOrElse
import org.apache.avro.specific.SpecificRecord
import org.apache.kafka.common.serialization.Deserializer

class AvroDeserializer<T : SpecificRecord>(
    private val mapper: (ByteArray) -> T,
) : Deserializer<T> {
    override fun deserialize(topic: String, data: ByteArray): T {
        return Either.catch {
            /*
               KafkaAvroSerializer legger på 5 bytes, 1 magic byte og 4 som sier noe om hvilke entry i schema registeret som
               brukes. Siden vi ikke ønsker å ha et dependency til schema registryet har vi en egen deserializer og skipper de
               5 første bytene
               https://docs.confluent.io/3.2.0/schema-registry/docs/serializer-formatter.html#wire-format
             */
            // Check if the magic byte is present
            if (data[0].toInt() == 0) {
                // Skip the first 5 bytes (1 for magic byte, 4 for schema ID)
                val payload = data.copyOfRange(5, data.size)
                mapper(payload)
            } else {
                mapper(data)
            }
        }.getOrElse {
            throw RuntimeException(
                "Kunne ikke deserialisere avromelding fra topic: $topic. Antall bytes: ${data.size}. Første 15 bytes: ${data.take(15).joinToString(",")}.",
                it,
            )
        }
    }
}
