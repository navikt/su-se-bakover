package no.nav.su.se.bakover.service.søknad

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import io.kotest.assertions.arrow.either.shouldBeLeft
import io.kotest.assertions.arrow.either.shouldBeRight
import no.nav.su.se.bakover.database.søknad.SøknadRepo
import no.nav.su.se.bakover.domain.Søknad
import org.junit.jupiter.api.Test
import java.util.UUID

internal class SøknadKanLukkesTest {
    @Test
    fun `fant ikke søknad`() {
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn null
        }
        SøknadKanLukkes(søknadRepoMock).kanLukkes(UUID.randomUUID()) shouldBeLeft KunneIkkeLukkeSøknad.FantIkkeSøknad
        verify(søknadRepoMock).hentSøknad(any())
    }

    @Test
    fun `søknad er allerede lukket`() {
        val søknadMock = mock<Søknad> {
            on { erLukket() } doReturn true
        }
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn søknadMock
        }
        SøknadKanLukkes(søknadRepoMock).kanLukkes(UUID.randomUUID()) shouldBeLeft KunneIkkeLukkeSøknad.SøknadErAlleredeLukket
        verify(søknadRepoMock).hentSøknad(any())
    }

    @Test
    fun `søknad er knyttet til påbegynt behandling`() {
        val søknadMock = mock<Søknad> {
            on { erLukket() } doReturn false
        }
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn søknadMock
            on { harSøknadPåbegyntBehandling(any()) } doReturn true
        }
        SøknadKanLukkes(søknadRepoMock).kanLukkes(UUID.randomUUID()) shouldBeLeft KunneIkkeLukkeSøknad.SøknadHarEnBehandling
        verify(søknadRepoMock).hentSøknad(any())
    }

    @Test
    fun `returnerer søknad hvis den kan trekkes`() {
        val søknadMock = mock<Søknad> {
            on { erLukket() } doReturn false
        }
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn søknadMock
            on { harSøknadPåbegyntBehandling(any()) } doReturn false
        }
        SøknadKanLukkes(søknadRepoMock).kanLukkes(UUID.randomUUID()) shouldBeRight søknadMock
        verify(søknadRepoMock).hentSøknad(any())
    }
}
