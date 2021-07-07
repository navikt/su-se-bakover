package no.nav.su.se.bakover.test

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

/** Fixed UTC clock at 2021-01-01T01:02:03.456789000Z */
val fixedClock: Clock =
    Clock.fixed(1.januar(2021).atTime(1, 2, 3, 456789000).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

/**
 * Fixed Tidspunkt at 2021-01-01T01:02:03.456789000Z
 * Correlates with `fixedClock`
 */
val fixedTidspunkt: Tidspunkt = Tidspunkt.now(fixedClock)

/**
 * Fixed LocalDate at 2021-01-01
 * Correlates with `fixedClock`
 */
val fixedLocalDate: LocalDate = LocalDate.of(2021, 1, 1)

val saksbehandler = NavIdentBruker.Saksbehandler("Sak S. Behandler")

val saksnummer = Saksnummer(nummer = 12345676)

val fnr = Fnr.generer()

val aktørId = AktørId("aktørId")

val stønadsperiode2021 = Stønadsperiode.create(periode2021, "stønadsperiode2021")

val attestant = NavIdentBruker.Attestant("attestant")

val attesteringIverksatt = Attestering.Iverksatt(
    attestant = attestant,
    tidspunkt = fixedTidspunkt
)

val attesteringUnderkjent = Attestering.Underkjent(
    attestant = attestant,
    grunn = Attestering.Underkjent.Grunn.DOKUMENTASJON_MANGLER,
    kommentar = "attesteringUnderkjent",
    tidspunkt = fixedTidspunkt
)
