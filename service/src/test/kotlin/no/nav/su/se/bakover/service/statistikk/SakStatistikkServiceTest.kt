package no.nav.su.se.bakover.service.statistikk

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import no.nav.su.se.bakover.common.domain.auth.Kontekst
import no.nav.su.se.bakover.common.domain.auth.TokenContext
import no.nav.su.se.bakover.common.domain.statistikk.SakStatistikk
import no.nav.su.se.bakover.common.domain.statistikk.StatistikkKode6Kontekst
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.statistikk.SakStatistikkRepo
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.nySøknadsbehandlingUtenStønadsperiode
import no.nav.su.se.bakover.test.oversendtKlage
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.slf4j.Logger
import toBehandlingsstatistikkOverordnet
import java.math.BigInteger
import java.time.LocalDate

internal class SakStatistikkServiceTest {
    private val fiktivSaksbehandler = NavIdentBruker.Saksbehandler("V123456")
    private val behandling = nySøknadsbehandlingUtenStønadsperiode(saksbehandler = fiktivSaksbehandler).second
    private val hendelse = StatistikkEvent.Behandling.Søknad.Opprettet(behandling, fiktivSaksbehandler)
    private val repo = mock<SakStatistikkRepo>()
    private val log = mock<Logger>()
    private val service = SakStatistikkService(repo, fixedClock, log)
    private val identForKode6 = "SKJERMET"
    private val vikafossen = "2103"

    @Test
    fun `kode 6 hos søker erstatter oppretter men bevarer fiktiv saksbehandler og manglende beslutter`() = runBlocking<Unit> {
        val kontekst = kontekst()
        kontekst.statistikkKode6.registrerPersonoppslag(behandling.fnr, harKode6 = true)

        withContext(Kontekst.asContextElement(kontekst)) {
            service.lagre(hendelse, null)
        }

        val rad = lagretRad()
        rad.opprettetAv shouldBe identForKode6
        rad.saksbehandler shouldBe fiktivSaksbehandler.navIdent
        rad.ansvarligBeslutter shouldBe null
        rad.ansvarligEnhet shouldBe vikafossen
        verifyNoInteractions(log)
    }

    @Test
    fun `kode 6 hos en annen involvert person erstatter også beslutterident`() = runBlocking<Unit> {
        val vedtak = vedtakSøknadsbehandlingIverksattInnvilget(
            clock = fixedClock,
            saksbehandler = fiktivSaksbehandler,
        ).second
        val iverksatt = StatistikkEvent.Behandling.Søknad.Iverksatt.Innvilget(vedtak)
        val annenPerson = Fnr.generer()
        val kontekst = kontekst()
        kontekst.statistikkKode6.registrerPersonoppslag(vedtak.behandling.fnr, harKode6 = false)
        kontekst.statistikkKode6.registrerPersonoppslag(annenPerson, harKode6 = true)

        withContext(Kontekst.asContextElement(kontekst)) {
            service.lagre(iverksatt, null)
        }

        val rad = lagretRad()
        rad.opprettetAv shouldBe identForKode6
        rad.ansvarligBeslutter shouldBe identForKode6
        rad.saksbehandler shouldBe fiktivSaksbehandler.navIdent
        rad.ansvarligEnhet shouldBe vikafossen
    }

    @Test
    fun `manglende gruppe logges men endrer ikke statistikk for kode 6`() = runBlocking<Unit> {
        val kontekst = kontekst(harTilgangTilStrengtFortroligAdresse = false)
        kontekst.statistikkKode6.registrerPersonoppslag(behandling.fnr, harKode6 = true)

        withContext(Kontekst.asContextElement(kontekst)) {
            service.lagre(hendelse, null)
        }

        lagretRad().ansvarligEnhet shouldBe vikafossen
        verify(log).error("Sakstatistikk har kode 6, men brukeren mangler forventet gruppe for strengt fortrolig adresse")
    }

    @Test
    fun `gruppe alene gir ikke kode 6-statistikk for en ordinær behandling`() = runBlocking<Unit> {
        val kontekst = kontekst()
        kontekst.statistikkKode6.registrerPersonoppslag(behandling.fnr, harKode6 = false)
        val ordinærStatistikk = hendelse.toBehandlingsstatistikkOverordnet(fixedClock)

        withContext(Kontekst.asContextElement(kontekst)) {
            service.lagre(hendelse, null)
        }

        lagretRad() shouldBe ordinærStatistikk
        verifyNoInteractions(log)
    }

    @Test
    fun `manglende eller feilede personoppslag gir ingen statistikkrad`() = runBlocking<Unit> {
        val kontekst = kontekst()
        withContext(Kontekst.asContextElement(kontekst)) {
            shouldThrow<IllegalStateException> { service.lagre(hendelse, null) }
            kontekst.statistikkKode6.registrerPersonoppslag(behandling.fnr, harKode6 = false)
            kontekst.statistikkKode6.registrerFeiletPersonoppslag(Fnr.generer())
            shouldThrow<IllegalStateException> { service.lagre(hendelse, null) }
        }

        verifyNoInteractions(repo)
    }

    @Test
    fun `uten brukerkontekst beholdes eksisterende statistikk`() {
        service.lagre(hendelse, null)

        lagretRad() shouldBe hendelse.toBehandlingsstatistikkOverordnet(fixedClock)
        verifyNoInteractions(log)
    }

    @Test
    fun `automatisk behandling kan utelate kode 6-kontekst fra et driftskall`() = runBlocking<Unit> {
        withContext(Kontekst.asContextElement(kontekst())) {
            service.lagre(hendelse, null, kode6Kontekst = null)
        }

        lagretRad() shouldBe hendelse.toBehandlingsstatistikkOverordnet(fixedClock)
        verifyNoInteractions(log)
    }

    @Test
    fun `oversendt kode 6-klage rapporteres med Vikafossen-enhet`() = runBlocking<Unit> {
        val klage = oversendtKlage(clock = fixedClock, saksbehandler = fiktivSaksbehandler).second
        val kontekst = kontekst()
        kontekst.statistikkKode6.registrerPersonoppslag(klage.fnr, harKode6 = true)

        withContext(Kontekst.asContextElement(kontekst)) {
            service.lagre(StatistikkEvent.Behandling.Klage.Oversendt(klage), null)
        }

        val rad = lagretRad()
        rad.ansvarligEnhet shouldBe vikafossen
        rad.opprettetAv shouldBe identForKode6
        rad.saksbehandler shouldBe fiktivSaksbehandler.navIdent
    }

    @Test
    fun `BigQuery og erstatning sender kode 6-raden uten brukerkontekst`() = runBlocking<Unit> {
        val kontekst = kontekst()
        kontekst.statistikkKode6.registrerPersonoppslag(behandling.fnr, harKode6 = true)
        withContext(Kontekst.asContextElement(kontekst)) {
            service.lagre(hendelse, null)
        }
        val sekvensId = 1L
        val sekvensIder = listOf(sekvensId)
        val unikeSekvensIder = sekvensIder.toSet()
        val rad = lagretRad().copy(sekvensId = BigInteger.valueOf(sekvensId))
        val rader = listOf(rad)
        val dato = LocalDate.now(fixedClock)
        whenever(repo.hentSakStatistikk(dato, dato)).thenReturn(rader)
        whenever(repo.hentSakStatistikk(unikeSekvensIder)).thenReturn(rader)
        val gateway = mock<SakStatistikkBigQueryGateway>()
        whenever(gateway.hentAntallRaderPerSekvensId(unikeSekvensIder)).thenReturn(mapOf(sekvensId to 1L))
        val bigQueryService = SakStatistikkBigQueryServiceImpl(repo, gateway)

        bigQueryService.lastTilBigQuery(dato)
        bigQueryService.erstattSakStatistikk(sekvensIder)

        verify(gateway, times(2)).writeToBigQuery(rader)
        rad.opprettetAv shouldBe identForKode6
        rad.ansvarligEnhet shouldBe vikafossen
        rader.toCsv().trimEnd().split(",").subList(20, 24) shouldBe listOf(
            "SKJERMET",
            fiktivSaksbehandler.navIdent,
            "",
            "2103",
        )
    }

    private fun kontekst(harTilgangTilStrengtFortroligAdresse: Boolean = true) = TokenContext(
        token = "test-token",
        statistikkKode6 = StatistikkKode6Kontekst(harTilgangTilStrengtFortroligAdresse),
    )

    private fun lagretRad(): SakStatistikk = argumentCaptor<SakStatistikk>().run {
        verify(repo).lagreSakStatistikk(capture(), eq(null))
        firstValue
    }
}
