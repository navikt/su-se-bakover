package no.nav.su.se.bakover.database

import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.nio.file.Files
import java.nio.file.Path

internal class FlywayMigrationVersionTest {

    @Test
    fun `migreringsversjoner er unike på tvers av alle kataloger`() {
        val repositoryRoot = findRepositoryRoot()
        val migrationRoot = repositoryRoot.resolve("database/src/main/resources/db")
        val migrationFiles = Files.walk(migrationRoot).use { paths ->
            paths
                .filter { Files.isRegularFile(it) }
                .map { repositoryRoot.relativize(it).toString() }
                .toList()
        }

        validateMigrationScriptVersions(migrationFiles)
    }

    @Test
    fun `godtar unike migreringsversjoner på tvers av kataloger`() {
        assertDoesNotThrow {
            validateMigrationScriptVersions(
                listOf(
                    "db/migration/V1__opprett_tabell.sql",
                    "db/prod/V2__oppdater_data.sql",
                ),
            )
        }
    }

    @Test
    fun `avviser samme migreringsversjon på tvers av kataloger`() {
        val exception = assertThrows<IllegalArgumentException> {
            validateMigrationScriptVersions(
                listOf(
                    "db/migration/V2__endre_tabell.sql",
                    "db/prod/V2__oppdater_data.sql",
                ),
            )
        }

        exception.message shouldContain "V2"
        exception.message shouldContain "db/migration/V2__endre_tabell.sql"
        exception.message shouldContain "db/prod/V2__oppdater_data.sql"
    }

    @Test
    fun `avviser migreringsfil som ikke er sql`() {
        val exception = assertThrows<IllegalArgumentException> {
            validateMigrationScriptVersions(listOf("db/migration/V1__opprett_tabell.kt"))
        }

        exception.message shouldContain "db/migration/V1__opprett_tabell.kt"
    }

    @Test
    fun `avviser ugyldig navn på migreringsfil`() {
        val exception = assertThrows<IllegalArgumentException> {
            validateMigrationScriptVersions(listOf("db/migration/V1_opprett_tabell.sql"))
        }

        exception.message shouldContain "db/migration/V1_opprett_tabell.sql"
    }

    private fun findRepositoryRoot(): Path =
        generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .firstOrNull { Files.isDirectory(it.resolve("database/src/main/resources/db")) }
            ?: error("Fant ikke repository-roten")
}

private fun validateMigrationScriptVersions(files: List<String>) {
    val migrations = files.map { path ->
        require(path.endsWith(".sql")) {
            "Flyway-migrering må være en SQL-fil: $path"
        }
        val fileName = path.substringAfterLast("/")
        val match = requireNotNull(migrationFilePattern.matchEntire(fileName)) {
            "Ugyldig navn på Flyway-migrering: $path"
        }
        Migration(
            version = match.groupValues[1].toInt(),
            path = path,
        )
    }

    migrations
        .groupBy { it.version }
        .filterValues { it.size > 1 }
        .forEach { (version, duplicates) ->
            throw IllegalArgumentException(
                "Flyway-migreringer kan ikke bruke samme versjon på tvers av kataloger. " +
                    "V$version: ${duplicates.joinToString { it.path }}",
            )
        }
}

private data class Migration(
    val version: Int,
    val path: String,
)

private val migrationFilePattern = Regex("""^V(\d+)__.+\.sql$""")
