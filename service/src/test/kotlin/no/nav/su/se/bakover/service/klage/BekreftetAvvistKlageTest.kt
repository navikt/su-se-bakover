package no.nav.su.se.bakover.service.klage

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.klage.AvvistKlage
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.klage.KlageTilAttestering
import no.nav.su.se.bakover.domain.klage.KunneIkkeBekrefteKlagesteg
import no.nav.su.se.bakover.domain.klage.VilkårsvurderingerTilKlage
import no.nav.su.se.bakover.domain.klage.VilkårsvurdertKlage
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.test.bekreftetAvvistKlage
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

internal class BekreftetAvvistKlageTest {

    @Nested
    inner class UgyldigeStatusoverganger {
        private fun verifiserUgyldigTilstandsovergang(klage: Klage) {
            val mocks = KlageServiceMocks(
                klageRepoMock = mock {
                    on { hentKlage(any()) } doReturn klage
                },
            )
            mocks.service.bekreftAvvistFritekst(
                klageId = klage.id,
                saksbehandler = NavIdentBruker.Saksbehandler("saksbehandler"),
            ) shouldBe KunneIkkeBekrefteKlagesteg.UgyldigTilstand(klage::class, AvvistKlage.Bekreftet::class)
                .left()

            Mockito.verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
            mocks.verifyNoMoreInteractions()
        }

        @Test
        fun `fra opprettet til bekreftetAvvist`() {
            verifiserUgyldigTilstandsovergang(opprettetKlage().second)
        }

        @Test
        fun `fra påbegyntVilkårsvurdert til bekreftetAvvist`() {
            verifiserUgyldigTilstandsovergang(påbegyntVilkårsvurdertKlage().second)
        }

        @Test
        fun `fra utfyltVilkårsvurdert(TilVurdering) til bekreftetAvvist`() {
            verifiserUgyldigTilstandsovergang(utfyltVilkårsvurdertKlageTilVurdering().second)
        }

        @Test
        fun `fra utfyltVilårsvurdert(Avvist) til bekreftetAvvist`() {
            verifiserUgyldigTilstandsovergang(utfyltAvvistVilkårsvurdertKlage().second)
        }

        @Test
        fun `fra bekreftetVilkårsvurdert(TilVurdering) til bekreftetAvvist`() {
            verifiserUgyldigTilstandsovergang(bekreftetVilkårsvurdertKlageTilVurdering().second)
        }

        @Test
        fun `fra bekreftetVilkårsvurdert(Avvist) til bekreftetAvvist`() {
            verifiserUgyldigTilstandsovergang(bekreftetAvvistVilkårsvurdertKlage().second)
        }

        @Test
        fun `fra påbegyntVurdert til bekreftetAvvist`() {
            verifiserUgyldigTilstandsovergang(påbegyntVurdertKlage().second)
        }

        @Test
        fun `fra utfyltVurdert til bekreftetAvvist`() {
            verifiserUgyldigTilstandsovergang(utfyltVurdertKlage().second)
        }

        @Test
        fun `fra bekreftetVurdert til bekreftetAvvist`() {
            verifiserUgyldigTilstandsovergang(bekreftetVurdertKlage().second)
        }

        @Test
        fun `fra tilAttestering(Vurdert) til bekreftetAvvist`() {
            verifiserUgyldigTilstandsovergang(vurdertKlageTilAttestering().second)
        }

        @Test
        fun `fra oversendt til bekreftetAvvist`() {
            verifiserUgyldigTilstandsovergang(oversendtKlage().second)
        }

        @Test
        fun `fra iverksattAvvist til bekreftetAvvist`() {
            verifiserUgyldigTilstandsovergang(iverksattAvvistKlage().second)
        }
    }

    @Nested
    inner class GyldigeStatusoverganger {

        @Test
        fun `fra påbegyntAvvist til bekreftetAvvist`() {
            val klage = påbegyntAvvistKlage().second

            val actual = klage.bekreftAvvistFritekstTilBrev(
                saksbehandler = NavIdentBruker.Saksbehandler("saksbehandler"),
            )

            actual shouldBe AvvistKlage.Bekreftet.create(
                forrigeSteg = VilkårsvurdertKlage.Bekreftet.Avvist.create(
                    klage.id, klage.opprettet, klage.sakId,
                    klage.saksnummer, klage.fnr, klage.journalpostId,
                    klage.oppgaveId, klage.saksbehandler,
                    klage.vilkårsvurderinger,
                    klage.vurderinger, klage.attesteringer, klage.datoKlageMottatt,
                ),
                fritekstTilBrev = "fritekstTilBrev",
            ).right()
        }

        @Test
        fun `fra bekreftetAvvist til påbegyntVilkårsvurdert`() {
            val klage = bekreftetAvvistKlage().second

            val actual = klage.vilkårsvurder(
                saksbehandler = NavIdentBruker.Saksbehandler("saksbehandler"),
                vilkårsvurderinger = VilkårsvurderingerTilKlage.Påbegynt.empty(),
            ).getOrFail()

            actual shouldBe VilkårsvurdertKlage.Påbegynt.create(
                klage.id, klage.opprettet, klage.sakId,
                klage.saksnummer, klage.fnr, klage.journalpostId,
                klage.oppgaveId, klage.saksbehandler,
                VilkårsvurderingerTilKlage.Påbegynt.empty(),
                klage.vurderinger, klage.attesteringer, klage.datoKlageMottatt,
            )
        }

        @Test
        fun `fra bekreftetAvvist til utfyltVilkårsvurdert(TilVurdering)`() {
            val klage = bekreftetAvvistKlage().second

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
                klage.id, klage.opprettet, klage.sakId,
                klage.saksnummer, klage.fnr, klage.journalpostId,
                klage.oppgaveId, klage.saksbehandler,
                actual.vilkårsvurderinger as VilkårsvurderingerTilKlage.Utfylt,
                klage.vurderinger, klage.attesteringer, klage.datoKlageMottatt,
            )
        }

        @Test
        fun `fra bekreftetAvvist til utfyltVilkårsvurdert(Avvist)`() {
            val klage = bekreftetAvvistKlage().second

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
                klage.id, klage.opprettet, klage.sakId,
                klage.saksnummer, klage.fnr, klage.journalpostId,
                klage.oppgaveId, klage.saksbehandler,
                actual.vilkårsvurderinger as VilkårsvurderingerTilKlage.Utfylt,
                klage.vurderinger, klage.attesteringer, klage.datoKlageMottatt,
            )
        }

        @Test
        fun `fra bekreftetAvvist til bekreftetVilkårsvurdert(Avvist)`() {
            val klage = bekreftetAvvistKlage().second

            val actual = klage.bekreftVilkårsvurderinger(
                saksbehandler = NavIdentBruker.Saksbehandler("saksbehandler"),
            ).getOrFail()

            actual shouldBe VilkårsvurdertKlage.Bekreftet.Avvist.create(
                klage.id, klage.opprettet, klage.sakId,
                klage.saksnummer, klage.fnr, klage.journalpostId,
                klage.oppgaveId, klage.saksbehandler,
                actual.vilkårsvurderinger,
                klage.vurderinger, klage.attesteringer, klage.datoKlageMottatt,
            )
        }

        @Test
        fun `fra bekreftetAvvist til tilAttestering(Avvist)`() {
            val klage = bekreftetAvvistKlage().second

            val actual = klage.sendTilAttestering(
                saksbehandler = NavIdentBruker.Saksbehandler("saksbehandler"),
            ) { OppgaveId("oppgaveId").right() }.getOrFail()

            actual shouldBe KlageTilAttestering.Avvist.create(
                klage.id, klage.opprettet, klage.sakId,
                klage.saksnummer, klage.fnr, klage.journalpostId,
                OppgaveId("oppgaveId"), klage.saksbehandler,
                klage.vilkårsvurderinger,
                klage.vurderinger, klage.attesteringer, klage.datoKlageMottatt,
                klage.fritekstTilBrev,
            )
        }
    }
}
