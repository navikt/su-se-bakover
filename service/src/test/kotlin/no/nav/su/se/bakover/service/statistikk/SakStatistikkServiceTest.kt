package no.nav.su.se.bakover.service.statistikk

import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import dokument.domain.Dokumenttilstand
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.domain.statistikk.SakStatistikk
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.mars
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.domain.statistikk.SakStatistikkRepo
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.test.attestant
import no.nav.su.se.bakover.test.beregnetRevurdering
import no.nav.su.se.bakover.test.bosituasjonEpsUnder67
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.iverksattAvvistKlage
import no.nav.su.se.bakover.test.kravgrunnlag.grunnlagsperiode
import no.nav.su.se.bakover.test.kravgrunnlag.kravgrunnlag
import no.nav.su.se.bakover.test.nySøknadsbehandlingUtenStønadsperiode
import no.nav.su.se.bakover.test.opprettetKlage
import no.nav.su.se.bakover.test.oversendtKlage
import no.nav.su.se.bakover.test.person
import no.nav.su.se.bakover.test.personMedAdresse
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import no.nav.su.se.bakover.test.vilkår.formuevilkårMedEps0Innvilget
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
import tilbakekreving.domain.IverksattTilbakekrevingsbehandling
import tilbakekreving.domain.TilbakekrevingsbehandlingId
import tilbakekreving.domain.Tilbakekrevingsbehandlinger
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import tilbakekreving.domain.vedtak.VedtakTilbakekrevingsbehandling
import toBehandlingsstatistikkOverordnet
import java.math.BigInteger
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.temporal.ChronoUnit
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
            .thenReturn(person().copy(adressebeskyttelse = gradering.name).right())
        val vurdering = service.vurderKode6ForStønadsbehandling(behandling)
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
            .thenReturn(person().copy(adressebeskyttelse = gradering.name).right())
        val vurdering = service.vurderKode6ForStønadsbehandling(vedtak.behandling)
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
        val vurdering = service.vurderKode6ForStønadsbehandling(behandling)
        service.lagreForBrukerflyt(hendelse, vurdering, null)

        lagretRad() shouldBe ordinærStatistikk
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `feilet oppslag på søker eller EPS avbryter før statistikk lagres`(feilHosSøker: Boolean) {
        ordinærePersoner()
        whenever(personOppslag.person(behandling.fnr, behandling.sakstype))
            .thenReturn(person().copy(adressebeskyttelse = Adressebeskyttelse.STRENGT_FORTROLIG.name).right())
        val eps = requireNotNull(behandling.søknad.søknadInnhold.boforhold.ektefellePartnerSamboer).fnr
        val personMedFeil = if (feilHosSøker) behandling.fnr else eps
        whenever(personOppslag.person(personMedFeil, behandling.sakstype)).thenReturn(KunneIkkeHentePerson.Ukjent.left())
        shouldThrow<SakStatistikkService.KunneIkkeVurdereKode6> {
            val vurdering = service.vurderKode6ForStønadsbehandling(behandling)
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
            .thenReturn(person().copy(adressebeskyttelse = Adressebeskyttelse.STRENGT_FORTROLIG.name).right())
        val vurdering = service.vurderKode6ForKlage(klage, sak)
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
            .thenReturn(person().copy(adressebeskyttelse = Adressebeskyttelse.STRENGT_FORTROLIG.name).right())

        service.vurderKode6ForRelatertBehandling(sak, revurdering.id.value).harKode6 shouldBe true

        verifyNoInteractions(repo, sakRepo)
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `klage på avvist klage følger vedtakskjeden til EPS i stønadsbehandlingen`(viaVedtak: Boolean) {
        val (sakMedStønadsvedtak, stønadsvedtak) = vedtakSøknadsbehandlingIverksattInnvilget(clock = fixedClock)
        val (sakMedKlagevedtak, tidligereKlage) = iverksattAvvistKlage(
            sakId = sakMedStønadsvedtak.id,
            sakMedVedtak = sakMedStønadsvedtak,
            vedtakId = stønadsvedtak.id,
            clock = fixedClock,
        )
        val klagevedtak = sakMedKlagevedtak.vedtakListe.single { it.behandling.id == tidligereKlage.id }
        val (sak, klage) = oversendtKlage(
            sakId = sakMedKlagevedtak.id,
            sakMedVedtak = sakMedKlagevedtak,
            vedtakId = klagevedtak.id,
            opprettet = tidligereKlage.opprettet.plus(1, ChronoUnit.DAYS),
            clock = fixedClock,
        )
        val eps = requireNotNull(stønadsvedtak.behandling.søknad.søknadInnhold.boforhold.ektefellePartnerSamboer).fnr
        ordinærePersoner()
        whenever(personOppslag.person(eps, sak.type))
            .thenReturn(person().copy(adressebeskyttelse = Adressebeskyttelse.STRENGT_FORTROLIG.name).right())

        val vurdering = if (viaVedtak) {
            service.vurderKode6ForKlage(klage, sak)
        } else {
            service.vurderKode6ForRelatertBehandling(sak, tidligereKlage.id.value)
        }

        vurdering.harKode6 shouldBe true
        verify(personOppslag).person(eps, sak.type)
        verifyNoInteractions(repo, sakRepo)
    }

    @Test
    fun `klage uten valgt vedtak følger relatert klage fra den første statistikklinjen`() {
        val (sakMedStønadsvedtak, stønadsvedtak) = vedtakSøknadsbehandlingIverksattInnvilget(clock = fixedClock)
        val (sakMedKlagevedtak, tidligereKlage) = iverksattAvvistKlage(
            sakId = sakMedStønadsvedtak.id,
            sakMedVedtak = sakMedStønadsvedtak,
            vedtakId = stønadsvedtak.id,
            clock = fixedClock,
        )
        val (sak, klage) = opprettetKlage(
            sakId = sakMedKlagevedtak.id,
            sakMedVedtak = sakMedKlagevedtak,
            opprettet = tidligereKlage.opprettet.plus(1, ChronoUnit.DAYS),
        )
        val førsteLinje = StatistikkEvent.Behandling.Klage.Opprettet(klage, tidligereKlage.id.value, null)
            .toBehandlingsstatistikkOverordnet(fixedClock)
        whenever(repo.hentInitiellBehandlingsstatistikk(klage.id, null)).thenReturn(førsteLinje)
        ordinærePersoner()
        val eps = requireNotNull(stønadsvedtak.behandling.søknad.søknadInnhold.boforhold.ektefellePartnerSamboer).fnr
        whenever(personOppslag.person(eps, sak.type))
            .thenReturn(person().copy(adressebeskyttelse = Adressebeskyttelse.STRENGT_FORTROLIG_UTLAND.name).right())

        service.vurderKode6ForKlage(klage, sak).harKode6 shouldBe true

        verify(personOppslag).person(eps, sak.type)
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `klage på tilbakekreving vurderer bare EPS i de faktiske kravgrunnlagsperiodene`(viaVedtak: Boolean) {
        val januar = januar(2021)
        val februar = februar(2021)
        val mars = mars(2021)
        val epsIJanuar = Fnr.generer()
        val epsIFebruar = Fnr.generer()
        val epsIMars = Fnr.generer()
        val månedsperioder = listOf(januar to epsIJanuar, februar to epsIFebruar, mars to epsIMars)
        val månedsvedtak = månedsperioder.mapIndexed { indeks, (måned, eps) ->
            val bosituasjon = bosituasjonEpsUnder67(periode = måned, fnr = eps)
            vedtakSøknadsbehandlingIverksattInnvilget(
                clock = Clock.offset(fixedClock, Duration.ofDays((indeks - månedsperioder.size).toLong())),
                stønadsperiode = Stønadsperiode.create(måned),
                customGrunnlag = listOf(bosituasjon),
                customVilkår = listOf(
                    formuevilkårMedEps0Innvilget(periode = måned, bosituasjon = nonEmptyListOf(bosituasjon)),
                ),
            )
        }
        val førsteSak = månedsvedtak.first().first
        val sakMedStønadsvedtak = førsteSak.copy(
            vedtakListe = månedsvedtak.map { it.second },
            behandlinger = førsteSak.behandlinger.copy(
                søknadsbehandlinger = månedsvedtak.map { it.second.behandling },
            ),
        )
        val (sakMedTilbakekrevingsvedtak, tilbakekrevingsvedtak) = sakMedTilbakekrevingsvedtak(
            sakMedStønadsvedtak,
            kravgrunnlag(
                grunnlagsperioder = nonEmptyListOf(grunnlagsperiode(periode = januar), grunnlagsperiode(periode = mars)),
            ),
        )
        val (sak, klage) = oversendtKlage(
            sakId = sakMedTilbakekrevingsvedtak.id,
            sakMedVedtak = sakMedTilbakekrevingsvedtak,
            vedtakId = tilbakekrevingsvedtak.id,
            clock = fixedClock,
        )
        ordinærePersoner()
        whenever(personOppslag.person(epsIFebruar, sak.type))
            .thenReturn(person().copy(adressebeskyttelse = Adressebeskyttelse.STRENGT_FORTROLIG.name).right())

        val vurdering = if (viaVedtak) {
            service.vurderKode6ForKlage(klage, sak)
        } else {
            service.vurderKode6ForRelatertBehandling(sak, tilbakekrevingsvedtak.behandling.id.value)
        }

        vurdering.harKode6 shouldBe false
        verify(personOppslag).person(sak.fnr, sak.type)
        verify(personOppslag).person(epsIJanuar, sak.type)
        verify(personOppslag).person(epsIMars, sak.type)
        org.mockito.kotlin.verifyNoMoreInteractions(personOppslag)
        verifyNoInteractions(repo, sakRepo)
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `klage på tilbakekreving avbryter ved feilet oppslag på søker eller EPS`(feilHosSøker: Boolean) {
        val eps = Fnr.generer()
        val bosituasjon = bosituasjonEpsUnder67(fnr = eps)
        val (sakMedStønadsvedtak, _) = vedtakSøknadsbehandlingIverksattInnvilget(
            clock = fixedClock,
            customGrunnlag = listOf(bosituasjon),
            customVilkår = listOf(formuevilkårMedEps0Innvilget(bosituasjon = nonEmptyListOf(bosituasjon))),
        )
        val (sakMedVedtak, tilbakekrevingsvedtak) = sakMedTilbakekrevingsvedtak(sakMedStønadsvedtak, kravgrunnlag())
        val (sak, klage) = oversendtKlage(
            sakId = sakMedVedtak.id,
            sakMedVedtak = sakMedVedtak,
            vedtakId = tilbakekrevingsvedtak.id,
            clock = fixedClock,
        )
        val personMedFeil = if (feilHosSøker) sak.fnr else eps
        ordinærePersoner()
        whenever(personOppslag.person(personMedFeil, sak.type)).thenReturn(KunneIkkeHentePerson.Ukjent.left())

        shouldThrow<SakStatistikkService.KunneIkkeVurdereKode6> {
            val vurdering = service.vurderKode6ForKlage(klage, sak)
            service.lagreForBrukerflyt(StatistikkEvent.Behandling.Klage.Oversendt(klage), vurdering, null)
        }

        verifyNoInteractions(repo)
    }

    @Test
    fun `sirkulær klagereferanse avbryter uten personoppslag`() {
        val (sak, klage) = opprettetKlage()
        val førsteLinje = StatistikkEvent.Behandling.Klage.Opprettet(klage, klage.id.value, null)
            .toBehandlingsstatistikkOverordnet(fixedClock)
        whenever(repo.hentInitiellBehandlingsstatistikk(klage.id, null)).thenReturn(førsteLinje)

        shouldThrow<IllegalStateException> {
            service.vurderKode6ForKlage(klage, sak)
        }

        verifyNoInteractions(personOppslag, sakRepo)
    }

    @Test
    fun `ukjent relatert behandling avbryter uten personoppslag eller lagring`() {
        val sak = nySøknadsbehandlingUtenStønadsperiode().first

        shouldThrow<IllegalStateException> {
            service.vurderKode6ForRelatertBehandling(sak, UUID.randomUUID())
        }

        verifyNoInteractions(personOppslag, repo)
    }

    @Test
    fun `BigQuery og erstatning sender kode 6-raden uten brukerkontekst`() = runBlocking<Unit> {
        ordinærePersoner()
        whenever(personOppslag.person(behandling.fnr, behandling.sakstype))
            .thenReturn(person().copy(adressebeskyttelse = Adressebeskyttelse.STRENGT_FORTROLIG.name).right())
        val vurdering = service.vurderKode6ForStønadsbehandling(behandling)
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

    private fun sakMedTilbakekrevingsvedtak(
        sak: Sak,
        kravgrunnlag: Kravgrunnlag,
    ): Pair<Sak, VedtakTilbakekrevingsbehandling> {
        val tilbakekreving = mock<IverksattTilbakekrevingsbehandling>()
        whenever(tilbakekreving.id).thenReturn(TilbakekrevingsbehandlingId.generer())
        whenever(tilbakekreving.sakId).thenReturn(sak.id)
        whenever(tilbakekreving.kravgrunnlag).thenReturn(kravgrunnlag)
        whenever(tilbakekreving.versjon).thenReturn(Hendelsesversjon.ny())
        val vedtak = VedtakTilbakekrevingsbehandling(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            saksbehandler = fiktivSaksbehandler,
            attestant = attestant,
            dokumenttilstand = Dokumenttilstand.GENERERT,
            behandling = tilbakekreving,
        )
        return sak.copy(
            vedtakListe = sak.vedtakListe + vedtak,
            behandlinger = sak.behandlinger.copy(
                tilbakekrevinger = Tilbakekrevingsbehandlinger(sak.id, listOf(tilbakekreving)),
            ),
        ) to vedtak
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
