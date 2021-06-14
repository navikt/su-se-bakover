package no.nav.su.se.bakover.service.revurdering

import arrow.core.getOrHandle
import arrow.core.nonEmptyListOf
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import io.kotest.assertions.fail
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.database.revurdering.RevurderingRepo
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.RevurderingsutfallSomIkkeStøttes
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.domain.vilkår.Vurderingsperiode
import no.nav.su.se.bakover.service.fixedTidspunkt
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import org.junit.jupiter.api.Test
import java.util.UUID

class RevurderingBeregnOgSimulerTest {

    @Test
    fun `legger ved feilmeldinger for tilfeller som ikke støttes`() {
        val uføregrunnlag = Grunnlag.Uføregrunnlag(
            periode = RevurderingTestUtils.periode,
            uføregrad = Uføregrad.parse(20),
            forventetInntekt = 10,
        )
        val opprettetRevurdering = OpprettetRevurdering(
            id = RevurderingTestUtils.revurderingId,
            periode = RevurderingTestUtils.periode,
            opprettet = Tidspunkt.EPOCH,
            tilRevurdering = RevurderingTestUtils.søknadsbehandlingVedtak,
            saksbehandler = RevurderingTestUtils.saksbehandler,
            oppgaveId = OppgaveId("oppgaveid"),
            fritekstTilBrev = "",
            revurderingsårsak = RevurderingTestUtils.revurderingsårsak,
            forhåndsvarsel = null,
            behandlingsinformasjon = RevurderingTestUtils.søknadsbehandlingVedtak.behandlingsinformasjon,
            grunnlagsdata = Grunnlagsdata(
                fradragsgrunnlag = listOf(
                    Grunnlag.Fradragsgrunnlag(
                        fradrag = FradragFactory.ny(
                            type = Fradragstype.Arbeidsinntekt,
                            månedsbeløp = 150500.0,
                            periode = RevurderingTestUtils.søknadsbehandlingVedtak.periode,
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),
                ),
                bosituasjon = listOf(
                    Grunnlag.Bosituasjon.Fullstendig.Enslig(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        periode = RevurderingTestUtils.periode,
                        begrunnelse = null,
                    ),
                )
            ),
            vilkårsvurderinger = Vilkårsvurderinger(
                uføre = Vilkår.Uførhet.Vurdert.create(
                    vurderingsperioder = nonEmptyListOf(
                        Vurderingsperiode.Uføre.create(
                            id = UUID.randomUUID(),
                            opprettet = fixedTidspunkt,
                            resultat = Resultat.Avslag,
                            grunnlag = uføregrunnlag,
                            periode = RevurderingTestUtils.periode,
                            begrunnelse = "ok2k",
                        ),
                    ),
                ),
            ),
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
        )

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(RevurderingTestUtils.revurderingId) } doReturn opprettetRevurdering
        }
        val simulertUtbetaling = mock<Utbetaling.SimulertUtbetaling> {
            on { simulering } doReturn mock()
        }
        val utbetalingServiceMock = mock<UtbetalingService> {
            on { simulerOpphør(any(), any(), any()) } doReturn simulertUtbetaling.right()
        }

        val response = RevurderingTestUtils.createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            utbetalingService = utbetalingServiceMock,
        ).beregnOgSimuler(
            revurderingId = RevurderingTestUtils.revurderingId,
            saksbehandler = RevurderingTestUtils.saksbehandler,
        ).getOrHandle { fail("Skulle returnert en instans av ${BeregnOgSimulerResponse::class}") }

        response.feilmeldinger shouldBe listOf(
            RevurderingsutfallSomIkkeStøttes.OpphørOgAndreEndringerIKombinasjon,
        )
    }
}
