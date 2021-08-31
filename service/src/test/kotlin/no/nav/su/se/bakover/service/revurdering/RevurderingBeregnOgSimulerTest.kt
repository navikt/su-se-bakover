package no.nav.su.se.bakover.service.revurdering

import arrow.core.getOrHandle
import arrow.core.nonEmptyListOf
import arrow.core.right
import io.kotest.assertions.fail
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.database.revurdering.RevurderingRepo
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.RevurderingsutfallSomIkkeStøttes
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.domain.vilkår.Vurderingsperiode
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.test.create
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.lagFradragsgrunnlag
import no.nav.su.se.bakover.test.lagGrunnlagsdata
import no.nav.su.se.bakover.test.revurderingId
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.UUID

class RevurderingBeregnOgSimulerTest {

    @Test
    fun `legger ved feilmeldinger for tilfeller som ikke støttes`() {
        val tilRevurdering = vedtakSøknadsbehandlingIverksattInnvilget().second
        val uføregrunnlag = Grunnlag.Uføregrunnlag(
            periode = tilRevurdering.periode,
            uføregrad = Uføregrad.parse(20),
            forventetInntekt = 10,
            opprettet = fixedTidspunkt,
        )

        val opprettetRevurdering = OpprettetRevurdering(
            id = revurderingId,
            periode = tilRevurdering.periode,
            opprettet = Tidspunkt.EPOCH,
            tilRevurdering = tilRevurdering,
            saksbehandler = saksbehandler,
            oppgaveId = OppgaveId("oppgaveid"),
            fritekstTilBrev = "",
            revurderingsårsak = RevurderingTestUtils.revurderingsårsak,
            forhåndsvarsel = null,
            grunnlagsdata = lagGrunnlagsdata(
                fradragsgrunnlag = listOf(
                    lagFradragsgrunnlag(
                        type = Fradragstype.Arbeidsinntekt,
                        månedsbeløp = 150500.0,
                        periode = tilRevurdering.periode,
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    )
                ),
                bosituasjon = listOf(
                    Grunnlag.Bosituasjon.Fullstendig.Enslig(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        periode = tilRevurdering.periode,
                        begrunnelse = null,
                    ),
                ),
            ),
            vilkårsvurderinger = Vilkårsvurderinger(
                uføre = Vilkår.Uførhet.Vurdert.create(
                    vurderingsperioder = nonEmptyListOf(
                        Vurderingsperiode.Uføre.create(
                            id = UUID.randomUUID(),
                            opprettet = fixedTidspunkt,
                            resultat = Resultat.Avslag,
                            grunnlag = uføregrunnlag,
                            periode = tilRevurdering.periode,
                            begrunnelse = "ok2k",
                        ),
                    ),
                ),
            ),
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
        )

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(revurderingId) } doReturn opprettetRevurdering
        }
        val simulertUtbetaling = mock<Utbetaling.SimulertUtbetaling> {
            on { simulering } doReturn mock()
        }
        val utbetalingMock = mock<Utbetaling> {
            on { utbetalingslinjer } doReturn nonEmptyListOf(
                Utbetalingslinje.Ny(
                    fraOgMed = tilRevurdering.periode.fraOgMed,
                    tilOgMed = tilRevurdering.periode.tilOgMed,
                    forrigeUtbetalingslinjeId = null,
                    beløp = 20000,
                ),
            )
        }
        val utbetalingServiceMock = mock<UtbetalingService> {
            on { simulerOpphør(any(), any(), any()) } doReturn simulertUtbetaling.right()
            on { hentUtbetalinger(any()) } doReturn listOf(utbetalingMock)
        }

        val response = RevurderingTestUtils.createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            utbetalingService = utbetalingServiceMock,
        ).beregnOgSimuler(
            revurderingId = revurderingId,
            saksbehandler = saksbehandler,
        ).getOrHandle { fail("Skulle returnert en instans av ${BeregnOgSimulerResponse::class}") }

        response.feilmeldinger shouldBe listOf(
            RevurderingsutfallSomIkkeStøttes.OpphørOgAndreEndringerIKombinasjon,
        )
    }
}
