package br.a320sim.cartridges.propulsao;

/** PW1100G (neo): parâmetro principal é o N1, resposta mais rápida. */
public class FadecPw extends Fadec {

    public FadecPw() {
        super("PW1100G", "N1", 0.6);
    }
}
