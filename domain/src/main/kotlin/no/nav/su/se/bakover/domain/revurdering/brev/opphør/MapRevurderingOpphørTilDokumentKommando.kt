package no.nav.su.se.bakover.domain.revurdering.brev.opphør

import arrow.core.getOrElse
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.brev.Satsoversikt
import no.nav.su.se.bakover.domain.brev.command.IverksettRevurderingDokumentCommand
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Bosituasjon.Companion.harEPS
import no.nav.su.se.bakover.domain.grunnlag.harForventetInntektStørreEnn0
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.vilkår.hentUføregrunnlag
import sats.domain.SatsFactory
import java.time.Clock

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
        satsoversikt = Satsoversikt.fra(revurdering, satsFactory),
        // TODO("håndter_formue egentlig knyttet til formuegrenser")
        halvtGrunnbeløp = satsFactory.grunnbeløp(revurdering.periode.fraOgMed)
            .halvtGrunnbeløpPerÅrAvrundet(),
    )
}
