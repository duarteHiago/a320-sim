package br.a320sim.cartridges.propulsao;

/** IAE V2500 (ceo): parâmetro principal é o EPR, resposta mais lenta. */
public class FadecIae extends Fadec {

    public FadecIae() {
        super("IAE V2500", "EPR", 0.35);
    }
}
