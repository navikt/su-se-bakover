package no.nav.su.se.bakover.domain.revurdering.opprett

import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.revurdering.årsak.Revurderingsårsak
import person.domain.KunneIkkeHentePerson

sealed interface KunneIkkeOppretteRevurdering {

    data object MåVelgeInformasjonSomSkalRevurderes : KunneIkkeOppretteRevurdering

    data class VedtakInnenforValgtPeriodeKanIkkeRevurderes(
        val feil: Sak.GjeldendeVedtaksdataErUgyldigForRevurdering,
    ) : KunneIkkeOppretteRevurdering

    data class OpphørteVilkårMåRevurderes(val feil: Sak.OpphørtVilkårMåRevurderes) : KunneIkkeOppretteRevurdering

    data class UgyldigRevurderingsårsak(
        val feil: Revurderingsårsak.UgyldigRevurderingsårsak,
    ) : KunneIkkeOppretteRevurdering

    data class FantIkkeAktørId(val feil: KunneIkkeHentePerson) : KunneIkkeOppretteRevurdering

    data class KunneIkkeOppretteOppgave(val feil: no.nav.su.se.bakover.oppgave.domain.KunneIkkeOppretteOppgave) : KunneIkkeOppretteRevurdering

    data object SakFinnesIkke : KunneIkkeOppretteRevurdering

    data object MåhaOmgjøringsgrunn : KunneIkkeOppretteRevurdering
}
