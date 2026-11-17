package br.a320sim.host.cartridge;

import br.a320sim.core.module.AvionicsModule;

/**
 * Cria as caixas a partir de um class loader: o do próprio programa (modo dev, sd-dev/)
 * ou o do .jar copiado do cartão ({@link JarCartridgeLoader}).
 */
public class ClasspathModuleFactory implements ModuleFactory {

    private final ClassLoader classLoader;

    public ClasspathModuleFactory() {
        this(ClasspathModuleFactory.class.getClassLoader());
    }

    public ClasspathModuleFactory(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @Override
    public AvionicsModule create(String className) {
        try {
            Class<?> type = Class.forName(className, true, classLoader);
            if (!AvionicsModule.class.isAssignableFrom(type)) {
                throw new IllegalArgumentException(className + " não é um AvionicsModule");
            }
            return (AvionicsModule) type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException | LinkageError e) {
            throw new IllegalArgumentException("Não consegui carregar " + className, e);
        }
    }
}
