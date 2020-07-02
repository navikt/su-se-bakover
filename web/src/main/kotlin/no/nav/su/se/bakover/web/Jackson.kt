package no.nav.su.se.bakover.web

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import no.nav.su.se.bakover.web.routes.søknad.receiveTextUTF8

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

internal fun serialize(value: Any): String = objectMapper.writeValueAsString(value)

internal suspend inline fun <reified T> deserialize(call: ApplicationCall): T = deserialize(call.receiveTextUTF8())
internal inline fun <reified T> deserialize(value: String): T = objectMapper.readValue(value)
