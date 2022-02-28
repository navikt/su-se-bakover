package no.nav.su.se.bakover.service.revurdering

import arrow.core.getOrHandle
import arrow.core.nonEmptyListOf
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.grunnlag.Formuegrunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.revurdering.RevurderingRepo
import no.nav.su.se.bakover.domain.revurdering.RevurderingsutfallSomIkkeStøttes
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.UtenlandsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.domain.vilkår.Vurderingsperiode
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.vilkår.LeggTilUførevilkårRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilUførevurderingerRequest
import no.nav.su.se.bakover.service.vilkår.UførevilkårStatus
import no.nav.su.se.bakover.test.create
import no.nav.su.se.bakover.test.empty
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.revurderingId
import no.nav.su.se.bakover.test.stønadsperiode2021
import org.junit.jupiter.api.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import java.util.UUID

internal class RevurderingLeggTilUføregrunnlagTest {

    @Test
    fun `avslår uførhet, med avslått formue, gir feilmelding om at utfallet ikke støttes`() {
        val opprettetRevurdering = opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak(
            grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderinger.Revurdering(
                grunnlagsdata = Grunnlagsdata.create(
                    bosituasjon = listOf(
                        Grunnlag.Bosituasjon.Fullstendig.Enslig(
                            id = UUID.randomUUID(), opprettet = fixedTidspunkt,
                            periode = stønadsperiode2021.periode,
                            begrunnelse = ":)",
                        ),
                    ),
                ),
                vilkårsvurderinger = Vilkårsvurderinger.Revurdering(
                    uføre = Vilkår.Uførhet.Vurdert.create(
                        vurderingsperioder = nonEmptyListOf(
                            Vurderingsperiode.Uføre.create(
                                id = UUID.randomUUID(),
                                opprettet = fixedTidspunkt,
                                resultat = Resultat.Innvilget,
                                grunnlag = null,
                                periode = stønadsperiode2021.periode,
                                begrunnelse = ":)",
                            ),
                        ),
                    ),
                    formue = Vilkår.Formue.Vurdert.createFromVilkårsvurderinger(
                        vurderingsperioder = nonEmptyListOf(
                            Vurderingsperiode.Formue.create(
                                id = UUID.randomUUID(), opprettet = fixedTidspunkt, resultat = Resultat.Avslag,
                                grunnlag = Formuegrunnlag.create(
                                    id = UUID.randomUUID(),
                                    periode = stønadsperiode2021.periode,
                                    opprettet = fixedTidspunkt,
                                    epsFormue = null,
                                    søkersFormue = Formuegrunnlag.Verdier.empty(),
                                    begrunnelse = null,
                                    bosituasjon = Grunnlag.Bosituasjon.Fullstendig.Enslig(
                                        id = UUID.randomUUID(),
                                        opprettet = fixedTidspunkt,
                                        periode = stønadsperiode2021.periode,
                                        begrunnelse = ":>",
                                    ),
                                    behandlingsPeriode = stønadsperiode2021.periode,
                                ),
                                periode = stønadsperiode2021.periode,
                            ),
                        ),
                    ),
                    utenlandsopphold = UtenlandsoppholdVilkår.IkkeVurdert
                )
            ),
        ).second

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(revurderingId) } doReturn opprettetRevurdering
        }

        val revurderingService = RevurderingTestUtils.createRevurderingService(
            revurderingRepo = revurderingRepoMock,
        )

        val actual = revurderingService.leggTilUføregrunnlag(
            request = LeggTilUførevurderingerRequest(
                behandlingId = opprettetRevurdering.id,
                vurderinger = nonEmptyListOf(
                    LeggTilUførevilkårRequest(
                        behandlingId = opprettetRevurdering.id,
                        periode = stønadsperiode2021.periode,
                        uføregrad = Uføregrad.parse(1),
                        forventetInntekt = 0,
                        oppfylt = UførevilkårStatus.VilkårIkkeOppfylt,
                        begrunnelse = ":<",
                    ),
                ),
            ),
        ).getOrHandle { throw IllegalStateException(it.toString()) }

        actual.feilmeldinger.shouldContain(RevurderingsutfallSomIkkeStøttes.OpphørAvFlereVilkår)

        verify(revurderingRepoMock).hent(revurderingId)
        verify(revurderingRepoMock).defaultTransactionContext()
        verify(revurderingRepoMock).lagre(
            argThat {
                it shouldBe actual.revurdering
            },
            anyOrNull()
        )
        verifyNoMoreInteractions(revurderingRepoMock)
    }
}
