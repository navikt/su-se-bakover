package no.nav.su.se.bakover.service.klage

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.klage.AvvistKlage
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.klage.KlageTilAttestering
import no.nav.su.se.bakover.domain.klage.KunneIkkeBekrefteKlagesteg
import no.nav.su.se.bakover.domain.klage.KunneIkkeLeggeTilFritekstForAvvist
import no.nav.su.se.bakover.domain.klage.KunneIkkeSendeTilAttestering
import no.nav.su.se.bakover.domain.klage.KunneIkkeVurdereKlage
import no.nav.su.se.bakover.domain.klage.VilkårsvurderingerTilKlage
import no.nav.su.se.bakover.domain.klage.VilkårsvurdertKlage
import no.nav.su.se.bakover.domain.klage.VurderingerTilKlage
import no.nav.su.se.bakover.domain.klage.VurdertKlage
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.test.avvistKlageTilAttestering
import no.nav.su.se.bakover.test.bekreftetAvvistVilkårsvurdertKlage
import no.nav.su.se.bakover.test.bekreftetVilkårsvurdertKlageTilVurdering
import no.nav.su.se.bakover.test.bekreftetVurdertKlage
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.iverksattAvvistKlage
import no.nav.su.se.bakover.test.opprettetKlage
import no.nav.su.se.bakover.test.oversendtKlage
import no.nav.su.se.bakover.test.påbegyntAvvistKlage
import no.nav.su.se.bakover.test.påbegyntVilkårsvurdertKlage
import no.nav.su.se.bakover.test.påbegyntVurdertKlage
import no.nav.su.se.bakover.test.utfyltAvvistVilkårsvurdertKlage
import no.nav.su.se.bakover.test.utfyltVilkårsvurdertKlageTilVurdering
import no.nav.su.se.bakover.test.utfyltVurdertKlage
import no.nav.su.se.bakover.test.vurdertKlageTilAttestering
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.UUID

internal class PåbegyntAvvistKlageTest {

    @Nested
    inner class UgyldigStatusoverganger {
        private fun verifiserUgyldigTilstandsovergang(klage: Klage) {
            val mocks = KlageServiceMocks(
                klageRepoMock = mock {
                    on { hentKlage(any()) } doReturn klage
                },
            )
            mocks.service.leggTilAvvistFritekstTilBrev(
                klageId = klage.id,
                saksbehandler = NavIdentBruker.Saksbehandler("saksbehandler"),
                fritekst = null,
            ) shouldBe KunneIkkeLeggeTilFritekstForAvvist.UgyldigTilstand(klage::class, AvvistKlage.Påbegynt::class)
                .left()

            Mockito.verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
            mocks.verifyNoMoreInteractions()
        }

        @Test
        fun `fra opprettet til påbegyntAvvist`() {
            verifiserUgyldigTilstandsovergang(opprettetKlage().second)
        }

        @Test
        fun `fra påbegyntVilkårsvurdert til påbegyntAvvist`() {
            verifiserUgyldigTilstandsovergang(påbegyntVilkårsvurdertKlage().second)
        }

        @Test
        fun `fra utfyltVilkårsvurdert(TilVurdering) til påbegyntAvvist`() {
            verifiserUgyldigTilstandsovergang(utfyltVilkårsvurdertKlageTilVurdering().second)
        }

        @Test
        fun `fra utfyltVilkårsvurdert(Avvist) til påbegyntAvvist`() {
            verifiserUgyldigTilstandsovergang(utfyltAvvistVilkårsvurdertKlage().second)
        }

        @Test
        fun `fra bekreftetVilkårsvurdert(TilVurdering) til påbegyntAvvist`() {
            verifiserUgyldigTilstandsovergang(bekreftetVilkårsvurdertKlageTilVurdering().second)
        }

        @Test
        fun `fra påbegyntVurdert til påbegyntAvvist`() {
            verifiserUgyldigTilstandsovergang(påbegyntVurdertKlage().second)
        }

        @Test
        fun `fra utfyltVurdert til påbegyntAvvist`() {
            verifiserUgyldigTilstandsovergang(utfyltVurdertKlage().second)
        }

        @Test
        fun `fra bekreftetVurdert til påbegyntAvvist`() {
            verifiserUgyldigTilstandsovergang(bekreftetVurdertKlage().second)
        }

        @Test
        fun `fra påbegyntAvvist til påbegyntVurdert`() {
            val klage = påbegyntAvvistKlage().second

            val actual = klage.vurder(
                saksbehandler = NavIdentBruker.Saksbehandler("saksbehandler"),
                vurderinger = VurderingerTilKlage.create(
                    "dette skal ikke gå ok",
                    vedtaksvurdering = null,
                ),
            )

            actual shouldBe KunneIkkeVurdereKlage.UgyldigTilstand(
                AvvistKlage.Påbegynt::class,
                VurdertKlage::class,
            ).left()
        }

        @Test
        fun `fra påbegyntAvvist til bekreftetVurdert`() {
            val klage = påbegyntAvvistKlage().second

            val actual = klage.bekreftVurderinger(
                saksbehandler = NavIdentBruker.Saksbehandler("saksbehandler"),
            )

            actual shouldBe KunneIkkeBekrefteKlagesteg.UgyldigTilstand(
                AvvistKlage.Påbegynt::class,
                VurdertKlage.Bekreftet::class,
            ).left()
        }

        @Test
        fun `fra påbegyntAvvist til TilAttestering`() {
            val klage = påbegyntAvvistKlage().second

            val actual = klage.sendTilAttestering(
                saksbehandler = NavIdentBruker.Saksbehandler("saksbehandler"),
            ) {
                OppgaveId("oppgaveId").right()
            }

            actual shouldBe KunneIkkeSendeTilAttestering.UgyldigTilstand(
                AvvistKlage.Påbegynt::class,
                KlageTilAttestering::class,
            ).left()
        }

        @Test
        fun `fra TilAttestering(Vurdert) til påbegyntAvvist`() {
            verifiserUgyldigTilstandsovergang(vurdertKlageTilAttestering().second)
        }

        @Test
        fun `fra TilAttestering(Avvist) til påbegyntAvvist`() {
            verifiserUgyldigTilstandsovergang(avvistKlageTilAttestering().second)
        }

        @Test
        fun `fra Oversendt til påbegyntAvvist`() {
            verifiserUgyldigTilstandsovergang(oversendtKlage().second)
        }

        @Test
        fun `fra IverksattAvvist til påbegyntAvvist`() {
            verifiserUgyldigTilstandsovergang(iverksattAvvistKlage().second)
        }
    }

    @Nested
    inner class GyldigeStatusoverganger {
        @Test
        fun `fra bekreftetVilkårsvurdert(Avvist) til Avvist(påbegynt)`() {
            val klage = bekreftetAvvistVilkårsvurdertKlage().second

            val actual = klage.leggTilAvvistFritekstTilBrev(
                saksbehandler = NavIdentBruker.Saksbehandler("saksbehandler"),
                fritekst = null,
            )

            actual shouldBe AvvistKlage.Påbegynt.create(
                forrigeSteg = klage,
                fritekstTilBrev = null,
            ).right()
        }

        @Test
        fun `fra Avvist(påbegynt) til Avvist(bekreftet)`() {
            val klage = påbegyntAvvistKlage().second

            val actual = klage.bekreftAvvistFritekstTilBrev(
                saksbehandler = NavIdentBruker.Saksbehandler("saksbehandler"),
            )

            actual shouldBe AvvistKlage.Bekreftet.create(
                forrigeSteg = VilkårsvurdertKlage.Bekreftet.Avvist.create(
                    klage.id, klage.opprettet, klage.sakId, klage.saksnummer,
                    klage.fnr, klage.journalpostId,
                    klage.oppgaveId, klage.saksbehandler,
                    klage.vilkårsvurderinger,
                    klage.vurderinger, klage.attesteringer, klage.datoKlageMottatt,
                ),
                fritekstTilBrev = klage.fritekstTilBrev ?: "lawl",
            ).right()
        }

        @Test
        fun `fra Avvist(Påbegnyt) til påbegyntVilkårsvurdert(TilVurdering)`() {
            val klage = påbegyntAvvistKlage().second

            val actual = klage.vilkårsvurder(
                saksbehandler = NavIdentBruker.Saksbehandler("saksbehandler"),
                vilkårsvurderinger = VilkårsvurderingerTilKlage.Påbegynt.empty(),
            )

            actual shouldBe VilkårsvurdertKlage.Påbegynt.create(
                id = klage.id,
                opprettet = klage.opprettet,
                sakId = klage.sakId,
                saksnummer = klage.saksnummer,
                fnr = klage.fnr,
                journalpostId = klage.journalpostId,
                oppgaveId = klage.oppgaveId,
                saksbehandler = klage.saksbehandler,
                vilkårsvurderinger = VilkårsvurderingerTilKlage.Påbegynt.empty(),
                vurderinger = klage.vurderinger,
                attesteringer = klage.attesteringer,
                datoKlageMottatt = klage.datoKlageMottatt,
            ).right()
        }

        @Test
        fun `fra Avvist(Påbegynt) til utfyltVilkårsvurdert(TilVurdering)`() {
            val klage = påbegyntAvvistKlage().second

            val actual = klage.vilkårsvurder(
                saksbehandler = NavIdentBruker.Saksbehandler("saksbehandler"),
                vilkårsvurderinger = VilkårsvurderingerTilKlage.Utfylt(
                    vedtakId = UUID.randomUUID(),
                    innenforFristen = VilkårsvurderingerTilKlage.Svarord.JA,
                    klagesDetPåKonkreteElementerIVedtaket = true,
                    erUnderskrevet = VilkårsvurderingerTilKlage.Svarord.JA,
                    begrunnelse = "",
                ),
            ).getOrFail()

            actual shouldBe VilkårsvurdertKlage.Utfylt.TilVurdering.create(
                id = klage.id,
                opprettet = klage.opprettet,
                sakId = klage.sakId,
                saksnummer = klage.saksnummer,
                fnr = klage.fnr,
                journalpostId = klage.journalpostId,
                oppgaveId = klage.oppgaveId,
                saksbehandler = klage.saksbehandler,
                vilkårsvurderinger = actual.vilkårsvurderinger as VilkårsvurderingerTilKlage.Utfylt,
                vurderinger = klage.vurderinger,
                attesteringer = klage.attesteringer,
                datoKlageMottatt = klage.datoKlageMottatt,
            )
        }

        @Test
        fun `fra Avvist(Påbegynt) til utfyltVilkårsvurdert(Avvist)`() {
            val klage = påbegyntAvvistKlage().second

            val actual = klage.vilkårsvurder(
                saksbehandler = NavIdentBruker.Saksbehandler("saksbehandler"),
                vilkårsvurderinger = VilkårsvurderingerTilKlage.Utfylt(
                    vedtakId = UUID.randomUUID(),
                    innenforFristen = VilkårsvurderingerTilKlage.Svarord.JA,
                    klagesDetPåKonkreteElementerIVedtaket = false,
                    erUnderskrevet = VilkårsvurderingerTilKlage.Svarord.JA,
                    begrunnelse = "",
                ),
            ).getOrFail()

            actual shouldBe VilkårsvurdertKlage.Utfylt.Avvist.create(
                id = klage.id,
                opprettet = klage.opprettet,
                sakId = klage.sakId,
                saksnummer = klage.saksnummer,
                fnr = klage.fnr,
                journalpostId = klage.journalpostId,
                oppgaveId = klage.oppgaveId,
                saksbehandler = klage.saksbehandler,
                vilkårsvurderinger = actual.vilkårsvurderinger as VilkårsvurderingerTilKlage.Utfylt,
                vurderinger = klage.vurderinger,
                attesteringer = klage.attesteringer,
                datoKlageMottatt = klage.datoKlageMottatt,
            )
        }

        @Test
        fun `fra Avvist(Påbegynt) til bekreftetVilkårsvurdert(Avvist)`() {
            val klage = påbegyntAvvistKlage().second

            val actual = klage.bekreftVilkårsvurderinger(
                saksbehandler = NavIdentBruker.Saksbehandler("saksbehandler"),
            ).getOrFail()

            actual shouldBe VilkårsvurdertKlage.Bekreftet.Avvist.create(
                id = klage.id,
                opprettet = klage.opprettet,
                sakId = klage.sakId,
                saksnummer = klage.saksnummer,
                fnr = klage.fnr,
                journalpostId = klage.journalpostId,
                oppgaveId = klage.oppgaveId,
                saksbehandler = klage.saksbehandler,
                vilkårsvurderinger = actual.vilkårsvurderinger,
                vurderinger = klage.vurderinger,
                attesteringer = klage.attesteringer,
                datoKlageMottatt = klage.datoKlageMottatt,
            )
        }
    }
}
