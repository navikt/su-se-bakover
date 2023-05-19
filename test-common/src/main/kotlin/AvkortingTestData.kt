package no.nav.su.se.bakover.test

import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.desember
import no.nav.su.se.bakover.common.tid.periode.juni
import no.nav.su.se.bakover.common.tid.periode.november
import no.nav.su.se.bakover.common.tid.periode.oktober
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.revurdering.steg.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.steg.Revurderingsteg
import no.nav.su.se.bakover.domain.sak.Saksnummer
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Stønadsperiode
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetSøknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.VedtakOpphørMedUtbetaling
import no.nav.su.se.bakover.test.simulering.simuleringFeilutbetaling
import no.nav.su.se.bakover.test.vilkår.utenlandsoppholdAvslag
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

fun uhåndtertUteståendeAvkorting(
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
        Avkortingsvarsel.Utenlandsopphold.SkalAvkortes(
            Avkortingsvarsel.Utenlandsopphold.Opprettet(
                id = id,
                sakId = sakId,
                revurderingId = revurderingId,
                opprettet = opprettet,
                simulering = simulering,
            ),
        ),
    )
}

fun avkortingsvarselUtenlandsopphold(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    sakId: UUID = UUID.randomUUID(),
    revurderingId: UUID = UUID.randomUUID(),
    simulering: Simulering = simuleringFeilutbetaling(
        oktober(2020),
        november(2020),
        desember(2020),
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
    utbetalingerKjørtTilOgMed: LocalDate = revurderingsperiode.tilOgMed,
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
