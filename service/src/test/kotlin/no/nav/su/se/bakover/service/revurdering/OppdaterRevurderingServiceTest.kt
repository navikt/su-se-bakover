package no.nav.su.se.bakover.service.revurdering

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.endOfMonth
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.revurdering.RevurderingRepo
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.fixedLocalDate
import no.nav.su.se.bakover.service.fixedTidspunkt
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.søknadsbehandlingVedtak
import org.junit.jupiter.api.Test
import java.time.temporal.ChronoUnit
import java.util.UUID

val oppgaveId = OppgaveId("oppgaveId")

internal class OppdaterRevurderingServiceTest {
    private val behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt()

    private val opprettetFraOgMed = fixedLocalDate
    private val opprettetTilOgMed = fixedLocalDate.plus(11, ChronoUnit.MONTHS).endOfMonth()
    private val periode = Periode.create(
        fraOgMed = opprettetFraOgMed,
        tilOgMed = opprettetTilOgMed,
    )
    private val saksbehandler = NavIdentBruker.Saksbehandler("Sak S. behandler")
    private val revurderingId = UUID.randomUUID()

    private val revurderingsårsak = Revurderingsårsak(
        Revurderingsårsak.Årsak.MELDING_FRA_BRUKER,
        Revurderingsårsak.Begrunnelse.create("Ny informasjon"),
    )

    private val tilRevurdering = søknadsbehandlingVedtak.copy(periode = periode)
    private val opprettetRevurdering = OpprettetRevurdering(
        id = revurderingId,
        periode = periode,
        opprettet = fixedTidspunkt,
        tilRevurdering = tilRevurdering,
        saksbehandler = saksbehandler,
        oppgaveId = oppgaveId,
        fritekstTilBrev = "",
        revurderingsårsak = revurderingsårsak,
        behandlingsinformasjon = behandlingsinformasjon,
    )

    @Test
    fun `ugyldig begrunnelse`() {
        val mocks = RevurderingServiceMocks()
        val actual = mocks.revurderingService.oppdaterRevurdering(
            OppdaterRevurderingRequest(
                revurderingId = revurderingId,
                fraOgMed = opprettetFraOgMed,
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "",
                saksbehandler = saksbehandler,
            ),
        )
        actual shouldBe KunneIkkeOppdatereRevurderingsperiode.UgyldigBegrunnelse.left()
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `ugyldig årsak`() {
        val mocks = RevurderingServiceMocks()
        val actual = mocks.revurderingService.oppdaterRevurdering(
            OppdaterRevurderingRequest(
                revurderingId = revurderingId,
                fraOgMed = opprettetFraOgMed,
                årsak = "UGYLDIG_ÅRSAK",
                begrunnelse = "gyldig begrunnelse",
                saksbehandler = saksbehandler,
            ),
        )
        actual shouldBe KunneIkkeOppdatereRevurderingsperiode.UgyldigÅrsak.left()
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `Fant ikke revurdering`() {
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn null
        }
        val mocks = RevurderingServiceMocks(revurderingRepo = revurderingRepoMock)
        val actual = mocks.revurderingService.oppdaterRevurdering(
            OppdaterRevurderingRequest(
                revurderingId = revurderingId,
                fraOgMed = opprettetFraOgMed,
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "gyldig begrunnelse",
                saksbehandler = saksbehandler,
            ),
        )
        actual shouldBe KunneIkkeOppdatereRevurderingsperiode.FantIkkeRevurdering.left()
        verify(revurderingRepoMock).hent(argThat { it shouldBe revurderingId })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `Perioden må være innenfor allerede valgt stønadsperiode`() {
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn opprettetRevurdering
        }
        val mocks = RevurderingServiceMocks(revurderingRepo = revurderingRepoMock)
        val actual = mocks.revurderingService.oppdaterRevurdering(
            OppdaterRevurderingRequest(
                revurderingId = revurderingId,
                fraOgMed = periode.getFraOgMed().minus(1, ChronoUnit.MONTHS),
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "gyldig begrunnelse",
                saksbehandler = saksbehandler,
            ),
        )
        actual shouldBe KunneIkkeOppdatereRevurderingsperiode.PeriodenMåVæreInnenforAlleredeValgtStønadsperiode(periode)
            .left()
        verify(revurderingRepoMock).hent(argThat { it shouldBe revurderingId })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `Ugyldig periode - fra og med dato må være første dag i måneden`() {
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn opprettetRevurdering
        }
        val mocks = RevurderingServiceMocks(revurderingRepo = revurderingRepoMock)
        val actual = mocks.revurderingService.oppdaterRevurdering(
            OppdaterRevurderingRequest(
                revurderingId = revurderingId,
                fraOgMed = periode.getFraOgMed().plus(1, ChronoUnit.DAYS),
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "gyldig begrunnelse",
                saksbehandler = saksbehandler,
            ),
        )
        actual shouldBe KunneIkkeOppdatereRevurderingsperiode.UgyldigPeriode(Periode.UgyldigPeriode.FraOgMedDatoMåVæreFørsteDagIMåneden)
            .left()
        verify(revurderingRepoMock).hent(argThat { it shouldBe revurderingId })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `Ugyldig tilstand`() {
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn opprettetRevurdering.let {
                IverksattRevurdering.Innvilget(
                    id = it.id,
                    periode = it.periode,
                    opprettet = it.opprettet,
                    tilRevurdering = it.tilRevurdering,
                    saksbehandler = it.saksbehandler,
                    oppgaveId = it.oppgaveId,
                    fritekstTilBrev = it.fritekstTilBrev,
                    revurderingsårsak = it.revurderingsårsak,
                    beregning = mock(),
                    attestering = Attestering.Iverksatt(
                        attestant = NavIdentBruker.Attestant("navIdent"),
                    ),
                    behandlingsinformasjon = behandlingsinformasjon,
                    simulering = mock(),
                )
            }
        }
        val mocks = RevurderingServiceMocks(revurderingRepo = revurderingRepoMock)
        val actual = mocks.revurderingService.oppdaterRevurdering(
            OppdaterRevurderingRequest(
                revurderingId = revurderingId,
                fraOgMed = periode.getFraOgMed(),
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "gyldig begrunnelse",
                saksbehandler = saksbehandler,
            ),
        )
        actual shouldBe KunneIkkeOppdatereRevurderingsperiode.UgyldigTilstand(
            IverksattRevurdering.Innvilget::class,
            OpprettetRevurdering::class,
        ).left()
        verify(revurderingRepoMock).hent(argThat { it shouldBe revurderingId })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `oppdater en revurdering`() {
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn opprettetRevurdering
        }

        val mocks = RevurderingServiceMocks(revurderingRepo = revurderingRepoMock)
        val actual = mocks.revurderingService.oppdaterRevurdering(
            OppdaterRevurderingRequest(
                revurderingId = revurderingId,
                fraOgMed = periode.getFraOgMed(),
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "Ny informasjon",
                saksbehandler = saksbehandler,
            ),
        ).orNull()!!

        actual shouldBe OpprettetRevurdering(
            id = actual.id,
            periode = periode,
            opprettet = actual.opprettet,
            tilRevurdering = tilRevurdering,
            saksbehandler = saksbehandler,
            oppgaveId = oppgaveId,
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
            behandlingsinformasjon = tilRevurdering.behandlingsinformasjon,
        )
        inOrder(revurderingRepoMock) {
            verify(revurderingRepoMock).hent(argThat { it shouldBe revurderingId })
            verify(revurderingRepoMock).lagre(argThat { it.right() shouldBe actual.right() })
        }
        verifyNoMoreInteractions(revurderingRepoMock)
    }
}
