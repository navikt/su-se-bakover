package no.nav.su.se.bakover.domain.sak

import arrow.core.Either
import arrow.core.Nel
import arrow.core.left
import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUIDFactory
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.person.Person
import no.nav.su.se.bakover.domain.sak.SakFactory.KunneIkkeRegistrereSøknad.FantIkkePersonKnyttetTilFødselsnummer
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.søknad.SøknadRegistrertHendelse
import org.slf4j.LoggerFactory
import java.time.Clock

/**
 * Håndterer nye søknader, og oppretter nye saker, dersom vi ikke finner en sak og knytte søknaden til.
 */
object SakFactory {

    private val log = LoggerFactory.getLogger(this::class.java)

    /**
     * @return en [RegistrerSøknadCommand], dersom validert ok, fører alltid til en [SøknadRegistrertHendelse], men kun en [SakRegistrertHendelse] dersom det ikke fantes en sak knyttet til fødselsnummeret fra før.
     */
    fun registrerSøknad(
        command: RegistrerSøknadCommand,
        uuidFactory: UUIDFactory,
        clock: Clock,
        hentPerson: (f: Fnr) -> Person?,
        hentSak: (f: Nel<Fnr>, s: Sakstype) -> List<Sak>,
        // TODO jah: Diskuter med Jacob og Ramzi. Det føles mer naturlig å registrere SøknadRegistrert-hendelsen først (uten noe sakstilhørighet).
        //  Deretter kan vi prøve knytte den til en sak asynkront og evt. lage en sak hvis vi ikke finner.
        hentSisteSaksnummer: () -> Saksnummer,
    ): Either<KunneIkkeRegistrereSøknad, Pair<SakRegistrertHendelse?, SøknadRegistrertHendelse>> {

        val person = hentPerson(command.innsendtFnr) ?: return FantIkkePersonKnyttetTilFødselsnummer.left()
        val innsendtFnr = command.innsendtFnr
        val sisteFnr = person.ident.fnr
        if (sisteFnr != innsendtFnr) {
            // TODO jah: person.ident gir kun siste ident, her ønsker vi nok hente saker basert på alle personens aktive/tidlgiere fødselsnummer så vi ikke lager duplikate saker.
            //  Dette er ikke en nytt problem.
            log.error("Ny søknad: Personen har et nyere fødselsnummer i PDL enn det som ble sendt inn. Bruker det nyeste fødselsnummeret istedet. Personoppslaget burde ha returnert det nyeste fødselsnummeret og bør sjekkes opp.")
            sikkerLogg.error("Ny søknad: Personen har et nyere fødselsnummer i PDL $sisteFnr enn det som ble sendt inn ${innsendtFnr}. Bruker det nyeste fødselsnummeret istedet. Personoppslaget burde ha returnert det nyeste fødselsnummeret og bør sjekkes opp.")
        }
        val sak = hentSak(nonEmptyListOf(innsendtFnr, sisteFnr), command.sakstype).let {
            when {
                it.isEmpty() -> null
                it.size == 1 -> it.single()
                else -> throw IllegalStateException("Fant flere saker for søker. saksnummer: ${it.map { it.saksnummer }}")
            }
        }

        if(sak == null) {
            // Finnes sannsynligvis ikke en sak fra før, så vi oppretter en.
            command.toSakRegistrertHendelse(
                sakId =uuidFactory.newUUID(),
                saksnummer = Saksnummer(nummer = 0),
                fnr = Fnr(fnr = ""),
                clock =
            )
        }

        val opprettet = Tidspunkt.now(clock)
        val sakId = uuidFactory.newUUID()
        return RegistrerSøknadCommand(
            id = sakId,
            innsendtFnr = sisteFnr,
            opprettet = opprettet,
            søknad = Søknad.Ny(
                id = uuidFactory.newUUID(),
                opprettet = opprettet,
                sakId = sakId,
                søknadInnhold = søknadInnhold,
                innsendtAv = innsendtAv,
            ),
        )
    }

    sealed interface KunneIkkeRegistrereSøknad {
        object FantIkkePersonKnyttetTilFødselsnummer : KunneIkkeRegistrereSøknad
    }
}
