package gal.conxugal.application;

import io.micronaut.runtime.Micronaut;

/**
 * Entry point and composition root of the conxugal server. Boots the Micronaut
 * context, which wires the driving adapters in this module together with the
 * driven adapters contributed by the infrastructure module at runtime.
 */
public final class Application {

    private Application() {
    }

    public static void main(String[] args) {
        Micronaut.run(Application.class, args);
    }
}
