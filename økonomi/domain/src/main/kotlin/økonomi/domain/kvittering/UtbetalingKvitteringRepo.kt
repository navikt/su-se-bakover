package økonomi.domain.kvittering

interface UtbetalingKvitteringRepo {
    fun lagre(hendelse: RåKvitteringHendelse)
    fun lagre(hendelse: KvitteringPåSakHendelse)
    // TODO jah: Trenger en funksjon for å hente de uprosesserte rå kvitteringene
}
