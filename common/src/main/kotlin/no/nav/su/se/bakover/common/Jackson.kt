package no.nav.su.se.bakover.common

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue

val objectMapper: ObjectMapper = JsonMapper.builder()
    .addModule(JavaTimeModule())
    .addModule(KotlinModule.Builder().build())
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    .disable(MapperFeature.ALLOW_FINAL_FIELDS_AS_MUTATORS)
    .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
    .enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
    .build()

inline fun <reified K, reified V> ObjectMapper.readMap(value: String): Map<K, V> = readValue<Map<K, V>>(
    value,
    typeFactory.constructMapType(
        HashMap::class.java,
        K::class.java,
        V::class.java
    )
)

fun serialize(value: Any): String = objectMapper.writeValueAsString(value)
inline fun <reified T> List<T>.serialize(): String {
    val listType = objectMapper.typeFactory.constructCollectionLikeType(List::class.java, T::class.java)
    return objectMapper.writerFor(listType).writeValueAsString(this)
}
inline fun <reified T> String.deserializeList(): List<T> {
    val listType = objectMapper.typeFactory.constructCollectionLikeType(List::class.java, T::class.java)
    // return objectMapper.readerFor(listType).readValue(this)
    return objectMapper.readerFor(listType).readValue(this)
}

inline fun <reified T> deserialize(value: String): T = objectMapper.readValue(value)
