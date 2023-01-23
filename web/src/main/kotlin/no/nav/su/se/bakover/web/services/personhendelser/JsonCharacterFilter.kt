package no.nav.su.se.bakover.web.services.personhendelser

/**
 * PDL avro-serialiserer key-strengen (aktørId) som prepender den med en null-byte.
 * Fjerner ulovlige json characters.
 * https://en.wikipedia.org/wiki/Null_character
 *
 * Dette skal være en aktørId eller
 */
internal fun String.removeUnwantedJsonCharacters(): String {
    return this
        .replace("\\P{Print}".toRegex(), "")
        .replace("\\", "")
        .replace("\"", "")
}
