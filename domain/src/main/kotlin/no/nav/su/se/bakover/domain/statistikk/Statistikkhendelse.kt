package no.nav.su.se.bakover.domain.statistikk

import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.klage.AvsluttetKlage
import no.nav.su.se.bakover.domain.klage.IverksattAvvistKlage
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.klage.OpprettetKlage
import no.nav.su.se.bakover.domain.klage.OversendtKlage
import no.nav.su.se.bakover.domain.revurdering.AvsluttetRevurdering
import no.nav.su.se.bakover.domain.revurdering.GjenopptaYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.StansAvYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.UnderkjentRevurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.LukketSøknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.domain.revurdering.Revurdering as DomeneRevurdering
import no.nav.su.se.bakover.domain.søknad.Søknad as DomeneSøknad
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling as DomeneSøknadsbehandling

sealed interface Statistikkhendelse {
    data class SakOpprettet(val sak: Sak) : Statistikkhendelse

    sealed interface Søknad : Statistikkhendelse {
        val søknad: DomeneSøknad
        val saksnummer: Saksnummer

        data class Mottatt(
            override val søknad: DomeneSøknad,
            override val saksnummer: Saksnummer,
        ) : Søknad

        data class Lukket(
            override val søknad: DomeneSøknad.Journalført.MedOppgave.Lukket,
            override val saksnummer: Saksnummer,
        ) : Søknad
    }

    sealed interface Søknadsbehandling : Statistikkhendelse {
        val søknadsbehandling: DomeneSøknadsbehandling

        data class Opprettet(
            override val søknadsbehandling: DomeneSøknadsbehandling.Vilkårsvurdert.Uavklart,
        ) : Søknadsbehandling

        data class Underkjent(
            override val søknadsbehandling: DomeneSøknadsbehandling.Underkjent,
        ) : Søknadsbehandling

        data class TilAttestering(
            override val søknadsbehandling: DomeneSøknadsbehandling.TilAttestering,
        ) : Søknadsbehandling

        data class Iverksatt(
            override val søknadsbehandling: DomeneSøknadsbehandling.Iverksatt,
        ) : Søknadsbehandling

        data class Lukket(
            override val søknadsbehandling: LukketSøknadsbehandling,
        ) : Søknadsbehandling
    }

    sealed interface Revurdering : Statistikkhendelse {
        val revurdering: DomeneRevurdering

        data class Opprettet(
            override val revurdering: OpprettetRevurdering,
        ) : Revurdering

        data class Attestering(
            override val revurdering: RevurderingTilAttestering,
        ) : Revurdering

        data class Iverksatt(
            override val revurdering: IverksattRevurdering,
        ) : Revurdering

        data class Underkjent(
            override val revurdering: UnderkjentRevurdering,
        ) : Revurdering

        data class Avsluttet(
            override val revurdering: AvsluttetRevurdering,
        ) : Revurdering

        data class Stans(val stans: StansAvYtelseRevurdering) : Statistikkhendelse
        data class Gjenoppta(val gjenoppta: GjenopptaYtelseRevurdering) : Statistikkhendelse
    }

    sealed interface Klagestatistikk : Statistikkhendelse {
        val klage: Klage

        data class Opprettet(override val klage: OpprettetKlage) : Klagestatistikk
        data class Oversendt(override val klage: OversendtKlage) : Klagestatistikk
        data class Avvist(override val klage: IverksattAvvistKlage) : Klagestatistikk
        data class Avsluttet(override val klage: AvsluttetKlage) : Klagestatistikk
    }

    data class Vedtak(val vedtak: VedtakSomKanRevurderes.EndringIYtelse) : Statistikkhendelse
}
