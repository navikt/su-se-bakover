package no.nav.su.se.bakover.web

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

internal val objectMapper = jacksonObjectMapper()
    .registerModule(JavaTimeModule())
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

inline fun <reified K, reified V> ObjectMapper.readMap(value: String): Map<K, V> = readValue<Map<K, V>>(
    value,
    typeFactory.constructMapType(
        HashMap::class.java,
        K::class.java,
        V::class.java
    )
)
