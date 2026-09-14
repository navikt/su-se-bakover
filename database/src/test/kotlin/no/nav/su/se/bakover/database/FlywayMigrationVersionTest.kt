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
        val sqlMigrationFiles = findFiles(
            repositoryRoot = repositoryRoot,
            roots = listOf(repositoryRoot.resolve("database/src/main/resources/db")),
        )
        val classMigrationFiles = findFiles(
            repositoryRoot = repositoryRoot,
            roots = listOf(
                repositoryRoot.resolve("database/src/main/kotlin/db/migration"),
                repositoryRoot.resolve("database/src/main/java/db/migration"),
            ),
        )

        validateMigrationScriptVersions(
            sqlMigrationFiles = sqlMigrationFiles,
            classMigrationFiles = classMigrationFiles,
        )
    }

    @Test
    fun `godtar unike migreringsversjoner på tvers av kataloger`() {
        assertDoesNotThrow {
            validateMigrationScriptVersions(
                sqlMigrationFiles = listOf(
                    "db/migration/V1__opprett_tabell.sql",
                    "db/prod/V2__oppdater_data.sql",
                ),
                classMigrationFiles = listOf("db/migration/V3__oppdater_data.kt"),
            )
        }
    }

    @Test
    fun `avviser samme migreringsversjon på tvers av kataloger`() {
        val exception = assertThrows<IllegalArgumentException> {
            validateMigrationScriptVersions(
                sqlMigrationFiles = listOf(
                    "db/migration/V2__endre_tabell.sql",
                ),
                classMigrationFiles = listOf("db/migration/V2__oppdater_data.kt"),
            )
        }

        exception.message shouldContain "V2"
        exception.message shouldContain "db/migration/V2__endre_tabell.sql"
        exception.message shouldContain "db/migration/V2__oppdater_data.kt"
    }

    @Test
    fun `avviser migreringsfil som ikke er sql`() {
        val exception = assertThrows<IllegalArgumentException> {
            validateMigrationScriptVersions(
                sqlMigrationFiles = listOf("db/migration/V1__opprett_tabell.kt"),
                classMigrationFiles = emptyList(),
            )
        }

        exception.message shouldContain "db/migration/V1__opprett_tabell.kt"
    }

    @Test
    fun `avviser ugyldig navn på migreringsfil`() {
        val exception = assertThrows<IllegalArgumentException> {
            validateMigrationScriptVersions(
                sqlMigrationFiles = listOf("db/migration/V1_opprett_tabell.sql"),
                classMigrationFiles = emptyList(),
            )
        }

        exception.message shouldContain "db/migration/V1_opprett_tabell.sql"
    }

    private fun findFiles(
        repositoryRoot: Path,
        roots: List<Path>,
    ): List<String> = roots.flatMap { root ->
        if (Files.notExists(root)) {
            emptyList()
        } else {
            Files.walk(root).use { paths ->
                paths
                    .filter { Files.isRegularFile(it) }
                    .map { repositoryRoot.relativize(it).toString() }
                    .toList()
            }
        }
    }

    private fun findRepositoryRoot(): Path =
        generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .firstOrNull { Files.isDirectory(it.resolve("database/src/main/resources/db")) }
            ?: error("Fant ikke repository-roten")
}

private fun validateMigrationScriptVersions(
    sqlMigrationFiles: List<String>,
    classMigrationFiles: List<String>,
) {
    val migrations =
        parseMigrations(
            files = sqlMigrationFiles,
            filePattern = sqlMigrationFilePattern,
            expectedFileType = "SQL-fil",
        ) +
            parseMigrations(
                files = classMigrationFiles,
                filePattern = classMigrationFilePattern,
                expectedFileType = "Kotlin- eller Java-fil",
            )

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

private fun parseMigrations(
    files: List<String>,
    filePattern: Regex,
    expectedFileType: String,
): List<Migration> = files.map { path ->
    val fileName = path.substringAfterLast("/")
    val match = requireNotNull(filePattern.matchEntire(fileName)) {
        "Flyway-migrering må være en gyldig $expectedFileType: $path"
    }
    Migration(
        version = match.groupValues[1].toInt(),
        path = path,
    )
}

private data class Migration(
    val version: Int,
    val path: String,
)

private val sqlMigrationFilePattern = Regex("""^V(\d+)__.+\.sql$""")
private val classMigrationFilePattern = Regex("""^V(\d+)__.+\.(?:kt|java)$""")
