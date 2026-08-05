package no.nav.su.se.bakover.service.person

import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.person
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import person.domain.BorPåAdresse
import person.domain.Person
import person.domain.PersonOppslag
import person.domain.PersonRepo

internal class PersonServiceImplTest {

    private val personRepo = mock<PersonRepo>()

    private fun service(personOppslag: PersonOppslag) = PersonServiceImpl(personOppslag, personRepo)

    private fun personMedAdresselinje(adresselinje: String): Person =
        person().copy(
            adresse = listOf(
                Person.Adresse(
                    adresselinje = adresselinje,
                    poststed = Person.Poststed(postnummer = "1234", poststed = "OSLO"),
                    bruksenhet = null,
                    kommune = null,
                    adressetype = "Bostedsadresse",
                    adresseformat = "Vegadresse",
                ),
            ),
        )

    private fun borPåAdresse(husnummer: String) = BorPåAdresse(
        søktAdresse = "STORGATA $husnummer , 1234",
        treff = emptyList(),
    )

    @Test
    fun `borPåAdresse deler opp husnummer  og husbokstav`() {
        val personOppslag = mock<PersonOppslag> {
            on { person(fnr, Sakstype.UFØRE) } doReturn personMedAdresselinje("STORGATA 12B").right()
            on { borPåAdresse(any(), any()) } doReturn borPåAdresse("12").right()
        }

        service(personOppslag).borPåAdresse(fnr, Sakstype.UFØRE) shouldBe borPåAdresse("12").right()

        verify(personOppslag).borPåAdresse(
            argThat {
                it.adressenavn shouldBe "STORGATA"
                it.husnummer shouldBe "12"
                it.husbokstav shouldBe "B"
                it.postnummer shouldBe "1234"
                it.bruksenhetsnummer shouldBe ""
            },
            eq(Sakstype.UFØRE),
        )
    }

    @Test
    fun `borPåAdresse med husnummer  uten bokstav gir tom husbokstav`() {
        val personOppslag = mock<PersonOppslag> {
            on { person(fnr, Sakstype.UFØRE) } doReturn personMedAdresselinje("STORGATA 42").right()
            on { borPåAdresse(any(), any()) } doReturn borPåAdresse("42").right()
        }

        service(personOppslag).borPåAdresse(fnr, Sakstype.UFØRE) shouldBe borPåAdresse("42").right()

        verify(personOppslag).borPåAdresse(
            argThat {
                it.adressenavn shouldBe "STORGATA"
                it.husnummer shouldBe "42"
                it.husbokstav shouldBe ""
                it.postnummer shouldBe "1234"
            },
            eq(Sakstype.UFØRE),
        )
    }

    @Test
    fun `borPåAdresse beholder resten av gatenavnet med flere ord`() {
        val personOppslag = mock<PersonOppslag> {
            on { person(fnr, Sakstype.UFØRE) } doReturn personMedAdresselinje("GAMLE STORGATA 12B").right()
            on { borPåAdresse(any(), any()) } doReturn borPåAdresse("12").right()
        }

        service(personOppslag).borPåAdresse(fnr, Sakstype.UFØRE) shouldBe borPåAdresse("12").right()

        verify(personOppslag).borPåAdresse(
            argThat {
                it.adressenavn shouldBe "GAMLE STORGATA"
                it.husnummer shouldBe "12"
                it.husbokstav shouldBe "B"
                it.postnummer shouldBe "1234"
            },
            eq(Sakstype.UFØRE),
        )
    }
}
