import java.io.Serial;
import java.io.Serializable;

public class InitialiazerOnDemandIdiom implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Holder class ensures lazy initialization
    private static class Holder {
        private static final InitialiazerOnDemandIdiom INSTANCE = new InitialiazerOnDemandIdiom();
    }

    /**
     * Private constructor.
     * Constructor guard prevents reflection-based second instance.
     */
    private InitialiazerOnDemandIdiom() {
        if (Holder.INSTANCE != null) {
            throw new IllegalStateException("Instance already created");
        }
    }

    /**
     * Returns the lazily-initialized singleton instance.
     */
    public static InitialiazerOnDemandIdiom getInstance() {
        return Holder.INSTANCE;
    }

    /**
     * Ensures singleton identity during deserialization.
     * Without this, deserialization would create a *new* instance.
     */
    @Serial
    private Object readResolve() {
        return Holder.INSTANCE;
    }

    // Example method
    public void doWork() {
        System.out.println("Holder-based Singleton is working...");
    }
}
