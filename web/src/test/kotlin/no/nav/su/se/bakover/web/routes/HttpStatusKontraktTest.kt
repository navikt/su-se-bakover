package no.nav.su.se.bakover.web.routes

import behandling.søknadsbehandling.domain.KunneIkkeStarteSøknadsbehandling
import behandling.søknadsbehandling.presentation.tilResultat
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.infrastructure.web.ErrorJson
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.web.routes.tilbakekreving.forhåndsvarsel.toResultat
import no.nav.su.se.bakover.web.routes.tilbakekreving.kravgrunnlag.tilResultat
import org.junit.jupiter.api.Test
import person.domain.KunneIkkeHentePerson
import tilbakekreving.domain.forhåndsvarsel.KunneIkkeHenteUtsendtForhåndsvarsel
import tilbakekreving.domain.kravgrunnlag.KunneIkkeOppdatereKravgrunnlag
import tilgangstyring.domain.IkkeTilgangTilSak

class HttpStatusKontraktTest {
    @Test
    fun `påbegynt behandling og manglende omgjøringsgrunn er klientfeil`() {
        listOf(
            KunneIkkeStarteSøknadsbehandling.BehandlingErAlleredePåbegynt.tilResultat() to "behandling_er_allerede_påbegynt",
            Feilresponser.Omgjøring.måHaomgjøringsgrunn to "kunne_ikke_opprette_revurdering_mangler_omgjøringsgrunn",
            KunneIkkeOppdatereKravgrunnlag.FantIkkeUteståendeKravgrunnlag.tilResultat() to "fant_ikke_utestående_kravgrunnlag_på_sak",
        ).forEach { (resultat, forventetKode) ->
            resultat.httpCode shouldBe HttpStatusCode.BadRequest
            deserialize<ErrorJson>(resultat.json).code shouldBe forventetKode
        }
    }

    @Test
    fun `manglende utsendt forhåndsvarsel er ikke funnet`() {
        val resultat = KunneIkkeHenteUtsendtForhåndsvarsel.FantIkkeDokument.toResultat()

        resultat.httpCode shouldBe HttpStatusCode.NotFound
        deserialize<ErrorJson>(resultat.json).code shouldBe "fant_ikke_forhåndsvarsel_dokument"
    }

    @Test
    fun `manglende persontilgang ved henting av utsendt forhåndsvarsel gir forbudt`() {
        val feil = IkkeTilgangTilSak(KunneIkkeHentePerson.IkkeTilgangTilPerson)
        val resultat = KunneIkkeHenteUtsendtForhåndsvarsel.IkkeTilgang(feil).toResultat()

        resultat.httpCode shouldBe HttpStatusCode.Forbidden
        deserialize<ErrorJson>(resultat.json).code shouldBe "ikke_tilgang_til_sak"
    }

    @Test
    fun `tekniske feil ved oppstart av behandling forblir serverfeil`() {
        KunneIkkeStarteSøknadsbehandling.FeilVedOpprettingAvOppgave.tilResultat()
            .httpCode shouldBe HttpStatusCode.InternalServerError
    }
}
