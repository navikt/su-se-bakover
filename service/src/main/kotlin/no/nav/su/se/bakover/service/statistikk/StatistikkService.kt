package no.nav.su.se.bakover.service.statistikk

import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.revurdering.AvsluttetRevurdering
import no.nav.su.se.bakover.domain.revurdering.GjenopptaYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.revurdering.StansAvYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.UnderkjentRevurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.LukketSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.Vedtak

interface StatistikkService {
    fun publiser(statistikk: Statistikk)
}

interface EventObserver {
    fun handle(event: Event)
}

sealed class Event {
    internal sealed class Statistikk : Event() {
        data class SakOpprettet(val sak: Sak) : Statistikk()

        sealed class SøknadStatistikk : Statistikk() {
            abstract val søknad: Søknad
            abstract val saksnummer: Saksnummer

            data class SøknadMottatt(override val søknad: Søknad, override val saksnummer: Saksnummer) : SøknadStatistikk()
            data class SøknadLukket(override val søknad: Søknad.Journalført.MedOppgave.Lukket, override val saksnummer: Saksnummer) : SøknadStatistikk()
        }

        sealed class SøknadsbehandlingStatistikk : Statistikk() {
            abstract val søknadsbehandling: Søknadsbehandling

            data class SøknadsbehandlingOpprettet(override val søknadsbehandling: Søknadsbehandling.Vilkårsvurdert.Uavklart) :
                SøknadsbehandlingStatistikk()

            data class SøknadsbehandlingUnderkjent(override val søknadsbehandling: Søknadsbehandling.Underkjent) :
                SøknadsbehandlingStatistikk()

            data class SøknadsbehandlingTilAttestering(override val søknadsbehandling: Søknadsbehandling.TilAttestering) :
                SøknadsbehandlingStatistikk()

            data class SøknadsbehandlingIverksatt(override val søknadsbehandling: Søknadsbehandling.Iverksatt) :
                SøknadsbehandlingStatistikk()

            data class SøknadsbehandlingLukket(override val søknadsbehandling: LukketSøknadsbehandling) :
                SøknadsbehandlingStatistikk()
        }

        sealed class RevurderingStatistikk : Statistikk() {
            abstract val revurdering: Revurdering

            data class RevurderingOpprettet(override val revurdering: OpprettetRevurdering) :
                RevurderingStatistikk()

            data class RevurderingTilAttestering(override val revurdering: no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering) :
                RevurderingStatistikk()

            data class RevurderingIverksatt(override val revurdering: IverksattRevurdering) :
                RevurderingStatistikk()

            data class RevurderingUnderkjent(override val revurdering: UnderkjentRevurdering) :
                RevurderingStatistikk()

            data class RevurderingAvsluttet(override val revurdering: AvsluttetRevurdering) :
                RevurderingStatistikk()

            data class Stans(val stans: StansAvYtelseRevurdering) : Statistikk()
            data class Gjenoppta(val gjenoppta: GjenopptaYtelseRevurdering) : Statistikk()
        }

        data class Vedtaksstatistikk(val vedtak: Vedtak.EndringIYtelse) : Statistikk()
    }
}
