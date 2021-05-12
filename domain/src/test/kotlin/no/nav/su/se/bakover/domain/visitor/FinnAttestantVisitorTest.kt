package no.nav.su.se.bakover.domain.visitor

import arrow.core.Nel
import arrow.core.right
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.FnrGenerator
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Månedsberegning
import no.nav.su.se.bakover.domain.beregning.MånedsberegningFactory
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.fixedTidspunkt
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.domain.vilkår.Vurderingsperiode
import org.junit.jupiter.api.Test
import java.util.UUID

internal class FinnAttestantVisitorTest {

    @Test
    fun `finner attestant for både søknadsbehandlinger og revurderinger`() {
        FinnAttestantVisitor().let {
            søknadsbehandling.accept(it)
            it.attestant shouldBe null
        }
        FinnAttestantVisitor().let {
            revurdering.accept(it)
            it.attestant shouldBe null
        }
        FinnAttestantVisitor().let {
            vilkårsvurdertInnvilgetSøknadsbehandling.accept(it)
            it.attestant shouldBe null
        }

        FinnAttestantVisitor().let {
            vilkårsvurdertAvslagSøknadsbehandling.accept(it)
            it.attestant shouldBe null
        }

        FinnAttestantVisitor().let {
            beregnetRevurdering.accept(it)
            it.attestant shouldBe null
        }

        FinnAttestantVisitor().let {
            beregnetAvslagSøknadbehandling.accept(it)
            it.attestant shouldBe null
        }

        FinnAttestantVisitor().let {
            beregnetInnvilgetøknadbehandling.accept(it)
            it.attestant shouldBe null
        }

        FinnAttestantVisitor().let {
            simulertSøknadsbehandling.accept(it)
            it.attestant shouldBe null
        }

        FinnAttestantVisitor().let {
            simulertRevurdering.accept(it)
            it.attestant shouldBe null
        }

        FinnAttestantVisitor().let {
            underkjentInnvilgetSøknadsbehandling.accept(it)
            it.attestant shouldBe attestant
        }

        FinnAttestantVisitor().let {
            tilAttesteringInnvilgetSøknadsbehandlng.accept(it)
            it.attestant shouldBe null
        }

        FinnAttestantVisitor().let {
            tilAttesteringAvslagSøknadsbehandlng.accept(it)
            it.attestant shouldBe null
        }

        FinnAttestantVisitor().let {
            tilAttesteringRevurdering.accept(it)
            it.attestant shouldBe null
        }

        FinnAttestantVisitor().let {
            underkjentInnvilgetSøknadsbehandling.accept(it)
            it.attestant shouldBe attestant
        }

        FinnAttestantVisitor().let {
            underkjentAvslagSøknadsbehandling.accept(it)
            it.attestant shouldBe attestant
        }

        FinnAttestantVisitor().let {
            iverksattInnvilgetSøknadsbehandling.accept(it)
            it.attestant shouldBe attestant
        }

        FinnAttestantVisitor().let {
            iverksattAvslagSøknadsbehandling.accept(it)
            it.attestant shouldBe attestant
        }

        FinnAttestantVisitor().let { visitor ->
            iverksattRevurdering.accept(visitor)
            visitor.attestant shouldBe attestant
        }
    }

    private val saksbehandler = NavIdentBruker.Saksbehandler("Z123")
    private val attestant = NavIdentBruker.Attestant("Z321")

    private val søknadsbehandling = Søknadsbehandling.Vilkårsvurdert.Uavklart(
        id = UUID.randomUUID(),
        opprettet = Tidspunkt.now(),
        sakId = UUID.randomUUID(),
        saksnummer = Saksnummer(2021),
        søknad = mock(),
        oppgaveId = OppgaveId(""),
        behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon(),
        fnr = FnrGenerator.random(),
        fritekstTilBrev = "",
        stønadsperiode = Stønadsperiode.create(Periode.create(1.januar(2021), 31.desember(2021))),
        vilkårsvurderinger = Vilkårsvurderinger.EMPTY,
    )

    private val behandlingsinformasjonMedAlleVilkårOppfylt = Behandlingsinformasjon.lagTomBehandlingsinformasjon()
        .withAlleVilkårOppfylt()

    private val vilkårsvurdertInnvilgetSøknadsbehandling = søknadsbehandling.tilVilkårsvurdert(
        behandlingsinformasjonMedAlleVilkårOppfylt,
    )

    private val vilkårsvurdertAvslagSøknadsbehandling = søknadsbehandling.tilVilkårsvurdert(
        behandlingsinformasjonMedAlleVilkårOppfylt,
    )

    private val månedsberegningAvslagMock = mock<Månedsberegning> { on { getSumYtelse() } doReturn 0 }
    private val månedsberegningInnvilgetMock = mock<Månedsberegning> { on { getSumYtelse() } doReturn 15000 }

    private val avslagBeregningMock = mock<Beregning> {
        on { getMånedsberegninger() } doReturn listOf(månedsberegningAvslagMock)
    }

    private val innvilgetBeregningMock = mock<Beregning> {
        on { getMånedsberegninger() } doReturn listOf(månedsberegningInnvilgetMock)
    }

    private val beregnetAvslagSøknadbehandling =
        vilkårsvurdertInnvilgetSøknadsbehandling.tilBeregnet(avslagBeregningMock)
    private val beregnetInnvilgetøknadbehandling =
        vilkårsvurdertInnvilgetSøknadsbehandling.tilBeregnet(innvilgetBeregningMock)
    private val simulertSøknadsbehandling = beregnetInnvilgetøknadbehandling.tilSimulert(mock())
    private val tilAttesteringInnvilgetSøknadsbehandlng = simulertSøknadsbehandling.tilAttestering(saksbehandler, "")
    private val tilAttesteringAvslagSøknadsbehandlng =
        (beregnetAvslagSøknadbehandling as Søknadsbehandling.Beregnet.Avslag)
            .tilAttestering(saksbehandler, "")
    private val underkjentInnvilgetSøknadsbehandling = tilAttesteringInnvilgetSøknadsbehandlng.tilUnderkjent(
        Attestering.Underkjent(
            attestant,
            Attestering.Underkjent.Grunn.ANDRE_FORHOLD,
            "",
        ),
    )
    private val underkjentAvslagSøknadsbehandling = tilAttesteringAvslagSøknadsbehandlng.tilUnderkjent(
        Attestering.Underkjent(
            attestant,
            Attestering.Underkjent.Grunn.ANDRE_FORHOLD,
            "",
        ),
    )
    private val iverksattInnvilgetSøknadsbehandling =
        tilAttesteringInnvilgetSøknadsbehandlng.tilIverksatt(Attestering.Iverksatt(attestant))
    private val iverksattAvslagSøknadsbehandling =
        tilAttesteringAvslagSøknadsbehandlng.tilIverksatt(Attestering.Iverksatt(attestant))

    private val beregningMock = mock<Beregning> {
        on { getMånedsberegninger() } doReturn listOf(
            MånedsberegningFactory.ny(
                periode = Periode.create(1.januar(2021), 31.januar(2021)),
                sats = Sats.HØY,
                fradrag = listOf(
                    FradragFactory.ny(
                        opprettet = fixedTidspunkt,
                        type = Fradragstype.Arbeidsinntekt,
                        månedsbeløp = 5000.0,
                        periode = Periode.create(1.januar(2021), 31.januar(2021)),
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                ),
            ),
        )
    }
    private val uføregrunnlag = Grunnlag.Uføregrunnlag(
        periode = Periode.create(1.januar(2021), 31.januar(2021)),
        uføregrad = Uføregrad.parse(20),
        forventetInntekt = 10,
    )
    private val revurdering = OpprettetRevurdering(
        id = UUID.randomUUID(),
        periode = Periode.create(1.januar(2021), 31.januar(2021)),
        opprettet = Tidspunkt.now(),
        tilRevurdering = mock {

            on { behandlingsinformasjon } doReturn behandlingsinformasjonMedAlleVilkårOppfylt
            on { beregning } doReturn beregningMock
        },
        saksbehandler = NavIdentBruker.Saksbehandler("Petter"),
        oppgaveId = OppgaveId("oppgaveid"),
        fritekstTilBrev = "",
        revurderingsårsak = Revurderingsårsak(
            Revurderingsårsak.Årsak.MELDING_FRA_BRUKER,
            Revurderingsårsak.Begrunnelse.create("Ny informasjon"),
        ),
        forhåndsvarsel = null,
        behandlingsinformasjon = behandlingsinformasjonMedAlleVilkårOppfylt,
        grunnlagsdata = Grunnlagsdata(
            uføregrunnlag = listOf(uføregrunnlag),
        ),
        vilkårsvurderinger = Vilkårsvurderinger(
            uføre = Vilkår.Vurdert.Uførhet.create(
                vurderingsperioder = Nel.of(
                    Vurderingsperiode.Uføre.create(
                        resultat = Resultat.Innvilget,
                        grunnlag = uføregrunnlag,
                        periode = Periode.create(1.januar(2021), 31.januar(2021)),
                        begrunnelse = null,
                    ),
                ),
            ),
        ),
    )

    private val beregnetRevurdering = when (val a = revurdering.beregn(emptyList()).orNull()!!) {
        is BeregnetRevurdering.Innvilget -> {
            a
        }
        else -> throw RuntimeException("Skal ikke skje")
    }
    private val simulertRevurdering = beregnetRevurdering.toSimulert(mock())
    private val tilAttesteringRevurdering =
        simulertRevurdering.tilAttestering(mock(), saksbehandler, "fritekst til brevet", mock())
    private val iverksattRevurdering = tilAttesteringRevurdering.tilIverksatt(attestant) { UUID30.randomUUID().right() }
        .orNull()!!
}
