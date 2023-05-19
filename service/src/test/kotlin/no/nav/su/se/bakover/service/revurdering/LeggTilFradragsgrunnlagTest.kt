package no.nav.su.se.bakover.service.revurdering

import arrow.core.getOrElse
import arrow.core.left
import io.kotest.assertions.fail
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.grunnlag.fradrag.LeggTilFradragsgrunnlagRequest
import no.nav.su.se.bakover.domain.revurdering.repo.RevurderingRepo
import no.nav.su.se.bakover.domain.revurdering.vilkår.fradag.KunneIkkeLeggeTilFradragsgrunnlag
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.lagFradragsgrunnlag
import no.nav.su.se.bakover.test.opprettetRevurdering
import no.nav.su.se.bakover.test.revurderingId
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoMoreInteractions

internal class LeggTilFradragsgrunnlagTest {

    @Test
    fun `lagreFradrag happy case`() {
        val (sak, eksisterendeRevurdering) = opprettetRevurdering()

        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn eksisterendeRevurdering
            },
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
        ).also {
            val request = LeggTilFradragsgrunnlagRequest(
                behandlingId = eksisterendeRevurdering.id,
                fradragsgrunnlag = listOf(
                    lagFradragsgrunnlag(
                        type = Fradragstype.Arbeidsinntekt,
                        månedsbeløp = 0.0,
                        periode = år(2021),
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                ),
            )

            val actual = it.revurderingService.leggTilFradragsgrunnlag(
                request,
            ).getOrElse { fail("Feilet med $it") }

            inOrder(
                it.revurderingRepo,
            ) {
                verify(it.revurderingRepo).hent(argThat { it shouldBe eksisterendeRevurdering.id })

                verify(it.revurderingRepo).defaultTransactionContext()
                verify(it.revurderingRepo).lagre(argThat { it shouldBe actual.revurdering }, anyOrNull())
            }

            verifyNoMoreInteractions(it.revurderingRepo)
        }
    }

    @Test
    fun `leggTilFradragsgrunnlag - lagreFradrag finner ikke revurdering`() {
        RevurderingServiceMocks(
            revurderingRepo = mock<RevurderingRepo> {
                on { hent(any()) } doReturn null
            },
        ).also {
            val request = LeggTilFradragsgrunnlagRequest(
                behandlingId = revurderingId,
                fradragsgrunnlag = listOf(
                    lagFradragsgrunnlag(
                        type = Fradragstype.Arbeidsinntekt,
                        månedsbeløp = 0.0,
                        periode = år(2021),
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                ),
            )

            it.revurderingService.leggTilFradragsgrunnlag(
                request,
            ) shouldBe KunneIkkeLeggeTilFradragsgrunnlag.FantIkkeBehandling.left()

            inOrder(
                it.revurderingRepo,
            ) {
                verify(it.revurderingRepo).hent(argThat { it shouldBe revurderingId })
            }

            it.verifyNoMoreInteractions()
        }
    }
}
