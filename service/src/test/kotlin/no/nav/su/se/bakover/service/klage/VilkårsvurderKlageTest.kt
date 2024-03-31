package no.nav.su.se.bakover.service.klage

import arrow.core.left
import arrow.core.right
import behandling.klage.domain.KlageId
import behandling.klage.domain.VilkårsvurderingerTilKlage
import behandling.klage.domain.VurderingerTilKlage
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import no.nav.su.se.bakover.common.domain.attestering.Attesteringshistorikk
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.klage.KlageinstansUtfall
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
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import vedtak.domain.Vedtak
import java.util.UUID

internal class VilkårsvurderKlageTest {

    @Test
    fun `fant ikke klage`() {
        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn null
            },
        )
        val klageId = KlageId.generer()
        val request = VurderKlagevilkårRequest(
            saksbehandler = NavIdentBruker.Saksbehandler("s2"),
            klageId = klageId,
            vedtakId = null,
            innenforFristen = null,
            klagesDetPåKonkreteElementerIVedtaket = null,
            erUnderskrevet = null,
            begrunnelse = null,
        )
        mocks.service.vilkårsvurder(request) shouldBe KunneIkkeVilkårsvurdereKlage.FantIkkeKlage.left()

        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klageId })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `fant ikke vedtak`() {
        val mocks = KlageServiceMocks(
            vedtakServiceMock = mock {
                on { hentForVedtakId(any()) } doReturn null
            },
        )
        val vedtakId = UUID.randomUUID()
        val request = VurderKlagevilkårRequest(
            saksbehandler = NavIdentBruker.Saksbehandler("nySaksbehandler"),
            klageId = KlageId.generer(),
            vedtakId = vedtakId,
            innenforFristen = null,
            klagesDetPåKonkreteElementerIVedtaket = null,
            erUnderskrevet = null,
            begrunnelse = null,
        )
        mocks.service.vilkårsvurder(request) shouldBe KunneIkkeVilkårsvurdereKlage.FantIkkeVedtak.left()

        verify(mocks.vedtakServiceMock).hentForVedtakId(vedtakId)
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `kan ikke velge et vedtak som ikke skal sende brev ved vilkårsvurdering`() {
        val vedtak = vedtakRevurderingIverksattInnvilget(
            brevvalg = BrevvalgRevurdering.Valgt.IkkeSendBrev(
                null,
                BrevvalgRevurdering.BestemtAv.Behandler(saksbehandler.navIdent),
            ),
        ).second

        val mocks = KlageServiceMocks(
            vedtakServiceMock = mock { on { hentForVedtakId(any()) } doReturn vedtak },
        )

        mocks.service.vilkårsvurder(
            VurderKlagevilkårRequest(
                klageId = KlageId.generer(),
                saksbehandler = saksbehandler,
                vedtakId = vedtak.id,
                innenforFristen = null,
                klagesDetPåKonkreteElementerIVedtaket = null,
                erUnderskrevet = null,
                begrunnelse = null,
            ),
        ) shouldBe KunneIkkeVilkårsvurdereKlage.VedtakSkalIkkeSendeBrev.left()

        verify(mocks.vedtakServiceMock).hentForVedtakId(argShouldBe(vedtak.id))
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `Ugyldig tilstandsovergang fra til attestering`() {
        verifiserUgyldigTilstandsovergang(
            klage = vurdertKlageTilAttestering().second,
        )
    }

    @Test
    fun `Ugyldig tilstandsovergang fra iverksatt`() {
        verifiserUgyldigTilstandsovergang(
            klage = oversendtKlage().second,
        )
    }

    @Test
    fun `ugyldig tilstandsovergang fra Avvist`() {
        verifiserUgyldigTilstandsovergang(
            klage = iverksattAvvistKlage().second,
        )
    }

    @Test
    fun `kan ikke vilkårsvurdere fra vurdert, som er tidligere oversendt, til avvist`() {
        val klage = oversendtKlage().second.leggTilNyKlageinstanshendelse(
            TolketKlageinstanshendelse(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                avsluttetTidspunkt = fixedTidspunkt,
                klageId = KlageId.generer(),
                utfall = KlageinstansUtfall.RETUR,
                journalpostIDer = emptyList(),
            ),
            lagOppgaveCallback = { OppgaveId("o").right() },
        ).getOrFail()

        klage.shouldBeTypeOf<VurdertKlage.Bekreftet>()

        klage.vilkårsvurder(
            NavIdentBruker.Saksbehandler("sa"),
            VilkårsvurderingerTilKlage.Utfylt(
                vedtakId = UUID.randomUUID(),
                innenforFristen = VilkårsvurderingerTilKlage.Svarord.NEI,
                klagesDetPåKonkreteElementerIVedtaket = false,
                erUnderskrevet = VilkårsvurderingerTilKlage.Svarord.JA,
                begrunnelse = "b",
            ),
        ) shouldBe KunneIkkeVilkårsvurdereKlage.KanIkkeAvviseEnKlageSomHarVærtOversendt.left()
    }

    @Test
    fun `kan ikke vilkårsvurdere fra bekreftet vilkårsvurdert(TilVurdering), som er tidligere oversendt, til avvist`() {
        val klage = oversendtKlage().second.leggTilNyKlageinstanshendelse(
            TolketKlageinstanshendelse(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                avsluttetTidspunkt = fixedTidspunkt,
                klageId = KlageId.generer(),
                utfall = KlageinstansUtfall.RETUR,
                journalpostIDer = emptyList(),
            ),
            lagOppgaveCallback = { OppgaveId("o").right() },
        ).getOrFail()

        klage.shouldBeTypeOf<VurdertKlage.Bekreftet>()

        val klageSomHarEndretSvarMenFortsattTilVurdering = klage.vilkårsvurder(
            NavIdentBruker.Saksbehandler("sa"),
            VilkårsvurderingerTilKlage.Utfylt(
                vedtakId = UUID.randomUUID(),
                innenforFristen = VilkårsvurderingerTilKlage.Svarord.NEI_MEN_SKAL_VURDERES,
                klagesDetPåKonkreteElementerIVedtaket = true,
                erUnderskrevet = VilkårsvurderingerTilKlage.Svarord.JA,
                begrunnelse = "b",
            ),
        ).getOrFail()

        klageSomHarEndretSvarMenFortsattTilVurdering.shouldBeTypeOf<VilkårsvurdertKlage.Utfylt.TilVurdering>()

        val bekreftet =
            klageSomHarEndretSvarMenFortsattTilVurdering.bekreftVilkårsvurderinger(NavIdentBruker.Saksbehandler("sa"))
                .getOrFail()

        bekreftet.shouldBeTypeOf<VilkårsvurdertKlage.Bekreftet.TilVurdering>()

        bekreftet.vilkårsvurder(
            NavIdentBruker.Saksbehandler("sa"),
            VilkårsvurderingerTilKlage.Utfylt(
                vedtakId = UUID.randomUUID(),
                innenforFristen = VilkårsvurderingerTilKlage.Svarord.NEI,
                klagesDetPåKonkreteElementerIVedtaket = true,
                erUnderskrevet = VilkårsvurderingerTilKlage.Svarord.JA,
                begrunnelse = "b",
            ),
        ) shouldBe KunneIkkeVilkårsvurdereKlage.KanIkkeAvviseEnKlageSomHarVærtOversendt.left()
    }

    private fun verifiserUgyldigTilstandsovergang(
        klage: Klage,
    ) {
        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn klage
            },
        )
        val request = VurderKlagevilkårRequest(
            saksbehandler = NavIdentBruker.Saksbehandler("nySaksbehandler"),
            klageId = klage.id,
            vedtakId = null,
            innenforFristen = null,
            klagesDetPåKonkreteElementerIVedtaket = null,
            erUnderskrevet = null,
            begrunnelse = null,
        )
        mocks.service.vilkårsvurder(request) shouldBe KunneIkkeVilkårsvurdereKlage.UgyldigTilstand(
            klage::class,
        ).left()

        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
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
            vedtak = vedtak,
            klage = klage,
        )
        verifiserGyldigStatusovergangTilUtfylt(
            vedtak = vedtak,
            klage = klage,
        )
    }

    @Test
    fun `Skal kunne vilkårsvurdere påbegynt vilkårsvurdert klage`() {
        val (sak, klage) = påbegyntVilkårsvurdertKlage()
        val vedtak = sak.vedtakListe.first()

        verifiserGyldigStatusovergangTilPåbegynt(
            vedtak = vedtak,
            klage = klage,
        )
        verifiserGyldigStatusovergangTilUtfylt(
            vedtak = vedtak,
            klage = klage,
        )
    }

    @Test
    fun `Skal kunne vilkårsvurdere utfylt vilkårsvurdert klage til vurdering`() {
        val (sak, klage) = utfyltVilkårsvurdertKlageTilVurdering()
        val vedtak = sak.vedtakListe.first()
        verifiserGyldigStatusovergangTilPåbegynt(
            vedtak = vedtak,
            klage = klage,
        )
        verifiserGyldigStatusovergangTilUtfylt(
            vedtak = vedtak,
            klage = klage,
        )
    }

    @Test
    fun `Skal kunne vilkårsvurdere utfylt avvist vilkårsvurdert klage`() {
        val (sak, klage) = utfyltAvvistVilkårsvurdertKlage()
        val vedtak = sak.vedtakListe.first()
        verifiserGyldigStatusovergangTilPåbegynt(
            vedtak = vedtak,
            klage = klage,
        )
        verifiserGyldigStatusovergangTilUtfylt(
            vedtak = vedtak,
            klage = klage,
        )
    }

    @Test
    fun `Skal kunne vilkårsvurdere bekreftet vilkårsvurdert klage som er til vurdering`() {
        val (sak, klage) = bekreftetVilkårsvurdertKlageTilVurdering()
        val vedtak = sak.vedtakListe.first()

        verifiserGyldigStatusovergangTilPåbegynt(
            vedtak = vedtak,
            klage = klage,
        )
        verifiserGyldigStatusovergangTilUtfylt(
            vedtak = vedtak,
            klage = klage,
        )
    }

    @Test
    fun `Skal kunne vilkårsvurdere bekreftet avvist vilkårsvurdert klage`() {
        val (sak, klage) = bekreftetAvvistVilkårsvurdertKlage()
        val vedtak = sak.vedtakListe.first()

        verifiserGyldigStatusovergangTilPåbegynt(
            vedtak = vedtak,
            klage = klage,
        )
        verifiserGyldigStatusovergangTilUtfylt(
            vedtak = vedtak,
            klage = klage,
        )
    }

    @Test
    fun `Skal kunne vilkårsvurdere påbegynt vurdert klage`() {
        val (sak, klage) = påbegyntVurdertKlage()
        val vedtak = sak.vedtakListe.first()
        verifiserGyldigStatusovergangTilPåbegynt(
            vedtak = vedtak,
            klage = klage,
        )
        verifiserGyldigStatusovergangTilUtfylt(
            vedtak = vedtak,
            klage = klage,
            vurderingerTilKlage = klage.vurderinger,
        )
    }

    @Test
    fun `Skal kunne vilkårsvurdere utfylt vurdert klage`() {
        val (sak, klage) = utfyltVurdertKlage()
        val vedtak = sak.vedtakListe.first()

        verifiserGyldigStatusovergangTilPåbegynt(
            vedtak = vedtak,
            klage = klage,
        )
        verifiserGyldigStatusovergangTilUtfylt(
            vedtak = vedtak,
            klage = klage,
            vurderingerTilKlage = klage.vurderinger,
        )
    }

    @Test
    fun `Skal kunne vilkårsvurdere bekreftet vurdert klage`() {
        val (sak, klage) = bekreftetVurdertKlage()
        val vedtak = sak.vedtakListe.first()

        verifiserGyldigStatusovergangTilPåbegynt(
            vedtak = vedtak,
            klage = klage,
        )
        verifiserGyldigStatusovergangTilUtfylt(
            vedtak = vedtak,
            klage = klage,
            vurderingerTilKlage = klage.vurderinger,
        )
    }

    @Test
    fun `Skal kunne vilkårsvurdere underkjent klage`() {
        val (sak, klage) = underkjentKlageTilVurdering()
        val vedtak = sak.vedtakListe.first()

        verifiserGyldigStatusovergangTilPåbegynt(
            vedtak = vedtak,
            klage = klage,
            attesteringer = klage.attesteringer,
        )
        verifiserGyldigStatusovergangTilUtfylt(
            vedtak = vedtak,
            klage = klage,
            vurderingerTilKlage = klage.vurderinger,
            attesteringer = klage.attesteringer,
        )
    }

    @Test
    fun `får tilbake en utfylt avvist vilkårsvurdert klage dersom minst et av feltene er besvart 'nei' eller false`() {
        val forventetAvvistVilkårsvurdertKlage = påbegyntVilkårsvurdertKlage().second.vilkårsvurder(
            saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "saksbehandlerensen"),
            vilkårsvurderinger = VilkårsvurderingerTilKlage.create(
                vedtakId = UUID.randomUUID(),
                innenforFristen = VilkårsvurderingerTilKlage.Svarord.NEI,
                klagesDetPåKonkreteElementerIVedtaket = true,
                erUnderskrevet = VilkårsvurderingerTilKlage.Svarord.JA,
                begrunnelse = "en god og fin begrunnelse",
            ),
        ).getOrFail()

        forventetAvvistVilkårsvurdertKlage.shouldBeTypeOf<VilkårsvurdertKlage.Utfylt.Avvist>()
    }

    private fun verifiserGyldigStatusovergangTilPåbegynt(
        vedtak: Vedtak,
        klage: Klage,
        attesteringer: Attesteringshistorikk = Attesteringshistorikk.empty(),
    ) {
        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn klage
                on { defaultTransactionContext() } doReturn TestSessionFactory.transactionContext
            },
            vedtakServiceMock = mock {
                on { hentForVedtakId(any()) } doReturn vedtak
            },
        )

        val request = VurderKlagevilkårRequest(
            saksbehandler = NavIdentBruker.Saksbehandler("nySaksbehandler"),
            klageId = klage.id,
            vedtakId = null,
            innenforFristen = null,
            klagesDetPåKonkreteElementerIVedtaket = null,
            erUnderskrevet = null,
            begrunnelse = null,
        )

        var expectedKlage: VilkårsvurdertKlage.Påbegynt?
        mocks.service.vilkårsvurder(request).getOrFail().also {
            expectedKlage = VilkårsvurdertKlage.Påbegynt(
                id = it.id,
                opprettet = it.opprettet,
                sakId = klage.sakId,
                saksnummer = klage.saksnummer,
                fnr = klage.fnr,
                journalpostId = klage.journalpostId,
                oppgaveId = klage.oppgaveId,
                saksbehandler = NavIdentBruker.Saksbehandler("nySaksbehandler"),
                vilkårsvurderinger = VilkårsvurderingerTilKlage.empty(),
                attesteringer = attesteringer,
                datoKlageMottatt = 15.januar(2021),
            )
            it shouldBe expectedKlage
        }

        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
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
            vedtakServiceMock = mock {
                on { hentForVedtakId(any()) } doReturn vedtak
            },
        )
        val request = VurderKlagevilkårRequest(
            saksbehandler = NavIdentBruker.Saksbehandler("nySaksbehandler"),
            klageId = klage.id,
            vedtakId = vedtak.id,
            innenforFristen = VilkårsvurderingerTilKlage.Svarord.JA,
            klagesDetPåKonkreteElementerIVedtaket = true,
            erUnderskrevet = VilkårsvurderingerTilKlage.Svarord.JA,
            begrunnelse = "SomeBegrunnelse",
        )
        var expectedKlage: VilkårsvurdertKlage.Utfylt?
        mocks.service.vilkårsvurder(request).getOrFail().also {
            expectedKlage = VilkårsvurdertKlage.Utfylt.create(
                id = it.id,
                opprettet = it.opprettet,
                sakId = klage.sakId,
                saksnummer = klage.saksnummer,
                fnr = klage.fnr,
                journalpostId = klage.journalpostId,
                oppgaveId = klage.oppgaveId,
                saksbehandler = NavIdentBruker.Saksbehandler("nySaksbehandler"),
                vilkårsvurderinger = VilkårsvurderingerTilKlage.Utfylt(
                    vedtakId = it.vilkårsvurderinger.vedtakId!!,
                    innenforFristen = VilkårsvurderingerTilKlage.Svarord.JA,
                    klagesDetPåKonkreteElementerIVedtaket = true,
                    erUnderskrevet = VilkårsvurderingerTilKlage.Svarord.JA,
                    begrunnelse = "SomeBegrunnelse",
                ),
                vurderinger = vurderingerTilKlage,
                attesteringer = attesteringer,
                datoKlageMottatt = 15.januar(2021),
                klageinstanshendelser = Klageinstanshendelser.empty(),
                fritekstTilAvvistVedtaksbrev = null,
            )
            it shouldBe expectedKlage
        }

        verify(mocks.vedtakServiceMock).hentForVedtakId(vedtak.id)
        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
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
