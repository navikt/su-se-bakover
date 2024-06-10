package no.nav.su.se.bakover.dokument.infrastructure.database

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.test.nyDistribueringsAdresse
import org.junit.jupiter.api.Test

class DistribueringsAdresseJsonTest {

    @Test
    fun `ser & deser`() {
        val adresse = nyDistribueringsAdresse()
        val json = adresse.toDbJson()
        //language=json
        json shouldBe """{"adresselinje1":"Goldshire Inn","adresselinje2":"Elwynn Forest","adresselinje3":null,"postnummer":"123","poststed":"Elwynn"}"""
        deserializeDistribueringsadresse(json) shouldBe adresse
    }
}
