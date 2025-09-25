package no.nav.su.se.bakover.service.klage

import arrow.core.left
import arrow.core.right
import behandling.klage.domain.FormkravTilKlage
import behandling.klage.domain.KlageId
import behandling.klage.domain.VurderingerTilKlage
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import no.nav.su.se.bakover.common.domain.attestering.Attesteringshistorikk
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.klage.AvsluttetKlageinstansUtfall
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.klage.Klageinstanshendelser
import no.nav.su.se.bakover.domain.klage.KunneIkkeVilkårsvurdereKlage
import no.nav.su.se.bakover.domain.klage.TolketKlageinstanshendelse
import no.nav.su.se.bakover.domain.klage.VilkårsvurdertKlage
import no.nav.su.se.bakover.domain.klage.VurdertKlage
import no.nav.su.se.bakover.domain.revurdering.brev.BrevvalgRevurdering
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.argShouldBe
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.bekreftetAvvistVilkårsvurdertKlage
import no.nav.su.se.bakover.test.bekreftetVilkårsvurdertKlageTilVurdering
import no.nav.su.se.bakover.test.bekreftetVurdertKlage
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.iverksattAvvistKlage
import no.nav.su.se.bakover.test.opprettetKlage
import no.nav.su.se.bakover.test.oversendtKlage
import no.nav.su.se.bakover.test.påbegyntVilkårsvurdertKlage
import no.nav.su.se.bakover.test.påbegyntVurdertKlage
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.underkjentKlageTilVurdering
import no.nav.su.se.bakover.test.utfyltAvvistVilkårsvurdertKlage
import no.nav.su.se.bakover.test.utfyltVilkårsvurdertKlageTilVurdering
import no.nav.su.se.bakover.test.utfyltVurdertKlage
import no.nav.su.se.bakover.test.vedtakRevurderingIverksattInnvilget
import no.nav.su.se.bakover.test.vurdertKlageTilAttestering
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import vedtak.domain.Vedtak
import java.util.UUID

internal class VilkårsvurderKlageTest {

    @Test
    fun `fant ikke klage`() {
        val (sak, _) = opprettetKlage()
        val mocks = KlageServiceMocks(
            sakServiceMock = mock {
                on { hentSak(any<UUID>()) } doReturn sak.right()
            },
        )
        val klageId = KlageId.generer()
        val request = VurderKlagevilkårCommand(
            saksbehandler = NavIdentBruker.Saksbehandler("s2"),
            klageId = klageId,
            vedtakId = null,
            innenforFristen = null,
            klagesDetPåKonkreteElementerIVedtaket = null,
            erUnderskrevet = null,
            sakId = sak.id,
        )
        mocks.service.vilkårsvurder(request) shouldBe KunneIkkeVilkårsvurdereKlage.FantIkkeKlage.left()

        verify(mocks.sakServiceMock).hentSak(argThat<UUID> { it shouldBe sak.id })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `fant ikke vedtak`() {
        val (sak, klage) = opprettetKlage()
        val mocks = KlageServiceMocks(
            sakServiceMock = mock {
                on { hentSak(any<UUID>()) } doReturn sak.right()
            },
        )
        val vedtakId = UUID.randomUUID()
        val request = VurderKlagevilkårCommand(
            saksbehandler = NavIdentBruker.Saksbehandler("nySaksbehandler"),
            klageId = klage.id,
            vedtakId = vedtakId,
            innenforFristen = null,
            klagesDetPåKonkreteElementerIVedtaket = null,
            erUnderskrevet = null,
            sakId = sak.id,
        )
        mocks.service.vilkårsvurder(request) shouldBe KunneIkkeVilkårsvurdereKlage.FantIkkeVedtak.left()

        verify(mocks.sakServiceMock).hentSak(argThat<UUID> { it shouldBe sak.id })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `kan ikke velge et vedtak som ikke skal sende brev ved vilkårsvurdering`() {
        val (sak, klage) = opprettetKlage(
            sakMedVedtak = vedtakRevurderingIverksattInnvilget(
                brevvalg = BrevvalgRevurdering.Valgt.IkkeSendBrev(
                    null,
                    BrevvalgRevurdering.BestemtAv.Behandler(saksbehandler.navIdent),
                ),
            ).first,
        )

        val revurderingsvedtak = sak.vedtakListe[1]

        val mocks = KlageServiceMocks(
            sakServiceMock = mock {
                on { hentSak(any<UUID>()) } doReturn sak.right()
            },
        )

        mocks.service.vilkårsvurder(
            VurderKlagevilkårCommand(
                klageId = klage.id,
                saksbehandler = saksbehandler,
                vedtakId = revurderingsvedtak.id,
                innenforFristen = null,
                klagesDetPåKonkreteElementerIVedtaket = null,
                erUnderskrevet = null,
                sakId = sak.id,
            ),
        ) shouldBe KunneIkkeVilkårsvurdereKlage.VedtakSkalIkkeSendeBrev.left()

        verify(mocks.sakServiceMock).hentSak(argShouldBe(sak.id))
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `Ugyldig tilstandsovergang fra til attestering`() {
        val (sak, klage) = vurdertKlageTilAttestering()
        verifiserUgyldigTilstandsovergang(
            sak = sak,
            klage = klage,
        )
    }

    @Test
    fun `Ugyldig tilstandsovergang fra iverksatt`() {
        val (sak, klage) = oversendtKlage()
        verifiserUgyldigTilstandsovergang(
            sak = sak,
            klage = klage,
        )
    }

    @Test
    fun `ugyldig tilstandsovergang fra Avvist`() {
        val (sak, klage) = iverksattAvvistKlage()
        verifiserUgyldigTilstandsovergang(
            sak = sak,
            klage = klage,
        )
    }

    @Test
    fun `kan ikke vilkårsvurdere fra vurdert, som er tidligere oversendt, til avvist`() {
        val klage = oversendtKlage().second.leggTilNyKlageinstanshendelse(
            TolketKlageinstanshendelse.KlagebehandlingAvsluttet(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                avsluttetTidspunkt = fixedTidspunkt,
                klageId = KlageId.generer(),
                utfall = AvsluttetKlageinstansUtfall.Retur,
                journalpostIDer = emptyList(),
            ),
            lagOppgaveCallback = { OppgaveId("o").right() },
        ).getOrFail()

        klage.shouldBeTypeOf<VurdertKlage.Bekreftet>()

        klage.vilkårsvurder(
            NavIdentBruker.Saksbehandler("sa"),
            FormkravTilKlage.create(
                vedtakId = UUID.randomUUID(),
                innenforFristen = FormkravTilKlage.Svarord.NEI,
                klagesDetPåKonkreteElementerIVedtaket = false,
                erUnderskrevet = FormkravTilKlage.Svarord.JA,
            ),
        ) shouldBe KunneIkkeVilkårsvurdereKlage.KanIkkeAvviseEnKlageSomHarVærtOversendt.left()
    }

    @Test
    fun `kan ikke vilkårsvurdere fra bekreftet vilkårsvurdert(TilVurdering), som er tidligere oversendt, til avvist`() {
        val klage = oversendtKlage().second.leggTilNyKlageinstanshendelse(
            TolketKlageinstanshendelse.KlagebehandlingAvsluttet(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                avsluttetTidspunkt = fixedTidspunkt,
                klageId = KlageId.generer(),
                utfall = AvsluttetKlageinstansUtfall.Retur,
                journalpostIDer = emptyList(),
            ),
            lagOppgaveCallback = { OppgaveId("o").right() },
        ).getOrFail()

        klage.shouldBeTypeOf<VurdertKlage.Bekreftet>()

        val klageSomHarEndretSvarMenFortsattTilVurdering = klage.vilkårsvurder(
            NavIdentBruker.Saksbehandler("sa"),
            FormkravTilKlage.create(
                vedtakId = UUID.randomUUID(),
                innenforFristen = FormkravTilKlage.Svarord.NEI_MEN_SKAL_VURDERES,
                klagesDetPåKonkreteElementerIVedtaket = true,
                erUnderskrevet = FormkravTilKlage.Svarord.JA,
            ),
        ).getOrFail()

        klageSomHarEndretSvarMenFortsattTilVurdering.shouldBeTypeOf<VilkårsvurdertKlage.Utfylt.TilVurdering>()

        val bekreftet =
            klageSomHarEndretSvarMenFortsattTilVurdering.bekreftVilkårsvurderinger(NavIdentBruker.Saksbehandler("sa"))
                .getOrFail()

        bekreftet.shouldBeTypeOf<VilkårsvurdertKlage.Bekreftet.TilVurdering>()

        bekreftet.vilkårsvurder(
            NavIdentBruker.Saksbehandler("sa"),
            FormkravTilKlage.create(
                vedtakId = UUID.randomUUID(),
                innenforFristen = FormkravTilKlage.Svarord.NEI,
                klagesDetPåKonkreteElementerIVedtaket = true,
                erUnderskrevet = FormkravTilKlage.Svarord.JA,
            ),
        ) shouldBe KunneIkkeVilkårsvurdereKlage.KanIkkeAvviseEnKlageSomHarVærtOversendt.left()
    }

    private fun verifiserUgyldigTilstandsovergang(
        sak: Sak,
        klage: Klage,
    ) {
        val mocks = KlageServiceMocks(
            sakServiceMock = mock {
                on { hentSak(any<UUID>()) } doReturn sak.right()
            },
        )
        val request = VurderKlagevilkårCommand(
            saksbehandler = NavIdentBruker.Saksbehandler("nySaksbehandler"),
            klageId = klage.id,
            vedtakId = null,
            innenforFristen = null,
            klagesDetPåKonkreteElementerIVedtaket = null,
            erUnderskrevet = null,
            sakId = sak.id,
        )
        mocks.service.vilkårsvurder(request) shouldBe KunneIkkeVilkårsvurdereKlage.UgyldigTilstand(
            klage::class,
        ).left()

        verify(mocks.sakServiceMock).hentSak(argThat<UUID> { it shouldBe sak.id })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `en påbegyntVilkårsvurdert klage er en åpen klage`() {
        val klage = påbegyntVilkårsvurdertKlage().second
        klage.erÅpen() shouldBe true
    }

    @Test
    fun `en utfyltVilkårsvurdert(tilVurdering) klage er en åpen klage`() {
        val klage = utfyltVilkårsvurdertKlageTilVurdering().second
        klage.erÅpen() shouldBe true
    }

    @Test
    fun `en utfyltVilkårsvurdert(avvist) klage er en åpen klage`() {
        val klage = utfyltAvvistVilkårsvurdertKlage().second
        klage.erÅpen() shouldBe true
    }

    @Test
    fun `Skal kunne vilkårsvurdere opprettet klage`() {
        val (sak, klage) = opprettetKlage()
        val vedtak = sak.vedtakListe.first()
        verifiserGyldigStatusovergangTilPåbegynt(
            klage = klage,
            sak = sak,
        )
        verifiserGyldigStatusovergangTilUtfylt(
            sak = sak,
            vedtak = vedtak,
            klage = klage,
        )
    }

    @Test
    fun `Skal kunne vilkårsvurdere påbegynt vilkårsvurdert klage`() {
        val (sak, klage) = påbegyntVilkårsvurdertKlage()
        val vedtak = sak.vedtakListe.first()

        verifiserGyldigStatusovergangTilPåbegynt(
            sak = sak,
            klage = klage,
        )
        verifiserGyldigStatusovergangTilUtfylt(
            sak = sak,
            vedtak = vedtak,
            klage = klage,
        )
    }

    @Test
    fun `Skal kunne vilkårsvurdere utfylt vilkårsvurdert klage til vurdering`() {
        val (sak, klage) = utfyltVilkårsvurdertKlageTilVurdering()
        val vedtak = sak.vedtakListe.first()
        verifiserGyldigStatusovergangTilPåbegynt(
            klage = klage,
            sak = sak,
        )
        verifiserGyldigStatusovergangTilUtfylt(
            sak = sak,
            vedtak = vedtak,
            klage = klage,
        )
    }

    @Test
    fun `Skal kunne vilkårsvurdere utfylt avvist vilkårsvurdert klage`() {
        val (sak, klage) = utfyltAvvistVilkårsvurdertKlage()
        val vedtak = sak.vedtakListe.first()
        verifiserGyldigStatusovergangTilPåbegynt(
            klage = klage,
            sak = sak,
        )
        verifiserGyldigStatusovergangTilUtfylt(
            sak = sak,
            vedtak = vedtak,
            klage = klage,
        )
    }

    @Test
    fun `Skal kunne vilkårsvurdere bekreftet vilkårsvurdert klage som er til vurdering`() {
        val (sak, klage) = bekreftetVilkårsvurdertKlageTilVurdering()
        val vedtak = sak.vedtakListe.first()

        verifiserGyldigStatusovergangTilPåbegynt(
            klage = klage,
            sak = sak,
        )
        verifiserGyldigStatusovergangTilUtfylt(
            sak = sak,
            vedtak = vedtak,
            klage = klage,
        )
    }

    @Test
    fun `Skal kunne vilkårsvurdere bekreftet avvist vilkårsvurdert klage`() {
        val (sak, klage) = bekreftetAvvistVilkårsvurdertKlage()
        val vedtak = sak.vedtakListe.first()

        verifiserGyldigStatusovergangTilPåbegynt(
            klage = klage,
            sak = sak,
        )
        verifiserGyldigStatusovergangTilUtfylt(
            sak = sak,
            vedtak = vedtak,
            klage = klage,
        )
    }

    @Test
    fun `Skal kunne vilkårsvurdere påbegynt vurdert klage`() {
        val (sak, klage) = påbegyntVurdertKlage()
        val vedtak = sak.vedtakListe.first()
        verifiserGyldigStatusovergangTilPåbegynt(
            klage = klage,
            sak = sak,
        )
        verifiserGyldigStatusovergangTilUtfylt(
            vedtak = vedtak,
            klage = klage,
            vurderingerTilKlage = klage.vurderinger,
            sak = sak,
        )
    }

    @Test
    fun `Skal kunne vilkårsvurdere utfylt vurdert klage`() {
        val (sak, klage) = utfyltVurdertKlage()
        val vedtak = sak.vedtakListe.first()

        verifiserGyldigStatusovergangTilPåbegynt(
            klage = klage,
            sak = sak,
        )
        verifiserGyldigStatusovergangTilUtfylt(
            vedtak = vedtak,
            klage = klage,
            vurderingerTilKlage = klage.vurderinger,
            sak = sak,
        )
    }

    @Test
    fun `Skal kunne vilkårsvurdere bekreftet vurdert klage`() {
        val (sak, klage) = bekreftetVurdertKlage()
        val vedtak = sak.vedtakListe.first()

        verifiserGyldigStatusovergangTilPåbegynt(
            klage = klage,
            sak = sak,
        )
        verifiserGyldigStatusovergangTilUtfylt(
            vedtak = vedtak,
            klage = klage,
            vurderingerTilKlage = klage.vurderinger,
            sak = sak,
        )
    }

    @Test
    fun `Skal kunne vilkårsvurdere underkjent klage`() {
        val (sak, klage) = underkjentKlageTilVurdering()
        val vedtak = sak.vedtakListe.first()

        verifiserGyldigStatusovergangTilPåbegynt(
            klage = klage,
            attesteringer = klage.attesteringer,
            sak = sak,
        )
        verifiserGyldigStatusovergangTilUtfylt(
            vedtak = vedtak,
            klage = klage,
            vurderingerTilKlage = klage.vurderinger,
            attesteringer = klage.attesteringer,
            sak = sak,
        )
    }

    @Test
    fun `får tilbake en utfylt avvist vilkårsvurdert klage dersom minst et av feltene er besvart 'nei' eller false`() {
        val forventetAvvistVilkårsvurdertKlage = påbegyntVilkårsvurdertKlage().second.vilkårsvurder(
            saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "saksbehandlerensen"),
            vilkårsvurderinger = FormkravTilKlage.create(
                vedtakId = UUID.randomUUID(),
                innenforFristen = FormkravTilKlage.Svarord.NEI,
                klagesDetPåKonkreteElementerIVedtaket = true,
                erUnderskrevet = FormkravTilKlage.Svarord.JA,
            ),
        ).getOrFail()

        forventetAvvistVilkårsvurdertKlage.shouldBeTypeOf<VilkårsvurdertKlage.Utfylt.Avvist>()
    }

    private fun verifiserGyldigStatusovergangTilPåbegynt(
        sak: Sak,
        klage: Klage,
        attesteringer: Attesteringshistorikk = Attesteringshistorikk.empty(),
    ) {
        val mocks = KlageServiceMocks(
            sakServiceMock = mock {
                on { hentSak(any<UUID>()) } doReturn sak.right()
            },
            klageRepoMock = mock {
                on { defaultTransactionContext() } doReturn TestSessionFactory.transactionContext
                doNothing().whenever(it).lagre(any(), any())
            },
        )

        val request = VurderKlagevilkårCommand(
            saksbehandler = NavIdentBruker.Saksbehandler("nySaksbehandler"),
            klageId = klage.id,
            vedtakId = null,
            innenforFristen = null,
            klagesDetPåKonkreteElementerIVedtaket = null,
            erUnderskrevet = null,
            sakId = sak.id,
        )

        var expectedKlage: VilkårsvurdertKlage.Påbegynt?
        mocks.service.vilkårsvurder(request).getOrFail().also {
            expectedKlage = VilkårsvurdertKlage.Påbegynt(
                id = it.id,
                opprettet = it.opprettet,
                sakId = klage.sakId,
                saksnummer = klage.saksnummer,
                sakstype = klage.sakstype,
                fnr = klage.fnr,
                journalpostId = klage.journalpostId,
                oppgaveId = klage.oppgaveId,
                saksbehandler = NavIdentBruker.Saksbehandler("nySaksbehandler"),
                vilkårsvurderinger = FormkravTilKlage.empty(),
                attesteringer = attesteringer,
                datoKlageMottatt = 15.januar(2021),
            )
            it shouldBe expectedKlage
        }

        verify(mocks.sakServiceMock).hentSak(argThat<UUID> { it shouldBe sak.id })
        verify(mocks.klageRepoMock).defaultTransactionContext()
        verify(mocks.klageRepoMock).lagre(
            argThat {
                it shouldBe expectedKlage
            },
            argThat {
                it shouldBe TestSessionFactory.transactionContext
            },
        )
        mocks.verifyNoMoreInteractions()
    }

    private fun verifiserGyldigStatusovergangTilUtfylt(
        sak: Sak,
        vedtak: Vedtak,
        klage: Klage,
        vurderingerTilKlage: VurderingerTilKlage? = null,
        attesteringer: Attesteringshistorikk = Attesteringshistorikk.empty(),
    ) {
        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn klage
                on { defaultTransactionContext() } doReturn TestSessionFactory.transactionContext
            },
            sakServiceMock = mock {
                on { hentSak(any<UUID>()) } doReturn sak.right()
            },
        )
        val request = VurderKlagevilkårCommand(
            saksbehandler = NavIdentBruker.Saksbehandler("nySaksbehandler"),
            klageId = klage.id,
            vedtakId = vedtak.id,
            innenforFristen = FormkravTilKlage.Svarord.JA,
            klagesDetPåKonkreteElementerIVedtaket = true,
            erUnderskrevet = FormkravTilKlage.Svarord.JA,
            sakId = sak.id,
        )
        var expectedKlage: VilkårsvurdertKlage.Utfylt?
        mocks.service.vilkårsvurder(request).getOrFail().also {
            expectedKlage = VilkårsvurdertKlage.Utfylt.create(
                id = it.id,
                opprettet = it.opprettet,
                sakId = klage.sakId,
                saksnummer = klage.saksnummer,
                sakstype = klage.sakstype,
                fnr = klage.fnr,
                journalpostId = klage.journalpostId,
                oppgaveId = klage.oppgaveId,
                saksbehandler = NavIdentBruker.Saksbehandler("nySaksbehandler"),
                vilkårsvurderinger = FormkravTilKlage.create(
                    vedtakId = it.vilkårsvurderinger.vedtakId!!,
                    innenforFristen = FormkravTilKlage.Svarord.JA,
                    klagesDetPåKonkreteElementerIVedtaket = true,
                    erUnderskrevet = FormkravTilKlage.Svarord.JA,
                ) as VilkårsvurderingerTilKlage.Utfylt,
                vurderinger = vurderingerTilKlage,
                attesteringer = attesteringer,
                datoKlageMottatt = 15.januar(2021),
                klageinstanshendelser = Klageinstanshendelser.empty(),
                fritekstTilAvvistVedtaksbrev = null,
            )
            it shouldBe expectedKlage
        }
        verify(mocks.sakServiceMock).hentSak(argThat<UUID> { it shouldBe sak.id })
        verify(mocks.klageRepoMock).defaultTransactionContext()
        verify(mocks.klageRepoMock).lagre(
            argThat {
                it shouldBe expectedKlage
            },
            argThat {
                it shouldBe TestSessionFactory.transactionContext
            },
        )
        mocks.verifyNoMoreInteractions()
    }
}
