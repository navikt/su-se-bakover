package no.nav.su.se.bakover.domain.revurdering.opprett

import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.oppgave.OppgaveFeil
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.revurdering.avkorting.KanIkkeRevurderePgaAvkorting
import no.nav.su.se.bakover.domain.revurdering.årsak.Revurderingsårsak

sealed interface KunneIkkeOppretteRevurdering {

    object MåVelgeInformasjonSomSkalRevurderes : KunneIkkeOppretteRevurdering

    data class VedtakInnenforValgtPeriodeKanIkkeRevurderes(
        val feil: Sak.GjeldendeVedtaksdataErUgyldigForRevurdering,
    ) : KunneIkkeOppretteRevurdering

    data class OpphørteVilkårMåRevurderes(val feil: Sak.OpphørtVilkårMåRevurderes) : KunneIkkeOppretteRevurdering

    data class UgyldigRevurderingsårsak(
        val feil: Revurderingsårsak.UgyldigRevurderingsårsak,
    ) : KunneIkkeOppretteRevurdering

    data class FantIkkeAktørId(val feil: KunneIkkeHentePerson) : KunneIkkeOppretteRevurdering

    data class KunneIkkeOppretteOppgave(val feil: OppgaveFeil.KunneIkkeOppretteOppgave) : KunneIkkeOppretteRevurdering

    data class Avkorting(val underliggende: KanIkkeRevurderePgaAvkorting) : KunneIkkeOppretteRevurdering
}
