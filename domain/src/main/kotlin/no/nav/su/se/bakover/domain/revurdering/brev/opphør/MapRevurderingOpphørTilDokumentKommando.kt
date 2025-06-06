package no.nav.su.se.bakover.domain.revurdering.brev.opphør

import beregning.domain.Beregning
import no.nav.su.se.bakover.domain.brev.Satsoversikt
import no.nav.su.se.bakover.domain.brev.command.IverksettRevurderingDokumentCommand
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.vilkår.hentUføregrunnlag
import satser.domain.SatsFactory
import vilkår.bosituasjon.domain.grunnlag.Bosituasjon.Companion.harEPS
import vilkår.vurderinger.domain.harForventetInntektStørreEnn0
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
        sakstype = revurdering.sakstype,
        harEktefelle = revurdering.grunnlagsdata.bosituasjon.harEPS(),
        beregning = beregning,
        fritekst = revurdering.brevvalgRevurdering.skalSendeBrev().getOrNull()?.fritekst,
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
