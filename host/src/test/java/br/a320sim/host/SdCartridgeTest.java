package br.a320sim.host;

import br.a320sim.core.bus.DataBus;
import br.a320sim.core.bus.Labels;
import br.a320sim.core.cartridge.Cartridge;
import br.a320sim.core.cartridge.CartridgeGroup;
import br.a320sim.core.cartridge.CartridgeRejectedException;
import br.a320sim.core.cartridge.CartridgeSignature;
import br.a320sim.core.module.AvionicsModule;
import br.a320sim.host.cartridge.AutoReader;
import br.a320sim.host.cartridge.CartridgeLibrary;
import br.a320sim.host.cartridge.CartridgeLoader;
import br.a320sim.host.cartridge.ClasspathModuleFactory;
import br.a320sim.host.cartridge.DetectedCartridge;
import br.a320sim.host.cartridge.DevFolderReader;
import br.a320sim.host.cartridge.HotPlug;
import br.a320sim.host.cartridge.JarCartridgeLoader;
import br.a320sim.host.cartridge.SdCardReader;
import br.a320sim.host.command.RepositionCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cartões SD simulados: cada pasta temporária faz papel da raiz de um cartão num leitor,
 * com as imagens geradas por :cartridges:sdImages (cartridge.json + caixas.jar).
 * Cada cartão é assinado aqui com uma chave de teste, então o teste não depende da chave da equipe.
 */
class SdCartridgeTest {

    /** "Fábrica" dos testes; a chave de verdade da equipe nunca é usada aqui. */
    private static final KeyPair FACTORY = CartridgeSignature.generateKeyPair();

    private static final Path IMAGES = Path.of(System.getProperty("sim.test.sdImages", "cartridges/build/sd"));

    @TempDir
    Path temp;

    /** Os "leitores" do hub; um leitor vazio é uma pasta sem nada. */
    private final List<Path> readers = new ArrayList<>();
    private CartridgeLibrary library;
    private JarCartridgeLoader jars;
    private final Host host = new Host();
    private final DataBus bus = host.bus();

    @BeforeEach
    void setUp() throws IOException {
        for (String letter : new String[]{"G", "H", "I", "J"}) {
            readers.add(Files.createDirectories(temp.resolve(letter)));
        }
        jars = new JarCartridgeLoader(temp.resolve("cache"));
        library = new CartridgeLibrary(new SdCardReader(() -> readers),
                new CartridgeLoader(new ClasspathModuleFactory(), jars, FACTORY.getPublic()));
    }

    @AfterEach
    void tearDown() {
        host.powerOff();
        jars.close(); // solta os .jar do cache para a pasta temporária poder ser apagada
    }

    private void insertCard(String partNumber, int reader) throws IOException {
        insertCard(partNumber, reader, FACTORY);
    }

    /** Copia a imagem para o "leitor" e assina com a chave dada (simula a gravação na fábrica). */
    private void insertCard(String partNumber, int reader, KeyPair signer) throws IOException {
        Path image = IMAGES.resolve(partNumber);
        Path card = readers.get(reader);
        try (Stream<Path> files = Files.list(image)) {
            for (Path file : files.toList()) {
                Files.copy(file, card.resolve(file.getFileName()));
            }
        }
        CartridgeSignature.sign(card, signer.getPrivate(), "cartridge.json", "caixas.jar");
    }

    private void pullCard(int reader) throws IOException {
        try (Stream<Path> files = Files.list(readers.get(reader))) {
            for (Path file : files.toList()) {
                Files.delete(file);
            }
        }
    }

    private void bootNeo() throws IOException {
        insertCard("CKP-A320-NEO", 0); // o grupo vem do manifesto, não do leitor
        insertCard("PLT-A320-NEO", 1);
        insertCard("PRP-A320-NEO", 2);
        for (String pn : new String[]{"PLT-A320-NEO", "PRP-A320-NEO", "CKP-A320-NEO"}) {
            host.rack().insert(library.load(library.find(pn).orElseThrow()));
        }
        assertTrue(host.powerOn().report().ok());
        host.execute(new RepositionCommand(8000, 250));
        bus.publish(Labels.SIM_THRUST_LEVER, 0.6);
        host.advance(1);
    }

    @Test
    void readsManifestsFromCardRootsAndIgnoresEmptyReaders() throws IOException {
        insertCard("PLT-A320-CEO", 2);
        List<DetectedCartridge> found = library.rescan();
        assertEquals(1, found.size());
        assertEquals("PLT-A320-CEO", found.get(0).partNumber());
        assertEquals(CartridgeGroup.PLATAFORMA, found.get(0).group());
        assertTrue(found.get(0).removable());
    }

    @Test
    void boxesComeFromTheJarOnTheCardNotFromTheProgram() throws IOException {
        insertCard("PRP-A320-CEO", 0);
        Cartridge cartridge = library.load(library.rescan().get(0));
        for (AvionicsModule module : cartridge.modules()) {
            assertNotSame(getClass().getClassLoader(), module.getClass().getClassLoader(),
                    module.info().friendlyName() + " deveria vir do caixas.jar");
        }
        // O software foi copiado para o cache: o cartão pode sair sem travar nada
        try (Stream<Path> cached = Files.list(temp.resolve("cache"))) {
            assertTrue(cached.anyMatch(p -> p.getFileName().toString().endsWith(".jar")));
        }
        pullCard(0);
        assertTrue(library.rescan().isEmpty());
    }

    @Test
    void pullingCockpitCardInFlightBlanksScreensAndPluggingBackReloads() throws IOException {
        bootNeo();
        HotPlug hotPlug = new HotPlug(host, library);
        hotPlug.update();
        assertEquals("LCD", bus.getString(Labels.DMC_STYLE, ""));
        assertTrue(bus.getList(Labels.RACK_MISSING).isEmpty());

        pullCard(0);
        library.rescan();
        assertEquals(List.of("CARTUCHO CKP-A320-NEO REMOVIDO"), hotPlug.update());
        assertTrue(host.rack().slot(CartridgeGroup.COCKPIT).isEmpty());
        assertFalse(bus.has(Labels.DMC_STYLE), "sem o cartão, as telas apagam");
        assertEquals(List.of("COCKPIT"), bus.getList(Labels.RACK_MISSING));
        host.advance(2); // o resto do avião continua voando
        assertTrue(bus.getDouble(Labels.ADR_ALT, 0) > 7000);

        insertCard("CKP-A320-NEO", 3); // volta em outro leitor do hub
        library.rescan();
        assertEquals(List.of("CARTUCHO CKP-A320-NEO CARREGADO"), hotPlug.update());
        host.advance(0.5);
        assertEquals("LCD", bus.getString(Labels.DMC_STYLE, ""));
        assertTrue(bus.getList(Labels.RACK_MISSING).isEmpty());
    }

    @Test
    void incompatibleCardIsRefusedInFlight() throws IOException {
        bootNeo();
        HotPlug hotPlug = new HotPlug(host, library);
        hotPlug.update();
        pullCard(0);
        insertCard("CKP-A320-CEO", 0); // PROPULSAO neo exige COCKPIT neo
        library.rescan();
        List<String> events = hotPlug.update();
        assertTrue(events.contains("CARTUCHO CKP-A320-CEO INCOMPATIVEL"), events.toString());
        assertTrue(host.rack().slot(CartridgeGroup.COCKPIT).isEmpty());
    }

    @Test
    void autoUsesDevFoldersOnlyWhenNoCardIsPlugged() throws IOException {
        AutoReader auto = new AutoReader(new SdCardReader(() -> readers), new DevFolderReader(Path.of("sd-dev")),
                AutoReader.Source.AUTO);
        assertEquals(6, auto.scan().size());
        assertTrue(auto.scan().stream().noneMatch(DetectedCartridge::removable));

        insertCard("PLT-A320-NEO", 1);
        assertEquals(List.of("PLT-A320-NEO"), auto.scan().stream().map(DetectedCartridge::partNumber).toList());
    }

    @Test
    void cardWithoutSignatureIsRefusedBeforeAnyCodeRuns() throws IOException {
        insertCard("PLT-A320-CEO", 0);
        Files.delete(readers.get(0).resolve(CartridgeSignature.SIGNATURE_FILE));
        DetectedCartridge detected = library.rescan().get(0);
        CartridgeRejectedException e = assertThrows(CartridgeRejectedException.class, () -> library.load(detected));
        assertEquals(CartridgeRejectedException.Reason.SEM_ASSINATURA, e.reason());
        assertFalse(Files.exists(temp.resolve("cache")), "nada do cartão pode ter sido copiado para o cache");
    }

    @Test
    void cardSignedWithAnotherKeyIsRefused() throws IOException {
        insertCard("PLT-A320-CEO", 0, CartridgeSignature.generateKeyPair()); // "pirata"
        DetectedCartridge detected = library.rescan().get(0);
        CartridgeRejectedException e = assertThrows(CartridgeRejectedException.class, () -> library.load(detected));
        assertEquals(CartridgeRejectedException.Reason.ASSINATURA_INVALIDA, e.reason());
    }

    @Test
    void jarChangedAfterSigningIsRefused() throws IOException {
        insertCard("PLT-A320-CEO", 0);
        Files.write(readers.get(0).resolve("caixas.jar"), new byte[]{0}, StandardOpenOption.APPEND);
        DetectedCartridge detected = library.rescan().get(0);
        CartridgeRejectedException e = assertThrows(CartridgeRejectedException.class, () -> library.load(detected));
        assertEquals(CartridgeRejectedException.Reason.ARQUIVO_CORROMPIDO, e.reason());
    }

    @Test
    void unsignedCardPluggedInFlightIsRefusedAndTheSlotStaysEmpty() throws IOException {
        bootNeo();
        HotPlug hotPlug = new HotPlug(host, library);
        hotPlug.update();
        pullCard(0);
        library.rescan();
        hotPlug.update();

        insertCard("CKP-A320-NEO", 0);
        Files.delete(readers.get(0).resolve(CartridgeSignature.SIGNATURE_FILE));
        library.rescan();
        assertEquals(List.of("CARTUCHO CKP-A320-NEO RECUSADO: SEM ASSINATURA"), hotPlug.update());
        assertTrue(host.rack().slot(CartridgeGroup.COCKPIT).isEmpty());
    }
}
