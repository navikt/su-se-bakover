package no.nav.su.se.bakover.service.revurdering

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslag
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.revurdering.RevurderingRepo
import no.nav.su.se.bakover.database.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vurderingsperiode
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.grunnlag.GrunnlagService
import no.nav.su.se.bakover.service.grunnlag.VilkårsvurderingService
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.vedtak.VedtakService
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak
import org.mockito.kotlin.mock
import java.time.Clock
import java.time.LocalDate

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
        microsoftGraphApiClient: MicrosoftGraphApiOppslag = mock(),
        brevService: BrevService = mock(),
        clock: Clock = fixedClock,
        vedtakRepo: VedtakRepo = mock(),
        vilkårsvurderingService: VilkårsvurderingService = mock(),
        grunnlagService: GrunnlagService = mock(),
        sakService: SakService = mock()
    ) =
        RevurderingServiceImpl(
            utbetalingService = utbetalingService,
            revurderingRepo = revurderingRepo,
            oppgaveService = oppgaveService,
            personService = personService,
            microsoftGraphApiClient = microsoftGraphApiClient,
            brevService = brevService,
            clock = clock,
            vedtakRepo = vedtakRepo,
            vilkårsvurderingService = vilkårsvurderingService,
            grunnlagService = grunnlagService,
            vedtakService = vedtakService,
            sakService = sakService,
        )

    /**
     * - Uten fradrag
     * - Enslig ektefelle
     * - Årsak: Melding fra bruker
     * - Simulering: no.nav.su.se.bakover.test.simulering()
     */
    internal val simulertRevurderingInnvilget: SimulertRevurdering.Innvilget by lazy {
        simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(
            stønadsperiode = stønadsperiodeNesteMånedOgTreMånederFram,
            revurderingsperiode = stønadsperiodeNesteMånedOgTreMånederFram.periode,
        ).second
    }
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
