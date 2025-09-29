package no.nav.su.se.bakover.service.klage

import arrow.core.left
import arrow.core.right
import behandling.klage.domain.VilkårsvurderingerTilKlage
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.domain.klage.AvvistKlage
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.klage.KlageTilAttestering
import no.nav.su.se.bakover.domain.klage.Klageinstanshendelser
import no.nav.su.se.bakover.domain.klage.KunneIkkeLeggeTilFritekstForAvvist
import no.nav.su.se.bakover.domain.klage.VilkårsvurdertKlage
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.avvistKlage
import no.nav.su.se.bakover.test.avvistKlageTilAttestering
import no.nav.su.se.bakover.test.bekreftetAvvistVilkårsvurdertKlage
import no.nav.su.se.bakover.test.bekreftetVilkårsvurdertKlageTilVurdering
import no.nav.su.se.bakover.test.bekreftetVurdertKlage
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.iverksattAvvistKlage
import no.nav.su.se.bakover.test.opprettetKlage
import no.nav.su.se.bakover.test.oversendtKlage
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

internal class AvvistKlageTest {

    @Nested
    inner class UgyldigStatusoverganger {
        private fun kanIkkeLeggeTilFritekstFraTilstand(klage: Klage) {
            val mocks = KlageServiceMocks(
                klageRepoMock = mock {
                    on { hentKlage(any()) } doReturn klage
                },
            )
            mocks.service.leggTilAvvistFritekstTilBrev(
                klageId = klage.id,
                saksbehandler = NavIdentBruker.Saksbehandler("saksbehandler"),
                fritekst = "min fritekst",
            ) shouldBe KunneIkkeLeggeTilFritekstForAvvist.UgyldigTilstand(klage::class)
                .left()

            Mockito.verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
            mocks.verifyNoMoreInteractions()
        }

        @Test
        fun `fra opprettet til avvist`() {
            kanIkkeLeggeTilFritekstFraTilstand(opprettetKlage().second)
        }

        @Test
        fun `fra påbegyntVilkårsvurdert til avvist`() {
            kanIkkeLeggeTilFritekstFraTilstand(påbegyntVilkårsvurdertKlage().second)
        }

        @Test
        fun `fra utfyltVilkårsvurdert(TilVurdering) til avvist`() {
            kanIkkeLeggeTilFritekstFraTilstand(utfyltVilkårsvurdertKlageTilVurdering().second)
        }

        @Test
        fun `fra utfyltVilkårsvurdert(Avvist) til avvist`() {
            kanIkkeLeggeTilFritekstFraTilstand(utfyltAvvistVilkårsvurdertKlage().second)
        }

        @Test
        fun `fra bekreftetVilkårsvurdert(TilVurdering) til avvist`() {
            kanIkkeLeggeTilFritekstFraTilstand(bekreftetVilkårsvurdertKlageTilVurdering().second)
        }

        @Test
        fun `fra påbegyntVurdert til avvist`() {
            kanIkkeLeggeTilFritekstFraTilstand(påbegyntVurdertKlage().second)
        }

        @Test
        fun `fra utfyltVurdert til avvist`() {
            kanIkkeLeggeTilFritekstFraTilstand(utfyltVurdertKlage().second)
        }

        @Test
        fun `fra bekreftetVurdert til avvist`() {
            kanIkkeLeggeTilFritekstFraTilstand(bekreftetVurdertKlage().second)
        }

        @Test
        fun `fra TilAttestering(Vurdert) til avvist`() {
            kanIkkeLeggeTilFritekstFraTilstand(vurdertKlageTilAttestering().second)
        }

        @Test
        fun `kan ikke gå fra TilAttestering(Avvist) til avvist ved å legge inn fritekst`() {
            kanIkkeLeggeTilFritekstFraTilstand(avvistKlageTilAttestering().second)
        }

        @Test
        fun `fra Oversendt til avvist`() {
            kanIkkeLeggeTilFritekstFraTilstand(oversendtKlage().second)
        }

        @Test
        fun `fra IverksattAvvist til avvist`() {
            kanIkkeLeggeTilFritekstFraTilstand(iverksattAvvistKlage().second)
        }
    }

    @Nested
    inner class GyldigeStatusoverganger {
        @Test
        fun `fra bekreftetVilkårsvurdert(Avvist) til Avvist`() {
            val klage = bekreftetAvvistVilkårsvurdertKlage().second

            val mocks = KlageServiceMocks(
                klageRepoMock = mock {
                    on { hentKlage(any()) } doReturn klage
                    on { defaultTransactionContext() } doReturn TestSessionFactory.transactionContext
                },
            )

            val actual = mocks.service.leggTilAvvistFritekstTilBrev(
                klageId = klage.id,
                saksbehandler = NavIdentBruker.Saksbehandler("saksbehandler"),
                fritekst = "en nice fritekst",
            ).getOrFail()

            val expected = AvvistKlage(
                forrigeSteg = klage,
                saksbehandler = NavIdentBruker.Saksbehandler("saksbehandler"),
                fritekstTilVedtaksbrev = "en nice fritekst",
                sakstype = klage.sakstype,
            )

            actual shouldBe expected

            Mockito.verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })

            Mockito.verify(mocks.klageRepoMock).lagre(
                argThat { it shouldBe expected },
                argThat { it shouldBe TestSessionFactory.transactionContext },
            )
        }

        @Test
        fun `fra avvist til påbegyntVilkårsvurdert(TilVurdering)`() {
            val klage = avvistKlage().second

            val actual = klage.vilkårsvurder(
                saksbehandler = NavIdentBruker.Saksbehandler("saksbehandler"),
                vilkårsvurderinger = VilkårsvurderingerTilKlage.Påbegynt.empty(),
            )

            actual shouldBe VilkårsvurdertKlage.Påbegynt(
                id = klage.id,
                opprettet = klage.opprettet,
                sakId = klage.sakId,
                sakstype = klage.sakstype,
                saksnummer = klage.saksnummer,
                fnr = klage.fnr,
                journalpostId = klage.journalpostId,
                oppgaveId = klage.oppgaveId,
                saksbehandler = klage.saksbehandler,
                vilkårsvurderinger = VilkårsvurderingerTilKlage.Påbegynt.empty(),
                attesteringer = klage.attesteringer,
                datoKlageMottatt = klage.datoKlageMottatt,
            ).right()
        }

        @Test
        fun `fra avvist til utfyltVilkårsvurdert(TilVurdering)`() {
            val klage = avvistKlage().second

            val actual = klage.vilkårsvurder(
                saksbehandler = NavIdentBruker.Saksbehandler("saksbehandler"),
                vilkårsvurderinger = VilkårsvurderingerTilKlage.create(
                    vedtakId = UUID.randomUUID(),
                    innenforFristen = VilkårsvurderingerTilKlage.Svarord.JA,
                    klagesDetPåKonkreteElementerIVedtaket = true,
                    erUnderskrevet = VilkårsvurderingerTilKlage.Svarord.JA,
                    begrunnelse = "",
                ),
            ).getOrFail()

            actual shouldBe VilkårsvurdertKlage.Utfylt.TilVurdering(
                id = klage.id,
                opprettet = klage.opprettet,
                sakId = klage.sakId,
                sakstype = klage.sakstype,
                saksnummer = klage.saksnummer,
                fnr = klage.fnr,
                journalpostId = klage.journalpostId,
                oppgaveId = klage.oppgaveId,
                saksbehandler = klage.saksbehandler,
                vilkårsvurderinger = actual.vilkårsvurderinger as VilkårsvurderingerTilKlage.Utfylt,
                vurderinger = null,
                attesteringer = klage.attesteringer,
                datoKlageMottatt = klage.datoKlageMottatt,
                klageinstanshendelser = Klageinstanshendelser.empty(),
            )
        }

        @Test
        fun `fra avvist til utfyltVilkårsvurdert(Avvist)`() {
            val klage = avvistKlage().second

            val actual = klage.vilkårsvurder(
                saksbehandler = NavIdentBruker.Saksbehandler("saksbehandler"),
                vilkårsvurderinger = VilkårsvurderingerTilKlage.create(
                    vedtakId = UUID.randomUUID(),
                    innenforFristen = VilkårsvurderingerTilKlage.Svarord.JA,
                    klagesDetPåKonkreteElementerIVedtaket = false,
                    erUnderskrevet = VilkårsvurderingerTilKlage.Svarord.JA,
                    begrunnelse = "",
                ),
            ).getOrFail()

            actual shouldBe VilkårsvurdertKlage.Utfylt.Avvist(
                id = klage.id,
                opprettet = klage.opprettet,
                sakId = klage.sakId,
                sakstype = klage.sakstype,
                saksnummer = klage.saksnummer,
                fnr = klage.fnr,
                journalpostId = klage.journalpostId,
                oppgaveId = klage.oppgaveId,
                saksbehandler = klage.saksbehandler,
                vilkårsvurderinger = actual.vilkårsvurderinger as VilkårsvurderingerTilKlage.Utfylt,
                attesteringer = klage.attesteringer,
                datoKlageMottatt = klage.datoKlageMottatt,
                fritekstTilVedtaksbrev = klage.fritekstTilVedtaksbrev,
            )
        }

        @Test
        fun `fra avvist til bekreftetVilkårsvurdert(Avvist)`() {
            val klage = avvistKlage().second

            val actual: VilkårsvurdertKlage.Bekreftet = klage.bekreftVilkårsvurderinger(
                saksbehandler = NavIdentBruker.Saksbehandler("saksbehandler"),
            ).getOrFail()

            actual shouldBe VilkårsvurdertKlage.Bekreftet.Avvist(
                id = klage.id,
                opprettet = klage.opprettet,
                sakId = klage.sakId,
                saksnummer = klage.saksnummer,
                sakstype = klage.sakstype,
                fnr = klage.fnr,
                journalpostId = klage.journalpostId,
                oppgaveId = klage.oppgaveId,
                saksbehandler = klage.saksbehandler,
                vilkårsvurderinger = actual.vilkårsvurderinger,
                attesteringer = klage.attesteringer,
                datoKlageMottatt = klage.datoKlageMottatt,
                fritekstTilAvvistVedtaksbrev = klage.fritekstTilVedtaksbrev,
            )
        }
    }

    @Test
    fun `fra avvist til TilAttestering`() {
        val klage = avvistKlage().second

        val actual = klage.sendTilAttestering(
            saksbehandler = NavIdentBruker.Saksbehandler("saksbehandler"),
        ).getOrFail()

        actual shouldBe KlageTilAttestering.Avvist(
            forrigeSteg = klage,
            saksbehandler = klage.saksbehandler,
            sakstype = klage.sakstype,
        )
    }

    @Test
    fun `en avvist klage er en åpen klage`() {
        val klage = avvistKlage().second
        klage.erÅpen() shouldBe true
    }
}
