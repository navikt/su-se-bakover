package no.nav.su.se.bakover.service.statistikk

import arrow.core.left
import arrow.core.right
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.domain.statistikk.SakStatistikk
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.domain.statistikk.SakStatistikkRepo
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.test.beregnetRevurdering
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.nySøknadsbehandlingUtenStønadsperiode
import no.nav.su.se.bakover.test.oversendtKlage
import no.nav.su.se.bakover.test.personMedAdresse
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import person.domain.Adressebeskyttelse
import person.domain.KunneIkkeHentePerson
import person.domain.PersonOppslag
import toBehandlingsstatistikkOverordnet
import java.math.BigInteger
import java.time.LocalDate
import java.util.UUID

internal class SakStatistikkServiceTest {
    private val fiktivSaksbehandler = NavIdentBruker.Saksbehandler("V123456")
    private val behandling = nySøknadsbehandlingUtenStønadsperiode(saksbehandler = fiktivSaksbehandler).second
    private val hendelse = StatistikkEvent.Behandling.Søknad.Opprettet(behandling, fiktivSaksbehandler)
    private val repo = mock<SakStatistikkRepo>()
    private val personOppslag = mock<PersonOppslag>()
    private val sakRepo = mock<SakRepo>()
    private val service = SakStatistikkService(repo, fixedClock, personOppslag, sakRepo)
    private val identForKode6 = "SKJERMET"
    private val vikafossen = "2103"

    @ParameterizedTest
    @EnumSource(value = Adressebeskyttelse::class, names = ["STRENGT_FORTROLIG", "STRENGT_FORTROLIG_UTLAND"])
    fun `kode 6 hos søker erstatter oppretter men bevarer fiktiv saksbehandler og manglende beslutter`(
        gradering: Adressebeskyttelse,
    ) {
        ordinærePersoner()
        whenever(personOppslag.person(behandling.fnr, behandling.sakstype))
            .thenReturn(personMedAdresse().copy(adressebeskyttelse = gradering.name).right())
        val vurdering = service.vurderForBruker(behandling)
        service.lagreForBrukerflyt(hendelse, vurdering, null)

        val rad = lagretRad()
        rad.opprettetAv shouldBe identForKode6
        rad.saksbehandler shouldBe fiktivSaksbehandler.navIdent
        rad.ansvarligBeslutter shouldBe null
        rad.ansvarligEnhet shouldBe vikafossen
    }

    @ParameterizedTest
    @EnumSource(value = Adressebeskyttelse::class, names = ["STRENGT_FORTROLIG", "STRENGT_FORTROLIG_UTLAND"])
    fun `kode 6 hos EPS erstatter også beslutterident`(gradering: Adressebeskyttelse) {
        val vedtak = vedtakSøknadsbehandlingIverksattInnvilget(
            clock = fixedClock,
            saksbehandler = fiktivSaksbehandler,
        ).second
        val iverksatt = StatistikkEvent.Behandling.Søknad.Iverksatt.Innvilget(vedtak)
        val annenPerson = requireNotNull(vedtak.behandling.søknad.søknadInnhold.boforhold.ektefellePartnerSamboer).fnr
        ordinærePersoner()
        whenever(personOppslag.person(annenPerson, vedtak.behandling.sakstype))
            .thenReturn(personMedAdresse().copy(adressebeskyttelse = gradering.name).right())
        val vurdering = service.vurderForBruker(vedtak.behandling)
        service.lagreForBrukerflyt(iverksatt, vurdering, null)

        val rad = lagretRad()
        rad.opprettetAv shouldBe identForKode6
        rad.ansvarligBeslutter shouldBe identForKode6
        rad.saksbehandler shouldBe fiktivSaksbehandler.navIdent
        rad.ansvarligEnhet shouldBe vikafossen
    }

    @ParameterizedTest
    @EnumSource(value = Adressebeskyttelse::class, names = ["FORTROLIG", "UGRADERT"])
    fun `kode 7 og ugraderte personer beholder ordinær statistikk`(gradering: Adressebeskyttelse) {
        whenever(personOppslag.person(any(), any()))
            .thenReturn(personMedAdresse().copy(adressebeskyttelse = gradering.name).right())
        val ordinærStatistikk = hendelse.toBehandlingsstatistikkOverordnet(fixedClock)
        val vurdering = service.vurderForBruker(behandling)
        service.lagreForBrukerflyt(hendelse, vurdering, null)

        lagretRad() shouldBe ordinærStatistikk
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `feilet oppslag på søker eller EPS avbryter før statistikk lagres`(feilHosSøker: Boolean) {
        ordinærePersoner()
        whenever(personOppslag.person(behandling.fnr, behandling.sakstype))
            .thenReturn(personMedAdresse().copy(adressebeskyttelse = Adressebeskyttelse.STRENGT_FORTROLIG.name).right())
        val eps = requireNotNull(behandling.søknad.søknadInnhold.boforhold.ektefellePartnerSamboer).fnr
        val personMedFeil = if (feilHosSøker) behandling.fnr else eps
        whenever(personOppslag.person(personMedFeil, behandling.sakstype)).thenReturn(KunneIkkeHentePerson.Ukjent.left())
        shouldThrow<SakStatistikkService.KunneIkkeVurdereKode6> {
            val vurdering = service.vurderForBruker(behandling)
            service.lagreForBrukerflyt(hendelse, vurdering, null)
        }

        verifyNoInteractions(repo)
    }

    @Test
    fun `automatisk behandling krever ikke personoppslag`() {
        service.lagreAutomatiskSystemflyt(hendelse, null)

        lagretRad() shouldBe hendelse.toBehandlingsstatistikkOverordnet(fixedClock)
        verifyNoInteractions(personOppslag, sakRepo)
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `oversendt klage med kode 6 hos søker eller EPS rapporteres med Vikafossen-enhet`(kode6HosSøker: Boolean) {
        val (sakMedVedtak, vedtak) = vedtakSøknadsbehandlingIverksattInnvilget(clock = fixedClock)
        val (sak, klage) = oversendtKlage(
            clock = fixedClock,
            saksbehandler = fiktivSaksbehandler,
            sakId = sakMedVedtak.id,
            sakMedVedtak = sakMedVedtak,
            vedtakId = vedtak.id,
        )
        ordinærePersoner()
        val eps = requireNotNull(sak.søknadsbehandlinger.single().søknad.søknadInnhold.boforhold.ektefellePartnerSamboer).fnr
        val kode6Person = if (kode6HosSøker) klage.fnr else eps
        whenever(personOppslag.person(kode6Person, klage.sakstype))
            .thenReturn(personMedAdresse().copy(adressebeskyttelse = Adressebeskyttelse.STRENGT_FORTROLIG.name).right())
        val vurdering = service.vurderForBruker(klage, sak)
        service.lagreForBrukerflyt(StatistikkEvent.Behandling.Klage.Oversendt(klage), vurdering, null)

        val rad = lagretRad()
        rad.ansvarligEnhet shouldBe vikafossen
        rad.opprettetAv shouldBe identForKode6
        rad.saksbehandler shouldBe fiktivSaksbehandler.navIdent
    }

    @Test
    fun `relatert revurdering kan vurderes før en klage er lagret`() {
        val (sak, revurdering) = beregnetRevurdering()
        ordinærePersoner()
        whenever(personOppslag.person(revurdering.fnr, revurdering.sakstype))
            .thenReturn(personMedAdresse().copy(adressebeskyttelse = Adressebeskyttelse.STRENGT_FORTROLIG.name).right())

        service.vurderForRelatertBehandling(sak, revurdering.id.value).harKode6 shouldBe true

        verifyNoInteractions(repo, sakRepo)
    }

    @Test
    fun `ukjent relatert behandling avbryter uten personoppslag eller lagring`() {
        val sak = nySøknadsbehandlingUtenStønadsperiode().first

        shouldThrow<IllegalStateException> {
            service.vurderForRelatertBehandling(sak, UUID.randomUUID())
        }

        verifyNoInteractions(personOppslag, repo)
    }

    @Test
    fun `BigQuery og erstatning sender kode 6-raden uten brukerkontekst`() = runBlocking<Unit> {
        ordinærePersoner()
        whenever(personOppslag.person(behandling.fnr, behandling.sakstype))
            .thenReturn(personMedAdresse().copy(adressebeskyttelse = Adressebeskyttelse.STRENGT_FORTROLIG.name).right())
        val vurdering = service.vurderForBruker(behandling)
        service.lagreForBrukerflyt(hendelse, vurdering, null)
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
            identForKode6,
            fiktivSaksbehandler.navIdent,
            "",
            vikafossen,
        )
    }

    private fun ordinærePersoner() {
        whenever(personOppslag.person(any(), any()))
            .thenReturn(personMedAdresse().copy(adressebeskyttelse = Adressebeskyttelse.UGRADERT.name).right())
    }

    private fun lagretRad(): SakStatistikk = argumentCaptor<SakStatistikk>().run {
        verify(repo).lagreSakStatistikk(capture(), eq(null))
        firstValue
    }
}
