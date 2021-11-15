package no.nav.su.se.bakover.service.revurdering

import arrow.core.left
import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.database.revurdering.RevurderingRepo
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsteg
import no.nav.su.se.bakover.domain.vilkår.OppholdIUtlandetVilkår
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeOppholdIUtlandet
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.vilkår.LeggTilOppholdIUtlandetRequest
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.iverksattRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.revurderingId
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

internal class RevurderingLeggTilOppholdIUtlandetTest {

    @Test
    fun `legg til oppholdIUtlandet vilkår happy case`() {
        val opprettetRevurdering = opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak().second
        val revurderingIkkeVurdert = opprettetRevurdering.copy(
            vilkårsvurderinger = opprettetRevurdering.vilkårsvurderinger.leggTil(OppholdIUtlandetVilkår.IkkeVurdert),
        )

        val expected = opprettetRevurdering.copy(
            informasjonSomRevurderes = opprettetRevurdering.informasjonSomRevurderes.markerSomVurdert(Revurderingsteg.OppholdIUtlandet),
            vilkårsvurderinger = opprettetRevurdering.vilkårsvurderinger.leggTil(
                OppholdIUtlandetVilkår.Vurdert.tryCreate(
                    vurderingsperioder = nonEmptyListOf(
                        VurderingsperiodeOppholdIUtlandet.tryCreate(
                            opprettet = opprettetRevurdering.opprettet,
                            resultat = Resultat.Innvilget,
                            grunnlag = null,
                            vurderingsperiode = opprettetRevurdering.periode,
                            begrunnelse = "begrunnelse",
                        ).getOrFail(),
                    ),
                ).getOrFail(),
            ),
        )

        RevurderingServiceMocks(
            revurderingRepo = mock<RevurderingRepo> {
                on { hent(revurderingId) } doReturn revurderingIkkeVurdert
            },
        ).let {
            val actual = it.revurderingService.leggTilUtlandsopphold(
                request = LeggTilOppholdIUtlandetRequest(
                    behandlingId = opprettetRevurdering.id,
                    status = LeggTilOppholdIUtlandetRequest.Status.SkalHoldeSegINorge,
                    begrunnelse = "begrunnelse",
                ),
            ).getOrFail()

            actual.revurdering shouldBe expected
            actual.feilmeldinger shouldBe emptyList()

            verify(it.revurderingRepo).hent(revurderingId)
            verify(it.revurderingRepo).lagre(
                argThat {
                    it shouldBe expected
                },
            )
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `legg til oppholdIUtlandet vilkår ugyldig tilstand`() {
        val opprettetRevurdering = iverksattRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak().second

        RevurderingServiceMocks(
            revurderingRepo = mock<RevurderingRepo> {
                on { hent(revurderingId) } doReturn opprettetRevurdering
            },
        ).let {
            it.revurderingService.leggTilUtlandsopphold(
                request = LeggTilOppholdIUtlandetRequest(
                    behandlingId = opprettetRevurdering.id,
                    status = LeggTilOppholdIUtlandetRequest.Status.SkalHoldeSegINorge,
                    begrunnelse = "begrunnelse",
                ),
            ) shouldBe KunneIkkeLeggeTilUtlandsopphold.UgyldigTilstand(
                fra = IverksattRevurdering.Innvilget::class,
                til = OpprettetRevurdering::class,
            ).left()

            verify(it.revurderingRepo).hent(revurderingId)
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `legg til oppholdIUtlandet finner ikke revurdering`() {
        val opprettetRevurdering = iverksattRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak().second

        RevurderingServiceMocks(
            revurderingRepo = mock<RevurderingRepo> {
                on { hent(revurderingId) } doReturn null
            },
        ).let {
            it.revurderingService.leggTilUtlandsopphold(
                request = LeggTilOppholdIUtlandetRequest(
                    behandlingId = opprettetRevurdering.id,
                    status = LeggTilOppholdIUtlandetRequest.Status.SkalHoldeSegINorge,
                    begrunnelse = "begrunnelse",
                ),
            ) shouldBe KunneIkkeLeggeTilUtlandsopphold.FantIkkeBehandling.left()

            verify(it.revurderingRepo).hent(revurderingId)
            it.verifyNoMoreInteractions()
        }
    }
}
