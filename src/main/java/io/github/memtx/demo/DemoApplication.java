package io.github.memtx.demo;

/**
 * Entry point used by the README and local verification commands.
 */
public final class DemoApplication {

    private DemoApplication() {
    }

    public static void main(String[] args) {
        new ScenarioPrinter().run();
    }
}
