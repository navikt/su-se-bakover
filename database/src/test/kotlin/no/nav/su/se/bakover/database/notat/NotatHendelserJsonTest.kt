package no.nav.su.se.bakover.database.notat

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.deserializeList
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.database.notat.NotatHendelserJson.Companion.toJson
import no.nav.su.se.bakover.domain.notat.NotatHandling
import no.nav.su.se.bakover.domain.notat.NotatHendelse
import org.junit.jupiter.api.Test
import java.time.Clock

internal class NotatHendelserJsonTest {

    private val clock = Clock.systemUTC()

    @Test
    fun `bevarer rolle for saksbehandler og attestant ved serialisering`() {
        val nå = Tidspunkt.now(clock)
        val hendelser = listOf(
            NotatHendelse(
                navIdent = NavIdentBruker.Saksbehandler("Z123456"),
                tidspunkt = nå,
                handling = NotatHandling.OPPRETTET,
            ),
            NotatHendelse(
                navIdent = NavIdentBruker.Attestant("Z654321"),
                tidspunkt = nå,
                handling = NotatHandling.OPPDATERT,
            ),
        )

        val actual = deserializeList<NotatHendelserJson>(serialize(hendelser.toJson()))
            .map { it.toDomain() }

        actual shouldBe hendelser
    }

    @Test
    fun `mangler rolle i gammel json tolkes som saksbehandler`() {
        val nå = Tidspunkt.now(clock)
        val gammelJson = """
            [
              {
                "tidspunkt": "$nå",
                "navIdent": "Z123456",
                "handling": "OPPRETTET"
              }
            ]
        """.trimIndent()

        val actual = deserializeList<NotatHendelserJson>(gammelJson)
            .map { it.toDomain() }

        actual shouldBe listOf(
            NotatHendelse(
                navIdent = NavIdentBruker.Saksbehandler("Z123456"),
                tidspunkt = nå,
                handling = NotatHandling.OPPRETTET,
            ),
        )
    }
}
