package person.domain

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.person.AktørId
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.person.Ident
import no.nav.su.se.bakover.common.tid.periode.Periode
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.Year

internal class PersonTest {
    private val periodeÅr = 2026
    private val fødselsårFor67IPerioden = 1959
    private val periode = Periode.create(
        fraOgMed = LocalDate.of(periodeÅr, 1, 1),
        tilOgMed = LocalDate.of(periodeÅr, 12, 31),
    )

    @Test
    fun `fyller 67 på periodens første dag`() {
        val fødsel = Person.Fødsel.MedFødselsdato(LocalDate.of(fødselsårFor67IPerioden, 1, 1))

        personMedFødsel(fødsel).fyller67ILøpetAvPeriode(periode) shouldBe true
    }

    @Test
    fun `fyller 67 inne i perioden`() {
        val fødsel = Person.Fødsel.MedFødselsdato(LocalDate.of(fødselsårFor67IPerioden, 6, 15))

        personMedFødsel(fødsel).fyller67ILøpetAvPeriode(periode) shouldBe true
    }

    @Test
    fun `fyller 67 på periodens siste dag`() {
        val fødsel = Person.Fødsel.MedFødselsdato(LocalDate.of(fødselsårFor67IPerioden, 12, 31))

        personMedFødsel(fødsel).fyller67ILøpetAvPeriode(periode) shouldBe true
    }

    @Test
    fun `fyller 67 dagen før perioden`() {
        val fødsel = Person.Fødsel.MedFødselsdato(LocalDate.of(1958, 12, 31))

        personMedFødsel(fødsel).fyller67ILøpetAvPeriode(periode) shouldBe false
    }

    @Test
    fun `fyller 67 dagen etter perioden`() {
        val fødsel = Person.Fødsel.MedFødselsdato(LocalDate.of(1960, 1, 1))

        personMedFødsel(fødsel).fyller67ILøpetAvPeriode(periode) shouldBe false
    }

    @Test
    fun `bare fødselsår gir ukjent resultat`() {
        val fødsel = Person.Fødsel.MedFødselsår(Year.of(fødselsårFor67IPerioden))

        personMedFødsel(fødsel).fyller67ILøpetAvPeriode(periode) shouldBe null
    }

    @Test
    fun `manglende fødselsopplysninger gir ukjent resultat`() {
        personMedFødsel(null).fyller67ILøpetAvPeriode(periode) shouldBe null
    }

    private fun personMedFødsel(fødsel: Person.Fødsel?) = Person(
        ident = Ident(
            fnr = Fnr("00000000000"),
            aktørId = AktørId("0000000000000"),
        ),
        navn = Person.Navn(
            fornavn = "Test",
            mellomnavn = null,
            etternavn = "Person",
        ),
        fødsel = fødsel,
    )
}
