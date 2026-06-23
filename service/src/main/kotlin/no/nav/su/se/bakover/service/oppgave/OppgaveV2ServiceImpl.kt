package no.nav.su.se.bakover.service.oppgave

import arrow.core.Either
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.kodeverk.Behandlingstema
import no.nav.su.se.bakover.common.domain.kodeverk.Behandlingstype
import no.nav.su.se.bakover.common.domain.kodeverk.Tema
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.oppgave.OppgaveV2Client
import no.nav.su.se.bakover.domain.oppgave.OppgaveV2Data
import no.nav.su.se.bakover.domain.oppgave.OppgaveV2Service
import no.nav.su.se.bakover.domain.oppgave.OppgavebeskrivelseMapper
import no.nav.su.se.bakover.domain.personhendelse.Personhendelse
import no.nav.su.se.bakover.oppgave.domain.KunneIkkeOppretteOppgave
import no.nav.su.se.bakover.oppgave.domain.OppgaveHttpKallResponse
import no.nav.su.se.bakover.oppgave.domain.Oppgavetype
import java.time.Clock
import java.time.LocalDate

private fun Personhendelse.Hendelse.skalHaHøyPrioritet(): Boolean {
    return when (this) {
        is Personhendelse.Hendelse.Dødsfall,
        is Personhendelse.Hendelse.Sivilstand,
        is Personhendelse.Hendelse.UtflyttingFraNorge,
        -> true

        is Personhendelse.Hendelse.Bostedsadresse,
        is Personhendelse.Hendelse.Kontaktadresse,
        is Personhendelse.Hendelse.FolkeregisteridentifikatorEndring,
        -> false
    }
}

class OppgaveV2ServiceImpl(
    private val oppgaveV2Client: OppgaveV2Client,
) : OppgaveV2Service {

    override fun opprettOppgaveMedSystembruker(
        saksnummer: Saksnummer,
        fnr: Fnr,
        sakstype: Sakstype,
        personhendelser: Collection<Personhendelse.TilknyttetSak.IkkeSendtTilOppgave>,
        clock: Clock,
    ): Either<KunneIkkeOppretteOppgave, OppgaveHttpKallResponse> {
        val aktivDato = LocalDate.now(clock)
        val prioritet = if (personhendelser.any { it.hendelse.skalHaHøyPrioritet() }) {
            OppgaveV2Data.Prioritet.HOY
        } else {
            OppgaveV2Data.Prioritet.NORMAL
        }

        val beskrivelse = (
            " Saksnummer : $saksnummer" +
                "\nPersonhendelsestyper: ${OppgavebeskrivelseMapper.mapHendelsestyper(personhendelser)}" +
                "\nPersonhendelse: ${OppgavebeskrivelseMapper.map(personhendelser)}"
            )
            .take(2500)

        val config = OppgaveV2Data(
            beskrivelse = beskrivelse,
            kategorisering = OppgaveV2Data.Kategorisering(
                tema = OppgaveV2Data.Kode(Tema.SUPPLERENDE_STØNAD.value),
                oppgavetype = OppgaveV2Data.Kode(Oppgavetype.VURDER_KONSEKVENS_FOR_YTELSE.value),
                behandlingstema = OppgaveV2Data.Kode(sakstype.toBehandlingstema().value),
                behandlingstype = OppgaveV2Data.Kode(Behandlingstype.REVURDERING.value),
            ),
            bruker = OppgaveV2Data.Bruker(
                ident = fnr.toString(),
                type = OppgaveV2Data.Bruker.Type.PERSON,
            ),
            aktivDato = aktivDato,
            fristDato = aktivDato.plusDays(7),
            prioritet = prioritet,
            nokkelord = OppgavebeskrivelseMapper.mapNøkkelord(personhendelser),
            arkivreferanse = OppgaveV2Data.Arkivreferanse(saksnr = saksnummer.toString()),
        )

        return oppgaveV2Client.opprettOppgaveMedSystembruker(config)
    }

    private fun Sakstype.toBehandlingstema(): Behandlingstema =
        when (this) {
            Sakstype.ALDER -> Behandlingstema.SU_ALDER
            Sakstype.UFØRE -> Behandlingstema.SU_UFØRE_FLYKTNING
        }
}
