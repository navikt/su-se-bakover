package no.nav.su.se.bakover.database

import com.nhaarman.mockitokotlin2.mock
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.postgresql.util.PSQLException

internal class DatabaseExKtTest {
    private val datasource = EmbeddedDatabase.instance()

    @Test
    fun `kaster exception med hjelpende feilmelding hvis man forsøker å bruke særnorske tegn i parameter mapping`() {
        assertThrows<IllegalArgumentException> {
            "min flotte sql".insert(mapOf("æ" to 1), mock())
        }
        assertThrows<IllegalArgumentException> {
            "min flotte sql".insert(mapOf("Æ" to 1), mock())
        }
        assertThrows<IllegalArgumentException> {
            "min flotte sql".hent(mapOf("ø" to 1), mock()) {}
        }
        assertThrows<IllegalArgumentException> {
            "min flotte sql".hent(mapOf("Ø" to 1), mock()) {}
        }
        assertThrows<IllegalArgumentException> {
            "min flotte sql".hentListe(mapOf("å" to 1), mock()) {}
        }
        assertThrows<IllegalArgumentException> {
            "min flotte sql".hentListe(mapOf("Å" to 1), mock()) {}
        }
        assertThrows<IllegalArgumentException> {
            "min flotte sql".insert(mapOf("abcdefghijklmnopqrstuvwxyzæøå" to 1), mock())
        }
    }

    @Test
    fun `klarer å mappe andre ting enn særnorske tegn`() {
        assertDoesNotThrow {
            "min flotte sql".insert(mapOf("a" to 1), mock())
        }
        assertDoesNotThrow {
            "min flotte sql".insert(mapOf("abcdefghijklmnopqrstuvwxyz" to 1), mock())
        }
    }

    @Test
    fun `transaksjonelle spørringer committer og lagrer i databasen hvis alt er ok`() {
        withMigratedDb {
            datasource.withTransaction {
                """
                    CREATE TABLE IF NOT EXISTS test (id int not null)
                """.trimIndent()
                    .insert(emptyMap(), it)
                """
                    INSERT INTO test (id) VALUES (1)
                """.trimIndent()
                    .insert(emptyMap(), it)
            }
            datasource.withSession {
                """
                    SELECT COUNT (*) FROM test
                """.trimIndent().antall(emptyMap(), it) shouldBe 1
            }
        }
    }

    @Test
    fun `transaksjonelle spørringer ruller tilbake dersom noe går galt`() {
        withMigratedDb {
            try {
                datasource.withTransaction {
                    """
                        CREATE TABLE IF NOT EXISTS test (id int not null)
                    """.trimIndent()
                        .insert(emptyMap(), it)
                    """
                        INSERT INTO test (id) VALUES ('dette funker vel ikke')
                    """.trimIndent()
                        .insert(emptyMap(), it)
                }
            } catch (ex: Exception) {
            }
            assertThrows<PSQLException> {
                datasource.withSession {
                    """
                    SELECT COUNT (*) FROM test
                    """.trimIndent().antall(emptyMap(), it)
                }
            }.let {
                it.message shouldContain """relation "test" does not exist"""
            }
        }
    }
}
