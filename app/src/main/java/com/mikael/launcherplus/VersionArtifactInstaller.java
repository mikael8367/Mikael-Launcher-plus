package com.mikael.launcherplus;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * Downloads and verifies only the official Minecraft version metadata and client JAR.
 * This is an artifact-staging step, not a complete installation: libraries, assets,
 * Java runtime and engine game-directory binding are intentionally not claimed here.
 */
public final class VersionArtifactInstaller {
    private static final int MAX_METADATA_BYTES = 8 * 1024 * 1024;
    private static final long MAX_CLIENT_BYTES = 1024L * 1024L * 1024L;
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 20000;
    private final InstanceManager instances;

    public VersionArtifactInstaller(InstanceManager manager) {
        if (manager == null) throw new IllegalArgumentException("Gerenciador de instâncias obrigatório.");
        instances = manager;
    }

    public JSONObject stageVanillaClient(String instanceName, String versionId, String metadataUrl)
            throws IOException {
        String id = validateVersionId(versionId);
        validateHttpsUrl(metadataUrl, true);
        final File instance;
        try {
            instance = instances.getInstanceDirectory(instanceName);
        } catch (org.json.JSONException e) {
            throw new IOException("Não foi possível ler o índice de instâncias.", e);
        }
        File versionsRoot = safeChild(instance, "versions");
        ensureDirectory(versionsRoot);
        File versionDirectory = safeChild(versionsRoot, id);
        ensureDirectory(versionDirectory);

        File metadataFile = safeChild(versionDirectory, id + ".json");
        File clientFile = safeChild(versionDirectory, id + ".jar");
        File metadataTemp = safeChild(versionDirectory, id + ".json.part");
        File clientTemp = safeChild(versionDirectory, id + ".jar.part");
        deleteTemp(metadataTemp);
        deleteTemp(clientTemp);

        boolean finished = false;
        try {
            byte[] metadataBytes = downloadBytes(metadataUrl, MAX_METADATA_BYTES);
            JSONObject metadata = new JSONObject(new String(metadataBytes, java.nio.charset.StandardCharsets.UTF_8));
            if (!id.equals(metadata.optString("id", ""))) {
                throw new IOException("O identificador do JSON oficial não corresponde à versão selecionada.");
            }
            JSONObject downloads = metadata.optJSONObject("downloads");
            JSONObject client = downloads == null ? null : downloads.optJSONObject("client");
            if (client == null) throw new IOException("Esta versão não publicou um arquivo client no manifesto oficial.");
            String clientUrl = client.optString("url", "");
            String expectedSha1 = client.optString("sha1", "").toLowerCase(Locale.ROOT);
            long expectedSize = client.optLong("size", -1L);
            validateHttpsUrl(clientUrl, false);
            if (!expectedSha1.matches("[0-9a-f]{40}") || expectedSize <= 0 || expectedSize > MAX_CLIENT_BYTES) {
                throw new IOException("Tamanho ou SHA-1 do cliente oficial inválido.");
            }

            downloadToFile(clientUrl, clientTemp, expectedSize);
            String actualSha1 = sha1(clientTemp);
            if (!expectedSha1.equals(actualSha1)) {
                throw new IOException("A verificação SHA-1 do cliente falhou; o arquivo incompleto foi descartado.");
            }
            writeBytesAtomically(metadataTemp, metadataBytes);
            replaceFile(metadataTemp, metadataFile);
            replaceFile(clientTemp, clientFile);

            JSONObject result = new JSONObject();
            try {
                result.put("versionId", id);
                result.put("clientBytes", expectedSize);
                result.put("clientSha1", actualSha1);
                result.put("metadataBytes", metadataBytes.length);
                result.put("metadataSha1", sha1(metadataBytes));
                result.put("relativePath", "versions/" + id + "/" + id);
                result.put("status", "client_artifacts_staged");
            } catch (org.json.JSONException e) {
                throw new IOException("Não foi possível preparar o resumo da instalação.", e);
            }
            instances.updateArtifactStatus(instanceName, result);
            finished = true;
            return result;
        } catch (org.json.JSONException e) {
            throw new IOException("O manifesto de versão oficial não é um JSON válido.", e);
        } finally {
            if (!finished) {
                deleteTemp(metadataTemp);
                deleteTemp(clientTemp);
            }
        }
    }

    private byte[] downloadBytes(String address, int limit) throws IOException {
        HttpURLConnection connection = open(address);
        try {
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) throw new IOException("Download de metadados respondeu HTTP " + status + ".");
            int declared = connection.getContentLength();
            if (declared > limit) throw new IOException("Os metadados excedem o limite de segurança.");
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (output.size() + count > limit) throw new IOException("Os metadados excedem o limite de segurança.");
                    output.write(buffer, 0, count);
                }
                if (output.size() == 0) throw new IOException("O servidor retornou metadados vazios.");
                return output.toByteArray();
            }
        } finally {
            connection.disconnect();
        }
    }

    private void downloadToFile(String address, File target, long expectedSize) throws IOException {
        HttpURLConnection connection = open(address);
        boolean success = false;
        try {
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) throw new IOException("Download do cliente respondeu HTTP " + status + ".");
            long declared = connection.getContentLengthLong();
            if (declared >= 0 && declared != expectedSize) throw new IOException("O tamanho recebido não corresponde ao manifesto oficial.");
            long total = 0;
            try (InputStream input = connection.getInputStream(); FileOutputStream output = new FileOutputStream(target)) {
                byte[] buffer = new byte[64 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > expectedSize || total > MAX_CLIENT_BYTES) {
                        throw new IOException("O download ultrapassou o tamanho esperado.");
                    }
                    output.write(buffer, 0, count);
                }
                output.flush();
                output.getFD().sync();
            }
            if (total != expectedSize) throw new IOException("Download incompleto: esperado " + expectedSize + " bytes, recebido " + total + ".");
            success = true;
        } finally {
            connection.disconnect();
            if (!success) target.delete();
        }
    }

    private HttpURLConnection open(String address) throws IOException {
        validateHttpsUrl(address, false);
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept", "*/*");
        connection.setRequestProperty("User-Agent", "MikaelLauncherPlus/0.1.0");
        int status = connection.getResponseCode();
        if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
            String location = connection.getHeaderField("Location");
            connection.disconnect();
            if (location == null || location.trim().isEmpty()) throw new IOException("Redirecionamento oficial sem destino.");
            URL redirected = new URL(new URL(address), location);
            validateHttpsUrl(redirected.toString(), false);
            HttpURLConnection next = (HttpURLConnection) redirected.openConnection();
            next.setConnectTimeout(CONNECT_TIMEOUT_MS);
            next.setReadTimeout(READ_TIMEOUT_MS);
            next.setInstanceFollowRedirects(false);
            next.setRequestProperty("Accept", "*/*");
            next.setRequestProperty("User-Agent", "MikaelLauncherPlus/0.1.0");
            return next;
        }
        return connection;
    }

    private void validateHttpsUrl(String address, boolean metadata) throws IOException {
        try {
            URL url = new URL(address);
            String host = url.getHost().toLowerCase(Locale.ROOT);
            boolean allowed = metadata
                    ? host.equals("piston-meta.mojang.com")
                    : host.equals("piston-meta.mojang.com") || host.equals("piston-data.mojang.com")
                        || host.equals("launcher.mojang.com") || host.equals("resources.download.minecraft.net");
            if (!"https".equalsIgnoreCase(url.getProtocol()) || !allowed || url.getUserInfo() != null) {
                throw new IOException("URL fora dos domínios HTTPS oficiais permitidos.");
            }
        } catch (java.net.MalformedURLException e) {
            throw new IOException("URL oficial inválida.", e);
        }
    }

    private String validateVersionId(String value) throws IOException {
        if (value == null || !value.matches("[A-Za-z0-9._-]{1,100}") || ".".equals(value) || "..".equals(value)) {
            throw new IOException("Identificador de versão inválido.");
        }
        return value;
    }

    private File safeChild(File parent, String name) throws IOException {
        File base = parent.getCanonicalFile();
        File child = new File(base, name).getCanonicalFile();
        if (!child.getPath().startsWith(base.getPath() + File.separator)) {
            throw new IOException("Caminho de instalação inválido.");
        }
        return child;
    }

    private void ensureDirectory(File directory) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs() && !directory.isDirectory()) {
            throw new IOException("Não foi possível criar a pasta " + directory.getName() + ".");
        }
    }

    private void writeBytesAtomically(File temporary, byte[] bytes) throws IOException {
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            output.write(bytes);
            output.flush();
            output.getFD().sync();
        }
    }

    private void replaceFile(File source, File target) throws IOException {
        if (target.exists() && !target.delete()) throw new IOException("Não foi possível substituir " + target.getName() + ".");
        if (!source.renameTo(target)) throw new IOException("Não foi possível finalizar " + target.getName() + ".");
    }

    private void deleteTemp(File file) throws IOException {
        if (file.exists() && !file.delete()) throw new IOException("Não foi possível limpar um download temporário.");
    }

    private String sha1(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            try (InputStream input = new FileInputStream(file)) {
                byte[] buffer = new byte[64 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
            }
            return hex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-1 não está disponível.", e);
        }
    }

    private String sha1(byte[] bytes) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            return hex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-1 não está disponível.", e);
        }
    }

    private String hex(byte[] bytes) {
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte item : bytes) value.append(String.format(Locale.ROOT, "%02x", item & 0xff));
        return value.toString();
    }
}
