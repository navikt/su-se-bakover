package no.nav.su.se.bakover.db

import io.ktor.config.ApplicationConfigurationException
import io.ktor.config.MapApplicationConfig
import io.ktor.util.KtorExperimentalAPI
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertTrue

@KtorExperimentalAPI
internal class DataSourceBuilderTest {

    @Test
    internal fun `bygger riktig datasource basert på vaultMountPath`() {
        val env = MapApplicationConfig(
                "db.jdbcUrl" to "postgresql://localhost",
                "db.username" to "postgres",
                "db.password" to "postgres",
                "db.name" to "dbName"
        )

        assertTrue(DataSourceBuilder(env.apply { this.put("db.vaultMountPath", "") }).build() is EmbeddedPostgres)
        assertTrue(DataSourceBuilder(env.apply { this.put("db.vaultMountPath", "thePath") }).build() is VaultPostgres)
    }

    @Test
    internal fun `kaster ikke exception når tilkobling konfigureres riktig`() {
        assertDoesNotThrow {
            DataSourceBuilder(MapApplicationConfig(
                    "db.jdbcUrl" to "foobar",
                    "db.vaultMountPath" to "foobar",
                    "db.name" to "foobar"
            )).build()
        }

        assertDoesNotThrow {
            DataSourceBuilder(MapApplicationConfig(
                    "db.jdbcUrl" to "foobar",
                    "db.vaultMountPath" to "",
                    "db.username" to "foobar",
                    "db.password" to "foobar"
            )).build()
        }
    }

    @Test
    internal fun `kaster exception ved mangende konfig`() {
        assertThrows<ApplicationConfigurationException> {
            DataSourceBuilder(MapApplicationConfig()).build()
        }

        assertThrows<ApplicationConfigurationException> {
            DataSourceBuilder(MapApplicationConfig(
                    "db.name" to "foobar",
                    "db.username" to "foobar"
            )
            ).build()
        }

        assertThrows<ApplicationConfigurationException> {
            DataSourceBuilder(MapApplicationConfig(
                    "db.jdbcUrl" to "foobar",
                    "db.vaultMountPath" to "foobar"
            )).build()
        }

        assertThrows<ApplicationConfigurationException> {
            DataSourceBuilder(MapApplicationConfig(
                    "db.vaultMountPath" to "foobar",
                    "db.username" to "foobar"
            )).build()
        }

        assertThrows<ApplicationConfigurationException> {
            DataSourceBuilder(MapApplicationConfig(
                    "db.vaultMountPath" to "",
                    "db.password" to "foobar"
            )).build()
        }

        assertThrows<ApplicationConfigurationException> {
            DataSourceBuilder(MapApplicationConfig(
                    "db.jdbcUrl" to "foobar",
                    "db.password" to "foobar",
                    "db.vaultMountPath" to ""
            )).build()
        }
    }
}
