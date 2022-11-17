package no.nav.su.se.bakover.test

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.periode.desember
import no.nav.su.se.bakover.common.periode.november
import no.nav.su.se.bakover.common.periode.oktober
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.revurdering.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.Revurderingsteg
import no.nav.su.se.bakover.domain.sak.Saksnummer
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.test.vilkår.utenlandsoppholdAvslag
import java.time.Clock
import java.util.UUID

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
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
): Triple<Sak, VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling, VedtakSomKanRevurderes.EndringIYtelse.OpphørtRevurdering> {
    val sakOgVedtakSomKanRevurderes = vedtakSøknadsbehandlingIverksattInnvilget(
        saksnummer = saksnummer,
        stønadsperiode = stønadsperiode,
        clock = clock,
    )
    val (sak, revurderingsvedtak) = vedtakRevurdering(
        stønadsperiode =stønadsperiode,
        revurderingsperiode = stønadsperiode.periode,
        sakOgVedtakSomKanRevurderes = sakOgVedtakSomKanRevurderes,
        clock = clock,
        informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Utenlandsopphold)),
        vilkårOverrides = listOf(
            utenlandsoppholdAvslag(
                periode = stønadsperiode.periode,
                opprettet = Tidspunkt.now(clock),
            ),
        ),
        utbetalingerKjørtTilOgMed = stønadsperiode.periode.tilOgMed,
    )
    return Triple(
        sak,
        sakOgVedtakSomKanRevurderes.second,
        revurderingsvedtak as VedtakSomKanRevurderes.EndringIYtelse.OpphørtRevurdering,
    )
}
