package br.a320sim.dados;

import br.a320sim.dados.repositorio.CartaoRepositorio;
import br.a320sim.dados.repositorio.PessoaRepositorio;
import br.a320sim.dados.repositorio.PosicaoRepositorio;
import br.a320sim.dados.repositorio.SessaoRepositorio;
import br.a320sim.dados.servico.CartaoServico;
import br.a320sim.dados.servico.PessoaServico;
import br.a320sim.dados.servico.PosicaoServico;
import br.a320sim.dados.servico.SessaoServico;

import java.time.Clock;

/** Ponto de entrada dos cadastros: um banco aberto e os serviços de cada entidade. */
public final class Dados implements AutoCloseable {

    private final Banco banco;
    private final PessoaServico pessoas;
    private final CartaoServico cartoes;
    private final PosicaoServico posicoes;
    private final SessaoServico sessoes;

    public Dados(Banco banco, Clock relogio) {
        this.banco = banco;
        var conexao = banco.conexao();
        pessoas = new PessoaServico(new PessoaRepositorio(conexao));
        cartoes = new CartaoServico(new CartaoRepositorio(conexao), relogio);
        posicoes = new PosicaoServico(new PosicaoRepositorio(conexao));
        sessoes = new SessaoServico(new SessaoRepositorio(conexao), relogio);
    }

    public Dados(Banco banco) {
        this(banco, Clock.systemDefaultZone());
    }

    public PessoaServico pessoas() {
        return pessoas;
    }

    public CartaoServico cartoes() {
        return cartoes;
    }

    public PosicaoServico posicoes() {
        return posicoes;
    }

    public SessaoServico sessoes() {
        return sessoes;
    }

    @Override
    public void close() {
        banco.close();
    }
}
