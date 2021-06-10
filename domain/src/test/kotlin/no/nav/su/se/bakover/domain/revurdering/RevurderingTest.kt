package no.nav.su.se.bakover.domain.revurdering

import arrow.core.getOrElse
import arrow.core.nonEmptyListOf
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import io.kotest.assertions.fail
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.FnrGenerator
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.avslag.Opphørsgrunn
import no.nav.su.se.bakover.domain.beregning.BeregningFactory
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragStrategy
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.fixedTidspunkt
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.domain.vilkår.Vurderingsperiode
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

internal class RevurderingTest {

    @Test
    fun `beregning gir opphør hvis vilkår ikke er oppfylt`() {
        val vurderingsperiode = Periode.create(1.januar(2021), 31.desember(2021))
        lagRevurdering(
            vilkårsvurderinger = Vilkårsvurderinger(
                uføre = Vilkår.Vurdert.Uførhet.create(
                    vurderingsperioder = nonEmptyListOf(
                        Vurderingsperiode.Uføre.create(
                            id = UUID.randomUUID(),
                            opprettet = Tidspunkt.now(),
                            resultat = Resultat.Avslag,
                            grunnlag = null,
                            periode = vurderingsperiode,
                            begrunnelse = null,
                        ),
                    ),
                ),
            ),
            bosituasjon = listOf(
                Grunnlag.Bosituasjon.Fullstendig.Enslig(
                    id = UUID.randomUUID(),
                    opprettet = fixedTidspunkt,
                    periode = vurderingsperiode,
                    begrunnelse = null,
                ),
            ),
        ).copy(informasjonSomRevurderes = InformasjonSomRevurderes.create(mapOf(Revurderingsteg.Inntekt to Vurderingstatus.IkkeVurdert)))
            .beregn().orNull()!!.let {
            it shouldBe beOfType<BeregnetRevurdering.Opphørt>()
            it.informasjonSomRevurderes[Revurderingsteg.Inntekt] shouldBe Vurderingstatus.Vurdert
        }
    }

    @Test
    fun `beregning gir ikke opphør hvis vilkår er oppfylt`() {
        val vurderingsperiode = Periode.create(1.januar(2021), 31.desember(2021))
        lagRevurdering(
            vilkårsvurderinger = Vilkårsvurderinger(
                uføre = Vilkår.Vurdert.Uførhet.create(
                    vurderingsperioder = nonEmptyListOf(
                        Vurderingsperiode.Uføre.create(
                            id = UUID.randomUUID(),
                            opprettet = Tidspunkt.now(),
                            resultat = Resultat.Innvilget,
                            grunnlag = null,
                            periode = vurderingsperiode,
                            begrunnelse = null,
                        ),
                    ),
                ),
                formue = Vilkår.Vurdert.Formue.create(
                    vurderingsperioder = nonEmptyListOf(
                        Vurderingsperiode.Formue.create(
                            id = UUID.randomUUID(),
                            opprettet = Tidspunkt.now(),
                            resultat = Resultat.Innvilget,
                            grunnlag = null,
                            periode = vurderingsperiode,
                            begrunnelse = null,
                        )
                    )
                ),
            ),
            bosituasjon = listOf(
                Grunnlag.Bosituasjon.Fullstendig.Enslig(
                    id = UUID.randomUUID(),
                    opprettet = fixedTidspunkt,
                    periode = vurderingsperiode,
                    begrunnelse = null,
                ),
            ),
        ).copy(informasjonSomRevurderes = InformasjonSomRevurderes.create(mapOf(Revurderingsteg.Inntekt to Vurderingstatus.IkkeVurdert)))
            .beregn().orNull()!!.let {
            it shouldNotBe beOfType<BeregnetRevurdering.Opphørt>()
            it.informasjonSomRevurderes[Revurderingsteg.Inntekt] shouldBe Vurderingstatus.Vurdert
        }
    }

    @Test
    fun `revurdering som er opphørt pga uførhet, blir utledet`() {
        val simulertRevurdering = SimulertRevurdering.Opphørt(
            id = mock(), periode = mock(), opprettet = mock(),
            tilRevurdering = tilRevurderingMock,
            saksbehandler = mock(),
            revurderingsårsak = mock(),
            fritekstTilBrev = "", beregning = tilRevurderingMock.beregning,
            simulering = mock(),
            forhåndsvarsel = null,
            behandlingsinformasjon = mock(),
            grunnlagsdata = mock(),
            vilkårsvurderinger = Vilkårsvurderinger(
                uføre = Vilkår.Vurdert.Uførhet.create(
                    vurderingsperioder = nonEmptyListOf(
                        Vurderingsperiode.Uføre.create(
                            id = UUID.randomUUID(),
                            opprettet = Tidspunkt.now(),
                            resultat = Resultat.Avslag,
                            grunnlag = null,
                            periode = Periode.create(1.januar(2021), 31.desember(2021)),
                            begrunnelse = null,
                        ),
                    ),
                ),
            ),
            informasjonSomRevurderes = mock(),
            oppgaveId = mock(),
        )

        simulertRevurdering.utledOpphørsgrunner() shouldBe listOf(Opphørsgrunn.UFØRHET)
    }

    @Test
    fun `revurdering som er opphørt pga for høy inntekt, blir utledet`() {
        val vurderingsperiode = Periode.create(1.januar(2021), 31.desember(2021))
        val revurdering = lagRevurdering(
            vilkårsvurderinger = Vilkårsvurderinger(
                uføre = Vilkår.Vurdert.Uførhet.create(
                    vurderingsperioder = nonEmptyListOf(
                        Vurderingsperiode.Uføre.create(
                            id = UUID.randomUUID(),
                            opprettet = Tidspunkt.now(),
                            resultat = Resultat.Innvilget,
                            grunnlag = null,
                            periode = vurderingsperiode,
                            begrunnelse = null,
                        ),
                    ),
                ),
                formue = Vilkår.Vurdert.Formue.create(
                    vurderingsperioder = nonEmptyListOf(
                        Vurderingsperiode.Formue.create(
                            id = UUID.randomUUID(),
                            opprettet = Tidspunkt.now(),
                            resultat = Resultat.Innvilget,
                            grunnlag = null,
                            periode = vurderingsperiode,
                            begrunnelse = null,
                        )
                    )
                )
            ),
            fradrag = listOf(
                Grunnlag.Fradragsgrunnlag(
                    fradrag = FradragFactory.ny(
                        type = Fradragstype.Arbeidsinntekt,
                        månedsbeløp = 2000000000.0,
                        periode = vurderingsperiode,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                ),
            ),
            bosituasjon = listOf(
                Grunnlag.Bosituasjon.Fullstendig.Enslig(
                    id = UUID.randomUUID(),
                    opprettet = fixedTidspunkt,
                    periode = vurderingsperiode,
                    begrunnelse = null,
                ),
            ),
        )

        val beregnet =
            revurdering.beregn().getOrElse { throw RuntimeException("Her skal det være en beregnet revurdering") }

        beregnet shouldBe beOfType<BeregnetRevurdering.Opphørt>()

        val simulert = beregnet.toSimulert(
            Simulering(
                gjelderId = FnrGenerator.random(),
                gjelderNavn = "",
                datoBeregnet = LocalDate.now(),
                nettoBeløp = 0,
                periodeList = listOf(),
            ),
        )

        simulert shouldBe beOfType<SimulertRevurdering.Opphørt>()
        (simulert as SimulertRevurdering.Opphørt).utledOpphørsgrunner() shouldBe listOf(Opphørsgrunn.FOR_HØY_INNTEKT)
    }

    @Test
    fun `revurdering som er opphørt pga under minstegrense, blir utledet`() {
        val vurderingsperiode = Periode.create(1.januar(2021), 31.desember(2021))
        val revurdering = lagRevurdering(
            vilkårsvurderinger = Vilkårsvurderinger(
                uføre = Vilkår.Vurdert.Uførhet.create(
                    vurderingsperioder = nonEmptyListOf(
                        Vurderingsperiode.Uføre.create(
                            id = UUID.randomUUID(),
                            opprettet = Tidspunkt.now(),
                            resultat = Resultat.Innvilget,
                            grunnlag = null,
                            periode = vurderingsperiode,
                            begrunnelse = null,
                        ),
                    ),
                ),
                formue = Vilkår.Vurdert.Formue.create(
                    vurderingsperioder = nonEmptyListOf(
                        Vurderingsperiode.Formue.create(
                            id = UUID.randomUUID(),
                            opprettet = Tidspunkt.now(),
                            resultat = Resultat.Innvilget,
                            grunnlag = null,
                            periode = vurderingsperiode,
                            begrunnelse = null,
                        )
                    )
                ),
            ),
            fradrag = listOf(
                Grunnlag.Fradragsgrunnlag(
                    fradrag = FradragFactory.ny(
                        type = Fradragstype.Arbeidsinntekt,
                        månedsbeløp = 20900.0,
                        periode = Periode.create(1.januar(2021), 30.april(2021)),
                        tilhører = FradragTilhører.BRUKER,
                    ),
                ),
                Grunnlag.Fradragsgrunnlag(
                    fradrag = FradragFactory.ny(
                        type = Fradragstype.Arbeidsinntekt,
                        månedsbeløp = 21900.0,
                        periode = Periode.create(1.mai(2021), 31.desember(2021)),
                        tilhører = FradragTilhører.BRUKER,
                    ),
                ),
            ),
            bosituasjon = listOf(
                Grunnlag.Bosituasjon.Fullstendig.Enslig(
                    id = UUID.randomUUID(),
                    opprettet = fixedTidspunkt,
                    periode = vurderingsperiode,
                    begrunnelse = null,
                ),
            ),
        )
        val beregnet =
            revurdering.beregn().getOrElse {
                fail("Her skal det være en beregnet revurdering")
            }

        beregnet shouldBe beOfType<BeregnetRevurdering.Opphørt>()

        val simulert = beregnet.toSimulert(
            Simulering(
                gjelderId = FnrGenerator.random(),
                gjelderNavn = "",
                datoBeregnet = LocalDate.now(),
                nettoBeløp = 0,
                periodeList = listOf(),
            ),
        )

        simulert shouldBe beOfType<SimulertRevurdering.Opphørt>()
        (simulert as SimulertRevurdering.Opphørt).utledOpphørsgrunner() shouldBe listOf(Opphørsgrunn.SU_UNDER_MINSTEGRENSE)
    }

    private fun lagRevurdering(
        tilRevurdering: Vedtak.EndringIYtelse = tilRevurderingMock,
        vilkårsvurderinger: Vilkårsvurderinger,
        fradrag: List<Grunnlag.Fradragsgrunnlag> = emptyList(),
        bosituasjon: List<Grunnlag.Bosituasjon>,
    ) = OpprettetRevurdering(
        id = UUID.randomUUID(),
        periode = Periode.create(1.januar(2021), 31.desember(2021)),
        opprettet = Tidspunkt.now(),
        tilRevurdering = tilRevurdering,
        saksbehandler = NavIdentBruker.Saksbehandler(navIdent = ""),
        oppgaveId = OppgaveId(value = ""),
        fritekstTilBrev = "",
        revurderingsårsak = Revurderingsårsak(
            årsak = Revurderingsårsak.Årsak.INFORMASJON_FRA_KONTROLLSAMTALE,
            begrunnelse = Revurderingsårsak.Begrunnelse.create(value = "b"),
        ),
        forhåndsvarsel = null,
        behandlingsinformasjon = mock(),
        vilkårsvurderinger = vilkårsvurderinger,
        grunnlagsdata = vilkårsvurderinger.grunnlagsdata.copy(
            bosituasjon = bosituasjon,
            fradragsgrunnlag = fradrag,
        ),
        informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
    )

    private val tilRevurderingMock: Vedtak.EndringIYtelse = mock {
        on { beregning } doReturn BeregningFactory.ny(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(),
            periode = Periode.create(1.januar(2021), 31.desember(2021)),
            sats = Sats.HØY,
            fradrag = listOf(
                FradragFactory.ny(
                    type = Fradragstype.ForventetInntekt,
                    månedsbeløp = 0.0,
                    periode = Periode.create(1.januar(2021), 31.desember(2021)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            fradragStrategy = FradragStrategy.Enslig,
            begrunnelse = null,
        )
    }
}
