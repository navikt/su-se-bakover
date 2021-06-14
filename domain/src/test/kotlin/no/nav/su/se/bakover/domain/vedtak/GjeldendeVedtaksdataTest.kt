package no.nav.su.se.bakover.domain.vedtak

import arrow.core.nonEmptyListOf
import com.nhaarman.mockitokotlin2.mock
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.FnrGenerator
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.beregning.BeregningFactory
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragStrategy
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.formueVilkår
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.Forhåndsvarsel
import no.nav.su.se.bakover.domain.revurdering.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.revurdering.Vurderingstatus
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.domain.vilkår.Vurderingsperiode
import org.junit.jupiter.api.Test
import java.util.UUID

internal class GjeldendeVedtaksdataTest {
    @Test
    fun `finner gjeldende vedtak for gitt dato`() {
        val førstegangsvedtak = førstegangsvedtak(Periode.create(1.januar(2021), 31.desember(2021)))
        val revurdering = revurdering(Periode.create(1.mai(2021), 31.desember(2021)), førstegangsvedtak)
        val data = GjeldendeVedtaksdata(
            periode = Periode.create(1.januar(2021), 31.desember(2021)),
            vedtakListe = nonEmptyListOf(
                førstegangsvedtak,
                revurdering,
            ),
        )
        data.gjeldendeVedtakPåDato(1.januar(2021)) shouldBe førstegangsvedtak
        data.gjeldendeVedtakPåDato(30.april(2021)) shouldBe førstegangsvedtak
        data.gjeldendeVedtakPåDato(1.mai(2021)) shouldBe revurdering
        data.gjeldendeVedtakPåDato(1.desember(2021)) shouldBe revurdering

        data.tidslinjeForVedtakErSammenhengende() shouldBe true
    }

    @Test
    fun `tidslinje inneholder hull mellom to vedtak`() {
        val førstegangsvedtak = førstegangsvedtak(Periode.create(1.januar(2021), 31.mars(2021)))
        val revurdering = revurdering(Periode.create(1.mai(2021), 31.desember(2021)), førstegangsvedtak)
        val data = GjeldendeVedtaksdata(
            periode = Periode.create(1.januar(2021), 31.desember(2021)),
            vedtakListe = nonEmptyListOf(
                førstegangsvedtak,
                revurdering,
            ),
        )

        data.gjeldendeVedtakPåDato(1.mars(2021)) shouldBe førstegangsvedtak
        data.gjeldendeVedtakPåDato(1.april(2021)) shouldBe null
        data.gjeldendeVedtakPåDato(1.desember(2021)) shouldBe revurdering
        data.tidslinjeForVedtakErSammenhengende() shouldBe false
    }

    @Test
    fun `tidslinje inneholder bare et vedtak`() {
        val førstegangsvedtak = førstegangsvedtak(Periode.create(1.januar(2021), 31.desember(2021)))
        val data = GjeldendeVedtaksdata(
            periode = Periode.create(1.januar(2021), 31.desember(2021)),
            vedtakListe = nonEmptyListOf(
                førstegangsvedtak,
            ),
        )
        data.tidslinjeForVedtakErSammenhengende() shouldBe true
    }

    private fun førstegangsvedtak(periode: Periode) = Vedtak.fromSøknadsbehandling(
        søknadsbehandling = Søknadsbehandling.Iverksatt.Innvilget(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(),
            sakId = UUID.randomUUID(),
            saksnummer = Saksnummer(9999),
            søknad = Søknad.Journalført.MedOppgave(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(),
                sakId = UUID.randomUUID(),
                søknadInnhold = SøknadInnholdTestdataBuilder.build(),
                journalpostId = JournalpostId(value = ""),
                oppgaveId = OppgaveId(value = ""),

            ),
            oppgaveId = OppgaveId(value = ""),
            behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon(),
            fnr = FnrGenerator.random(),
            beregning = BeregningFactory.ny(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(),
                periode = periode,
                sats = Sats.HØY,
                fradrag = listOf(
                    FradragFactory.ny(
                        type = Fradragstype.ForventetInntekt,
                        månedsbeløp = 0.0,
                        periode = periode,
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                    FradragFactory.ny(
                        type = Fradragstype.Arbeidsinntekt,
                        månedsbeløp = 5000.0,
                        periode = periode,
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                ),
                fradragStrategy = FradragStrategy.Enslig,
                begrunnelse = "just becausre",
            ),
            simulering = mock(),
            saksbehandler = NavIdentBruker.Saksbehandler("saks"),
            attestering = Attestering.Iverksatt(NavIdentBruker.Attestant("attestant")),
            fritekstTilBrev = "",
            stønadsperiode = Stønadsperiode.create(
                periode = periode,
                begrunnelse = "",
            ),
            grunnlagsdata = Grunnlagsdata(
                fradragsgrunnlag = listOf(),
            ),
            vilkårsvurderinger = Vilkårsvurderinger(
                uføre = Vilkår.Uførhet.Vurdert.create(
                    vurderingsperioder = nonEmptyListOf(
                        Vurderingsperiode.Uføre.create(
                            id = UUID.randomUUID(),
                            opprettet = Tidspunkt.now(),
                            resultat = Resultat.Innvilget,
                            grunnlag = null,
                            periode = periode,
                            begrunnelse = null,
                        ),
                    ),
                ),
                formue = formueVilkår(periode),
            ),
        ),
        utbetalingId = UUID30.randomUUID(),
    )

    private fun revurdering(periode: Periode, førstegangsvedtak: Vedtak.EndringIYtelse) = Vedtak.from(
        IverksattRevurdering.Innvilget(
            id = UUID.randomUUID(),
            periode = periode,
            opprettet = Tidspunkt.now(),
            tilRevurdering = førstegangsvedtak,
            saksbehandler = NavIdentBruker.Saksbehandler(navIdent = ""),
            oppgaveId = OppgaveId(value = ""),
            fritekstTilBrev = "",
            revurderingsårsak = Revurderingsårsak(
                årsak = Revurderingsårsak.Årsak.MELDING_FRA_BRUKER,
                begrunnelse = Revurderingsårsak.Begrunnelse.create(value = "beg"),
            ),
            beregning = BeregningFactory.ny(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(),
                periode = periode,
                sats = Sats.HØY,
                fradrag = listOf(
                    FradragFactory.ny(
                        type = Fradragstype.ForventetInntekt,
                        månedsbeløp = 1000.0,
                        periode = periode,
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                    FradragFactory.ny(
                        type = Fradragstype.Arbeidsinntekt,
                        månedsbeløp = 4000.0,
                        periode = periode,
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                ),
                fradragStrategy = FradragStrategy.Enslig,
                begrunnelse = "just becausre",
            ),
            attestering = Attestering.Iverksatt(attestant = NavIdentBruker.Attestant(navIdent = "")),
            forhåndsvarsel = Forhåndsvarsel.IngenForhåndsvarsel,
            behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon(),
            simulering = Simulering(gjelderId = FnrGenerator.random(), gjelderNavn = "", datoBeregnet = 1.mai(2021), nettoBeløp = 0, periodeList = listOf()),
            grunnlagsdata = Grunnlagsdata(
                fradragsgrunnlag = listOf(),
                bosituasjon = listOf(),
            ),
            vilkårsvurderinger = Vilkårsvurderinger(
                uføre = Vilkår.Uførhet.Vurdert.create(
                    vurderingsperioder = nonEmptyListOf(
                        Vurderingsperiode.Uføre.create(
                            id = UUID.randomUUID(),
                            opprettet = Tidspunkt.now(),
                            resultat = Resultat.Innvilget,
                            grunnlag = null,
                            periode = periode,
                            begrunnelse = null,
                        ),
                    ),
                ),
                formue = formueVilkår(periode),
            ),
            informasjonSomRevurderes = InformasjonSomRevurderes.create(
                revurderingsteg = mapOf(
                    Revurderingsteg.Uførhet to Vurderingstatus.Vurdert,
                    Revurderingsteg.Inntekt to Vurderingstatus.Vurdert,
                ),
            ),
        ),
        UUID30.randomUUID(),
    )
}
