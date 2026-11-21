package br.a320sim.host;

import br.a320sim.core.cartridge.CartridgeRejectedException;
import br.a320sim.core.cartridge.CartridgeSignature;
import br.a320sim.host.cartridge.CardPolicy;
import br.a320sim.host.cartridge.CardReading;
import br.a320sim.host.cartridge.CartridgeLibrary;
import br.a320sim.host.cartridge.CartridgeLoader;
import br.a320sim.host.cartridge.ClasspathModuleFactory;
import br.a320sim.host.cartridge.DetectedCartridge;
import br.a320sim.host.cartridge.DevFolderReader;
import br.a320sim.host.cartridge.JarCartridgeLoader;
import br.a320sim.host.cartridge.SdCardReader;
import br.a320sim.host.cartridge.VolumeId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A {@link CardPolicy} é chamada depois da assinatura, e a leitura nova só uma vez por inserção. */
class CardPolicyTest {

    private static final KeyPair FACTORY = CartridgeSignature.generateKeyPair();
    private static final Path IMAGES = Path.of(System.getProperty("sim.test.sdImages", "cartridges/build/sd"));

    /** Política de teste: anota cada chamada e recusa os part numbers bloqueados. */
    private static final class Recording implements CardPolicy {
        final List<Boolean> calls = new ArrayList<>();
        final List<CardReading> readings = new ArrayList<>();
        final Set<String> blocked = new HashSet<>();

        @Override
        public void check(CardReading reading, boolean newInsertion) {
            calls.add(newInsertion);
            readings.add(reading);
            if (blocked.contains(reading.partNumber())) {
                throw new CartridgeRejectedException(CartridgeRejectedException.Reason.CARTAO_BLOQUEADO, "teste");
            }
        }
    }

    @TempDir
    Path temp;

    private final List<Path> readers = new ArrayList<>();
    private final Recording policy = new Recording();
    private JarCartridgeLoader jars;
    private CartridgeLibrary library;

    @BeforeEach
    void setUp() throws IOException {
        readers.add(Files.createDirectories(temp.resolve("G")));
        readers.add(Files.createDirectories(temp.resolve("H")));
        jars = new JarCartridgeLoader(temp.resolve("cache"));
        library = new CartridgeLibrary(new SdCardReader(() -> readers),
                new CartridgeLoader(new ClasspathModuleFactory(), jars, FACTORY.getPublic(), policy));
    }

    @AfterEach
    void tearDown() {
        jars.close();
    }

    private void insertCard(String partNumber, int reader) throws IOException {
        Path card = readers.get(reader);
        try (Stream<Path> files = Files.list(IMAGES.resolve(partNumber))) {
            for (Path file : files.toList()) {
                Files.copy(file, card.resolve(file.getFileName()));
            }
        }
        CartridgeSignature.sign(card, FACTORY.getPrivate(), "cartridge.json", "caixas.jar");
    }

    private void pullCard(int reader) throws IOException {
        try (Stream<Path> files = Files.list(readers.get(reader))) {
            for (Path file : files.toList()) {
                Files.delete(file);
            }
        }
    }

    private DetectedCartridge find(String partNumber) {
        return library.available().stream().filter(c -> c.partNumber().equals(partNumber)).findFirst().orElseThrow();
    }

    @Test
    void policyRecebeOHashGarantidoPelaAssinatura() throws IOException {
        insertCard("PLT-A320-CEO", 0);
        library.rescan();
        library.load(find("PLT-A320-CEO"));
        CardReading reading = policy.readings.get(0);
        assertEquals("PLT-A320-CEO", reading.partNumber());
        assertEquals("PLT-A320-CEO", reading.volumeId(), "pasta fingindo ser leitor usa o part number");
        assertEquals(CartridgeSignature.sha256(Files.readAllBytes(readers.get(0).resolve("caixas.jar"))), reading.jarSha256());
        assertNotNull(reading.gravadoEm());
    }

    @Test
    void leituraNovaSoNaPrimeiraCargaDepoisDaInsercao() throws IOException {
        insertCard("PLT-A320-CEO", 0);
        library.rescan(); // primeira varredura do programa: o cartão conta como inserido
        library.load(find("PLT-A320-CEO"));
        library.load(find("PLT-A320-CEO")); // a tela do rack recarrega a cada seta
        library.load(find("PLT-A320-CEO"));
        assertEquals(List.of(true, false, false), policy.calls, "o bloqueio é conferido em toda carga");
    }

    @Test
    void outroCartaoEntrandoNaoContaDeNovoOQueJaEstava() throws IOException {
        insertCard("PLT-A320-CEO", 0);
        library.rescan();
        library.load(find("PLT-A320-CEO"));
        insertCard("PRP-A320-CEO", 1);
        library.rescan();
        library.load(find("PLT-A320-CEO"));
        library.load(find("PRP-A320-CEO"));
        assertEquals(List.of(true, false, true), policy.calls);
    }

    @Test
    void tirarEColocarDeNovoEOutraLeitura() throws IOException {
        insertCard("PLT-A320-CEO", 0);
        library.rescan();
        library.load(find("PLT-A320-CEO"));
        pullCard(0);
        library.rescan();
        insertCard("PLT-A320-CEO", 0);
        library.rescan();
        library.load(find("PLT-A320-CEO"));
        assertEquals(List.of(true, true), policy.calls);
    }

    @Test
    void cartaoBloqueadoERecusadoENaoGastaALeitura() throws IOException {
        policy.blocked.add("PLT-A320-CEO");
        insertCard("PLT-A320-CEO", 0);
        library.rescan();
        CartridgeRejectedException e = assertThrows(CartridgeRejectedException.class,
                () -> library.load(find("PLT-A320-CEO")));
        assertEquals(CartridgeRejectedException.Reason.CARTAO_BLOQUEADO, e.reason());
        assertEquals("CARTAO BLOQUEADO", e.reason().label());

        policy.blocked.clear(); // desbloqueado no cadastro com o cartão ainda no leitor
        library.load(find("PLT-A320-CEO"));
        assertEquals(List.of(true, true), policy.calls, "a leitura só conta quando a carga dá certo");
    }

    @Test
    void cartaoSemAssinaturaNemChegaNaPolitica() throws IOException {
        insertCard("PLT-A320-CEO", 0);
        Files.delete(readers.get(0).resolve(CartridgeSignature.SIGNATURE_FILE));
        library.rescan();
        assertThrows(CartridgeRejectedException.class, () -> library.load(find("PLT-A320-CEO")));
        assertTrue(policy.calls.isEmpty());
    }

    @Test
    void pastasDeDesenvolvimentoNaoPassamPelaPolitica() {
        CartridgeLibrary dev = new CartridgeLibrary(new DevFolderReader(Path.of("sd-dev")),
                new CartridgeLoader(new ClasspathModuleFactory(), jars, FACTORY.getPublic(), policy));
        dev.load(dev.find("PLT-A320-NEO").orElseThrow());
        assertTrue(policy.calls.isEmpty());
    }

    @Test
    void volumeIdDePastaEOPartNumber() {
        assertEquals("PRP-A320-NEO", VolumeId.of(temp, "PRP-A320-NEO"));
    }
}
