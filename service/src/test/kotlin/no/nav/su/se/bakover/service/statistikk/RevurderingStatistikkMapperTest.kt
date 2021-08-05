package no.nav.su.se.bakover.service.statistikk

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.Forhåndsvarsel
import no.nav.su.se.bakover.domain.revurdering.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import org.junit.jupiter.api.Test
import java.time.Clock
import java.util.UUID

internal class RevurderingStatistikkMapperTest {
    private val fixedClock: Clock = Clock.fixed(1.januar(2021).startOfDay().instant, zoneIdOslo)
    private val revurderingsårsak = Revurderingsårsak(
        Revurderingsårsak.Årsak.MELDING_FRA_BRUKER,
        Revurderingsårsak.Begrunnelse.create("Ny informasjon"),
    )

    @Test
    fun `mapper opprettet revurdering`() {
        val behandlingMock = mock<Behandling> {
            on { sakId } doReturn UUID.randomUUID()
            on { saksnummer } doReturn Saksnummer(2021)
        }
        val opprettetRevurdering = OpprettetRevurdering(
            id = UUID.randomUUID(),
            periode = Periode.create(1.januar(2021), 31.januar(2021)),
            opprettet = Tidspunkt.now(fixedClock),
            tilRevurdering = mock {
                on { behandling } doReturn behandlingMock
                on { id } doReturn UUID.randomUUID()
            },
            saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "7"),
            oppgaveId = OppgaveId("oppgaveid"),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
            forhåndsvarsel = null,
            grunnlagsdata = Grunnlagsdata.EMPTY,
            vilkårsvurderinger = Vilkårsvurderinger.IkkeVurdert,
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
        )

        RevurderingStatistikkMapper(fixedClock).map(opprettetRevurdering) shouldBe Statistikk.Behandling(
            funksjonellTid = opprettetRevurdering.opprettet,
            tekniskTid = Tidspunkt.now(fixedClock),
            mottattDato = opprettetRevurdering.opprettet.toLocalDate(zoneIdOslo),
            registrertDato = opprettetRevurdering.opprettet.toLocalDate(zoneIdOslo),
            behandlingId = opprettetRevurdering.id,
            relatertBehandlingId = opprettetRevurdering.tilRevurdering.id,
            sakId = opprettetRevurdering.sakId,
            søknadId = null,
            saksnummer = opprettetRevurdering.saksnummer.nummer,
            behandlingType = Statistikk.Behandling.BehandlingType.REVURDERING,
            behandlingTypeBeskrivelse = Statistikk.Behandling.BehandlingType.REVURDERING.beskrivelse,
            behandlingStatus = "OPPRETTET",
            behandlingStatusBeskrivelse = "Ny revurdering opprettet",
            utenlandstilsnitt = "NASJONAL",
            utenlandstilsnittBeskrivelse = null,
            ansvarligEnhetKode = "4815",
            ansvarligEnhetType = "NORG",
            behandlendeEnhetKode = "4815",
            behandlendeEnhetType = "NORG",
            totrinnsbehandling = true,
            avsender = "su-se-bakover",
            versjon = fixedClock.millis(),
            vedtaksDato = null,
            vedtakId = null,
            resultat = null,
            resultatBegrunnelse = null,
            resultatBegrunnelseBeskrivelse = null,
            resultatBeskrivelse = null,
            beslutter = null,
            saksbehandler = "7",
            behandlingOpprettetAv = null,
            behandlingOpprettetType = null,
            behandlingOpprettetTypeBeskrivelse = null,
            datoForUttak = null,
            datoForUtbetaling = null,
            avsluttet = false,
        )
    }

    @Test
    fun `mapper iverksatt revurdering`() {
        val periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021))

        val behandlingMock = mock<Behandling> {
            on { sakId } doReturn UUID.randomUUID()
            on { saksnummer } doReturn Saksnummer(2021)
        }
        val iverksattRevurdering = IverksattRevurdering.Innvilget(
            id = UUID.randomUUID(),
            periode = periode,
            opprettet = Tidspunkt.now(fixedClock),
            tilRevurdering = mock {
                on { behandling } doReturn behandlingMock
                on { id } doReturn UUID.randomUUID()
            },
            saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "99"),
            oppgaveId = OppgaveId(value = "7"),
            beregning = mock {
                on { this.periode } doReturn periode
            },
            simulering = mock(),
            grunnlagsdata = Grunnlagsdata.EMPTY,
            attesteringer = Attesteringshistorikk.empty().leggTilNyAttestering(Attestering.Iverksatt(NavIdentBruker.Attestant(navIdent = "2"), Tidspunkt.now(fixedClock))),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
            forhåndsvarsel = Forhåndsvarsel.IngenForhåndsvarsel,
            vilkårsvurderinger = Vilkårsvurderinger.IkkeVurdert,
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
        )
        RevurderingStatistikkMapper(fixedClock).map(iverksattRevurdering) shouldBe Statistikk.Behandling(
            funksjonellTid = iverksattRevurdering.opprettet,
            tekniskTid = Tidspunkt.now(fixedClock),
            mottattDato = iverksattRevurdering.opprettet.toLocalDate(zoneIdOslo),
            registrertDato = iverksattRevurdering.opprettet.toLocalDate(zoneIdOslo),
            behandlingId = iverksattRevurdering.id,
            relatertBehandlingId = iverksattRevurdering.tilRevurdering.id,
            sakId = iverksattRevurdering.sakId,
            søknadId = null,
            saksnummer = iverksattRevurdering.saksnummer.nummer,
            behandlingType = Statistikk.Behandling.BehandlingType.REVURDERING,
            behandlingTypeBeskrivelse = Statistikk.Behandling.BehandlingType.REVURDERING.beskrivelse,
            behandlingStatus = "IVERKSATT_INNVILGET",
            behandlingStatusBeskrivelse = "Innvilget revurdering iverksatt",
            utenlandstilsnitt = "NASJONAL",
            utenlandstilsnittBeskrivelse = null,
            ansvarligEnhetKode = "4815",
            ansvarligEnhetType = "NORG",
            behandlendeEnhetKode = "4815",
            behandlendeEnhetType = "NORG",
            totrinnsbehandling = true,
            avsender = "su-se-bakover",
            versjon = fixedClock.millis(),
            vedtaksDato = null,
            vedtakId = null,
            resultat = "Innvilget",
            resultatBegrunnelse = "Endring i søkers inntekt",
            resultatBegrunnelseBeskrivelse = null,
            resultatBeskrivelse = null,
            beslutter = "2",
            saksbehandler = "99",
            behandlingOpprettetAv = null,
            behandlingOpprettetType = null,
            behandlingOpprettetTypeBeskrivelse = null,
            datoForUttak = null,
            datoForUtbetaling = null,
            avsluttet = true,
        )
    }

    @Test
    fun `mapper uendret iverksettinger`() {
        val periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021))

        val behandlingMock = mock<Behandling> {
            on { sakId } doReturn UUID.randomUUID()
            on { saksnummer } doReturn Saksnummer(2021)
        }
        val iverksattRevurdering = IverksattRevurdering.IngenEndring(
            id = UUID.randomUUID(),
            periode = periode,
            opprettet = Tidspunkt.now(fixedClock),
            tilRevurdering = mock {
                on { behandling } doReturn behandlingMock
                on { id } doReturn UUID.randomUUID()
            },
            saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "99"),
            oppgaveId = OppgaveId(value = "7"),
            beregning = mock {
                on { this.periode } doReturn periode
            },
            attesteringer = Attesteringshistorikk.empty().leggTilNyAttestering(Attestering.Iverksatt(NavIdentBruker.Attestant(navIdent = "2"), Tidspunkt.now(fixedClock))),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
            forhåndsvarsel = null,
            skalFøreTilBrevutsending = true,
            grunnlagsdata = Grunnlagsdata.EMPTY,
            vilkårsvurderinger = Vilkårsvurderinger.IkkeVurdert,
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
        )
        RevurderingStatistikkMapper(fixedClock).map(iverksattRevurdering) shouldBe Statistikk.Behandling(
            funksjonellTid = iverksattRevurdering.opprettet,
            tekniskTid = Tidspunkt.now(fixedClock),
            mottattDato = iverksattRevurdering.opprettet.toLocalDate(zoneIdOslo),
            registrertDato = iverksattRevurdering.opprettet.toLocalDate(zoneIdOslo),
            behandlingId = iverksattRevurdering.id,
            relatertBehandlingId = iverksattRevurdering.tilRevurdering.id,
            sakId = iverksattRevurdering.sakId,
            søknadId = null,
            saksnummer = iverksattRevurdering.saksnummer.nummer,
            behandlingType = Statistikk.Behandling.BehandlingType.REVURDERING,
            behandlingTypeBeskrivelse = Statistikk.Behandling.BehandlingType.REVURDERING.beskrivelse,
            behandlingStatus = "IVERKSATT_INGEN_ENDRING",
            behandlingStatusBeskrivelse = "Revurdering uten endring i ytelse iverksatt",
            utenlandstilsnitt = "NASJONAL",
            utenlandstilsnittBeskrivelse = null,
            ansvarligEnhetKode = "4815",
            ansvarligEnhetType = "NORG",
            behandlendeEnhetKode = "4815",
            behandlendeEnhetType = "NORG",
            totrinnsbehandling = true,
            avsender = "su-se-bakover",
            versjon = fixedClock.millis(),
            vedtaksDato = null,
            vedtakId = null,
            resultat = "Uendret",
            resultatBegrunnelse = "Endring i søkers inntekt",
            resultatBegrunnelseBeskrivelse = null,
            resultatBeskrivelse = null,
            beslutter = "2",
            saksbehandler = "99",
            behandlingOpprettetAv = null,
            behandlingOpprettetType = null,
            behandlingOpprettetTypeBeskrivelse = null,
            datoForUttak = null,
            datoForUtbetaling = null,
            avsluttet = true,
        )
    }
}
