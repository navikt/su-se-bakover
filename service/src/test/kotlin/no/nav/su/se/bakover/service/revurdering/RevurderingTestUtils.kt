package no.nav.su.se.bakover.service.revurdering

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.avkorting.AvkortingsvarselRepo
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.person.IdentClient
import no.nav.su.se.bakover.domain.revurdering.RevurderingRepo
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.vilkår.UføreVilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeUføre
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.kontrollsamtale.KontrollsamtaleService
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.service.tilbakekreving.TilbakekrevingService
import no.nav.su.se.bakover.service.toggles.ToggleService
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.vedtak.VedtakService
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.formuegrenserFactoryTestPåDato
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import no.nav.su.se.bakover.test.simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak
import org.mockito.kotlin.mock
import java.time.Clock
import java.time.LocalDate

internal object RevurderingTestUtils {
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
    internal val stønadsperiodeNesteMånedOgTreMånederFram =
        Stønadsperiode.create(periode = periodeNesteMånedOgTreMånederFram)

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

    internal fun createRevurderingService(
        vedtakService: VedtakService = mock(),
        utbetalingService: UtbetalingService = mock(),
        revurderingRepo: RevurderingRepo = mock(),
        oppgaveService: OppgaveService = mock(),
        personService: PersonService = mock(),
        identClient: IdentClient = mock(),
        brevService: BrevService = mock(),
        clock: Clock = fixedClock,
        vedtakRepo: VedtakRepo = mock(),
        sakService: SakService = mock(),
        kontrollsamtaleService: KontrollsamtaleService = mock(),
        sessionFactory: SessionFactory = TestSessionFactory(),
        avkortingsvarselRepo: AvkortingsvarselRepo = mock(),
        toggleService: ToggleService = mock(),
        tilbakekrevingService: TilbakekrevingService = mock(),
        satsFactory: SatsFactory = satsFactoryTestPåDato(),
    ) =
        RevurderingServiceImpl(
            utbetalingService = utbetalingService,
            revurderingRepo = revurderingRepo,
            oppgaveService = oppgaveService,
            personService = personService,
            identClient = identClient,
            brevService = brevService,
            clock = clock,
            vedtakRepo = vedtakRepo,
            vedtakService = vedtakService,
            kontrollsamtaleService = kontrollsamtaleService,
            sessionFactory = sessionFactory,
            formuegrenserFactory = formuegrenserFactoryTestPåDato(),
            sakService = sakService,
            avkortingsvarselRepo = avkortingsvarselRepo,
            toggleService = toggleService,
            tilbakekrevingService = tilbakekrevingService,
            satsFactory = satsFactory,
        )

    /**
     * - Uten fradrag
     * - Enslig ektefelle
     * - Årsak: Melding fra bruker
     * - Simulering: no.nav.su.se.bakover.test.simulering()
     */
    internal val simulertRevurderingInnvilget: SimulertRevurdering.Innvilget =
        simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(
            stønadsperiode = stønadsperiodeNesteMånedOgTreMånederFram,
            revurderingsperiode = stønadsperiodeNesteMånedOgTreMånederFram.periode,
        ).second
}

internal fun Grunnlag.Uføregrunnlag.ekvivalentMed(other: Grunnlag.Uføregrunnlag) {
    opprettet shouldBe other.opprettet
    forventetInntekt shouldBe other.forventetInntekt
    periode shouldBe other.periode
    uføregrad shouldBe other.uføregrad
}

@Suppress("UNCHECKED_CAST")
internal fun Vilkår.ekvivalentMed(other: UføreVilkår.Vurdert) {
    this should beOfType<UføreVilkår.Vurdert>()
    (this as UføreVilkår.Vurdert).let {
        (vurderingsperioder).let {
            it shouldHaveSize other.vurderingsperioder.size
            it.forEachIndexed { index, vurderingsperiode ->
                vurderingsperiode.ekvivalentMed((other.vurderingsperioder)[index])
            }
        }
    }
}

internal fun VurderingsperiodeUføre.ekvivalentMed(other: VurderingsperiodeUføre) {
    opprettet shouldBe other.opprettet
    vurdering shouldBe other.vurdering
    grunnlag!!.ekvivalentMed(other.grunnlag!!)
    periode shouldBe other.periode
}
