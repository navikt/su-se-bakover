package no.nav.su.se.bakover.domain.revurdering.brev.opphør

import arrow.core.getOrElse
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.brev.Satsoversikt
import no.nav.su.se.bakover.domain.brev.command.IverksettRevurderingDokumentCommand
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Bosituasjon.Companion.harEPS
import no.nav.su.se.bakover.domain.grunnlag.harForventetInntektStørreEnn0
import no.nav.su.se.bakover.domain.revurdering.AvsluttetRevurdering
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.revurdering.UnderkjentRevurdering
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.vilkår.hentUføregrunnlag
import org.slf4j.LoggerFactory
import java.lang.IllegalArgumentException
import java.time.Clock

private val log = LoggerFactory.getLogger("MapRevurderingOpphørTilDokumentKommando.kt")

/**
 * Ment for internt bruk innenfor revurdering/brev pakken.
 * @throws IllegalArgumentException dersom revurderingen ikke er et opphør eller dersom man ikke skulle sende brev.
 */
internal fun lagRevurderingOpphørtDokumentKommando(
    revurdering: Revurdering,
    beregning: Beregning,
    satsFactory: SatsFactory,
    clock: Clock,
): IverksettRevurderingDokumentCommand.Opphør {
    require(revurdering.erOpphørt) {
        "Kan ikke lage opphørsbrev for en revurdering som ikke er opphørt. RevurderingId: ${revurdering.id}"
    }
    val avkortingsbeløp = getAvkortingsbeløp(revurdering)

    return IverksettRevurderingDokumentCommand.Opphør(
        fødselsnummer = revurdering.fnr,
        saksnummer = revurdering.saksnummer,
        harEktefelle = revurdering.grunnlagsdata.bosituasjon.harEPS(),
        beregning = beregning,
        fritekst = if (revurdering.skalSendeVedtaksbrev()) {
            revurdering.brevvalgRevurdering.skalSendeBrev()
                .getOrElse { throw IllegalStateException("context mismatch: Revurderingen skal sende brev, men brevvalg skal ikke sendes. ${revurdering.id}") }.fritekst
                ?: ""
        } else {
            throw java.lang.IllegalStateException("Bedt om å generere brev for en revurdering som ikke skal sende brev. Saksnummer: ${revurdering.saksnummer} med revurderingid: ${revurdering.id}")
        },
        saksbehandler = revurdering.saksbehandler,
        attestant = revurdering.prøvHentSisteAttestant(),
        forventetInntektStørreEnn0 = revurdering.vilkårsvurderinger.hentUføregrunnlag().harForventetInntektStørreEnn0(),
        opphørsgrunner = revurdering.utledOpphørsgrunner(clock),
        opphørsperiode = revurdering.periode,
        avkortingsBeløp = avkortingsbeløp,
        satsoversikt = Satsoversikt.fra(revurdering, satsFactory),
        // TODO("håndter_formue egentlig knyttet til formuegrenser")
        halvtGrunnbeløp = satsFactory.grunnbeløp(revurdering.periode.fraOgMed)
            .halvtGrunnbeløpPerÅrAvrundet(),
    )
}

private fun getAvkortingsbeløp(
    revurdering: Revurdering,
) = when (revurdering) {
    // TODO jah: Føles ikke som dette ansvaret hører hjemme her. Kan vi refaktorere? Flytte inn i AvkortingVedRevurdering?
    is BeregnetRevurdering.Opphørt -> {
        when (revurdering.avkorting) {
            is AvkortingVedRevurdering.DelvisHåndtert.AnnullerUtestående,
            is AvkortingVedRevurdering.DelvisHåndtert.IngenUtestående,
            is AvkortingVedRevurdering.DelvisHåndtert.KanIkkeHåndtere,
            -> null
        }
    }

    is IverksattRevurdering.Opphørt -> {
        when (revurdering.avkorting) {
            is AvkortingVedRevurdering.Iverksatt.AnnullerUtestående,
            is AvkortingVedRevurdering.Iverksatt.IngenNyEllerUtestående,
            is AvkortingVedRevurdering.Iverksatt.KanIkkeHåndteres,
            -> null

            is AvkortingVedRevurdering.Iverksatt.OpprettNyttAvkortingsvarsel -> {
                revurdering.avkorting.avkortingsvarsel.simulering.bruttoTidligereUtbetalt
            }

            is AvkortingVedRevurdering.Iverksatt.OpprettNyttAvkortingsvarselOgAnnullerUtestående -> {
                revurdering.avkorting.avkortingsvarsel.simulering.bruttoTidligereUtbetalt
            }
        }
    }

    is RevurderingTilAttestering.Opphørt -> {
        when (revurdering.avkorting) {
            is AvkortingVedRevurdering.Håndtert.AnnullerUtestående,
            is AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående,
            is AvkortingVedRevurdering.Håndtert.KanIkkeHåndteres,
            -> null

            is AvkortingVedRevurdering.Håndtert.OpprettNyttAvkortingsvarsel -> {
                revurdering.avkorting.avkortingsvarsel.simulering.bruttoTidligereUtbetalt
            }

            is AvkortingVedRevurdering.Håndtert.OpprettNyttAvkortingsvarselOgAnnullerUtestående -> {
                revurdering.avkorting.avkortingsvarsel.simulering.bruttoTidligereUtbetalt
            }
        }
    }

    is SimulertRevurdering.Opphørt -> {
        when (revurdering.avkorting) {
            is AvkortingVedRevurdering.Håndtert.AnnullerUtestående,
            is AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående,
            is AvkortingVedRevurdering.Håndtert.KanIkkeHåndteres,
            -> null

            is AvkortingVedRevurdering.Håndtert.OpprettNyttAvkortingsvarsel -> {
                revurdering.avkorting.avkortingsvarsel.simulering.bruttoTidligereUtbetalt
            }

            is AvkortingVedRevurdering.Håndtert.OpprettNyttAvkortingsvarselOgAnnullerUtestående -> {
                revurdering.avkorting.avkortingsvarsel.simulering.bruttoTidligereUtbetalt
            }
        }
    }

    is UnderkjentRevurdering.Opphørt -> {
        when (revurdering.avkorting) {
            is AvkortingVedRevurdering.Håndtert.AnnullerUtestående,
            is AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående,
            is AvkortingVedRevurdering.Håndtert.KanIkkeHåndteres,
            -> null

            is AvkortingVedRevurdering.Håndtert.OpprettNyttAvkortingsvarsel -> {
                revurdering.avkorting.avkortingsvarsel.simulering.bruttoTidligereUtbetalt
            }

            is AvkortingVedRevurdering.Håndtert.OpprettNyttAvkortingsvarselOgAnnullerUtestående -> {
                revurdering.avkorting.avkortingsvarsel.simulering.bruttoTidligereUtbetalt
            }
        }
    }

    is AvsluttetRevurdering,
    is BeregnetRevurdering.Innvilget,
    is IverksattRevurdering.Innvilget,
    is OpprettetRevurdering,
    is RevurderingTilAttestering.Innvilget,
    is SimulertRevurdering.Innvilget,
    is UnderkjentRevurdering.Innvilget,
    -> null.also {
        log.error("Vi skal ikke komme hit. Kan ikke opphøre revurdering av type ${revurdering::class.simpleName}")
    }
}
