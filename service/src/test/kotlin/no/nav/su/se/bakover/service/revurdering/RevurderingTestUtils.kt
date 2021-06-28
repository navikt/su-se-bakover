package no.nav.su.se.bakover.service.revurdering

import arrow.core.nonEmptyListOf
import com.nhaarman.mockitokotlin2.mock
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiClient
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.revurdering.RevurderingRepo
import no.nav.su.se.bakover.database.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.Forhåndsvarsel
import no.nav.su.se.bakover.domain.revurdering.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.domain.vilkår.Vurderingsperiode
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.formueVilkår
import no.nav.su.se.bakover.service.grunnlag.GrunnlagService
import no.nav.su.se.bakover.service.grunnlag.VilkårsvurderingService
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.vedtak.FerdigstillVedtakService
import no.nav.su.se.bakover.service.vedtak.VedtakService
import no.nav.su.se.bakover.test.create
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.oversendtUtbetalingUtenKvittering
import no.nav.su.se.bakover.test.revurderingId
import no.nav.su.se.bakover.test.sakId
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.saksnummer
import no.nav.su.se.bakover.test.simuleringNy
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

object RevurderingTestUtils {
    private val dagensDato = fixedLocalDate.let {
        LocalDate.of(
            it.year,
            it.month,
            1,
        )
    }
    private val nesteMåned =
        LocalDate.of(
            dagensDato.year,
            dagensDato.month.plus(1),
            1,
        )

    /**
     * I.e. Periode som inneholder februar, mars, april og mai.
     */
    internal val periodeNesteMånedOgTreMånederFram = Periode.create(
        fraOgMed = nesteMåned,
        tilOgMed = nesteMåned.let {
            val treMånederFramITid = it.plusMonths(3)
            LocalDate.of(
                treMånederFramITid.year,
                treMånederFramITid.month,
                treMånederFramITid.lengthOfMonth(),
            )
        },
    )
    internal val stønadsperiodeNesteMånedOgTreMånederFram = Stønadsperiode.create(
        periode = periodeNesteMånedOgTreMånederFram,
        begrunnelse = "begrunnelsen for perioden",
    )
    internal val attesteringUnderkjent = Attestering.Underkjent(
        NavIdentBruker.Attestant("Attes T. Ant"),
        Attestering.Underkjent.Grunn.BEREGNINGEN_ER_FEIL,
        "kommentar",
    )

    internal val beregning = no.nav.su.se.bakover.test.beregning(
        periode = periodeNesteMånedOgTreMånederFram,
    )

    internal val revurderingsårsak = Revurderingsårsak(
        Revurderingsårsak.Årsak.MELDING_FRA_BRUKER,
        Revurderingsårsak.Begrunnelse.create("Ny informasjon"),
    )

    internal val revurderingsårsakRegulerGrunnbeløp = Revurderingsårsak(
        Revurderingsårsak.Årsak.REGULER_GRUNNBELØP,
        Revurderingsårsak.Begrunnelse.create("Nytt Grunnbeløp"),
    )

    internal val uføregrunnlag = Grunnlag.Uføregrunnlag(
        periode = periodeNesteMånedOgTreMånederFram,
        uføregrad = Uføregrad.parse(20),
        forventetInntekt = 10,
        opprettet = fixedTidspunkt,
    )

    internal val vurderingsperiodeUføre = Vurderingsperiode.Uføre.create(
        id = UUID.randomUUID(),
        opprettet = fixedTidspunkt,
        resultat = Resultat.Avslag,
        grunnlag = uføregrunnlag,
        periode = periodeNesteMånedOgTreMånederFram,
        begrunnelse = "ok2k",
    )

    internal val vilkårsvurderinger = Vilkårsvurderinger(
        uføre = Vilkår.Uførhet.Vurdert.create(
            vurderingsperioder = nonEmptyListOf(
                vurderingsperiodeUføre,
            ),
        ),
        formue = formueVilkår(periodeNesteMånedOgTreMånederFram),
    )

    internal val søknadsbehandlingsvedtakIverksattInnvilget = vedtakSøknadsbehandlingIverksattInnvilget(
        stønadsperiode = stønadsperiodeNesteMånedOgTreMånederFram,
    )

    internal val sak = Sak(
        id = sakId,
        saksnummer = saksnummer,
        opprettet = fixedTidspunkt,
        fnr = fnr,
        søknader = listOf(),
        utbetalinger = listOf(oversendtUtbetalingUtenKvittering(periode = periodeNesteMånedOgTreMånederFram)),
        vedtakListe = listOf(søknadsbehandlingsvedtakIverksattInnvilget),
    )

    internal fun createRevurderingService(
        vedtakService: VedtakService = mock(),
        utbetalingService: UtbetalingService = mock(),
        revurderingRepo: RevurderingRepo = mock(),
        oppgaveService: OppgaveService = mock(),
        personService: PersonService = mock(),
        microsoftGraphApiClient: MicrosoftGraphApiClient = mock(),
        brevService: BrevService = mock(),
        clock: Clock = fixedClock,
        vedtakRepo: VedtakRepo = mock(),
        ferdigstillVedtakService: FerdigstillVedtakService = mock(),
        vilkårsvurderingService: VilkårsvurderingService = mock(),
        grunnlagService: GrunnlagService = mock(),
    ) =
        RevurderingServiceImpl(
            vedtakService = vedtakService,
            utbetalingService = utbetalingService,
            revurderingRepo = revurderingRepo,
            oppgaveService = oppgaveService,
            personService = personService,
            microsoftGraphApiClient = microsoftGraphApiClient,
            brevService = brevService,
            clock = clock,
            vedtakRepo = vedtakRepo,
            ferdigstillVedtakService = ferdigstillVedtakService,
            vilkårsvurderingService = vilkårsvurderingService,
            grunnlagService = grunnlagService,
        )

    internal val opprettetRevurdering = opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak(
        revurderingsperiode = periodeNesteMånedOgTreMånederFram,
        stønadsperiode = Stønadsperiode.create(periodeNesteMånedOgTreMånederFram),
    )

    /**
     * - Uten fradrag
     * - Enslig ektefelle
     * - Årsak: Melding fra bruker
     * - Simulering: no.nav.su.se.bakover.test.simulering()
     */
    internal val simulertRevurderingInnvilget = SimulertRevurdering.Innvilget(
        id = revurderingId,
        periode = periodeNesteMånedOgTreMånederFram,
        opprettet = fixedTidspunkt,
        tilRevurdering = søknadsbehandlingsvedtakIverksattInnvilget,
        saksbehandler = saksbehandler,
        oppgaveId = OppgaveId("Oppgaveid"),
        fritekstTilBrev = "",
        revurderingsårsak = revurderingsårsak,
        behandlingsinformasjon = søknadsbehandlingsvedtakIverksattInnvilget.behandlingsinformasjon,
        simulering = simuleringNy(),
        beregning = beregning,
        forhåndsvarsel = Forhåndsvarsel.IngenForhåndsvarsel,
        grunnlagsdata = Grunnlagsdata(
            bosituasjon = listOf(
                Grunnlag.Bosituasjon.Fullstendig.Enslig(
                    id = UUID.randomUUID(),
                    opprettet = fixedTidspunkt,
                    periode = periodeNesteMånedOgTreMånederFram,
                    begrunnelse = null,
                ),
            ),
            fradragsgrunnlag = emptyList(),
        ),
        vilkårsvurderinger = vilkårsvurderinger,
        informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
    )
}

internal fun Grunnlag.Uføregrunnlag.ekvivalentMed(other: Grunnlag.Uføregrunnlag) {
    opprettet shouldBe other.opprettet
    forventetInntekt shouldBe other.forventetInntekt
    periode shouldBe other.periode
    uføregrad shouldBe other.uføregrad
}

@Suppress("UNCHECKED_CAST")
internal fun Vilkår.ekvivalentMed(other: Vilkår.Uførhet.Vurdert) {
    this should beOfType<Vilkår.Uførhet.Vurdert>()
    (this as Vilkår.Uførhet.Vurdert).let {
        (vurderingsperioder).let {
            it shouldHaveSize other.vurderingsperioder.size
            it.forEachIndexed { index, vurderingsperiode ->
                vurderingsperiode.ekvivalentMed((other.vurderingsperioder)[index])
            }
        }
    }
}

internal fun Vurderingsperiode.Uføre.ekvivalentMed(other: Vurderingsperiode.Uføre) {
    opprettet shouldBe other.opprettet
    resultat shouldBe other.resultat
    grunnlag!!.ekvivalentMed(other.grunnlag!!)
    periode shouldBe other.periode
    begrunnelse shouldBe other.begrunnelse
}
