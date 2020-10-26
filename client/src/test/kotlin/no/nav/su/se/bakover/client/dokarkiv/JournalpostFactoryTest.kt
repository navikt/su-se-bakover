package no.nav.su.se.bakover.client.dokarkiv

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import io.kotest.matchers.beOfType
import io.kotest.matchers.should
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Ident
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.brev.Brevdata
import no.nav.su.se.bakover.domain.brev.Brevtype
import org.junit.jupiter.api.Test
import java.util.UUID

internal class JournalpostFactoryTest {

    private val personMock = mock<Person>() {
        on { ident } doReturn Ident(Fnr("12345678910"), AktørId("12345"))
        on { navn } doReturn Person.Navn("fornavn", "mellomnavn", "etternavn")
    }
    private val sakId = UUID.randomUUID()
    private val pdf = "".toByteArray()

    @Test
    fun `lager vedtakspost for avslagsvedtak`() {
        val brevdata = mock<Brevdata>() {
            on { brevtype() } doReturn Brevtype.AvslagsVedtak
            on { toJson() } doReturn ""
        }
        JournalpostFactory.lagJournalpost(personMock, sakId, brevdata, pdf) should beOfType<Journalpost.Vedtakspost>()
    }

    @Test
    fun `lager vedtakspost for innvilget vedtak`() {
        val brevdata = mock<Brevdata>() {
            on { brevtype() } doReturn Brevtype.InnvilgetVedtak
            on { toJson() } doReturn ""
        }
        JournalpostFactory.lagJournalpost(personMock, sakId, brevdata, pdf) should beOfType<Journalpost.Vedtakspost>()
    }

    @Test
    fun `lager journalpost for en trukket søknad`() {
        val brevdata = mock<Brevdata>() {
            on { brevtype() } doReturn Brevtype.TrukketSøknad
            on { toJson() } doReturn ""
        }
        JournalpostFactory.lagJournalpost(personMock, sakId, brevdata, pdf) should beOfType<Journalpost.TrukketSøknad>()
    }
}
