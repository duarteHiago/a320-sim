package br.a320sim.dados.modelo;

/** O plano de voo que o aluno inseriu no MCDU, gravado na sessão para o debriefing. */
public record PlanoVoo(String origem, String destino, String pista, int fl, int zfwKg, int combustivelKg,
                       int v1, int vr, int v2) {

    public int towKg() {
        return zfwKg + combustivelKg;
    }

    /** "SBKP > SBGR 10L FL100 TOW 68000" */
    public String resumo() {
        return String.format("%s > %s %s FL%03d TOW %d", origem, destino, pista, fl, towKg());
    }

    /** "140/143/148" */
    public String velocidades() {
        return v1 + "/" + vr + "/" + v2;
    }
}
