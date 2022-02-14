package no.nav.su.se.bakover.database

import no.nav.su.se.bakover.database.beregning.PersistertBeregning
import no.nav.su.se.bakover.database.beregning.toSnapshot
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.regulering.Regulering
import no.nav.su.se.bakover.domain.revurdering.AbstraktRevurdering
import no.nav.su.se.bakover.domain.revurdering.AvsluttetRevurdering
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.GjenopptaYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.revurdering.StansAvYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.UnderkjentRevurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.LukketSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.Avslagsvedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes

/**
 * Hjelpefunksjoner for å transformere fra domeneobjekter til persistert-variant av samme objekt.
 */

internal inline fun <reified T : Søknadsbehandling> T.persistertVariant(): T {
    return when (this) {
        is Søknadsbehandling.Beregnet.Avslag -> {
            copy(beregning = beregning.persistertVariant())
        }
        is Søknadsbehandling.Beregnet.Innvilget -> {
            copy(beregning = beregning.persistertVariant())
        }
        is Søknadsbehandling.Iverksatt.Avslag.MedBeregning -> {
            copy(beregning = beregning.persistertVariant())
        }
        is Søknadsbehandling.Iverksatt.Avslag.UtenBeregning -> {
            this
        }
        is Søknadsbehandling.Iverksatt.Innvilget -> {
            copy(beregning = beregning.persistertVariant())
        }
        is LukketSøknadsbehandling -> {
            this
        }
        is Søknadsbehandling.Simulert -> {
            copy(beregning = beregning.persistertVariant())
        }
        is Søknadsbehandling.TilAttestering.Avslag.MedBeregning -> {
            copy(beregning = beregning.persistertVariant())
        }
        is Søknadsbehandling.TilAttestering.Avslag.UtenBeregning -> {
            this
        }
        is Søknadsbehandling.TilAttestering.Innvilget -> {
            copy(beregning = beregning.persistertVariant())
        }
        is Søknadsbehandling.Underkjent.Avslag.MedBeregning -> {
            copy(beregning = beregning.persistertVariant())
        }
        is Søknadsbehandling.Underkjent.Avslag.UtenBeregning -> {
            this
        }
        is Søknadsbehandling.Underkjent.Innvilget -> {
            copy(beregning = beregning.persistertVariant())
        }
        is Søknadsbehandling.Vilkårsvurdert.Avslag -> {
            this
        }
        is Søknadsbehandling.Vilkårsvurdert.Innvilget -> {
            this
        }
        is Søknadsbehandling.Vilkårsvurdert.Uavklart -> {
            this
        }
        else -> null
    } as T
}

internal inline fun <reified T : Regulering> T.persistertVariant(): T {
    return this
}

internal inline fun <reified T : AbstraktRevurdering> T.persistertVariant(): T {
    return when (this) {
        is AvsluttetRevurdering -> {
            this
        }
        is BeregnetRevurdering.IngenEndring -> {
            copy(
                tilRevurdering = tilRevurdering.persistertVariant(),
                beregning = beregning.persistertVariant(),
            )
        }
        is BeregnetRevurdering.Innvilget -> {
            copy(
                tilRevurdering = tilRevurdering.persistertVariant(),
                beregning = beregning.persistertVariant(),
            )
        }
        is BeregnetRevurdering.Opphørt -> {
            copy(
                tilRevurdering = tilRevurdering.persistertVariant(),
                beregning = beregning.persistertVariant(),
            )
        }
        is IverksattRevurdering.IngenEndring -> {
            copy(
                tilRevurdering = tilRevurdering.persistertVariant(),
                beregning = beregning.persistertVariant(),
            )
        }
        is IverksattRevurdering.Innvilget -> {
            copy(
                tilRevurdering = tilRevurdering.persistertVariant(),
                beregning = beregning.persistertVariant(),
            )
        }
        is IverksattRevurdering.Opphørt -> {
            copy(
                tilRevurdering = tilRevurdering.persistertVariant(),
                beregning = beregning.persistertVariant(),
            )
        }
        is OpprettetRevurdering -> {
            copy(
                tilRevurdering = tilRevurdering.persistertVariant(),
            )
        }
        is RevurderingTilAttestering.IngenEndring -> {
            copy(
                tilRevurdering = tilRevurdering.persistertVariant(),
                beregning = beregning.persistertVariant(),
            )
        }
        is RevurderingTilAttestering.Innvilget -> {
            copy(
                tilRevurdering = tilRevurdering.persistertVariant(),
                beregning = beregning.persistertVariant(),
            )
        }
        is RevurderingTilAttestering.Opphørt -> {
            copy(
                tilRevurdering = tilRevurdering.persistertVariant(),
                beregning = beregning.persistertVariant(),
            )
        }
        is SimulertRevurdering.Innvilget -> {
            copy(
                tilRevurdering = tilRevurdering.persistertVariant(),
                beregning = beregning.persistertVariant(),
            )
        }
        is SimulertRevurdering.Opphørt -> {
            copy(
                tilRevurdering = tilRevurdering.persistertVariant(),
                beregning = beregning.persistertVariant(),
            )
        }
        is UnderkjentRevurdering.IngenEndring -> {
            copy(
                tilRevurdering = tilRevurdering.persistertVariant(),
                beregning = beregning.persistertVariant(),
            )
        }
        is UnderkjentRevurdering.Innvilget -> {
            copy(
                tilRevurdering = tilRevurdering.persistertVariant(),
                beregning = beregning.persistertVariant(),
            )
        }
        is UnderkjentRevurdering.Opphørt -> {
            copy(
                tilRevurdering = tilRevurdering.persistertVariant(),
                beregning = beregning.persistertVariant(),
            )
        }
        is StansAvYtelseRevurdering.SimulertStansAvYtelse -> {
            copy(
                tilRevurdering = tilRevurdering.persistertVariant(),
            )
        }
        is StansAvYtelseRevurdering.IverksattStansAvYtelse -> {
            copy(
                tilRevurdering = tilRevurdering.persistertVariant(),
            )
        }
        is GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse -> {
            copy(
                tilRevurdering = tilRevurdering.persistertVariant(),
            )
        }
        is GjenopptaYtelseRevurdering.IverksattGjenopptakAvYtelse -> {
            copy(
                tilRevurdering = tilRevurdering.persistertVariant(),
            )
        }
        else -> null
    } as T
}

internal fun VedtakSomKanRevurderes.persistertVariant(): VedtakSomKanRevurderes {
    return when (this) {
        is VedtakSomKanRevurderes.EndringIYtelse.GjenopptakAvYtelse -> {
            copy(
                behandling = behandling.persistertVariant(),
            )
        }
        is VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRevurdering -> {
            copy(
                behandling = behandling.persistertVariant(),
                beregning = beregning.persistertVariant(),
            )
        }
        is VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling -> {
            copy(
                behandling = behandling.persistertVariant(),
                beregning = beregning.persistertVariant(),
            )
        }
        is VedtakSomKanRevurderes.EndringIYtelse.OpphørtRevurdering -> {
            copy(
                behandling = behandling.persistertVariant(),
                beregning = beregning.persistertVariant(),
            )
        }
        is VedtakSomKanRevurderes.EndringIYtelse.StansAvYtelse -> {
            copy(
                behandling = behandling.persistertVariant(),
            )
        }
        is VedtakSomKanRevurderes.IngenEndringIYtelse -> {
            copy(
                behandling = behandling.persistertVariant(),
                beregning = beregning.persistertVariant(),
            )
        }
        is VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRegulering -> {
            copy(
                behandling = behandling.persistertVariant(),
                beregning = beregning.persistertVariant(),
            )
        }
    }
}

internal fun Avslagsvedtak.persistertVariant(): Avslagsvedtak {
    return when (this) {
        is Avslagsvedtak.AvslagBeregning -> {
            copy(
                behandling = behandling.persistertVariant(),
                beregning = beregning.persistertVariant(),
            )
        }
        is Avslagsvedtak.AvslagVilkår -> {
            copy(
                behandling = behandling.persistertVariant(),
            )
        }
    }
}

internal fun Beregning.persistertVariant(): PersistertBeregning {
    return this.toSnapshot()
}
