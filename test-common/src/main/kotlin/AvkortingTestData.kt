package no.nav.su.se.bakover.test

import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.juni
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.domain.revurdering.steg.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.steg.Revurderingsteg
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Stønadsperiode
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetSøknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.VedtakOpphørMedUtbetaling
import no.nav.su.se.bakover.test.simulering.simuleringFeilutbetaling
import no.nav.su.se.bakover.test.vilkår.utenlandsoppholdAvslag
import økonomi.domain.simulering.Simulering
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

fun avkortingVedRevurderingUhåndtertUtestående(
    vararg perioder: Periode = listOf(juni(2021)).toTypedArray(),
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    sakId: UUID = UUID.randomUUID(),
    revurderingId: UUID = UUID.randomUUID(),
    simulering: Simulering = simuleringFeilutbetaling(
        perioder = perioder,
    ),
): AvkortingVedRevurdering.Uhåndtert.UteståendeAvkorting {
    return AvkortingVedRevurdering.Uhåndtert.UteståendeAvkorting(
        avkortingsvarselSkalAvkortes(
            perioder = perioder,
            id = id,
            sakId = sakId,
            revurderingId = revurderingId,
            opprettet = opprettet,
            simulering = simulering,
        ),
    )
}

fun avkortingsvarselSkalAvkortes(
    vararg perioder: Periode = listOf(juni(2021)).toTypedArray(),
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    sakId: UUID = UUID.randomUUID(),
    revurderingId: UUID = UUID.randomUUID(),
    simulering: Simulering = simuleringFeilutbetaling(
        perioder = perioder,
    ),
) = Avkortingsvarsel.Utenlandsopphold.SkalAvkortes(
    avkortingsvarselUtenlandsoppholdOpprettet(
        id = id,
        sakId = sakId,
        revurderingId = revurderingId,
        opprettet = opprettet,
        simulering = simulering,
    ),
)

fun avkortingsvarselAvkortet(
    vararg perioder: Periode = listOf(juni(2021)).toTypedArray(),
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    sakId: UUID = UUID.randomUUID(),
    revurderingId: UUID = UUID.randomUUID(),
    simulering: Simulering = simuleringFeilutbetaling(
        perioder = perioder,
    ),
    søknadsbehandlingId: UUID = UUID.randomUUID(),
) = Avkortingsvarsel.Utenlandsopphold.Avkortet(
    avkortingsvarselSkalAvkortes(
        id = id,
        sakId = sakId,
        revurderingId = revurderingId,
        opprettet = opprettet,
        simulering = simulering,
    ),
    behandlingId = søknadsbehandlingId,
)

fun avkortingsvarselUtenlandsoppholdOpprettet(
    vararg perioder: Periode = listOf(juni(2021)).toTypedArray(),
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    sakId: UUID = UUID.randomUUID(),
    revurderingId: UUID = UUID.randomUUID(),
    simulering: Simulering = simuleringFeilutbetaling(
        perioder = perioder,
    ),
) = Avkortingsvarsel.Utenlandsopphold.Opprettet(
    id = id,
    opprettet = opprettet,
    sakId = sakId,
    revurderingId = revurderingId,
    simulering = simulering,
)

fun sakMedUteståendeAvkorting(
    clock: Clock = TikkendeKlokke(),
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    revurderingsperiode: Periode = stønadsperiode.periode,
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    utbetalingerKjørtTilOgMed: (clock: Clock) -> LocalDate = { revurderingsperiode.tilOgMed },
): Triple<Sak, VedtakInnvilgetSøknadsbehandling, VedtakOpphørMedUtbetaling> {
    val sakOgVedtakSomKanRevurderes = vedtakSøknadsbehandlingIverksattInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        clock = clock,
    )
    val (sak, revurderingsvedtak) = vedtakRevurdering(
        stønadsperiode = stønadsperiode,
        revurderingsperiode = revurderingsperiode,
        sakOgVedtakSomKanRevurderes = sakOgVedtakSomKanRevurderes,
        clock = clock,
        informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Utenlandsopphold)),
        vilkårOverrides = listOf(
            utenlandsoppholdAvslag(
                periode = revurderingsperiode,
                opprettet = Tidspunkt.now(clock),
            ),
        ),
        utbetalingerKjørtTilOgMed = utbetalingerKjørtTilOgMed,
    )
    return Triple(
        sak,
        sakOgVedtakSomKanRevurderes.second,
        revurderingsvedtak as VedtakOpphørMedUtbetaling,
    )
}
