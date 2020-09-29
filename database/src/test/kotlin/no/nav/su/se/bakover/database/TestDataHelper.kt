package no.nav.su.se.bakover.database

import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.database.utbetaling.UtbetalingPostgresRepo
import no.nav.su.se.bakover.domain.Behandling
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.oppdrag.Oppdragsmelding
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import java.util.UUID
import javax.sql.DataSource

internal class TestDataHelper(
    dataSource: DataSource = EmbeddedDatabase.instance()
) {
    private val repo = DatabaseRepo(dataSource)
    private val utbetalingRepo = UtbetalingPostgresRepo(dataSource)

    fun insertSak(fnr: Fnr) = repo.opprettSak(fnr)
    fun insertUtbetaling(oppdragId: UUID30, utbetaling: Utbetaling): Utbetaling =
        utbetaling.also { repo.opprettUtbetaling(oppdragId, utbetaling) }

    fun insertOppdragsmelding(utbetalingId: UUID30, oppdragsmelding: Oppdragsmelding) =
        repo.addOppdragsmelding(utbetalingId, oppdragsmelding)

    fun insertSøknad(sakId: UUID) = repo.opprettSøknad(
        sakId,
        Søknad(
            id = UUID.randomUUID(),
            søknadInnhold = SøknadInnholdTestdataBuilder.build()
        )
    )

    fun insertBehandling(sakId: UUID, søknad: Søknad) = repo.opprettSøknadsbehandling(
        sakId = sakId,
        behandling = Behandling(
            søknad = søknad,
            status = Behandling.BehandlingsStatus.VILKÅRSVURDERT_INNVILGET,
            sakId = sakId
        )
    )

    fun insertBeregning(behandlingId: UUID) = repo.opprettBeregning(
        behandlingId = behandlingId,
        beregning = Beregning(
            fom = 1.januar(2020),
            tom = 31.desember(2020),
            sats = Sats.HØY,
            fradrag = emptyList(),
            forventetInntekt = 0
        )
    )

    fun hentUtbetaling(utbetalingId: UUID30) = utbetalingRepo.hentUtbetaling(utbetalingId)
}
