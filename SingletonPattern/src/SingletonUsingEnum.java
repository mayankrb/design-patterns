public enum SingletonUsingEnum {

    INSTANCE;

    // Example field
    private String name;

    /**
     * Enum constructors are always private.
     * This constructor runs exactly once at class-loading time.
     */
    SingletonUsingEnum() {
        this.name = "SingletonUsingEnum";
    }

    // Getter
    public String getName() {
        return this.name;
    }

    // Setter
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Optional: Example of a lazily initialized heavy resource.
     * The enum constructor is not the best place for expensive initialization,
     * so you can use a holder or synchronized block for lazy loading.
     */
    private static class HeavyResourceHolder {
        private static final HeavyResource RESOURCE = new HeavyResource();
    }

    public HeavyResource getHeavyResource() {
        return HeavyResourceHolder.RESOURCE;
    }

    /**
     * Example heavy resource class (for demonstration).
     * Replace with your real service / object.
     */
    public static class HeavyResource {
        public void doWork() {
            System.out.println("HeavyResource is working...");
        }
    }
}

