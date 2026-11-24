package no.nav.su.se.bakover.institusjonsopphold.application.service

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.InstitusjonsoppholdHendelse
import no.nav.su.se.bakover.domain.InstitusjonsoppholdHendelseRepo
import no.nav.su.se.bakover.domain.InstitusjonsoppholdHendelserPåSak
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.institusjonsopphold.application.json.EksternInstitusjonsoppholdHendelseJson
import no.nav.su.se.bakover.institusjonsopphold.application.json.EksternInstitusjonsoppholdKildeJson
import no.nav.su.se.bakover.institusjonsopphold.application.json.EksternInstitusjonsoppholdTypeJson
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.nyEksternInstitusjonsoppholdHendelse
import no.nav.su.se.bakover.test.nyInstitusjonsoppholdHendelse
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattInnvilget
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import java.time.Clock

internal class InstitusjonsoppholdServiceTest {

    @Test
    fun `person har ikke sak blir prosessert men ingenting skjer`() {
        val correlationId = CorrelationId.generate()
        val fnrSomIkkeHarSak = Fnr.generer()
        val sakRepo = mock<SakRepo> { on { hentSaker(any()) } doReturn emptyList() }
        val testMocks = mockedServices(sakRepo = sakRepo)
        val melding = EksternInstitusjonsoppholdHendelseJson(
            hendelseId = 1,
            oppholdId = 2,
            norskident = fnrSomIkkeHarSak.toString(),
            type = EksternInstitusjonsoppholdTypeJson.INNMELDING,
            kilde = EksternInstitusjonsoppholdKildeJson.INST,
        )
        testMocks.institusjonsoppholdService()
            .process(serialize(melding), correlationId)
        verify(sakRepo).hentSaker(argThat { it shouldBe fnrSomIkkeHarSak })
        testMocks.verifyNoMoreInteractions()
    }

    @Test
    fun `melding uten øvrige hendelsesfelter ignoreres når personen ikke har sak`() {
        val correlationId = CorrelationId.generate()
        val fnrSomIkkeHarSak = Fnr.generer()
        val sakRepo = mock<SakRepo> { on { hentSaker(any()) } doReturn emptyList() }
        val testMocks = mockedServices(sakRepo = sakRepo)
        val meldingUtenHendelsesfelter = """{"norskident": "$fnrSomIkkeHarSak"}"""

        testMocks.institusjonsoppholdService().process(meldingUtenHendelsesfelter, correlationId)

        verify(sakRepo).hentSaker(argThat { it shouldBe fnrSomIkkeHarSak })
        testMocks.verifyNoMoreInteractions()
    }

    @Test
    fun `melding uten norskident feiler før saksoppslag`() {
        val correlationId = CorrelationId.generate()
        val testMocks = mockedServices()
        val meldingUtenNorskident = "{}"

        assertThrows<IllegalStateException> {
            testMocks.institusjonsoppholdService().process(meldingUtenNorskident, correlationId)
        }

        testMocks.verifyNoMoreInteractions()
    }

    @Test
    fun `ugyldig norskident gir nøytral feil uten identifikator før saksoppslag`() {
        val correlationId = CorrelationId.generate()
        val testMocks = mockedServices()
        val ugyldigNorskident = "${Fnr.generer()}A"
        val melding = EksternInstitusjonsoppholdHendelseJson(
            hendelseId = 1,
            oppholdId = 2,
            norskident = ugyldigNorskident,
            type = EksternInstitusjonsoppholdTypeJson.INNMELDING,
            kilde = EksternInstitusjonsoppholdKildeJson.INST,
        )

        val feil = assertThrows<IllegalArgumentException> {
            testMocks.institusjonsoppholdService().process(serialize(melding), correlationId)
        }

        feil.message shouldBe "Ugyldig norskident i institusjonsoppholdshendelse"
        feil.cause shouldBe null
        testMocks.verifyNoMoreInteractions()
    }

    @Test
    fun `melding med null kilde ignoreres selv om personen har sak`() {
        val correlationId = CorrelationId.generate()
        val sak = søknadsbehandlingIverksattInnvilget().first
        val sakRepo = mock<SakRepo> { on { hentSaker(any()) } doReturn listOf(sak) }
        val testMocks = mockedServices(sakRepo = sakRepo)
        val melding = EksternInstitusjonsoppholdHendelseJson(
            hendelseId = 1,
            oppholdId = 2,
            norskident = fnr.toString(),
            type = EksternInstitusjonsoppholdTypeJson.INNMELDING,
            kilde = null,
        )

        testMocks.institusjonsoppholdService().process(serialize(melding), correlationId)

        verify(sakRepo).hentSaker(argThat { it shouldBe fnr })
        testMocks.verifyNoMoreInteractions()
    }

    @Test
    fun `person har sak blir prosessert og hendelse blir knyttet til sak`() {
        val correlationId = CorrelationId.generate()
        val sak = søknadsbehandlingIverksattInnvilget().first
        val institusjonsoppholdHendelseRepo = mock<InstitusjonsoppholdHendelseRepo> {
            doNothing().whenever(it).lagre(any(), any())
            on { hentTidligereInstHendelserForOpphold(any(), any()) } doReturn emptyList()
            on { hentForSak(sak.id) } doReturn InstitusjonsoppholdHendelserPåSak(emptyList())
        }

        val sakRepo = mock<SakRepo> {
            on { hentSaker(any()) } doReturn listOf(sak)
        }
        val testMocks =
            mockedServices(sakRepo = sakRepo, institusjonsoppholdHendelseRepo = institusjonsoppholdHendelseRepo)
        val hendelse = nyEksternInstitusjonsoppholdHendelse()
        val melding = EksternInstitusjonsoppholdHendelseJson(
            hendelseId = hendelse.hendelseId,
            oppholdId = hendelse.oppholdId.value,
            norskident = hendelse.norskident.toString(),
            type = EksternInstitusjonsoppholdTypeJson.INNMELDING,
            kilde = EksternInstitusjonsoppholdKildeJson.INST,
        )
        testMocks.institusjonsoppholdService().process(serialize(melding), correlationId)
        verify(sakRepo).hentSaker(argThat { it shouldBe fnr })
        verify(institusjonsoppholdHendelseRepo).hentForSak(argThat { it shouldBe sak.id })
        verify(institusjonsoppholdHendelseRepo).hentTidligereInstHendelserForOpphold(
            argThat { it shouldBe sak.id },
            argThat { it shouldBe hendelse.oppholdId },
        )
        verify(institusjonsoppholdHendelseRepo).lagre(
            argThat {
                it shouldBe InstitusjonsoppholdHendelse(
                    sakId = sak.id,
                    hendelseId = it.hendelseId,
                    versjon = Hendelsesversjon(2),
                    eksterneHendelse = hendelse,
                    hendelsestidspunkt = fixedTidspunkt,
                )
            },
            argThat {
                it shouldBe DefaultHendelseMetadata.fraCorrelationId(correlationId)
            },
        )
        testMocks.verifyNoMoreInteractions()
    }

    @Test
    fun `knytter ny hendelse med en tidligere dersom de har samme oppholdId`() {
        val correlationId = CorrelationId.generate()
        val sak = søknadsbehandlingIverksattInnvilget().first
        val tidligereHendelse = nyInstitusjonsoppholdHendelse()
        val nyHendelse = nyEksternInstitusjonsoppholdHendelse(
            hendelseId = 2,
            oppholdId = tidligereHendelse.eksterneHendelse.oppholdId,
        )
        val melding = EksternInstitusjonsoppholdHendelseJson(
            hendelseId = nyHendelse.hendelseId,
            oppholdId = nyHendelse.oppholdId.value,
            norskident = nyHendelse.norskident.toString(),
            type = EksternInstitusjonsoppholdTypeJson.INNMELDING,
            kilde = EksternInstitusjonsoppholdKildeJson.INST,
        )

        val institusjonsoppholdHendelseRepo = mock<InstitusjonsoppholdHendelseRepo> {
            doNothing().whenever(it).lagre(any(), any())
            on { hentTidligereInstHendelserForOpphold(any(), any()) } doReturn listOf(tidligereHendelse)
            on { hentForSak(any()) } doReturn InstitusjonsoppholdHendelserPåSak(nonEmptyListOf(tidligereHendelse))
        }
        val sakRepo = mock<SakRepo> {
            on { hentSaker(any()) } doReturn listOf(sak)
        }
        val testMocks =
            mockedServices(
                sakRepo = sakRepo,
                institusjonsoppholdHendelseRepo = institusjonsoppholdHendelseRepo,
            )
        testMocks.institusjonsoppholdService().process(serialize(melding), correlationId)
        verify(sakRepo).hentSaker(argThat { it shouldBe fnr })
        verify(institusjonsoppholdHendelseRepo).hentForSak(argThat { it shouldBe sak.id })
        verify(institusjonsoppholdHendelseRepo).hentTidligereInstHendelserForOpphold(
            argThat { it shouldBe sak.id },
            argThat { it shouldBe nyHendelse.oppholdId },
        )
        verify(institusjonsoppholdHendelseRepo).lagre(
            argThat {
                it shouldBe InstitusjonsoppholdHendelse(
                    sakId = sak.id,
                    hendelseId = it.hendelseId,
                    tidligereHendelseId = tidligereHendelse.hendelseId,
                    // hendelse 2 fordi saken i denne testen ikke har tatt høyde for tidligere hendelsen
                    versjon = Hendelsesversjon(2),
                    eksterneHendelse = nyHendelse,
                    hendelsestidspunkt = fixedTidspunkt,
                )
            },
            argThat {
                it shouldBe DefaultHendelseMetadata.fraCorrelationId(correlationId)
            },
        )
        testMocks.verifyNoMoreInteractions()
    }

    private data class mockedServices(
        val institusjonsoppholdHendelseRepo: InstitusjonsoppholdHendelseRepo = mock(),
        val sakRepo: SakRepo = mock(),
        val clock: Clock = fixedClock,
    ) {
        fun institusjonsoppholdService(): EksternInstitusjonsoppholdKonsument = EksternInstitusjonsoppholdKonsument(
            institusjonsoppholdHendelseRepo = institusjonsoppholdHendelseRepo,
            sakRepo = sakRepo,
            clock = clock,
        )

        fun verifyNoMoreInteractions() {
            verifyNoMoreInteractions(
                institusjonsoppholdHendelseRepo,
                sakRepo,
            )
        }
    }
}
