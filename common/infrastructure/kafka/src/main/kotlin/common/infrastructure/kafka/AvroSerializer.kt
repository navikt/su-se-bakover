package common.infrastructure.kafka

import arrow.core.Either
import arrow.core.getOrElse
import org.apache.avro.specific.SpecificRecord
import org.apache.kafka.common.serialization.Serializer

class AvroSerializer<T : SpecificRecord>(
    private val mapper: (T) -> (ByteArray),
) : Serializer<T> {

    override fun serialize(topic: String?, data: T): ByteArray {
        return Either.catch {
//            // KafkaAvroSerializer legger på fem bytes, 1 magic byte og 4 som sier noe om hvilke entry i schema registeret,
//            // https://docs.confluent.io/3.2.0/schema-registry/docs/serializer-formatter.html#wire-format
            byteArrayOf(0, 0, 0, 0, 0) + mapper(data)
        }.getOrElse {
            throw RuntimeException(
                "Kunne ikke serialisere avromelding til topic: $topic. Skjema: ${data.schema.toString(true)}",
                it,
            )
        }
    }
}
