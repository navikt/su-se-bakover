package no.nav.su.se.bakover.domain.vedtak

import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.dokument.Dokumenttilstand
import no.nav.su.se.bakover.domain.klage.IverksattAvvistKlage
import no.nav.su.se.bakover.domain.klage.Klage
import java.time.Clock
import java.util.UUID

sealed interface Klagevedtak : Vedtak {
    val klage: Klage

    data class Avvist(
        override val id: UUID,
        override val opprettet: Tidspunkt,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val attestant: NavIdentBruker.Attestant,
        override val klage: IverksattAvvistKlage,
        override val dokumenttilstand: Dokumenttilstand,
    ) : Klagevedtak {

        init {
            require(dokumenttilstand != Dokumenttilstand.SKAL_IKKE_GENERERE)
            require(klage.skalSendeVedtaksbrev())
        }
        companion object {
            fun fromIverksattAvvistKlage(
                iverksattAvvistKlage: IverksattAvvistKlage,
                clock: Clock,
            ): Avvist {
                return Avvist(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(clock),
                    saksbehandler = iverksattAvvistKlage.saksbehandler,
                    attestant = (iverksattAvvistKlage.attesteringer.hentSisteAttestering() as Attestering.Iverksatt).attestant,
                    klage = iverksattAvvistKlage,
                    // Per tidspunkt er det implisitt at vi genererer og lagrer brev samtidig som vi oppretter vedtaket.
                    // TODO jah: Hvis vi heller flytter brevgenereringen ut til ferdigstill-jobben, blir det mer riktig og sette denne til IKKE_GENERERT_ENDA
                    dokumenttilstand = Dokumenttilstand.GENERERT,
                )
            }

            fun createFromPersistence(
                id: UUID,
                opprettet: Tidspunkt,
                saksbehandler: NavIdentBruker.Saksbehandler,
                attestant: NavIdentBruker.Attestant,
                klage: IverksattAvvistKlage,
                dokumenttilstand: Dokumenttilstand?,
            ) = Avvist(
                id = id,
                opprettet = opprettet,
                saksbehandler = saksbehandler,
                attestant = attestant,
                klage = klage,
                dokumenttilstand = when (dokumenttilstand) {
                    null -> when (klage.skalSendeVedtaksbrev()) {
                        true -> Dokumenttilstand.IKKE_GENERERT_ENDA
                        false -> Dokumenttilstand.SKAL_IKKE_GENERERE
                    }

                    else -> dokumenttilstand
                },
            )
        }
    }
}
