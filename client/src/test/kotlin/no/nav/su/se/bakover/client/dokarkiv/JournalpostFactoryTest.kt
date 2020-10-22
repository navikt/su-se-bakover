package no.nav.su.se.bakover.client.dokarkiv

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import io.kotest.matchers.beOfType
import io.kotest.matchers.should
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Avslagsgrunn
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Ident
import no.nav.su.se.bakover.domain.LukketSøknadBrevinnhold
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Satsgrunn
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.VedtakInnhold
import org.junit.jupiter.api.Test

internal class JournalpostFactoryTest {

    private val fnr = Fnr("12345678910")

    @Test
    fun `mapper template til korrekt journalposttype`() {
        val personMock = mock<Person> {
            on { ident } doReturn Ident(fnr, AktørId("12345"))
            on { navn } doReturn Person.Navn("Fornavn", "mellomnavn", "etternavn")
        }

        val trukketSøknadBrevinnhold = LukketSøknadBrevinnhold.TrukketSøknadBrevinnhold(
            dato = "",
            datoSøknadOpprettet = "",
            datoSøkerTrakkSøknad = "",
            fødselsnummer = fnr,
            fornavn = "",
            mellomnavn = null,
            etternavn = "",
            adresse = null,
            husnummer = null,
            bruksenhet = null,
            postnummer = null,
            poststed = null
        )

        JournalpostFactory.lagJournalpost(
            person = personMock,
            sakId = "1",
            brevinnhold = trukketSøknadBrevinnhold,
            pdf = "".toByteArray()
        ) should beOfType<Journalpost.LukkSøknad>()

        val avslagsbrev = VedtakInnhold.Avslagsvedtak(
            dato = "",
            fødselsnummer = fnr,
            fornavn = "",
            etternavn = "",
            adresse = null,
            husnummer = null,
            bruksenhet = null,
            postnummer = null,
            poststed = "",
            satsbeløp = 0,
            fradragSum = 0,
            avslagsgrunn = Avslagsgrunn.FLYKTNING,
            halvGrunnbeløp = 0

        )
        JournalpostFactory.lagJournalpost(
            person = personMock,
            sakId = "1",
            brevinnhold = avslagsbrev,
            pdf = "".toByteArray()
        ) should beOfType<Journalpost.Vedtakspost>()

        val innvilgetvedtak = VedtakInnhold.Innvilgelsesvedtak(
            dato = "",
            fødselsnummer = fnr,
            fornavn = "",
            etternavn = "",
            adresse = null,
            husnummer = null,
            bruksenhet = null,
            postnummer = null,
            poststed = null,
            satsbeløp = 0,
            fradragSum = 0,
            månedsbeløp = 0,
            fradato = "",
            tildato = "",
            sats = "",
            satsGrunn = Satsgrunn.DELER_BOLIG_MED_EKTEMAKE_SAMBOER_OVER_67,
            redusertStønadStatus = false,
            harEktefelle = false,
            fradrag = listOf()

        )

        JournalpostFactory.lagJournalpost(
            person = personMock,
            sakId = "1",
            brevinnhold = innvilgetvedtak,
            pdf = "".toByteArray()
        ) should beOfType<Journalpost.Vedtakspost>()

        val søknadinnhold = SøknadInnholdTestdataBuilder.build()
        JournalpostFactory.lagJournalpost(
            person = personMock,
            sakId = "1",
            brevinnhold = søknadinnhold,
            pdf = "".toByteArray()
        ) should beOfType<Journalpost.Søknadspost>()
    }
}
