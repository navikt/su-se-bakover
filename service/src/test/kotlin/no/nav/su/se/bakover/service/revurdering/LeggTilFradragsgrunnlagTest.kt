package no.nav.su.se.bakover.service.revurdering

import arrow.core.getOrHandle
import arrow.core.left
import io.kotest.assertions.fail
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.grunnlag.Konsistensproblem
import no.nav.su.se.bakover.domain.grunnlag.KunneIkkeLageGrunnlagsdata
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingRepo
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.grunnlag.LeggTilFradragsgrunnlagRequest
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.lagFradragsgrunnlag
import no.nav.su.se.bakover.test.opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.revurderingId
import no.nav.su.se.bakover.test.tilAttesteringRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.vilkårsvurderingerSøknadsbehandlingInnvilget
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoMoreInteractions
import java.util.UUID

internal class LeggTilFradragsgrunnlagTest {

    @Test
    fun `lagreFradrag happy case`() {
        val revurderingsperiode = år(2021)
        val eksisterendeRevurdering = opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak().second

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn eksisterendeRevurdering
        }

        val revurderingService = RevurderingTestUtils.createRevurderingService(
            revurderingRepo = revurderingRepoMock,
        )

        val request = LeggTilFradragsgrunnlagRequest(
            behandlingId = eksisterendeRevurdering.id,
            fradragsgrunnlag = listOf(
                lagFradragsgrunnlag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 0.0,
                    periode = revurderingsperiode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
        )

        val actual = revurderingService.leggTilFradragsgrunnlag(
            request,
        ).getOrHandle { fail("Feilet med $it") }

        inOrder(
            revurderingRepoMock,
        ) {
            verify(revurderingRepoMock).hent(argThat { it shouldBe eksisterendeRevurdering.id })

            verify(revurderingRepoMock).defaultTransactionContext()
            verify(revurderingRepoMock).lagre(argThat { it shouldBe actual.revurdering }, anyOrNull())
        }

        verifyNoMoreInteractions(revurderingRepoMock)
    }

    @Test
    fun `leggTilFradragsgrunnlag - lagreFradrag finner ikke revurdering`() {
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn null
        }

        val revurderingService = RevurderingTestUtils.createRevurderingService(
            revurderingRepo = revurderingRepoMock,
        )

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

        revurderingService.leggTilFradragsgrunnlag(
            request,
        ) shouldBe KunneIkkeLeggeTilFradragsgrunnlag.FantIkkeBehandling.left()

        inOrder(
            revurderingRepoMock,
        ) {
            verify(revurderingRepoMock).hent(argThat { it shouldBe revurderingId })
        }

        verifyNoMoreInteractions(revurderingRepoMock)
    }

    @Test
    fun `leggTilFradragsgrunnlag - lagreFradrag har en status som gjør at man ikke kan legge til fradrag`() {
        val eksisterendeRevurdering = tilAttesteringRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak().second

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn eksisterendeRevurdering
        }

        val revurderingService = RevurderingTestUtils.createRevurderingService(
            revurderingRepo = revurderingRepoMock,
        )

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

        revurderingService.leggTilFradragsgrunnlag(
            request,
        ) shouldBe
            KunneIkkeLeggeTilFradragsgrunnlag.UgyldigTilstand(
                fra = eksisterendeRevurdering::class,
                til = OpprettetRevurdering::class,
            ).left()

        inOrder(
            revurderingRepoMock,
        ) {
            verify(revurderingRepoMock).hent(argThat { it shouldBe eksisterendeRevurdering.id })
        }

        verifyNoMoreInteractions(revurderingRepoMock)
    }

    @Test
    fun `validerer fradragsgrunnlag`() {
        val revurderingsperiode = Periode.create(1.mai(2021), 31.desember(2021))
        val opprettetRevurdering = opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak(
            revurderingsperiode = revurderingsperiode,
            grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling(
                vilkårsvurderinger = vilkårsvurderingerSøknadsbehandlingInnvilget(periode = revurderingsperiode),
                grunnlagsdata = Grunnlagsdata.create(
                    bosituasjon = listOf(
                        Grunnlag.Bosituasjon.Fullstendig.Enslig(
                            id = UUID.randomUUID(),
                            opprettet = fixedTidspunkt,
                            periode = revurderingsperiode,
                        ),
                    ),
                ),
            ),
        )

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn opprettetRevurdering.second
        }

        val revurderingService = RevurderingTestUtils.createRevurderingService(
            revurderingRepo = revurderingRepoMock,
        )

        val request = LeggTilFradragsgrunnlagRequest(
            behandlingId = revurderingId,
            fradragsgrunnlag = listOf(
                lagFradragsgrunnlag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 0.0,
                    periode = revurderingsperiode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
                lagFradragsgrunnlag(
                    type = Fradragstype.Kontantstøtte,
                    månedsbeløp = 0.0,
                    periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.juli(2021)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
        )

        revurderingService.leggTilFradragsgrunnlag(request) shouldBe KunneIkkeLeggeTilFradragsgrunnlag.KunneIkkeEndreFradragsgrunnlag(
            KunneIkkeLageGrunnlagsdata.Konsistenssjekk(Konsistensproblem.BosituasjonOgFradrag.IngenBosituasjonForFradragsperiode),
        ).left()
    }
}
