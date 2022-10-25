package no.nav.su.se.bakover.domain.sak

import no.nav.su.se.bakover.common.Brukerrolle
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.domain.søknadinnhold.SøknadInnhold
import no.nav.su.se.bakover.hendelse.domain.HendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import java.time.Clock
import java.util.UUID

/**
 * Frontenden har ikke et forhold til sak når den registrer søknader.
 */
data class RegistrerSøknadCommand(
    val innsendtFnr: Fnr,
    val sakstype: Sakstype,
    val søknadsinnhold: SøknadInnhold,
    val registrertAv: NavIdentBruker.Saksbehandler,
    val correlationId: CorrelationId,
    val brukerroller: List<Brukerrolle>,
    val klientensSisteSaksversjon: Hendelsesversjon,
) {
    /**
     * @param fnr domenet overstyrer fødselsnummeret som saksbehandler har sendt inn, i tilfeller det finnes et nyere et.
     */
  fun toSakRegistrertHendelse(
        sakId: UUID,
        saksnummer: Saksnummer,
        fnr: Fnr,
        clock: Clock,
  ): SakRegistrertHendelse {
      return SakRegistrertHendelse.registrer(
          sakId =sakId,
          fnr = fnr,
          registrertAv = registrertAv,
          clock =clock,
          meta = HendelseMetadata(
              correlationId = correlationId,
              ident = registrertAv,
              brukerroller = brukerroller,
          ),
          sakstype =sakstype,
          saksnummer = saksnummer,
      )
  }
}
