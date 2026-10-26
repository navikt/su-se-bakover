package no.nav.su.se.bakover.web.routes.person

import arrow.core.left
import behandling.revurdering.domain.bosituasjon.KunneIkkeLeggeTilBosituasjongrunnlagForRevurdering
import behandling.revurdering.domain.bosituasjon.LeggTilBosituasjonForRevurderingCommand
import behandling.revurdering.presentation.bosituasjon.tilResultat
import behandling.søknadsbehandling.domain.bosituasjon.KunneIkkeLeggeTilBosituasjongrunnlag
import behandling.søknadsbehandling.domain.bosituasjon.LeggTilBosituasjonCommand
import behandling.søknadsbehandling.presentation.bosituasjon.tilResultat
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fnr
import org.junit.jupiter.api.Test
import person.domain.KunneIkkeHentePerson
import java.time.LocalDate

class BosituasjonPersonoppslagTest {
    private val periode = Periode.create(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31))

    @Test
    fun `søknadsbehandling bevarer feiltype og mapper personoppslag til riktig status`() {
        val command = LeggTilBosituasjonCommand(
            periode = periode,
            epsFnr = fnr.toString(),
            delerBolig = null,
            ektemakeEllerSamboerUførFlyktning = false,
            epsFylt67 = false,
        )
        val tilfeller = listOf(
            Triple(
                KunneIkkeHentePerson.FantIkkePerson,
                KunneIkkeLeggeTilBosituasjongrunnlag.FantIkkeEPS,
                HttpStatusCode.NotFound,
            ),
            Triple(
                KunneIkkeHentePerson.IkkeTilgangTilPerson,
                KunneIkkeLeggeTilBosituasjongrunnlag.IkkeTilgangTilEPS,
                HttpStatusCode.Forbidden,
            ),
            Triple(
                KunneIkkeHentePerson.Ukjent,
                KunneIkkeLeggeTilBosituasjongrunnlag.KunneIkkeSlåOppEPS,
                HttpStatusCode.InternalServerError,
            ),
        )
        tilfeller.forEach { (årsak, forventetFeil, forventetStatus) ->
            val resultat = command.toDomain(fixedClock) { årsak.left() }
            resultat shouldBe forventetFeil.left()
            resultat.mapLeft { it.tilResultat().httpCode } shouldBe forventetStatus.left()
        }
    }

    @Test
    fun `revurdering bevarer feiltype og mapper personoppslag til riktig status`() {
        val command = LeggTilBosituasjonForRevurderingCommand(
            periode = periode,
            epsFnr = fnr.toString(),
            delerBolig = null,
            ektemakeEllerSamboerUførFlyktning = false,
            epsFylt67 = false,
        )
        val tilfeller = listOf(
            Triple(
                KunneIkkeHentePerson.FantIkkePerson,
                KunneIkkeLeggeTilBosituasjongrunnlagForRevurdering.FantIkkeEPS,
                HttpStatusCode.NotFound,
            ),
            Triple(
                KunneIkkeHentePerson.IkkeTilgangTilPerson,
                KunneIkkeLeggeTilBosituasjongrunnlagForRevurdering.IkkeTilgangTilEPS,
                HttpStatusCode.Forbidden,
            ),
            Triple(
                KunneIkkeHentePerson.Ukjent,
                KunneIkkeLeggeTilBosituasjongrunnlagForRevurdering.KunneIkkeSlåOppEPS,
                HttpStatusCode.InternalServerError,
            ),
        )
        tilfeller.forEach { (årsak, forventetFeil, forventetStatus) ->
            val resultat = command.toDomain(fixedClock) { årsak.left() }
            resultat shouldBe forventetFeil.left()
            resultat.mapLeft { it.tilResultat().httpCode } shouldBe forventetStatus.left()
        }
    }
}
