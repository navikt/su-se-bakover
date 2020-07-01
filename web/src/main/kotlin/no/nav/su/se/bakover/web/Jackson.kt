package no.nav.su.se.bakover.web

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

internal val objectMapper = jacksonObjectMapper()

inline fun <reified K, reified V> ObjectMapper.readMap(value: String): Map<K, V> = readValue<Map<K, V>>(
    value,
    typeFactory.constructMapType(
        HashMap::class.java,
        K::class.java,
        V::class.java
    )
)
