package no.nav.su.se.bakover.common

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.serialization.jackson.JacksonConverter
import org.slf4j.LoggerFactory

/**
 * TODO jah: Denne bør ligger under common:infrastructure, men brukes blant annet av BrevInnhold, som ligger i domain. Dette krever en gradvis refaktorering.
 * Ikke bruk denne direkte. Bruk heller [serialize], [serializeNullable], [deserialize], [deserializeList] osv.
 * Den må være public for at inline/reified skal fungere.
 */

private val log = LoggerFactory.getLogger("no.nav.su.se.bakover.common.domain.Jackson")

@PublishedApi
internal val privateObjectMapper: ObjectMapper = JsonMapper.builder()
    .addModule(JavaTimeModule())
    .addModule(KotlinModule.Builder().build())
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    .disable(MapperFeature.ALLOW_FINAL_FIELDS_AS_MUTATORS)
    .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
    .enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
    .build()

inline fun <reified K, reified V> ObjectMapper.readMap(value: String): Map<K, V> {
    return readValue<Map<K, V>>(
        value,
        typeFactory.constructMapType(
            HashMap::class.java,
            K::class.java,
            V::class.java,
        ),
    ).onEach { throwIfContainsDomainObjects(it.key, it.value) }
}

/**
 * @param acceptDomainObjects If true, will throw if you try to serialize certain domain objects. In time it should throw if the package contains 'domain'.
 */
fun serialize(value: Any, acceptDomainObjects: Boolean = false): String {
    logIfIsString(value)
    if (!acceptDomainObjects) throwIfContainsDomainObjects(value)
    return privateObjectMapper.writeValueAsString(value)
}

fun serializeNullable(value: Any?): String? {
    return value?.let { serialize(it) }
}

inline fun <reified T> List<T>.serialize(): String {
    this.forEach { throwIfContainsDomainObjects(it) }
    this.forEach { logIfIsString(it) }
    val listType = privateObjectMapper.typeFactory.constructCollectionLikeType(List::class.java, T::class.java)
    return privateObjectMapper.writerFor(listType).writeValueAsString(this)
}

inline fun <reified T> String.deserializeList(): List<T> {
    val listType = privateObjectMapper.typeFactory.constructCollectionLikeType(List::class.java, T::class.java)
    return privateObjectMapper.readerFor(listType).readValue<List<T>?>(this).onEach { throwIfContainsDomainObjects(it) }
}

inline fun <reified T> deserialize(value: String): T {
    return privateObjectMapper.readValue<T>(value).also {
        throwIfContainsDomainObjects(it)
    }
}

inline fun <reified T> deserializeNullable(value: String?): T? {
    return value?.let { deserialize(it) }
}

inline fun <reified K, reified V> deserializeMap(value: String): Map<K, V> {
    return privateObjectMapper.readMap(value)
}

inline fun <reified K, reified V> deserializeMapNullable(value: String?): Map<K, V>? {
    return value?.let { deserializeMap(it) }
}

@JvmName("deserializeListValue")
inline fun <reified T> deserializeList(value: String): List<T> {
    return value.deserializeList()
}

inline fun <reified T> deserializeListNullable(value: String?): List<T>? {
    return value?.let { deserializeList(it) }
}

inline fun <reified Outer, reified Inner> deserializeParameterizedType(json: String): Outer {
    val type = privateObjectMapper.typeFactory.constructParametricType(Outer::class.java, Inner::class.java)
    return privateObjectMapper.readValue(json, type)
}

fun jsonNode(value: String): com.fasterxml.jackson.databind.JsonNode {
    return privateObjectMapper.readTree(value)
}

fun jacksonConverter(): JacksonConverter {
    return JacksonConverter(privateObjectMapper)
}

fun throwIfContainsDomainObjects(vararg types: Any?) {
    types.forEach { type ->
        if (type == null) return
        val exclusionList = listOf("Simulering")
        val currentType: String = type::class.java.simpleName
        if (exclusionList.contains(currentType)) {
            throw IllegalStateException("Don't serialize/deserialize domain types: $currentType")
        }
    }
}

fun <T> logIfIsString(value: T) {
    // På sikt ønsker vi endre denne til å kaste exception.
    if (value is String) {
        log.warn(
            "Oppdaget en tilfelle der vi serialiserer en string til json.",
            IllegalStateException("Trigger stacktrace for debug."),
        )
    }
}
