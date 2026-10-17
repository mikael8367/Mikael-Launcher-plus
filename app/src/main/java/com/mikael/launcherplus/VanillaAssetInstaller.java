package com.mikael.launcherplus;

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
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CancellationException;

/**
 * Downloads and verifies the official Minecraft asset index and its content-addressed assets.
 * This does not install game libraries or a Java runtime and does not make an instance playable.
 */
public final class VanillaAssetInstaller {
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 25000;
    private static final int MAX_PROFILE_BYTES = 256 * 1024;
    private static final int MAX_VERSION_JSON_BYTES = 8 * 1024 * 1024;
    private static final int MAX_ASSET_INDEX_BYTES = 16 * 1024 * 1024;
    private static final int MAX_LOGICAL_ENTRIES = 250000;
    private static final long MAX_SINGLE_ASSET_BYTES = 128L * 1024L * 1024L;
    private static final long MAX_TOTAL_OBJECT_BYTES = 4L * 1024L * 1024L * 1024L;
    private static final long DISK_RESERVE_BYTES = 16L * 1024L * 1024L;

    private final InstanceManager instanceManager;

    public interface ProgressListener {
        void onProgress(String phase, long completed, long total);
        boolean isCancelled();
    }

    public static final class DownloadCancelledException extends IOException {
        public DownloadCancelledException() {
            super("Download de assets cancelado. Os arquivos completos e verificados foram mantidos para retomar.");
        }
    }

    private static final class AssetObject {
        final String hash;
        final long size;

        AssetObject(String hash, long size) {
            this.hash = hash;
            this.size = size;
        }
    }

    private static final class LogicalAsset {
        final String path;
        final String hash;
        final long size;

        LogicalAsset(String path, String hash, long size) {
            this.path = path;
            this.hash = hash;
            this.size = size;
        }
    }

    public VanillaAssetInstaller(InstanceManager manager) {
        if (manager == null) throw new IllegalArgumentException("Gerenciador de instâncias obrigatório.");
        instanceManager = manager;
    }

    public JSONObject downloadAssets(String instanceName, ProgressListener listener)
            throws IOException {
        final File instanceDirectory;
        final JSONObject profile;
        try {
            instanceDirectory = instanceManager.getInstanceDirectory(instanceName);
            profile = readJsonObject(safeChild(instanceDirectory, "instance.json"), MAX_PROFILE_BYTES);
        } catch (JSONException e) {
            throw new IOException("Não foi possível ler o perfil ou o índice de instâncias.", e);
        }

        String profileVersion = profile.optString("version", "");
        String stagedVersion = profile.optString("stagedVersion", "");
        String loader = profile.optString("loader", "Vanilla");
        if (!"Vanilla".equals(loader)) {
            throw new IOException("Esta etapa baixa assets oficiais somente para instâncias Vanilla.");
        }
        if (!profileVersion.matches("[A-Za-z0-9._-]{1,100}")
                || !profileVersion.equals(stagedVersion)
                || !"client_artifacts_staged".equals(profile.optString("installStatus", ""))) {
            throw new IOException("Baixe e verifique primeiro os arquivos do cliente para esta instância.");
        }

        File versionDirectory = safeChild(safeChild(instanceDirectory, "versions"), profileVersion);
        File versionJsonFile = safeChild(versionDirectory, profileVersion + ".json");
        JSONObject versionJson;
        try {
            versionJson = readJsonObject(versionJsonFile, MAX_VERSION_JSON_BYTES);
        } catch (JSONException e) {
            throw new IOException("O JSON da versão está inválido.", e);
        }
        if (!profileVersion.equals(versionJson.optString("id", ""))) {
            throw new IOException("O JSON local não corresponde à versão da instância.");
        }
        JSONObject assetIndex = versionJson.optJSONObject("assetIndex");
        if (assetIndex == null) throw new IOException("Esta versão não declarou um índice oficial de assets.");

        String indexId = assetIndex.optString("id", "");
        String indexUrl = assetIndex.optString("url", "");
        String expectedIndexSha1 = assetIndex.optString("sha1", "").toLowerCase(Locale.ROOT);
        long expectedIndexSize = assetIndex.optLong("size", -1L);
        validateSafeId(indexId);
        validateHttpsUrl(indexUrl, true);
        if (!expectedIndexSha1.matches("[0-9a-f]{40}")
                || expectedIndexSize <= 0 || expectedIndexSize > MAX_ASSET_INDEX_BYTES) {
            throw new IOException("O manifesto declarou hash ou tamanho inválido para o índice de assets.");
        }

        File assetsDirectory = safeChild(instanceDirectory, "assets");
        File indexesDirectory = safeChild(assetsDirectory, "indexes");
        File objectsDirectory = safeChild(assetsDirectory, "objects");
        ensureDirectory(assetsDirectory);
        ensureDirectory(indexesDirectory);
        ensureDirectory(objectsDirectory);

        File indexFile = safeChild(indexesDirectory, indexId + ".json");
        File indexTemp = safeChild(indexesDirectory, indexId + ".json.part");
        boolean indexWasStaged = false;
        LinkedHashMap<String, AssetObject> uniqueObjects = new LinkedHashMap<>();
        List<LogicalAsset> logicalAssets = new ArrayList<>();
        long totalObjectBytes = 0;
        long verifiedObjectCount = 0;
        long verifiedLogicalCount = 0;
        long expectedMappedBytes = 0;
        long bytesForMissingObjects = 0;
        long bytesForMissingMappings = 0;
        boolean virtual = false;
        boolean mapToResources = false;

        try {
            checkCancelled(listener);
            byte[] indexBytes;
            if (indexFile.isFile() && indexFile.length() == expectedIndexSize
                    && expectedIndexSha1.equals(sha1(indexFile))) {
                indexBytes = readBytes(indexFile, MAX_ASSET_INDEX_BYTES);
                if (!expectedIndexSha1.equals(sha1(indexBytes))) {
                    throw new IOException("O índice local de assets não passou na verificação SHA-1.");
                }
            } else {
                deleteTemp(indexTemp);
                indexBytes = downloadBytes(indexUrl, expectedIndexSize, MAX_ASSET_INDEX_BYTES,
                        expectedIndexSha1, listener);
                writeBytes(indexTemp, indexBytes);
                replaceFile(indexTemp, indexFile);
            }

            JSONObject index;
            try {
                index = new JSONObject(new String(indexBytes, StandardCharsets.UTF_8));
            } catch (JSONException e) {
                throw new IOException("O índice oficial de assets não é um JSON válido.", e);
            }
            if (!indexId.equals(index.optString("name", indexId))) {
                throw new IOException("O identificador interno do índice de assets não corresponde ao manifesto.");
            }
            indexWasStaged = true;
            virtual = index.optBoolean("virtual", false);
            mapToResources = index.optBoolean("map_to_resources", false);
            JSONObject objects = index.optJSONObject("objects");
            if (objects == null || objects.length() == 0 || objects.length() > MAX_LOGICAL_ENTRIES) {
                throw new IOException("O índice de assets está vazio ou excede o limite de entradas.");
            }

            Enumeration<String> keys = objects.keys();
            while (keys.hasMoreElements()) {
                checkCancelled(listener);
                String logicalPath = keys.nextElement();
                validateLogicalPath(logicalPath);
                JSONObject record = objects.optJSONObject(logicalPath);
                if (record == null) throw new IOException("Uma entrada do índice de assets está malformada.");
                String hash = record.optString("hash", "").toLowerCase(Locale.ROOT);
                long size = record.optLong("size", -1L);
                if (!hash.matches("[0-9a-f]{40}") || size < 0 || size > MAX_SINGLE_ASSET_BYTES) {
                    throw new IOException("Uma entrada de asset declarou hash ou tamanho inválido.");
                }
                AssetObject previous = uniqueObjects.get(hash);
                if (previous != null && previous.size != size) {
                    throw new IOException("O mesmo hash de asset foi declarado com tamanhos diferentes.");
                }
                if (previous == null) {
                    if (totalObjectBytes > MAX_TOTAL_OBJECT_BYTES - size) {
                        throw new IOException("O tamanho total dos assets excede o limite de 4 GiB.");
                    }
                    uniqueObjects.put(hash, new AssetObject(hash, size));
                    totalObjectBytes += size;
                }
                logicalAssets.add(new LogicalAsset(logicalPath, hash, size));
            }

            File mappingRootVirtual = safeChild(assetsDirectory, "virtual" + File.separator + indexId);
            File mappingRootResources = safeChild(instanceDirectory, "resources");
            int mappingCount = (virtual ? 1 : 0) + (mapToResources ? 1 : 0);
            long totalWork = uniqueObjects.size() + (long) logicalAssets.size() * mappingCount;
            long doneWork = 0;

            for (AssetObject asset : uniqueObjects.values()) {
                checkCancelled(listener);
                File target = objectFile(objectsDirectory, asset.hash);
                ensureDirectory(target.getParentFile());
                if (!isVerifiedFile(target, asset.size, asset.hash)) {
                    if (target.exists() && !target.delete()) {
                        throw new IOException("Não foi possível substituir um asset local inválido.");
                    }
                    bytesForMissingObjects = safeAdd(bytesForMissingObjects, asset.size);
                } else {
                    verifiedObjectCount++;
                }
                doneWork++;
                reportProgress(listener, "Verificando assets existentes", doneWork, totalWork);
            }

            if (virtual || mapToResources) {
                for (LogicalAsset logical : logicalAssets) {
                    checkCancelled(listener);
                    File source = objectFile(objectsDirectory, logical.hash);
                    if (virtual) {
                        File target = logicalChild(mappingRootVirtual, logical.path);
                        if (!isVerifiedFile(target, logical.size, logical.hash)) {
                            bytesForMissingMappings = safeAdd(bytesForMissingMappings, logical.size);
                        } else {
                            verifiedLogicalCount++;
                        }
                        doneWork++;
                        reportProgress(listener, "Verificando mapeamento legado", doneWork, totalWork);
                    }
                    if (mapToResources) {
                        File target = logicalChild(mappingRootResources, logical.path);
                        if (!isVerifiedFile(target, logical.size, logical.hash)) {
                            bytesForMissingMappings = safeAdd(bytesForMissingMappings, logical.size);
                        } else {
                            verifiedLogicalCount++;
                        }
                        doneWork++;
                        reportProgress(listener, "Verificando mapeamento de recursos", doneWork, totalWork);
                    }
                }
            }

            long requiredSpace = safeAdd(bytesForMissingObjects, bytesForMissingMappings);
            long usableSpace = instanceDirectory.getUsableSpace();
            if (usableSpace > 0 && requiredSpace > Math.max(0L, usableSpace - DISK_RESERVE_BYTES)) {
                throw new IOException("Espaço insuficiente: faltam até "
                        + (requiredSpace / (1024L * 1024L)) + " MiB para assets, além de uma reserva de segurança de 16 MiB.");
            }

            // Partial progress after this point counts only completed work, not the preflight scan.
            verifiedObjectCount = 0;
            verifiedLogicalCount = 0;
            long workCompleted = 0;
            for (AssetObject asset : uniqueObjects.values()) {
                checkCancelled(listener);
                File target = objectFile(objectsDirectory, asset.hash);
                if (!isVerifiedFile(target, asset.size, asset.hash)) {
                    downloadObject(asset, target, listener);
                }
                verifiedObjectCount++;
                workCompleted++;
                reportProgress(listener, "Baixando/verificando assets", workCompleted, totalWork);
            }

            for (LogicalAsset logical : logicalAssets) {
                checkCancelled(listener);
                File source = objectFile(objectsDirectory, logical.hash);
                if (virtual) {
                    File target = logicalChild(mappingRootVirtual, logical.path);
                    if (!isVerifiedFile(target, logical.size, logical.hash)) {
                        copyVerifiedAsset(source, target, logical.size, logical.hash, listener);
                    }
                    verifiedLogicalCount++;
                    workCompleted++;
                    reportProgress(listener, "Preparando assets legados", workCompleted, totalWork);
                }
                if (mapToResources) {
                    File target = logicalChild(mappingRootResources, logical.path);
                    if (!isVerifiedFile(target, logical.size, logical.hash)) {
                        copyVerifiedAsset(source, target, logical.size, logical.hash, listener);
                    }
                    verifiedLogicalCount++;
                    workCompleted++;
                    reportProgress(listener, "Preparando recursos legados", workCompleted, totalWork);
                }
            }

            JSONObject result = new JSONObject();
            result.put("status", "assets_staged");
            result.put("assetIndexId", indexId);
            result.put("assetIndexSha1", expectedIndexSha1);
            result.put("assetIndexRelativePath", "assets/indexes/" + indexId + ".json");
            result.put("assetObjectCount", uniqueObjects.size());
            result.put("assetObjectsVerified", uniqueObjects.size());
            result.put("assetLogicalPathCount", logicalAssets.size());
            result.put("assetLogicalPathsVerified", logicalAssets.size() * mappingCount);
            result.put("assetTotalBytes", totalObjectBytes);
            result.put("assetVirtual", virtual);
            result.put("assetMapToResources", mapToResources);
            instanceManager.updateAssetStatus(instanceName, result);
            reportProgress(listener, "Assets concluídos e verificados", totalWork, totalWork);
            return result;
        } catch (DownloadCancelledException e) {
            savePartialStatus(instanceName, indexWasStaged, indexId, expectedIndexSha1,
                    uniqueObjects.size(), verifiedObjectCount, logicalAssets.size(), verifiedLogicalCount,
                    totalObjectBytes, virtual, mapToResources);
            throw e;
        } catch (IOException e) {
            if (indexWasStaged) {
                savePartialStatus(instanceName, true, indexId, expectedIndexSha1,
                        uniqueObjects.size(), verifiedObjectCount, logicalAssets.size(), verifiedLogicalCount,
                        totalObjectBytes, virtual, mapToResources);
            }
            throw e;
        } catch (JSONException e) {
            throw new IOException("Não foi possível salvar o estado dos assets da instância.", e);
        } finally {
            deleteTempQuietly(indexTemp);
        }
    }

    private void savePartialStatus(String instanceName, boolean indexWasStaged, String indexId, String indexSha1,
                                   long objectCount, long verifiedObjects, long logicalCount, long verifiedLogical,
                                   long totalBytes, boolean virtual, boolean mapToResources) {
        if (!indexWasStaged) return;
        try {
            JSONObject result = new JSONObject();
            result.put("status", "assets_partial");
            result.put("assetIndexId", indexId);
            result.put("assetIndexSha1", indexSha1);
            result.put("assetIndexRelativePath", "assets/indexes/" + indexId + ".json");
            result.put("assetObjectCount", objectCount);
            result.put("assetObjectsVerified", Math.min(objectCount, verifiedObjects));
            result.put("assetLogicalPathCount", logicalCount);
            result.put("assetLogicalPathsVerified", Math.min(logicalCount * ((virtual ? 1 : 0) + (mapToResources ? 1 : 0)), verifiedLogical));
            result.put("assetTotalBytes", totalBytes);
            result.put("assetVirtual", virtual);
            result.put("assetMapToResources", mapToResources);
            instanceManager.updateAssetStatus(instanceName, result);
        } catch (Exception ignored) {
            // Preserve the original cancellation or download error.
        }
    }

    private void downloadObject(AssetObject asset, File target, ProgressListener listener)
            throws IOException {
        ensureDirectory(target.getParentFile());
        File temporary = new File(target.getParentFile(), target.getName() + ".part");
        deleteTemp(temporary);
        String address = "https://resources.download.minecraft.net/"
                + asset.hash.substring(0, 2) + "/" + asset.hash;
        validateHttpsUrl(address, false);
        HttpURLConnection connection = open(address);
        boolean complete = false;
        try {
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("Download de asset respondeu HTTP " + status + ".");
            }
            long declared = connection.getContentLengthLong();
            if (declared >= 0 && declared != asset.size) {
                throw new IOException("Tamanho anunciado do asset não corresponde ao índice.");
            }
            long received = 0;
            MessageDigest digest = newSha1();
            try (InputStream input = connection.getInputStream();
                 FileOutputStream output = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[64 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    checkCancelled(listener);
                    received += count;
                    if (received > asset.size || received > MAX_SINGLE_ASSET_BYTES) {
                        throw new IOException("O asset ultrapassou o tamanho esperado.");
                    }
                    digest.update(buffer, 0, count);
                    output.write(buffer, 0, count);
                }
                output.flush();
                output.getFD().sync();
            }
            if (received != asset.size || !asset.hash.equals(toHex(digest.digest()))) {
                throw new IOException("O asset baixado falhou na verificação de tamanho ou SHA-1.");
            }
            replaceFile(temporary, target);
            complete = true;
        } finally {
            connection.disconnect();
            if (!complete) deleteTempQuietly(temporary);
        }
    }

    private void copyVerifiedAsset(File source, File target, long expectedSize, String expectedSha1,
                                   ProgressListener listener) throws IOException {
        if (!isVerifiedFile(source, expectedSize, expectedSha1)) {
            throw new IOException("Asset de origem não passou na verificação SHA-1.");
        }
        File parent = target.getParentFile();
        ensureDirectory(parent);
        File temporary = new File(parent, target.getName() + ".part");
        deleteTemp(temporary);
        boolean complete = false;
        long copied = 0;
        MessageDigest digest = newSha1();
        try (InputStream input = new FileInputStream(source);
             FileOutputStream output = new FileOutputStream(temporary)) {
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                checkCancelled(listener);
                copied += count;
                if (copied > expectedSize) throw new IOException("O mapeamento de asset ultrapassou o tamanho esperado.");
                digest.update(buffer, 0, count);
                output.write(buffer, 0, count);
            }
            output.flush();
            output.getFD().sync();
            if (copied != expectedSize || !expectedSha1.equals(toHex(digest.digest()))) {
                throw new IOException("O asset mapeado falhou na verificação SHA-1.");
            }
            replaceFile(temporary, target);
            complete = true;
        } finally {
            if (!complete) deleteTempQuietly(temporary);
        }
    }

    private byte[] downloadBytes(String address, long expectedSize, int maxBytes, String expectedSha1,
                                 ProgressListener listener) throws IOException {
        validateHttpsUrl(address, true);
        HttpURLConnection connection = open(address);
        try {
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("Download do índice de assets respondeu HTTP " + status + ".");
            }
            long declared = connection.getContentLengthLong();
            if (declared >= 0 && (declared > maxBytes || (expectedSize > 0 && declared != expectedSize))) {
                throw new IOException("Tamanho anunciado do índice de assets é inválido.");
            }
            try (InputStream input = connection.getInputStream();
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    checkCancelled(listener);
                    if (output.size() + count > maxBytes) {
                        throw new IOException("O índice de assets excede o limite de segurança.");
                    }
                    output.write(buffer, 0, count);
                }
                byte[] bytes = output.toByteArray();
                if (bytes.length == 0 || (expectedSize > 0 && bytes.length != expectedSize)
                        || !expectedSha1.equals(sha1(bytes))) {
                    throw new IOException("O índice de assets falhou na verificação de tamanho ou SHA-1.");
                }
                return bytes;
            }
        } finally {
            connection.disconnect();
        }
    }

    private HttpURLConnection open(String address) throws IOException {
        String current = address;
        for (int attempt = 0; attempt < 2; attempt++) {
            validateHttpsUrl(current, false);
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
                    throw new IOException("Redirecionamento de asset inválido ou em excesso.");
                }
                current = new URL(new URL(current), location).toString();
                continue;
            }
            return connection;
        }
        throw new IOException("Não foi possível abrir a URL oficial do asset.");
    }

    private void validateHttpsUrl(String address, boolean indexUrl) throws IOException {
        try {
            URL url = new URL(address);
            String host = url.getHost().toLowerCase(Locale.ROOT);
            boolean allowed = indexUrl
                    ? host.equals("piston-meta.mojang.com") || host.equals("launchermeta.mojang.com")
                        || host.equals("launcher.mojang.com")
                    : host.equals("piston-meta.mojang.com") || host.equals("launchermeta.mojang.com")
                        || host.equals("launcher.mojang.com") || host.equals("resources.download.minecraft.net");
            if (!"https".equalsIgnoreCase(url.getProtocol()) || !allowed
                    || url.getUserInfo() != null || url.getPort() != -1 && url.getPort() != 443) {
                throw new IOException("URL fora dos domínios HTTPS oficiais permitidos.");
            }
        } catch (java.net.MalformedURLException e) {
            throw new IOException("URL oficial de asset inválida.", e);
        }
    }

    private File objectFile(File objectsDirectory, String hash) throws IOException {
        return safeChild(objectsDirectory, hash.substring(0, 2) + File.separator + hash);
    }

    private File logicalChild(File root, String logicalPath) throws IOException {
        validateLogicalPath(logicalPath);
        return safeChild(root, logicalPath.replace('/', File.separatorChar));
    }

    private void validateLogicalPath(String path) throws IOException {
        if (path == null || path.isEmpty() || path.length() > 1024
                || path.startsWith("/") || path.contains("\\") || path.contains(":")) {
            throw new IOException("Caminho lógico de asset inválido.");
        }
        String[] parts = path.split("/", -1);
        for (String part : parts) {
            if (part.isEmpty() || ".".equals(part) || "..".equals(part)) {
                throw new IOException("O caminho do asset contém um segmento inválido.");
            }
            for (int i = 0; i < part.length(); i++) {
                if (Character.isISOControl(part.charAt(i))) {
                    throw new IOException("O caminho do asset contém caracteres de controle.");
                }
            }
        }
    }

    private void validateSafeId(String value) throws IOException {
        if (value == null || !value.matches("[A-Za-z0-9._-]{1,100}")
                || ".".equals(value) || "..".equals(value)) {
            throw new IOException("Identificador do índice de assets inválido.");
        }
    }

    private boolean isVerifiedFile(File file, long expectedSize, String expectedSha1) throws IOException {
        return file.isFile() && file.length() == expectedSize && expectedSha1.equals(sha1(file));
    }

    private byte[] readBytes(File file, int maxBytes) throws IOException {
        if (!file.isFile() || file.length() <= 0 || file.length() > maxBytes) {
            throw new IOException("Arquivo local ausente, vazio ou acima do limite.");
        }
        try (InputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (output.size() + count > maxBytes) throw new IOException("Arquivo local acima do limite.");
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }

    private JSONObject readJsonObject(File file, int maxBytes) throws IOException, JSONException {
        return new JSONObject(new String(readBytes(file, maxBytes), StandardCharsets.UTF_8));
    }

    private File safeChild(File parent, String childPath) throws IOException {
        File base = parent.getCanonicalFile();
        File child = new File(base, childPath).getCanonicalFile();
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

    private void writeBytes(File target, byte[] bytes) throws IOException {
        try (FileOutputStream output = new FileOutputStream(target)) {
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
        if (file.exists() && !file.delete()) throw new IOException("Não foi possível limpar arquivo temporário.");
    }

    private void deleteTempQuietly(File file) {
        if (file.exists()) file.delete();
    }

    private void checkCancelled(ProgressListener listener) throws DownloadCancelledException {
        if (Thread.currentThread().isInterrupted()
                || listener != null && listener.isCancelled()) {
            throw new DownloadCancelledException();
        }
    }

    private void reportProgress(ProgressListener listener, String phase, long complete, long total) {
        if (listener == null) return;
        long interval = Math.max(1L, total / 100L);
        if (complete == 0 || complete >= total || complete % interval == 0) {
            listener.onProgress(phase, complete, total);
        }
    }

    private long safeAdd(long first, long second) throws IOException {
        if (second > Long.MAX_VALUE - first) throw new IOException("Tamanho total de assets inválido.");
        return first + second;
    }

    private String sha1(File file) throws IOException {
        try (InputStream input = new FileInputStream(file)) {
            MessageDigest digest = newSha1();
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
            return toHex(digest.digest());
        }
    }

    private String sha1(byte[] bytes) throws IOException {
        MessageDigest digest = newSha1();
        return toHex(digest.digest(bytes));
    }

    private MessageDigest newSha1() throws IOException {
        try {
            return MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-1 não está disponível nesta instalação de Java.", e);
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte item : bytes) value.append(String.format(Locale.ROOT, "%02x", item & 0xff));
        return value.toString();
    }
}
