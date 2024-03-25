package no.nav.su.se.bakover.test

import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.person.AktørId
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.personhendelse.Personhendelse
import java.util.UUID

fun nyPersonhendelseIkkeKnyttetTilSak(
    endringstype: Personhendelse.Endringstype = Personhendelse.Endringstype.OPPRETTET,
    hendelse: Personhendelse.Hendelse = Personhendelse.Hendelse.Dødsfall(fixedLocalDate),
    aktørId: AktørId = no.nav.su.se.bakover.test.aktørId,
    fnr: Fnr = no.nav.su.se.bakover.test.fnr,
    hendelseId: String = "hendelseId",
): Personhendelse.IkkeTilknyttetSak {
    return Personhendelse.IkkeTilknyttetSak(
        endringstype = endringstype,
        hendelse = hendelse,
        metadata = Personhendelse.Metadata(
            hendelseId = hendelseId,
            personidenter = nonEmptyListOf(aktørId.toString(), fnr.toString()),
            tidligereHendelseId = null,
            offset = 0,
            partisjon = 0,
            master = "FREG",
            key = "someKey",
        ),
    )
}

fun nyPersonhendelseKnyttetTilSak(
    endringstype: Personhendelse.Endringstype = Personhendelse.Endringstype.OPPRETTET,
    hendelse: Personhendelse.Hendelse = Personhendelse.Hendelse.Dødsfall(fixedLocalDate),
    id: UUID = UUID.randomUUID(),
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    fnr: Fnr = no.nav.su.se.bakover.test.fnr,
    sakstype: Sakstype = Sakstype.UFØRE,
    opprettet: Tidspunkt = fixedTidspunkt,
): Personhendelse.TilknyttetSak.IkkeSendtTilOppgave {
    return nyPersonhendelseIkkeKnyttetTilSak(
        endringstype = endringstype,
        hendelse = hendelse,
        fnr = fnr,
        aktørId = aktørId,
        hendelseId = id.toString(),
    ).tilknyttSak(
        id = id,
        sakIdSaksnummerFnr = SakInfo(
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            type = sakstype,
        ),
        opprettet = opprettet,
    )
}

fun nyPersonhendelseSendtTilOppgave(
    endringstype: Personhendelse.Endringstype = Personhendelse.Endringstype.OPPRETTET,
    hendelse: Personhendelse.Hendelse = Personhendelse.Hendelse.Dødsfall(fixedLocalDate),
    id: UUID = UUID.randomUUID(),
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    fnr: Fnr = no.nav.su.se.bakover.test.fnr,
    sakstype: Sakstype = Sakstype.UFØRE,
    oppgaveId: OppgaveId = no.nav.su.se.bakover.test.oppgave.oppgaveId,
    opprettet: Tidspunkt = fixedTidspunkt,
): Personhendelse.TilknyttetSak.SendtTilOppgave {
    return nyPersonhendelseKnyttetTilSak(
        endringstype = endringstype,
        hendelse = hendelse,
        id = id,
        sakId = sakId,
        saksnummer = saksnummer,
        fnr = fnr,
        sakstype = sakstype,
        opprettet = opprettet,
    ).tilSendtTilOppgave(oppgaveId = oppgaveId)
}
