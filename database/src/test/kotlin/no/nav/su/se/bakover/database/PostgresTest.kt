package no.nav.su.se.bakover.database

import io.kotest.assertions.throwables.shouldNotThrowAny
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import org.junit.jupiter.api.Test

internal class PostgresTest {

    @Test
    internal fun `kaster ikke exception når tilkobling konfigureres riktig`() {
        shouldNotThrowAny {
            Postgres(
                ApplicationConfig.DatabaseConfig.StaticCredentials(
                    jdbcUrl = "postgresql://localhost",
                    username = "user",
                    password = "pwd",
                ),
            ).build()
        }
    }
}
