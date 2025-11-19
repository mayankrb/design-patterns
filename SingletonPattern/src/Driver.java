import java.io.*;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Enhanced demo driver for singletons.
 *
 * - Demonstrates enum singleton usage and serialization safety
 * - Demonstrates initialization-on-demand holder usage and identity check
 * - Runs a small multithreaded test to prove only one instance is returned
 * - Attempts reflection creation (and prints the result / exceptions)
 *
 * Note - this uses your existing classes:
 *   - SingletonUsingEnum (enum-based singleton)
 *   - InitialiazerOnDemandIdiom (holder-idiom singleton)
 *
 * If those classes are in a different package, adjust the package/imports accordingly.
 */
public class Driver {

    public static void main(String[] args) throws Exception {
        System.out.println("\n=== Basic enum singleton usage ===");
        demoEnumSingleton();

        System.out.println("\n=== Basic holder-idiom singleton usage ===");
        demoHolderSingleton();

        System.out.println("\n=== Multithreaded identity test (holder idiom) ===");
        runMultithreadedIdentityCheck();

        System.out.println("\n=== Serialization test for enum singleton ===");
        serializationRoundTripEnum();

        System.out.println("\n=== Serialization test for holder singleton (may fail if not Serializable) ===");
        serializationRoundTripHolder();

        System.out.println("\n=== Reflection attempt to create another holder-instance ===");
        reflectionAttackOnHolder();

        System.out.println("\nAll done.");
    }

    // 1. simple enum usage check
    private static void demoEnumSingleton() {
        // get and mutate a field to demonstrate shared state
        SingletonUsingEnum instanceA = SingletonUsingEnum.INSTANCE;
        instanceA.setName("EnumSingleton - Name A");
        System.out.println("instanceA.getName(): " + instanceA.getName());

        SingletonUsingEnum instanceB = SingletonUsingEnum.INSTANCE;
        System.out.println("instanceB.getName(): " + instanceB.getName());

        // identity check
        System.out.println("instanceA == instanceB: " + (instanceA == instanceB));
    }

    // 2. simple holder idiom usage check
    private static void demoHolderSingleton() {
        InitialiazerOnDemandIdiom s1 = InitialiazerOnDemandIdiom.getInstance();
        InitialiazerOnDemandIdiom s2 = InitialiazerOnDemandIdiom.getInstance();

        System.out.println("s1 == s2: " + (s1 == s2));
        System.out.println("s1.equals(s2): " + s1.equals(s2));
    }

    // 3. multithreaded identity test - spawn multiple tasks that call getInstance()
    private static void runMultithreadedIdentityCheck() throws InterruptedException, ExecutionException {
        int threads = 10;
        ExecutorService ex = Executors.newFixedThreadPool(threads);
        List<Callable<InitialiazerOnDemandIdiom>> tasks = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            tasks.add(() -> {
                // each task returns the instance it observed
                return InitialiazerOnDemandIdiom.getInstance();
            });
        }

        List<Future<InitialiazerOnDemandIdiom>> futures = ex.invokeAll(tasks);
        ex.shutdown();
        ex.awaitTermination(5, TimeUnit.SECONDS);

        // collect references and check identity
        InitialiazerOnDemandIdiom first = futures.get(0).get();
        boolean allSame = true;
        for (Future<InitialiazerOnDemandIdiom> f : futures) {
            if (f.get() != first) {
                allSame = false;
                break;
            }
        }

        System.out.println("All threads observed the same holder-instance: " + allSame);
    }

    // 4. serialize and deserialize the enum singleton - this must preserve identity
    private static void serializationRoundTripEnum() {
        try {
            SingletonUsingEnum original = SingletonUsingEnum.INSTANCE;
            original.setName("EnumNameForSerialization");

            // write to byte array
            byte[] bytes = toByteArray(original);

            // read back
            Object deserialized = fromByteArray(bytes);
            System.out.println("Deserialized is same instance: " + (deserialized == SingletonUsingEnum.INSTANCE));
            System.out.println("Deserialized name: " + ((SingletonUsingEnum) deserialized).getName());
        } catch (Exception e) {
            System.out.println("Enum serialization test failed: " + e.getMessage());
            e.printStackTrace(System.out);
        }
    }

    // 5. attempt serialization for holder - may throw NotSerializableException unless implemented
    private static void serializationRoundTripHolder() {
        try {
            InitialiazerOnDemandIdiom original = InitialiazerOnDemandIdiom.getInstance();

            byte[] bytes = toByteArray(original);
            Object deserialized = fromByteArray(bytes);

            System.out.println("Holder deserialized == original: " + (deserialized == original));
            System.out.println("Holder deserialized equals original: " + deserialized.equals(original));
        } catch (NotSerializableException nse) {
            System.out.println("Holder singleton is not Serializable - serialization test skipped.");
        } catch (Exception e) {
            System.out.println("Holder serialization attempt threw: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            e.printStackTrace(System.out);
        }
    }

    // 6. reflection attempt - try to call private constructor of holder class
    private static void reflectionAttackOnHolder() {
        try {
            Class<InitialiazerOnDemandIdiom> clazz = InitialiazerOnDemandIdiom.class;
            Constructor<InitialiazerOnDemandIdiom> ctor = clazz.getDeclaredConstructor();
            ctor.setAccessible(true);
            InitialiazerOnDemandIdiom reflectInstance = ctor.newInstance();

            System.out.println("Reflection created instance: " + reflectInstance);
            System.out.println("reflectInstance == InitialiazerOnDemandIdiom.getInstance(): " +
                    (reflectInstance == InitialiazerOnDemandIdiom.getInstance()));
        } catch (Exception e) {
            // many singletons guard against this by throwing in the private constructor
            System.out.println("Reflection attack failed or was blocked: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
    }

    // Helper - serialize object to byte[]
    private static byte[] toByteArray(Object obj) throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream out = new ObjectOutputStream(bos)) {
            out.writeObject(obj);
            out.flush();
            return bos.toByteArray();
        }
    }

    // Helper - deserialize from byte[]
    private static Object fromByteArray(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
             ObjectInputStream in = new ObjectInputStream(bis)) {
            return in.readObject();
        }
    }
}
