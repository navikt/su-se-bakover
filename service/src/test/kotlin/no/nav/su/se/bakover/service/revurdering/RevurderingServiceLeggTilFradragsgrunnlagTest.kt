package no.nav.su.se.bakover.service.revurdering

import arrow.core.getOrHandle
import arrow.core.nonEmptyListOf
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import io.kotest.assertions.arrow.either.shouldBeLeft
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.revurdering.RevurderingRepo
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.revurdering.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.Vurderingstatus
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.grunnlag.GrunnlagService
import no.nav.su.se.bakover.service.grunnlag.LeggTilFradragsgrunnlagRequest
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.revurderingId
import no.nav.su.se.bakover.test.tilAttesteringRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.util.UUID

class RevurderingServiceLeggTilFradragsgrunnlagTest {
    @Test
    fun `lagreFradrag happy case`() {
        val revurdering = opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak()

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn revurdering
        }

        val grunnlagServiceMock = mock<GrunnlagService>()

        val revurderingService = RevurderingTestUtils.createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            grunnlagService = grunnlagServiceMock,
        )

        val request = LeggTilFradragsgrunnlagRequest(
            behandlingId = revurdering.id,
            fradragsrunnlag = listOf(
                Grunnlag.Fradragsgrunnlag.tryCreate(
                    fradrag = FradragFactory.ny(
                        type = Fradragstype.Arbeidsinntekt,
                        månedsbeløp = 5000.0,
                        periode = revurdering.periode,
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                    opprettet = fixedTidspunkt,
                ).orNull()!!,
            ),
        )

        revurderingService.leggTilFradragsgrunnlag(request).getOrHandle { fail { "uventet respons" } }

        verify(revurderingRepoMock).hent(argThat { it shouldBe revurdering.id })
        verify(grunnlagServiceMock).lagreFradragsgrunnlag(
            argThat { it shouldBe revurdering.id },
            argThat { it shouldBe request.fradragsrunnlag },
        )
        verify(revurderingRepoMock).lagre(
            argThat {
                it shouldBe revurdering.copy(
                    informasjonSomRevurderes = InformasjonSomRevurderes.create(
                        mapOf(Revurderingsteg.Inntekt to Vurderingstatus.Vurdert),
                    ),
                    grunnlagsdata = Grunnlagsdata.tryCreate(
                        bosituasjon = revurdering.grunnlagsdata.bosituasjon,
                        fradragsgrunnlag = nonEmptyListOf(
                            fradragsgrunnlagArbeidsinntekt(periode = revurdering.periode, arbeidsinntekt = 5000.0).copy(
                                id = it.grunnlagsdata.fradragsgrunnlag.first().id,
                            ),
                        ),
                    ),
                )
            },
        )

        verifyNoMoreInteractions(revurderingRepoMock, grunnlagServiceMock)
    }

    @Test
    fun `lagreFradrag finner ikke revurdering`() {
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn null
        }

        val grunnlagServiceMock = mock<GrunnlagService>()

        val revurderingService = RevurderingTestUtils.createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            grunnlagService = grunnlagServiceMock,
        )

        val request = LeggTilFradragsgrunnlagRequest(
            behandlingId = revurderingId,
            fradragsrunnlag = listOf(
                Grunnlag.Fradragsgrunnlag.tryCreate(
                    fradrag = FradragFactory.ny(
                        type = Fradragstype.Arbeidsinntekt,
                        månedsbeløp = 0.0,
                        periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.desember(2021)),
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                    opprettet = fixedTidspunkt,
                ).orNull()!!,
            ),
        )

        revurderingService.leggTilFradragsgrunnlag(
            request,
        ).shouldBeLeft(KunneIkkeLeggeTilFradragsgrunnlag.FantIkkeBehandling)

        inOrder(
            revurderingRepoMock,
            grunnlagServiceMock,
        ) {
            verify(revurderingRepoMock).hent(argThat { it shouldBe revurderingId })
        }

        verifyNoMoreInteractions(revurderingRepoMock, grunnlagServiceMock)
    }

    @Test
    fun `lagreFradrag har en status som gjør at man ikke kan legge til fradrag`() {

        val tidligereRevurdering = tilAttesteringRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak()

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn tidligereRevurdering
        }

        val grunnlagServiceMock = mock<GrunnlagService>()

        val revurderingService = RevurderingTestUtils.createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            grunnlagService = grunnlagServiceMock,
        )

        val request = LeggTilFradragsgrunnlagRequest(
            behandlingId = tidligereRevurdering.id,
            fradragsrunnlag = listOf(
                Grunnlag.Fradragsgrunnlag.tryCreate(
                    fradrag = FradragFactory.ny(
                        type = Fradragstype.Arbeidsinntekt,
                        månedsbeløp = 0.0,
                        periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.desember(2021)),
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                    opprettet = fixedTidspunkt,
                ).orNull()!!,
            ),
        )

        revurderingService.leggTilFradragsgrunnlag(
            request,
        ).shouldBeLeft(
            KunneIkkeLeggeTilFradragsgrunnlag.UgyldigTilstand(
                fra = tidligereRevurdering::class,
                til = OpprettetRevurdering::class,
            ),
        )

        verify(revurderingRepoMock).hent(argThat { it shouldBe tidligereRevurdering.id })

        verifyNoMoreInteractions(revurderingRepoMock, grunnlagServiceMock)
    }

    @Test
    fun `validerer fradragsgrunnlag`() {
        val revurderingsperiode = Periode.create(1.mai(2021), 31.desember(2021))
        val opprettetRevurdering = opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak(
            revurderingsperiode = revurderingsperiode,
            grunnlagsdata = Grunnlagsdata.tryCreate(
                bosituasjon = listOf(
                    Grunnlag.Bosituasjon.Fullstendig.Enslig(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        periode = revurderingsperiode,
                        begrunnelse = null,
                    ),
                ),
            ),
        )

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn opprettetRevurdering
        }

        val revurderingService = RevurderingTestUtils.createRevurderingService(
            revurderingRepo = revurderingRepoMock,
        )

        val request = LeggTilFradragsgrunnlagRequest(
            behandlingId = revurderingId,
            fradragsrunnlag = listOf(
                Grunnlag.Fradragsgrunnlag.tryCreate(
                    fradrag = FradragFactory.ny(
                        type = Fradragstype.Arbeidsinntekt,
                        månedsbeløp = 0.0,
                        periode = opprettetRevurdering.periode,
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                    opprettet = fixedTidspunkt,
                ).orNull()!!,
                Grunnlag.Fradragsgrunnlag.tryCreate(
                    fradrag = FradragFactory.ny(
                        type = Fradragstype.Kontantstøtte,
                        månedsbeløp = 0.0,
                        periode = Periode.create(
                            fraOgMed = opprettetRevurdering.periode.fraOgMed.minusMonths(3),
                            tilOgMed = opprettetRevurdering.periode.tilOgMed,
                        ),
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                    opprettet = fixedTidspunkt,
                ).orNull()!!,
            ),
        )

        shouldThrow<IllegalArgumentException> {
            revurderingService.leggTilFradragsgrunnlag(
                request,
            )
        }
    }
}
