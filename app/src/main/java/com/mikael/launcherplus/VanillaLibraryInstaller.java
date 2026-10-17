package com.mikael.launcherplus;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Stages ordinary Java library artifacts declared by an official Vanilla version JSON.
 * It deliberately does not install native classifiers or a Java runtime and never marks
 * an instance playable. The manifest records anything that prevents a complete resolution.
 */
public final class VanillaLibraryInstaller {
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 25000;
    private static final int MAX_PROFILE_BYTES = 256 * 1024;
    private static final int MAX_VERSION_JSON_BYTES = 8 * 1024 * 1024;
    private static final int MAX_LIBRARY_COUNT = 1000;
    private static final long MAX_SINGLE_LIBRARY_BYTES = 512L * 1024L * 1024L;
    private static final long MAX_TOTAL_LIBRARY_BYTES = 2L * 1024L * 1024L * 1024L;
    private static final long DISK_RESERVE_BYTES = 16L * 1024L * 1024L;
    private static final String MANIFEST_PATH = "libraries/manifest.json";

    private final InstanceManager instances;

    public interface ProgressListener {
        void onProgress(String phase, long completed, long total);
        boolean isCancelled();
    }

    public static final class DownloadCancelledException extends IOException {
        public DownloadCancelledException() {
            super("Download de bibliotecas cancelado; arquivos completos verificados foram mantidos.");
        }
    }

    private static final class Library {
        final String name;
        final String path;
        final String url;
        final String sha1;
        final long size;

        Library(String name, String path, String url, String sha1, long size) {
            this.name = name;
            this.path = path;
            this.url = url;
            this.sha1 = sha1;
            this.size = size;
        }
    }

    public VanillaLibraryInstaller(InstanceManager manager) {
        if (manager == null) throw new IllegalArgumentException("Gerenciador de instâncias obrigatório.");
        instances = manager;
    }

    public JSONObject downloadLibraries(String instanceName, ProgressListener listener) throws IOException {
        final File instance;
        final JSONObject profile;
        try {
            instance = instances.getInstanceDirectory(instanceName);
            profile = readJson(safeChild(instance, "instance.json"), MAX_PROFILE_BYTES);
        } catch (JSONException e) {
            throw new IOException("Não foi possível ler o perfil da instância.", e);
        }
        String version = profile.optString("version", "");
        if (!"Vanilla".equals(profile.optString("loader", "Vanilla"))
                || !version.matches("[A-Za-z0-9._-]{1,100}")
                || !version.equals(profile.optString("stagedVersion", ""))
                || !"client_artifacts_staged".equals(profile.optString("installStatus", ""))) {
            throw new IOException("Prepare primeiro os arquivos oficiais do cliente de uma instância Vanilla.");
        }

        File versionDir = safeChild(safeChild(instance, "versions"), version);
        File versionJsonFile = safeChild(versionDir, version + ".json");
        JSONObject metadata;
        try {
            metadata = readJson(versionJsonFile, MAX_VERSION_JSON_BYTES);
        } catch (JSONException e) {
            throw new IOException("O JSON oficial da versão está inválido.", e);
        }
        if (!version.equals(metadata.optString("id", ""))) {
            throw new IOException("O JSON local não corresponde à versão da instância.");
        }
        String recordedMetadataSha1 = profile.optString("metadataSha1", "").toLowerCase(Locale.ROOT);
        if (!recordedMetadataSha1.matches("[0-9a-f]{40}")
                || !recordedMetadataSha1.equals(sha1(versionJsonFile))) {
            throw new IOException("O JSON da versão não corresponde ao hash verificado durante o download do cliente.");
        }

        JSONArray sourceLibraries = metadata.optJSONArray("libraries");
        if (sourceLibraries == null || sourceLibraries.length() == 0
                || sourceLibraries.length() > MAX_LIBRARY_COUNT) {
            throw new IOException("O manifesto não contém uma lista de bibliotecas válida.");
        }

        List<Library> libraries = new ArrayList<>();
        Set<String> uniquePaths = new HashSet<>();
        JSONArray unresolved = new JSONArray();
        long totalBytes = 0;
        for (int i = 0; i < sourceLibraries.length(); i++) {
            checkCancelled(listener);
            JSONObject library = sourceLibraries.optJSONObject(i);
            if (library == null) {
                unresolved.put("Entrada de biblioteca malformada no índice " + i);
                continue;
            }
            String name = library.optString("name", "biblioteca sem nome");
            if (!isAllowedByRules(library.optJSONArray("rules"))) continue;

            JSONObject downloads = library.optJSONObject("downloads");
            JSONObject artifact = downloads == null ? null : downloads.optJSONObject("artifact");
            JSONObject classifiers = downloads == null ? null : downloads.optJSONObject("classifiers");
            if (classifiers != null && classifiers.length() > 0) {
                unresolved.put(name + ": contém classificadores nativos; seleção/instalação nativa é responsabilidade do motor Android.");
            }
            if (artifact == null) {
                if (classifiers == null || classifiers.length() == 0) {
                    unresolved.put(name + ": manifesto não declarou downloads.artifact.");
                }
                continue;
            }
            String relativePath = artifact.optString("path", "");
            String url = artifact.optString("url", "");
            String expectedSha1 = artifact.optString("sha1", "").toLowerCase(Locale.ROOT);
            long size = artifact.optLong("size", -1L);
            validateRelativePath(relativePath);
            validateLibraryUrl(url);
            if (!expectedSha1.matches("[0-9a-f]{40}") || size < 0 || size > MAX_SINGLE_LIBRARY_BYTES) {
                unresolved.put(name + ": faltam SHA-1/tamanho válidos ou o tamanho excede o limite.");
                continue;
            }
            if (!uniquePaths.add(relativePath)) {
                unresolved.put(name + ": caminho de biblioteca duplicado no manifesto.");
                continue;
            }
            if (totalBytes > MAX_TOTAL_LIBRARY_BYTES - size) {
                throw new IOException("O conjunto de bibliotecas excede o limite de 2 GiB.");
            }
            totalBytes += size;
            libraries.add(new Library(name, relativePath, url, expectedSha1, size));
        }
        if (libraries.isEmpty()) throw new IOException("Nenhuma biblioteca Java verificável foi encontrada no manifesto.");

        File libraryRoot = safeChild(instance, "libraries");
        ensureDirectory(libraryRoot);
        File manifestFile = safeChild(libraryRoot, "manifest.json");
        File manifestTemp = safeChild(libraryRoot, "manifest.json.part");

        long missingBytes = 0;
        int alreadyVerified = 0;
        for (Library library : libraries) {
            checkCancelled(listener);
            File target = safeChild(libraryRoot, library.path);
            if (isVerified(target, library)) alreadyVerified++;
            else missingBytes = safeAdd(missingBytes, library.size);
        }
        long usable = instance.getUsableSpace();
        if (usable > 0 && missingBytes > Math.max(0L, usable - DISK_RESERVE_BYTES)) {
            throw new IOException("Espaço insuficiente para as bibliotecas, incluindo a reserva de segurança de 16 MiB.");
        }

        JSONArray verifiedEntries = new JSONArray();
        long completed = 0;
        long verifiedCount = 0;
        try {
            for (Library library : libraries) {
                checkCancelled(listener);
                File target = safeChild(libraryRoot, library.path);
                ensureDirectory(target.getParentFile());
                if (!isVerified(target, library)) {
                    File temporary = safeChild(target.getParentFile(), target.getName() + ".part");
                    deleteTemp(temporary);
                    downloadOne(library, temporary, listener);
                    if (target.exists() && !target.delete()) {
                        throw new IOException("Não foi possível substituir uma biblioteca local inválida.");
                    }
                    if (!temporary.renameTo(target)) {
                        throw new IOException("Não foi possível finalizar uma biblioteca baixada.");
                    }
                }
                if (!isVerified(target, library)) throw new IOException("Biblioteca falhou na verificação final: " + library.name);
                JSONObject entry = new JSONObject();
                entry.put("name", library.name);
                entry.put("path", library.path);
                entry.put("sha1", library.sha1);
                entry.put("size", library.size);
                verifiedEntries.put(entry);
                verifiedCount++;
                completed++;
                reportProgress(listener, "Baixando/verificando bibliotecas", completed, libraries.size());
            }

            JSONObject manifest = new JSONObject();
            manifest.put("format", 1);
            manifest.put("version", version);
            manifest.put("metadataSha1", recordedMetadataSha1);
            manifest.put("status", unresolved.length() == 0 ? "libraries_staged" : "libraries_partial");
            manifest.put("verifiedCount", verifiedCount);
            manifest.put("plannedCount", libraries.size());
            manifest.put("totalBytes", totalBytes);
            manifest.put("unresolved", unresolved);
            manifest.put("libraries", verifiedEntries);
            writeBytes(manifestTemp, manifest.toString().getBytes(StandardCharsets.UTF_8));
            if (manifestFile.exists() && !manifestFile.delete()) throw new IOException("Não foi possível substituir o manifesto de bibliotecas.");
            if (!manifestTemp.renameTo(manifestFile)) throw new IOException("Não foi possível finalizar o manifesto de bibliotecas.");

            JSONObject result = new JSONObject();
            result.put("status", unresolved.length() == 0 ? "libraries_staged" : "libraries_partial");
            result.put("version", version);
            result.put("manifestRelativePath", MANIFEST_PATH);
            result.put("manifestSha1", sha1(manifestFile));
            result.put("verifiedCount", verifiedCount);
            result.put("plannedCount", libraries.size());
            result.put("totalBytes", totalBytes);
            result.put("unresolvedCount", unresolved.length());
            result.put("unresolved", unresolved);
            instances.updateLibraryStatus(instanceName, result);
            reportProgress(listener, "Bibliotecas verificadas", libraries.size(), libraries.size());
            return result;
        } catch (DownloadCancelledException e) {
            savePartial(instanceName, version, recordedMetadataSha1, verifiedCount, libraries.size(), totalBytes);
            throw e;
        } catch (JSONException e) {
            savePartial(instanceName, version, recordedMetadataSha1, verifiedCount, libraries.size(), totalBytes);
            throw new IOException("Não foi possível criar o manifesto verificado de bibliotecas.", e);
        } catch (IOException e) {
            savePartial(instanceName, version, recordedMetadataSha1, verifiedCount, libraries.size(), totalBytes);
            throw e;
        } finally {
            deleteTempQuietly(manifestTemp);
        }
    }

    private void savePartial(String instanceName, String version, String metadataSha1,
                             long verified, long planned, long bytes) {
        try {
            File instance = instances.getInstanceDirectory(instanceName);
            File manifestFile = safeChild(safeChild(instance, "libraries"), "manifest.json");
            if (!manifestFile.isFile()) return;
            JSONObject result = new JSONObject();
            result.put("status", "libraries_partial");
            result.put("version", version);
            result.put("manifestRelativePath", MANIFEST_PATH);
            result.put("manifestSha1", sha1(manifestFile));
            result.put("verifiedCount", verified);
            result.put("plannedCount", planned);
            result.put("totalBytes", bytes);
            result.put("unresolvedCount", 1);
            instances.updateLibraryStatus(instanceName, result);
        } catch (Exception ignored) {
            // Keep cancellation as the primary result.
        }
    }

    private void downloadOne(Library library, File temporary, ProgressListener listener) throws IOException {
        HttpURLConnection connection = open(library.url);
        boolean complete = false;
        try {
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) throw new IOException("Download de biblioteca respondeu HTTP " + status + ".");
            long announced = connection.getContentLengthLong();
            if (announced >= 0 && announced != library.size) throw new IOException("Tamanho anunciado da biblioteca diverge do manifesto.");
            MessageDigest digest = newSha1();
            long received = 0;
            try (InputStream input = connection.getInputStream();
                 FileOutputStream output = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[64 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    checkCancelled(listener);
                    received += count;
                    if (received > library.size || received > MAX_SINGLE_LIBRARY_BYTES) {
                        throw new IOException("Biblioteca ultrapassou o tamanho esperado.");
                    }
                    digest.update(buffer, 0, count);
                    output.write(buffer, 0, count);
                }
                output.flush();
                output.getFD().sync();
            }
            if (received != library.size || !library.sha1.equals(toHex(digest.digest()))) {
                throw new IOException("A biblioteca baixada falhou na verificação SHA-1/tamanho.");
            }
            complete = true;
        } finally {
            connection.disconnect();
            if (!complete && temporary.exists()) temporary.delete();
        }
    }

    private boolean isAllowedByRules(JSONArray rules) throws IOException {
        if (rules == null || rules.length() == 0) return true;
        boolean allowed = false;
        for (int i = 0; i < rules.length(); i++) {
            JSONObject rule = rules.optJSONObject(i);
            if (rule == null) throw new IOException("Regra de biblioteca malformada.");
            String action = rule.optString("action", "");
            if (!"allow".equals(action) && !"disallow".equals(action)) throw new IOException("Ação de regra de biblioteca inválida.");
            JSONObject os = rule.optJSONObject("os");
            if (os != null) {
                String name = os.optString("name", "");
                String arch = os.optString("arch", "");
                String version = os.optString("version", "");
                if (!name.isEmpty() && !"linux".equalsIgnoreCase(name)) continue;
                if (!arch.isEmpty() && !matchesPattern(arch, System.getProperty("os.arch", "aarch64"))) continue;
                if (!version.isEmpty() && !matchesPattern(version, System.getProperty("os.version", ""))) continue;
            }
            JSONObject features = rule.optJSONObject("features");
            if (features != null) {
                boolean featureMatch = true;
                java.util.Iterator<String> keys = features.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    // This dashboard does not enable launcher-specific optional features.
                    if (features.optBoolean(key, false)) { featureMatch = false; break; }
                }
                if (!featureMatch) continue;
            }
            allowed = "allow".equals(action);
        }
        return allowed;
    }

    private boolean matchesPattern(String expression, String value) throws IOException {
        try { return Pattern.compile(expression).matcher(value).matches(); }
        catch (RuntimeException e) { throw new IOException("Expressão de regra do manifesto inválida.", e); }
    }

    private void validateRelativePath(String path) throws IOException {
        if (path == null || path.isEmpty() || path.length() > 512 || path.startsWith("/")
                || path.indexOf('\\') >= 0 || path.indexOf(':') >= 0) {
            throw new IOException("Caminho de biblioteca inválido.");
        }
        String[] segments = path.split("/");
        for (String segment : segments) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                throw new IOException("Caminho de biblioteca contém segmento inválido.");
            }
            for (int i = 0; i < segment.length(); i++) {
                if (Character.isISOControl(segment.charAt(i))) throw new IOException("Caminho de biblioteca contém controle.");
            }
        }
    }

    private void validateLibraryUrl(String address) throws IOException {
        try {
            URL url = new URL(address);
            String host = url.getHost().toLowerCase(Locale.ROOT);
            boolean allowed = host.equals("libraries.minecraft.net")
                    || host.equals("repo.maven.apache.org") || host.equals("repo1.maven.org")
                    || host.equals("maven.minecraftforge.net") || host.equals("maven.fabricmc.net")
                    || host.equals("maven.quiltmc.org") || host.equals("repo.spongepowered.org")
                    || host.equals("maven.neoforged.net");
            if (!"https".equalsIgnoreCase(url.getProtocol()) || !allowed
                    || url.getUserInfo() != null || (url.getPort() != -1 && url.getPort() != 443)) {
                throw new IOException("URL de biblioteca fora dos hosts HTTPS permitidos.");
            }
        } catch (java.net.MalformedURLException e) {
            throw new IOException("URL de biblioteca inválida.", e);
        }
    }

    private HttpURLConnection open(String address) throws IOException {
        String current = address;
        for (int attempt = 0; attempt < 2; attempt++) {
            validateLibraryUrl(current);
            HttpURLConnection connection = (HttpURLConnection) new URL(current).openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "*/*");
            connection.setRequestProperty("User-Agent", "MikaelLauncherPlus/0.1.0");
            int status = connection.getResponseCode();
            if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if (location == null || location.trim().isEmpty() || attempt > 0) {
                    throw new IOException("Redirecionamento de biblioteca inválido ou em excesso.");
                }
                current = new URL(new URL(current), location).toString();
                continue;
            }
            return connection;
        }
        throw new IOException("Não foi possível abrir a URL de biblioteca permitida.");
    }

    private JSONObject readJson(File file, int maxBytes) throws IOException, JSONException {
        if (!file.isFile() || file.length() <= 0 || file.length() > maxBytes) throw new IOException("JSON ausente ou acima do limite.");
        try (InputStream input = new FileInputStream(file); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (output.size() + count > maxBytes) throw new IOException("JSON excedeu o limite.");
                output.write(buffer, 0, count);
            }
            return new JSONObject(new String(output.toByteArray(), StandardCharsets.UTF_8));
        }
    }

    private File safeChild(File parent, String childPath) throws IOException {
        File base = parent.getCanonicalFile();
        File child = new File(base, childPath).getCanonicalFile();
        if (!child.getPath().startsWith(base.getPath() + File.separator)) throw new IOException("Caminho fora da instância.");
        return child;
    }

    private void ensureDirectory(File directory) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs() && !directory.isDirectory()) throw new IOException("Não foi possível criar pasta de biblioteca.");
    }

    private void writeBytes(File target, byte[] bytes) throws IOException {
        try (FileOutputStream output = new FileOutputStream(target)) {
            output.write(bytes); output.flush(); output.getFD().sync();
        }
    }

    private void deleteTemp(File file) throws IOException {
        if (file.exists() && !file.delete()) throw new IOException("Não foi possível remover download incompleto.");
    }

    private void deleteTempQuietly(File file) { if (file.exists()) file.delete(); }

    private void checkCancelled(ProgressListener listener) throws DownloadCancelledException {
        if (Thread.currentThread().isInterrupted() || (listener != null && listener.isCancelled())) {
            throw new DownloadCancelledException();
        }
    }

    private void reportProgress(ProgressListener listener, String phase, long complete, long total) {
        if (listener == null) return;
        long interval = Math.max(1L, total / 100L);
        if (complete == 0 || complete >= total || complete % interval == 0) listener.onProgress(phase, complete, total);
    }

    private long safeAdd(long a, long b) throws IOException {
        if (b > Long.MAX_VALUE - a) throw new IOException("Tamanho total de bibliotecas inválido.");
        return a + b;
    }

    private boolean isVerified(File file, Library library) throws IOException {
        return file.isFile() && file.length() == library.size && library.sha1.equals(sha1(file));
    }

    private String sha1(File file) throws IOException {
        MessageDigest digest = newSha1();
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[64 * 1024]; int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        return toHex(digest.digest());
    }

    private MessageDigest newSha1() throws IOException {
        try { return MessageDigest.getInstance("SHA-1"); }
        catch (NoSuchAlgorithmException e) { throw new IOException("SHA-1 não disponível.", e); }
    }

    private String toHex(byte[] bytes) {
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte item : bytes) value.append(String.format(Locale.ROOT, "%02x", item & 0xff));
        return value.toString();
    }
}
