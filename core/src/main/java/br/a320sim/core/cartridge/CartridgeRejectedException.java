package br.a320sim.core.cartridge;

/** O cartão foi lido, mas o data loader se recusou a carregar o software dele. */
public class CartridgeRejectedException extends RuntimeException {

    /** Motivo da recusa, já no texto curto que aparece na tela e no log do instrutor. */
    public enum Reason {
        SEM_CHAVE_PUBLICA("SEM CHAVE PUBLICA"),
        SEM_ASSINATURA("SEM ASSINATURA"),
        ASSINATURA_INVALIDA("ASSINATURA INVALIDA"),
        ARQUIVO_FALTANDO("ARQUIVO FALTANDO"),
        ARQUIVO_CORROMPIDO("ARQUIVO CORROMPIDO");

        private final String label;

        Reason(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private final Reason reason;

    public CartridgeRejectedException(Reason reason, String detail) {
        super(reason.label() + ": " + detail);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
