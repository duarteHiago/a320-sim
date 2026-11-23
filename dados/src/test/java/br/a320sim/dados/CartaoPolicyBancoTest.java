package br.a320sim.dados;

import br.a320sim.core.cartridge.CartridgeRejectedException;
import br.a320sim.core.cartridge.CartridgeSignature;
import br.a320sim.dados.modelo.Cartao;
import br.a320sim.dados.modelo.StatusCartao;
import br.a320sim.dados.servico.CartaoServico;
import br.a320sim.host.cartridge.CardReading;
import br.a320sim.host.cartridge.CartridgeLibrary;
import br.a320sim.host.cartridge.CartridgeLoader;
import br.a320sim.host.cartridge.ClasspathModuleFactory;
import br.a320sim.host.cartridge.JarCartridgeLoader;
import br.a320sim.host.cartridge.SdCardReader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cadastro de cartões ligado ao data loader de verdade, com um cartão assinado por uma chave de teste. */
class CartaoPolicyBancoTest {

    private static final KeyPair FABRICA = CartridgeSignature.generateKeyPair();
    private static final Path IMAGENS = Path.of(System.getProperty("sim.test.sdImages", "cartridges/build/sd"));

    @TempDir
    Path temp;

    private Dados dados;
    private CartaoServico cartoes;
    private Path leitor;
    private JarCartridgeLoader jars;
    private CartridgeLibrary gaveta;

    @BeforeEach
    void montar() throws IOException {
        dados = BancoDeTeste.dados();
        cartoes = dados.cartoes();
        leitor = Files.createDirectories(temp.resolve("G"));
        jars = new JarCartridgeLoader(temp.resolve("cache"));
        gaveta = new CartridgeLibrary(new SdCardReader(() -> List.of(leitor)),
                new CartridgeLoader(new ClasspathModuleFactory(), jars, FABRICA.getPublic(),
                        new CartaoPolicyBanco(cartoes)));
    }

    @AfterEach
    void desmontar() {
        jars.close();
        dados.close();
    }

    private void gravarEEncaixar(String partNumber) throws IOException {
        try (Stream<Path> arquivos = Files.list(IMAGENS.resolve(partNumber))) {
            for (Path arquivo : arquivos.toList()) {
                Files.copy(arquivo, leitor.resolve(arquivo.getFileName()));
            }
        }
        CartridgeSignature.sign(leitor, FABRICA.getPrivate(), "cartridge.json", "caixas.jar");
        gaveta.rescan();
    }

    private Cartao cadastro(String etiqueta) {
        return cartoes.listar().stream().filter(c -> etiqueta.equals(c.etiqueta())).findFirst().orElseThrow();
    }

    @Test
    void cartaoBloqueadoNoCadastroERecusado() throws IOException {
        Cartao sd01 = cadastro("SD-01"); // PLT-A320-CEO, do seed
        cartoes.salvar(new Cartao(sd01.id(), sd01.etiqueta(), sd01.partNumber(), null, null, null, null, 0,
                StatusCartao.BLOQUEADO, null));
        gravarEEncaixar("PLT-A320-CEO");

        CartridgeRejectedException e = assertThrows(CartridgeRejectedException.class,
                () -> gaveta.load(gaveta.available().get(0)));
        assertEquals(CartridgeRejectedException.Reason.CARTAO_BLOQUEADO, e.reason());
        assertTrue(e.getMessage().contains("SD-01"), e.getMessage());
        assertEquals(0, cadastro("SD-01").leituras(), "carga recusada nao conta como leitura");
    }

    @Test
    void cartaoPerdidoTambemERecusado() throws IOException {
        Cartao sd03 = cadastro("SD-03");
        cartoes.salvar(new Cartao(sd03.id(), sd03.etiqueta(), sd03.partNumber(), null, null, null, null, 0,
                StatusCartao.PERDIDO, "sumiu na aula"));
        gravarEEncaixar("PRP-A320-CEO");
        assertThrows(CartridgeRejectedException.class, () -> gaveta.load(gaveta.available().get(0)));
    }

    @Test
    void primeiraLeituraAdotaOCadastroManualERegistraUmaVezPorInsercao() throws IOException {
        gravarEEncaixar("CKP-A320-NEO");
        gaveta.load(gaveta.available().get(0));
        gaveta.load(gaveta.available().get(0));

        Cartao sd06 = cadastro("SD-06");
        assertEquals("CKP-A320-NEO", sd06.volumeId(), "pasta de teste: o identificador e o part number");
        assertEquals(1, sd06.leituras());
        assertEquals(BancoDeTeste.AGORA, sd06.ultimaLeitura());
        assertEquals(CartridgeSignature.sha256(Files.readAllBytes(leitor.resolve("caixas.jar"))), sd06.sha256());
        assertEquals(6, cartoes.listar().size(), "adotou, nao duplicou");
    }

    @Test
    void cartaoDesconhecidoECadastradoSozinho() {
        new CartaoPolicyBanco(cartoes).check(new CardReading("XYZ-NOVO", "BEEF-0001", "ab".repeat(32), null), true);
        Cartao novo = cartoes.listar().stream().filter(c -> "BEEF-0001".equals(c.volumeId())).findFirst().orElseThrow();
        assertEquals(StatusCartao.ATIVO, novo.status());
        assertEquals(null, novo.etiqueta());
        assertEquals(1, novo.leituras());
    }

    @Test
    void bancoForaDoArNaoDerrubaOCartao() {
        CartaoPolicyBanco politica = new CartaoPolicyBanco(cartoes);
        dados.close();
        politica.check(new CardReading("PLT-A320-CEO", "X", "ab".repeat(32), null), true);
    }
}
