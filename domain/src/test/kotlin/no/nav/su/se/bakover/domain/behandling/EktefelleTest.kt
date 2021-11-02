package no.nav.su.se.bakover.domain.behandling

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.generer
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class EktefelleTest {

    @Test
    fun `er aldri ikke-oppfylt`() {
        Behandlingsinformasjon.EktefellePartnerSamboer.Ektefelle(
            Fnr.generer(),
            navn = Person.Navn(fornavn = "fornavn", mellomnavn = null, etternavn = "etternavn"),
            kjønn = null,
            fødselsdato = null,
            adressebeskyttelse = null,
            skjermet = null,
        ).erVilkårIkkeOppfylt() shouldBe false

        Behandlingsinformasjon.EktefellePartnerSamboer.IngenEktefelle
            .erVilkårIkkeOppfylt() shouldBe false
    }

    @Test
    fun `er oppfylt uansett hva man putter inn`() {
        Behandlingsinformasjon.EktefellePartnerSamboer.Ektefelle(
            Fnr.generer(),
            navn = Person.Navn(fornavn = "fornavn", mellomnavn = null, etternavn = "etternavn"),
            kjønn = null,
            fødselsdato = null,
            adressebeskyttelse = null,
            skjermet = null,
        ).erVilkårOppfylt() shouldBe true
        Behandlingsinformasjon.EktefellePartnerSamboer.IngenEktefelle.erVilkårOppfylt() shouldBe true
    }

    @Test
    fun `alder skal være 30 hvis fødselsdato er 30 år siden`() {
        val ektefelle = Behandlingsinformasjon.EktefellePartnerSamboer.Ektefelle(
            Fnr.generer(),
            navn = Person.Navn(fornavn = "fornavn", mellomnavn = null, etternavn = "etternavn"),
            kjønn = null,
            fødselsdato = fixedLocalDate.minusYears(30),
            adressebeskyttelse = null,
            skjermet = null,
        )

        ektefelle.getAlder().shouldBe(30)
    }

    @Test
    fun `alder skal være 29 hvis fødselsdato er 30 år siden i morgen`() {
        val ektefelle = Behandlingsinformasjon.EktefellePartnerSamboer.Ektefelle(
            Fnr.generer(),
            navn = Person.Navn(fornavn = "fornavn", mellomnavn = null, etternavn = "etternavn"),
            kjønn = null,
            // TODO jah: Kan ikke bytte denne til fixedLocalDate før Behandlingsinformasjon's getAlder() tar inn clock eller lignende.
            fødselsdato = LocalDate.now().minusYears(30).plusDays(1),
            adressebeskyttelse = null,
            skjermet = null,
        )

        ektefelle.getAlder().shouldBe(29)
    }
}
