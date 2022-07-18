package no.nav.su.se.bakover.service.revurdering

import arrow.core.getOrHandle
import arrow.core.nonEmptyListOf
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.revurdering.RevurderingsutfallSomIkkeStøttes
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.vilkår.LeggTilUførevilkårRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilUførevurderingerRequest
import no.nav.su.se.bakover.service.vilkår.UførevilkårStatus
import no.nav.su.se.bakover.test.opprettetRevurdering
import no.nav.su.se.bakover.test.stønadsperiode2021
import no.nav.su.se.bakover.test.vilkår.formuevilkårAvslåttPgrBrukersformue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions

internal class RevurderingLeggTilUføregrunnlagTest {

    @Test
    fun `avslår uførhet, med avslått formue, gir feilmelding om at utfallet ikke støttes`() {
        val (sak, opprettetRevurdering) = opprettetRevurdering(
            vilkårOverrides = listOf(
                formuevilkårAvslåttPgrBrukersformue()
            )
        )

        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn opprettetRevurdering
            },
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            }
        ).also {
            val actual = it.revurderingService.leggTilUførevilkår(
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

            verify(it.revurderingRepo).hent(opprettetRevurdering.id)
            verify(it.revurderingRepo).defaultTransactionContext()
            verify(it.revurderingRepo).lagre(
                argThat {
                    it shouldBe actual.revurdering
                },
                anyOrNull(),
            )
            verifyNoMoreInteractions(it.revurderingRepo)
        }
    }
}
