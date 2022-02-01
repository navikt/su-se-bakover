package no.nav.su.se.bakover.service.revurdering

import arrow.core.left
import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsteg
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.UtenlandsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderingsresultat
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeUtenlandsopphold
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.vilkår.LeggTilFlereUtenlandsoppholdRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilUtenlandsoppholdRequest
import no.nav.su.se.bakover.service.vilkår.UtenlandsoppholdStatus
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.iverksattRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.periode2021
import no.nav.su.se.bakover.test.revurderingId
import org.junit.jupiter.api.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

internal class RevurderingLeggTilUtenlandsoppholdTest {

    @Test
    fun `legg til utenlandsopphold vilkår happy case`() {
        val opprettetRevurdering = opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak().second
        val revurderingIkkeVurdert = opprettetRevurdering.copy(
            vilkårsvurderinger = opprettetRevurdering.vilkårsvurderinger.leggTil(UtenlandsoppholdVilkår.IkkeVurdert),
        )

        revurderingIkkeVurdert.vilkårsvurderinger.resultat shouldBe Vilkårsvurderingsresultat.Uavklart(
            setOf(UtenlandsoppholdVilkår.IkkeVurdert),
        )

        val expected = opprettetRevurdering.copy(
            informasjonSomRevurderes = opprettetRevurdering.informasjonSomRevurderes.markerSomVurdert(Revurderingsteg.Utenlandsopphold),
            vilkårsvurderinger = opprettetRevurdering.vilkårsvurderinger.leggTil(
                UtenlandsoppholdVilkår.Vurdert.tryCreate(
                    vurderingsperioder = nonEmptyListOf(
                        VurderingsperiodeUtenlandsopphold.tryCreate(
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
            revurderingRepo = mock {
                on { hent(revurderingId) } doReturn revurderingIkkeVurdert
            },
        ).let {
            val actual = it.revurderingService.leggTilUtenlandsopphold(
                request = LeggTilFlereUtenlandsoppholdRequest(
                    behandlingId = revurderingId,
                    request = nonEmptyListOf(
                        LeggTilUtenlandsoppholdRequest(
                            behandlingId = opprettetRevurdering.id,
                            periode = periode2021,
                            status = UtenlandsoppholdStatus.SkalHoldeSegINorge,
                            begrunnelse = "begrunnelse",
                        ),
                    ),
                ),
            ).getOrFail()

            actual.revurdering shouldBe expected
            actual.feilmeldinger shouldBe emptyList()

            verify(it.revurderingRepo).hent(revurderingId)
            verify(it.revurderingRepo).defaultTransactionContext()
            verify(it.revurderingRepo).lagre(
                argThat { lagret ->
                    lagret shouldBe expected
                    lagret.vilkårsvurderinger.resultat shouldBe Vilkårsvurderingsresultat.Innvilget(
                        lagret.vilkårsvurderinger.vilkår,
                    )
                },
                anyOrNull()
            )
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `legg til utenlandsopphold vilkår ugyldig tilstand`() {
        val opprettetRevurdering = iverksattRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak().second

        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(revurderingId) } doReturn opprettetRevurdering
            },
        ).let {
            it.revurderingService.leggTilUtenlandsopphold(
                request = LeggTilFlereUtenlandsoppholdRequest(
                    behandlingId = revurderingId,
                    request = nonEmptyListOf(
                        LeggTilUtenlandsoppholdRequest(
                            behandlingId = opprettetRevurdering.id,
                            periode = periode2021,
                            status = UtenlandsoppholdStatus.SkalHoldeSegINorge,
                            begrunnelse = "begrunnelse",
                        ),
                    ),
                ),
            ) shouldBe KunneIkkeLeggeTilUtenlandsopphold.UgyldigTilstand(
                fra = IverksattRevurdering.Innvilget::class,
                til = OpprettetRevurdering::class,
            ).left()

            verify(it.revurderingRepo).hent(revurderingId)
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `legg til utenlandsopphold finner ikke revurdering`() {
        val opprettetRevurdering = iverksattRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak().second

        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(revurderingId) } doReturn null
            },
        ).let {
            it.revurderingService.leggTilUtenlandsopphold(
                request = LeggTilFlereUtenlandsoppholdRequest(
                    behandlingId = revurderingId,
                    request = nonEmptyListOf(
                        LeggTilUtenlandsoppholdRequest(
                            behandlingId = opprettetRevurdering.id,
                            periode = periode2021,
                            status = UtenlandsoppholdStatus.SkalHoldeSegINorge,
                            begrunnelse = "begrunnelse",
                        ),
                    ),
                ),
            ) shouldBe KunneIkkeLeggeTilUtenlandsopphold.FantIkkeBehandling.left()

            verify(it.revurderingRepo).hent(revurderingId)
            it.verifyNoMoreInteractions()
        }
    }
}
