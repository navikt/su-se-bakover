package no.nav.su.se.bakover.database

import no.nav.su.se.bakover.database.beregning.PersistertBeregning
import no.nav.su.se.bakover.database.beregning.toSnapshot
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
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
            copy(beregning = beregning.persistertVariant(), grunnlagsdata = grunnlagsdata.persistertVariant())
        }
        is Søknadsbehandling.Beregnet.Innvilget -> {
            copy(beregning = beregning.persistertVariant(), grunnlagsdata = grunnlagsdata.persistertVariant())
        }
        is Søknadsbehandling.Iverksatt.Avslag.MedBeregning -> {
            copy(beregning = beregning.persistertVariant(), grunnlagsdata = grunnlagsdata.persistertVariant())
        }
        is Søknadsbehandling.Iverksatt.Avslag.UtenBeregning -> {
            this.copy(grunnlagsdata = grunnlagsdata.persistertVariant())
        }
        is Søknadsbehandling.Iverksatt.Innvilget -> {
            copy(beregning = beregning.persistertVariant(), grunnlagsdata = grunnlagsdata.persistertVariant())
        }
        is LukketSøknadsbehandling -> {
            this
        }
        is Søknadsbehandling.Simulert -> {
            copy(beregning = beregning.persistertVariant(), grunnlagsdata = grunnlagsdata.persistertVariant())
        }
        is Søknadsbehandling.TilAttestering.Avslag.MedBeregning -> {
            copy(beregning = beregning.persistertVariant(), grunnlagsdata = grunnlagsdata.persistertVariant())
        }
        is Søknadsbehandling.TilAttestering.Avslag.UtenBeregning -> {
            this.copy(grunnlagsdata = grunnlagsdata.persistertVariant())
        }
        is Søknadsbehandling.TilAttestering.Innvilget -> {
            copy(beregning = beregning.persistertVariant(), grunnlagsdata = grunnlagsdata.persistertVariant())
        }
        is Søknadsbehandling.Underkjent.Avslag.MedBeregning -> {
            copy(beregning = beregning.persistertVariant(), grunnlagsdata = grunnlagsdata.persistertVariant())
        }
        is Søknadsbehandling.Underkjent.Avslag.UtenBeregning -> {
            this.copy(grunnlagsdata = grunnlagsdata.persistertVariant())
        }
        is Søknadsbehandling.Underkjent.Innvilget -> {
            copy(beregning = beregning.persistertVariant(), grunnlagsdata = grunnlagsdata.persistertVariant())
        }
        is Søknadsbehandling.Vilkårsvurdert.Avslag -> {
            this.copy(grunnlagsdata = grunnlagsdata.persistertVariant())
        }
        is Søknadsbehandling.Vilkårsvurdert.Innvilget -> {
            this.copy(grunnlagsdata = grunnlagsdata.persistertVariant())
        }
        is Søknadsbehandling.Vilkårsvurdert.Uavklart -> {
            this.copy(grunnlagsdata = grunnlagsdata.persistertVariant())
        }
        else -> null
    } as T
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
                grunnlagsdata = grunnlagsdata.persistertVariant(),
            )
        }
        is BeregnetRevurdering.Innvilget -> {
            copy(
                tilRevurdering = tilRevurdering.persistertVariant(),
                beregning = beregning.persistertVariant(),
                grunnlagsdata = grunnlagsdata.persistertVariant(),
            )
        }
        is BeregnetRevurdering.Opphørt -> {
            copy(
                tilRevurdering = tilRevurdering.persistertVariant(),
                beregning = beregning.persistertVariant(),
                grunnlagsdata = grunnlagsdata.persistertVariant(),
            )
        }
        is IverksattRevurdering.IngenEndring -> {
            copy(
                tilRevurdering = tilRevurdering.persistertVariant(),
                beregning = beregning.persistertVariant(),
                grunnlagsdata = grunnlagsdata.persistertVariant(),
            )
        }
        is IverksattRevurdering.Innvilget -> {
            copy(
                tilRevurdering = tilRevurdering.persistertVariant(),
                beregning = beregning.persistertVariant(),
                grunnlagsdata = grunnlagsdata.persistertVariant(),
            )
        }
        is IverksattRevurdering.Opphørt -> {
            copy(
                tilRevurdering = tilRevurdering.persistertVariant(),
                beregning = beregning.persistertVariant(),
                grunnlagsdata = grunnlagsdata.persistertVariant(),
            )
        }
        is OpprettetRevurdering -> {
            copy(
                tilRevurdering = tilRevurdering.persistertVariant(),
                grunnlagsdata = grunnlagsdata.persistertVariant(),
            )
        }
        is RevurderingTilAttestering.IngenEndring -> {
            copy(
                tilRevurdering = tilRevurdering.persistertVariant(),
                beregning = beregning.persistertVariant(),
                grunnlagsdata = grunnlagsdata.persistertVariant(),
            )
        }
        is RevurderingTilAttestering.Innvilget -> {
            copy(
                tilRevurdering = tilRevurdering.persistertVariant(),
                beregning = beregning.persistertVariant(),
                grunnlagsdata = grunnlagsdata.persistertVariant(),
            )
        }
        is RevurderingTilAttestering.Opphørt -> {
            copy(
                tilRevurdering = tilRevurdering.persistertVariant(),
                beregning = beregning.persistertVariant(),
                grunnlagsdata = grunnlagsdata.persistertVariant(),
            )
        }
        is SimulertRevurdering.Innvilget -> {
            copy(
                tilRevurdering = tilRevurdering.persistertVariant(),
                beregning = beregning.persistertVariant(),
                grunnlagsdata = grunnlagsdata.persistertVariant(),
            )
        }
        is SimulertRevurdering.Opphørt -> {
            copy(
                tilRevurdering = tilRevurdering.persistertVariant(),
                beregning = beregning.persistertVariant(),
                grunnlagsdata = grunnlagsdata.persistertVariant(),
            )
        }
        is UnderkjentRevurdering.IngenEndring -> {
            copy(
                tilRevurdering = tilRevurdering.persistertVariant(),
                beregning = beregning.persistertVariant(),
                grunnlagsdata = grunnlagsdata.persistertVariant(),
            )
        }
        is UnderkjentRevurdering.Innvilget -> {
            copy(
                tilRevurdering = tilRevurdering.persistertVariant(),
                beregning = beregning.persistertVariant(),
                grunnlagsdata = grunnlagsdata.persistertVariant(),
            )
        }
        is UnderkjentRevurdering.Opphørt -> {
            copy(
                tilRevurdering = tilRevurdering.persistertVariant(),
                beregning = beregning.persistertVariant(),
                grunnlagsdata = grunnlagsdata.persistertVariant(),
            )
        }
        is StansAvYtelseRevurdering.SimulertStansAvYtelse -> {
            copy(
                tilRevurdering = tilRevurdering.persistertVariant(),
                grunnlagsdata = grunnlagsdata.persistertVariant(),
            )
        }
        is StansAvYtelseRevurdering.IverksattStansAvYtelse -> {
            copy(
                tilRevurdering = tilRevurdering.persistertVariant(),
                grunnlagsdata = grunnlagsdata.persistertVariant(),
            )
        }
        is GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse -> {
            copy(
                tilRevurdering = tilRevurdering.persistertVariant(),
                grunnlagsdata = grunnlagsdata.persistertVariant(),
            )
        }
        is GjenopptaYtelseRevurdering.IverksattGjenopptakAvYtelse -> {
            copy(
                tilRevurdering = tilRevurdering.persistertVariant(),
                grunnlagsdata = grunnlagsdata.persistertVariant(),
            )
        }
        else -> null
    } as T
}

internal inline fun <reified T : Regulering> T.persistertVariant(): T {
    return when (this) {
        is Regulering.OpprettetRegulering -> this.persistertVariant()
        is Regulering.IverksattRegulering -> this.persistertVariant()
        else -> null
    } as T
}

internal fun Regulering.OpprettetRegulering.persistertVariant(): Regulering.OpprettetRegulering {
    return this.copy(
        grunnlagsdataOgVilkårsvurderinger = this.grunnlagsdataOgVilkårsvurderinger.persistertVariant(),
        beregning = this.beregning?.persistertVariant()
    )
}

internal fun Regulering.IverksattRegulering.persistertVariant(): Regulering.IverksattRegulering {
    return this.copy(
        opprettetRegulering = opprettetRegulering.persistertVariant(),
        beregning = beregning.persistertVariant(),
    )
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

internal fun Grunnlagsdata.persistertVariant(): Grunnlagsdata {
    return this.copy(
        fradragsgrunnlag = this.fradragsgrunnlag.map {
            it.copy(
                fradrag = it.fradrag.toSnapshot(),
            )
        },
    )
}

internal fun GrunnlagsdataOgVilkårsvurderinger.persistertVariant(): GrunnlagsdataOgVilkårsvurderinger {
    return when (this) {
        is GrunnlagsdataOgVilkårsvurderinger.Revurdering -> this.persistertVariant()
        is GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling -> this.persistertVariant()
    }
}

internal fun GrunnlagsdataOgVilkårsvurderinger.Revurdering.persistertVariant(): GrunnlagsdataOgVilkårsvurderinger.Revurdering {
    return this.copy(
        grunnlagsdata = this.grunnlagsdata.persistertVariant(),
    )
}

internal fun GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling.persistertVariant(): GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling {
    return this.copy(
        grunnlagsdata = this.grunnlagsdata.persistertVariant(),
    )
}
