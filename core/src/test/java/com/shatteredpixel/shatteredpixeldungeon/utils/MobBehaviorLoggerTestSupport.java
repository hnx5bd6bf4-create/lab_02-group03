package com.shatteredpixel.shatteredpixeldungeon.utils;

import com.badlogic.gdx.Files;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.files.FileHandle;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public final class MobBehaviorLoggerTestSupport {

    private MobBehaviorLoggerTestSupport() {
    }

    @SuppressWarnings("unchecked")
    public static Set<Integer> trackedMobs() {
        return (Set<Integer>) readPrivateStaticField("trackedMobs");
    }

    @SuppressWarnings("unchecked")
    public static Map<Integer, String> lastStates() {
        return (Map<Integer, String>) readPrivateStaticField("lastStates");
    }

    @SuppressWarnings("unchecked")
    public static Map<Integer, Integer> lastTargetIds() {
        return (Map<Integer, Integer>) readPrivateStaticField("lastTargetIds");
    }

    @SuppressWarnings("unchecked")
    public static Map<Integer, Integer> lastTargetCells() {
        return (Map<Integer, Integer>) readPrivateStaticField("lastTargetCells");
    }

    @SuppressWarnings("unchecked")
    public static Map<Integer, Boolean> lastAlertStates() {
        return (Map<Integer, Boolean>) readPrivateStaticField("lastAlertStates");
    }

    public static FileSandbox createSandbox() throws IOException {
        Path root = java.nio.file.Files.createTempDirectory("mob-logger-test");
        return new FileSandbox(root);
    }

    public static int countOccurrences(String text, String token) {
        if (text == null || text.isEmpty() || token == null || token.isEmpty()) {
            return 0;
        }

        int count = 0;
        int index = 0;
        while ((index = text.indexOf(token, index)) != -1) {
            count++;
            index += token.length();
        }
        return count;
    }

    public static String logFilePath() {
        try {
            Field field = MobBehaviorLogger.class.getDeclaredField("LOG_FILE");
            field.setAccessible(true);
            return (String) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to read LOG_FILE", e);
        }
    }

    /** Read the actual log file written by MobBehaviorLogger (absolute path). */
    public static String readActualLog() throws IOException {
        Path logPath = java.nio.file.Paths.get(logFilePath());
        if (!java.nio.file.Files.exists(logPath)) {
            return "";
        }
        byte[] contents = java.nio.file.Files.readAllBytes(logPath);
        return new String(contents, StandardCharsets.UTF_8);
    }

    /** Delete the actual log file so tests start with a clean slate. */
    public static void clearActualLog() throws IOException {
        Path logPath = java.nio.file.Paths.get(logFilePath());
        java.nio.file.Files.deleteIfExists(logPath);
    }

    private static Object readPrivateStaticField(String fieldName) {
        try {
            Field field = MobBehaviorLogger.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(null);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to read field: " + fieldName, exception);
        }
    }

    public static final class FileSandbox implements AutoCloseable {

        private final Path root;
        private final Files files;

        private FileSandbox(Path root) {
            this.root = root;
            this.files = new LocalFiles(root);
        }

        public Files files() {
            return files;
        }

        public String readLog() throws IOException {
            Path logFile = root.resolve("mob-behaviour.log");
            if (!java.nio.file.Files.exists(logFile)) {
                return "";
            }
            byte[] contents = java.nio.file.Files.readAllBytes(logFile);
            return new String(contents, StandardCharsets.UTF_8);
        }

        @Override
        public void close() throws IOException {
            try (Stream<Path> stream = java.nio.file.Files.walk(root)) {
                stream.sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                java.nio.file.Files.deleteIfExists(path);
                            } catch (IOException exception) {
                                throw new RuntimeException(exception);
                            }
                        });
            } catch (RuntimeException exception) {
                if (exception.getCause() instanceof IOException) {
                    throw (IOException) exception.getCause();
                }
                throw exception;
            }
        }
    }

    public static final class MemoryPreferences implements Preferences {

        private final Map<String, Object> values = new HashMap<>();

        @Override
        public Preferences putBoolean(String key, boolean value) {
            values.put(key, value);
            return this;
        }

        @Override
        public Preferences putInteger(String key, int value) {
            values.put(key, value);
            return this;
        }

        @Override
        public Preferences putLong(String key, long value) {
            values.put(key, value);
            return this;
        }

        @Override
        public Preferences putFloat(String key, float value) {
            values.put(key, value);
            return this;
        }

        @Override
        public Preferences putString(String key, String value) {
            values.put(key, value);
            return this;
        }

        @Override
        public Preferences put(Map<String, ?> vals) {
            values.putAll(vals);
            return this;
        }

        @Override
        public boolean getBoolean(String key) {
            return getBoolean(key, false);
        }

        @Override
        public int getInteger(String key) {
            return getInteger(key, 0);
        }

        @Override
        public long getLong(String key) {
            return getLong(key, 0L);
        }

        @Override
        public float getFloat(String key) {
            return getFloat(key, 0f);
        }

        @Override
        public String getString(String key) {
            return getString(key, "");
        }

        @Override
        public boolean getBoolean(String key, boolean defValue) {
            Object value = values.get(key);
            return value instanceof Boolean ? (Boolean) value : defValue;
        }

        @Override
        public int getInteger(String key, int defValue) {
            Object value = values.get(key);
            return value instanceof Integer ? (Integer) value : defValue;
        }

        @Override
        public long getLong(String key, long defValue) {
            Object value = values.get(key);
            return value instanceof Long ? (Long) value : defValue;
        }

        @Override
        public float getFloat(String key, float defValue) {
            Object value = values.get(key);
            return value instanceof Float ? (Float) value : defValue;
        }

        @Override
        public String getString(String key, String defValue) {
            Object value = values.get(key);
            return value instanceof String ? (String) value : defValue;
        }

        @Override
        public Map<String, ?> get() {
            return Collections.unmodifiableMap(values);
        }

        @Override
        public boolean contains(String key) {
            return values.containsKey(key);
        }

        @Override
        public void clear() {
            values.clear();
        }

        @Override
        public void remove(String key) {
            values.remove(key);
        }

        @Override
        public void flush() {
        }
    }

    private static final class LocalFiles implements Files {

        private final Path root;

        private LocalFiles(Path root) {
            this.root = root;
        }

        @Override
        public FileHandle getFileHandle(String path, FileType type) {
            switch (type) {
                case Absolute:
                    return new FileHandle(path);
                case Local:
                case External:
                    return new FileHandle(root.resolve(path).toFile());
                case Internal:
                case Classpath:
                    return resolveAssetHandle(path);
                default:
                    return new FileHandle(root.resolve(path).toFile());
            }
        }

        private FileHandle resolveAssetHandle(String path) {
            String normalizedPath = path.startsWith("/") ? path.substring(1) : path;

            String[] assetRoots = new String[]{
                    "src/main/assets/",
                    "core/src/main/assets/",
                    "desktop/src/main/assets/"
            };

            for (String assetRoot : assetRoots) {
                FileHandle assetRootHandle = new FileHandle(assetRoot);
                if (assetRootHandle.exists()) {
                    return new FileHandle(assetRoot + normalizedPath);
                }
            }

            return new FileHandle(root.resolve(normalizedPath).toFile());
        }

        @Override
        public FileHandle classpath(String path) {
            return getFileHandle(path, FileType.Classpath);
        }

        @Override
        public FileHandle internal(String path) {
            return getFileHandle(path, FileType.Internal);
        }

        @Override
        public FileHandle external(String path) {
            return getFileHandle(path, FileType.External);
        }

        @Override
        public FileHandle absolute(String path) {
            return getFileHandle(path, FileType.Absolute);
        }

        @Override
        public FileHandle local(String path) {
            return getFileHandle(path, FileType.Local);
        }

        @Override
        public String getExternalStoragePath() {
            return root.toString();
        }

        @Override
        public boolean isExternalStorageAvailable() {
            return true;
        }

        @Override
        public String getLocalStoragePath() {
            return root.toString();
        }

        @Override
        public boolean isLocalStorageAvailable() {
            return true;
        }
    }
}
