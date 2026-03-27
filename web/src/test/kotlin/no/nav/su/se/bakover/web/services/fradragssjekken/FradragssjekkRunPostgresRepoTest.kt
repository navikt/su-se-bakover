package no.nav.su.se.bakover.web.services.fradragssjekken

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.test.persistence.DbExtension
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.sql.DataSource

@ExtendWith(DbExtension::class)
internal class FradragssjekkRunPostgresRepoTest(private val dataSource: DataSource) {

    @Test
    fun `lagrer og henter kjøring og saksresultater`() {
        val helper = TestDataHelper(dataSource)
        val repo = FradragssjekkRunPostgresRepo(helper.sessionFactory)

        val eksternFeil = lagEksternFeilSaksresultat(
            sakId = UUID.randomUUID(),
            saksnummer = Saksnummer(2021001),
            fnr = Fnr("12345678901"),
        )
        val opprettetOppgave = lagOpprettetOppgaveSaksresultat(
            sakId = UUID.randomUUID(),
            saksnummer = Saksnummer(2021002),
            fnr = Fnr("10987654321"),
        )

        val resultat = FradragssjekkResultat(
            saksresultater = listOf(eksternFeil, opprettetOppgave),
        )

        val kjoringId = UUID.randomUUID()
        val opprettet = Instant.parse("2026-01-15T08:00:00Z")
        val ferdigstilt = Instant.parse("2026-01-15T08:05:00Z")

        val fullfortKjoring = FradragssjekkKjøring(
            id = kjoringId,
            dato = LocalDate.parse("2026-01-15"),
            status = FradragssjekkKjøringStatus.FULLFØRT,
            opprettet = opprettet,
            ferdigstilt = ferdigstilt,
            resultat = resultat,
        )

        repo.lagreKjoring(fullfortKjoring)
        repo.hentKjoring(kjoringId) shouldBe fullfortKjoring

        repo.hentSaksresultaterForKjoring(kjoringId) shouldContainExactlyInAnyOrder listOf(
            eksternFeil,
            opprettetOppgave,
        )

        repo.hentSaksresultaterMedEksternFeil(kjoringId) shouldBe listOf(eksternFeil)
    }

    private fun lagEksternFeilSaksresultat(
        sakId: UUID,
        saksnummer: Saksnummer,
        fnr: Fnr,
    ): FradragssjekkSakResultat {
        val sjekkpunkt = Sjekkpunkt(
            fnr = fnr,
            tilhører = FradragTilhører.BRUKER,
            fradragstype = Fradragstype.Arbeidsavklaringspenger,
            ytelse = EksternYtelse.AAP,
            lokaltBeløp = 5000.0,
        )

        return FradragssjekkSakResultat(
            sakId = sakId,
            status = FradragssjekkSakStatus.EKSTERN_FEIL,
            sjekkplan = SjekkPlanData(
                sak = SakInfo(
                    sakId = sakId,
                    saksnummer = saksnummer,
                    fnr = fnr,
                    type = Sakstype.UFØRE,
                ),
                sjekkpunkter = listOf(SjekkpunktData.fraDomain(sjekkpunkt)),
            ),
            eksterneFeil = listOf(
                EksternFeilPåSjekkpunkt(
                    sjekkpunkt = SjekkpunktData.fraDomain(sjekkpunkt),
                    grunn = "AAP-oppslag feilet",
                ),
            ),
        )
    }

    private fun lagOpprettetOppgaveSaksresultat(
        sakId: UUID,
        saksnummer: Saksnummer,
        fnr: Fnr,
    ): FradragssjekkSakResultat {
        return FradragssjekkSakResultat(
            sakId = sakId,
            status = FradragssjekkSakStatus.OPPGAVE_OPPRETTET,
            sjekkplan = SjekkPlanData(
                sak = SakInfo(
                    sakId = sakId,
                    saksnummer = saksnummer,
                    fnr = fnr,
                    type = Sakstype.ALDER,
                ),
                sjekkpunkter = listOf(
                    SjekkpunktData(
                        fnr = fnr,
                        tilhører = FradragTilhører.BRUKER,
                        fradragstype = FradragstypeData(
                            kategori = Fradragstype.Kategori.Alderspensjon,
                        ),
                        ytelse = EksternYtelse.PESYS_ALDER,
                        lokaltBeløp = 1200.0,
                    ),
                ),
            ),
            oppgaveAvvik = listOf(
                Fradragsfunn.Oppgaveavvik(
                    kode = OppgaveConfig.Fradragssjekk.AvvikKode.FRADRAG_DIFF_OVER_10KR,
                    oppgavetekst = "Bruker har avvik i alderpensjon",
                ),
            ),
            observasjoner = listOf(
                Fradragsfunn.Observasjon(
                    kode = Observasjonskode.INSIGNIFIKANT_BELOEPSDIFFERANSE,
                    loggtekst = "Insignifikant differanse for samme sak",
                ),
            ),
            opprettetOppgave = OppgaveopprettelseResultat.Opprettet(
                oppgaveId = OppgaveId("12345"),
                sakId = sakId,
            ),
        )
    }
}
