package no.nav.su.se.bakover.domain.behandling

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.FnrGenerator
import no.nav.su.se.bakover.domain.Person
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class EktefelleTest {

    @Test
    fun `er aldri ikke-oppfylt`() {
        Behandlingsinformasjon.EktefellePartnerSamboer.Ektefelle(
            FnrGenerator.random(),
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
            FnrGenerator.random(),
            navn = Person.Navn(fornavn = "fornavn", mellomnavn = null, etternavn = "etternavn"),
            kjønn = null,
            fødselsdato = null,
            adressebeskyttelse = null,
            skjermet = null,
        ).erVilkårOppfylt() shouldBe true
        Behandlingsinformasjon.EktefellePartnerSamboer.IngenEktefelle.erVilkårOppfylt() shouldBe true
    }

    @Test
    fun `har ingen avslagsgrunn`() {
        Behandlingsinformasjon.EktefellePartnerSamboer.Ektefelle(
            FnrGenerator.random(),
            navn = Person.Navn(fornavn = "fornavn", mellomnavn = null, etternavn = "etternavn"),
            kjønn = null,
            fødselsdato = null,
            adressebeskyttelse = null,
            skjermet = null,
        ).avslagsgrunn() shouldBe null
        Behandlingsinformasjon.EktefellePartnerSamboer.IngenEktefelle.avslagsgrunn() shouldBe null
    }

    @Test
    fun `alder skal være 30 hvis fødselsdato er 30 år siden`() {
        val ektefelle = Behandlingsinformasjon.EktefellePartnerSamboer.Ektefelle(
            FnrGenerator.random(),
            navn = Person.Navn(fornavn = "fornavn", mellomnavn = null, etternavn = "etternavn"),
            kjønn = null,
            fødselsdato = LocalDate.now().minusYears(30),
            adressebeskyttelse = null,
            skjermet = null,
        )

        ektefelle.getAlder().shouldBe(30)
    }

    @Test
    fun `alder skal være 29 hvis fødselsdato er 30 år siden i morgen`() {
        val ektefelle = Behandlingsinformasjon.EktefellePartnerSamboer.Ektefelle(
            FnrGenerator.random(),
            navn = Person.Navn(fornavn = "fornavn", mellomnavn = null, etternavn = "etternavn"),
            kjønn = null,
            fødselsdato = LocalDate.now().minusYears(30).plusDays(1),
            adressebeskyttelse = null,
            skjermet = null,
        )

        ektefelle.getAlder().shouldBe(29)
    }
}
