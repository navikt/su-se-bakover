package no.nav.su.se.bakover.presentation.web

import dokument.domain.journalføring.KunneIkkeSjekkeTilknytningTilSak
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.infrastructure.web.ErrorJson
import org.junit.jupiter.api.Test

class KunneIkkeKnytteJournalpostTilSakToResultatMapperTest {
    @Test
    fun `manglende tilgang til journalpost gir forbidden og beholder feilkoden`() {
        val resultat = KunneIkkeSjekkeTilknytningTilSak.IkkeTilgang.toErrorJson()

        resultat.httpCode shouldBe HttpStatusCode.Forbidden
        deserialize<ErrorJson>(resultat.json) shouldBe ErrorJson(
            "Ikke tilgang til Journalpost",
            "ikke_tilgang_til_journalpost",
        )
    }
}
