package com.mikael.launcherplus;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Creates and manages launcher profile directories inside the app-private sandbox.
 *
 * These are launcher profiles only. This class does not download Minecraft, install
 * a loader, authenticate an account, or make a profile playable.
 */
public final class InstanceManager {
    private static final String INDEX_NAME = "instances-index.json";
    private final File rootDirectory;
    private final File indexFile;

    public InstanceManager(Context context) throws IOException {
        File appFiles = context.getFilesDir().getCanonicalFile();
        rootDirectory = new File(appFiles, "instances").getCanonicalFile();
        if (!rootDirectory.exists() && !rootDirectory.mkdirs()) {
            throw new IOException("Não foi possível criar a pasta privada de instâncias.");
        }
        if (!rootDirectory.isDirectory()) {
            throw new IOException("O caminho privado de instâncias não é uma pasta.");
        }
        indexFile = new File(rootDirectory, INDEX_NAME);
    }

    public synchronized JSONArray getInstances() throws IOException, JSONException {
        if (!indexFile.isFile()) return new JSONArray();
        byte[] bytes;
        try (FileInputStream input = new FileInputStream(indexFile);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
                if (output.size() > 1024 * 1024) {
                    throw new IOException("O índice de instâncias excedeu o limite de segurança.");
                }
            }
            bytes = output.toByteArray();
        }
        return new JSONArray(new String(bytes, StandardCharsets.UTF_8));
    }

    public synchronized File getInstanceDirectory(String name) throws IOException, JSONException {
        String safeName = validateName(name);
        JSONArray instances = getInstances();
        boolean found = false;
        for (int i = 0; i < instances.length(); i++) {
            if (safeName.equals(instances.getJSONObject(i).optString("name", ""))) {
                found = true;
                break;
            }
        }
        if (!found) throw new IllegalArgumentException("A instância selecionada não existe mais.");
        File directory = profileDirectory(safeName);
        if (!directory.isDirectory()) throw new IOException("A pasta da instância não existe.");
        return directory;
    }

    public synchronized JSONObject createInstance(String name, String version, String loader)
            throws IOException, JSONException {
        String safeName = validateName(name);
        String safeVersion = version == null ? "" : version.trim();
        String safeLoader = loader == null ? "" : loader.trim();
        if (safeVersion.isEmpty() || safeVersion.length() > 100
                || !safeVersion.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException("Escolha uma versão válida do manifesto primeiro.");
        }
        if (!("Vanilla".equals(safeLoader) || "Forge".equals(safeLoader)
                || "Fabric".equals(safeLoader) || "OptiFine".equals(safeLoader)
                || "Forge + OptiFine".equals(safeLoader))) {
            throw new IllegalArgumentException("Tipo de instalação não reconhecido.");
        }

        JSONArray instances = getInstances();
        for (int i = 0; i < instances.length(); i++) {
            String existing = instances.getJSONObject(i).optString("name", "");
            if (existing.equalsIgnoreCase(safeName)) {
                throw new IllegalArgumentException("Já existe uma instância com esse nome.");
            }
        }

        File profileDirectory = profileDirectory(safeName);
        if (profileDirectory.exists()) {
            throw new IllegalArgumentException("Já existe uma pasta com esse nome. Escolha outro.");
        }
        if (!profileDirectory.mkdirs()) {
            throw new IOException("Não foi possível criar a pasta da instância.");
        }

        boolean completed = false;
        try {
            makeDirectory(profileDirectory, "mods");
            makeDirectory(profileDirectory, "resourcepacks");
            makeDirectory(profileDirectory, "shaderpacks");
            makeDirectory(profileDirectory, "saves");
            makeDirectory(profileDirectory, "config");
            makeDirectory(profileDirectory, "logs");

            JSONObject record = new JSONObject();
            record.put("name", safeName);
            record.put("version", safeVersion);
            record.put("loader", safeLoader);
            record.put("createdAt", System.currentTimeMillis());
            record.put("relativePath", "instances/" + safeName);
            record.put("status", "profile_only");
            record.put("gameInstalled", false);
            record.put("loaderInstalled", false);
            record.put("accountLinked", false);

            writeJsonAtomically(new File(profileDirectory, "instance.json"), record);
            instances.put(record);
            writeJsonAtomically(indexFile, instances);
            completed = true;
            return record;
        } finally {
            if (!completed) deleteTree(profileDirectory);
        }
    }

    /**
     * Records verified Mojang client artifacts without claiming the instance is playable.
     * The game engine is not yet configured to use this directory.
     */
    public synchronized void updateArtifactStatus(String name, JSONObject artifact)
            throws IOException, JSONException {
        String safeName = validateName(name);
        if (artifact == null || !"client_artifacts_staged".equals(artifact.optString("status", ""))) {
            throw new IllegalArgumentException("Estado dos arquivos oficiais inválido.");
        }
        JSONArray instances = getInstances();
        JSONObject matched = null;
        for (int i = 0; i < instances.length(); i++) {
            JSONObject record = instances.getJSONObject(i);
            if (safeName.equals(record.optString("name", ""))) {
                matched = record;
                record.put("installStatus", "client_artifacts_staged");
                record.put("gameInstalled", false);
                record.put("loaderInstalled", false);
                record.put("stagedVersion", artifact.optString("versionId", ""));
                record.put("clientSha1", artifact.optString("clientSha1", ""));
                record.put("metadataSha1", artifact.optString("metadataSha1", ""));
                record.put("clientBytes", artifact.optLong("clientBytes", 0L));
                record.put("stagedRelativePath", artifact.optString("relativePath", ""));
                record.put("artifactsStagedAt", System.currentTimeMillis());
                break;
            }
        }
        if (matched == null) throw new IllegalArgumentException("A instância selecionada não existe mais.");
        JSONObject profile = new JSONObject();
        File profileFile = new File(getInstanceDirectory(safeName), "instance.json");
        try (FileInputStream input = new FileInputStream(profileFile);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
                if (output.size() > 256 * 1024) throw new IOException("Os metadados da instância excedem o limite.");
            }
            profile = new JSONObject(new String(output.toByteArray(), StandardCharsets.UTF_8));
        }
        profile.put("installStatus", "client_artifacts_staged");
        profile.put("gameInstalled", false);
        profile.put("loaderInstalled", false);
        profile.put("stagedVersion", artifact.optString("versionId", ""));
        profile.put("clientSha1", artifact.optString("clientSha1", ""));
        profile.put("metadataSha1", artifact.optString("metadataSha1", ""));
        profile.put("clientBytes", artifact.optLong("clientBytes", 0L));
        profile.put("stagedRelativePath", artifact.optString("relativePath", ""));
        profile.put("artifactsStagedAt", System.currentTimeMillis());
        writeJsonAtomically(profileFile, profile);
        writeJsonAtomically(indexFile, instances);
    }

    public synchronized void updateAssetStatus(String name, JSONObject asset)
            throws IOException, JSONException {
        String safeName = validateName(name);
        String status = asset == null ? "" : asset.optString("status", "");
        if (!"assets_staged".equals(status) && !"assets_partial".equals(status)) {
            throw new IllegalArgumentException("Estado de assets inválido.");
        }
        String indexId = asset.optString("assetIndexId", "");
        String indexSha1 = asset.optString("assetIndexSha1", "").toLowerCase(Locale.ROOT);
        String relativePath = asset.optString("assetIndexRelativePath", "");
        if (!indexId.matches("[A-Za-z0-9._-]{1,100}") || ".".equals(indexId) || "..".equals(indexId)
                || !indexSha1.matches("[0-9a-f]{40}")
                || !("assets/indexes/" + indexId + ".json").equals(relativePath)) {
            throw new IllegalArgumentException("Metadados do índice de assets inválidos.");
        }

        JSONArray instances = getInstances();
        JSONObject matched = null;
        for (int i = 0; i < instances.length(); i++) {
            JSONObject record = instances.getJSONObject(i);
            if (safeName.equals(record.optString("name", ""))) {
                matched = record;
                writeAssetFields(record, asset, status, indexId, indexSha1, relativePath);
                break;
            }
        }
        if (matched == null) throw new IllegalArgumentException("A instância selecionada não existe mais.");

        File profileFile = new File(getInstanceDirectory(safeName), "instance.json");
        JSONObject profile = readJsonFile(profileFile, 256 * 1024);
        writeAssetFields(profile, asset, status, indexId, indexSha1, relativePath);
        profile.put("gameInstalled", false);
        writeJsonAtomically(profileFile, profile);
        writeJsonAtomically(indexFile, instances);
    }

    private void writeAssetFields(JSONObject target, JSONObject asset, String status,
                                  String indexId, String indexSha1, String relativePath)
            throws JSONException {
        target.put("assetInstallStatus", status);
        target.put("assetsInstalled", "assets_staged".equals(status));
        target.put("assetIndexId", indexId);
        target.put("assetIndexSha1", indexSha1);
        target.put("assetIndexRelativePath", relativePath);
        target.put("assetObjectCount", Math.max(0L, asset.optLong("assetObjectCount", 0L)));
        target.put("assetObjectsVerified", Math.max(0L, asset.optLong("assetObjectsVerified", 0L)));
        target.put("assetLogicalPathCount", Math.max(0L, asset.optLong("assetLogicalPathCount", 0L)));
        target.put("assetLogicalPathsVerified", Math.max(0L, asset.optLong("assetLogicalPathsVerified", 0L)));
        target.put("assetTotalBytes", Math.max(0L, asset.optLong("assetTotalBytes", 0L)));
        target.put("assetVirtual", asset.optBoolean("assetVirtual", false));
        target.put("assetMapToResources", asset.optBoolean("assetMapToResources", false));
        target.put("assetsCheckedAt", System.currentTimeMillis());
    }

    private JSONObject readJsonFile(File file, int maxBytes) throws IOException, JSONException {
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
                if (output.size() > maxBytes) throw new IOException("Os metadados da instância excedem o limite.");
            }
            return new JSONObject(new String(output.toByteArray(), StandardCharsets.UTF_8));
        }
    }

    public synchronized boolean deleteInstance(String name) throws IOException, JSONException {
        String safeName = validateName(name);
        JSONArray current = getInstances();
        boolean indexed = false;
        JSONArray updated = new JSONArray();
        for (int i = 0; i < current.length(); i++) {
            JSONObject record = current.getJSONObject(i);
            String recordName = record.optString("name", "");
            if (recordName.equals(safeName)) {
                indexed = true;
            } else {
                updated.put(record);
            }
        }
        if (!indexed) return false;

        File directory = profileDirectory(safeName);
        if (directory.exists() && !deleteTree(directory)) {
            throw new IOException("Não foi possível remover a pasta da instância.");
        }
        writeJsonAtomically(indexFile, updated);
        return true;
    }

    private String validateName(String value) {
        String name = value == null ? "" : value.trim();
        if (name.isEmpty() || name.length() > 32
                || !name.matches("[A-Za-z0-9][A-Za-z0-9 _.-]{0,31}")
                || ".".equals(name) || "..".equals(name)) {
            throw new IllegalArgumentException(
                    "Use de 1 a 32 caracteres: letras, números, espaço, ponto, hífen ou sublinhado.");
        }
        return name;
    }

    private File profileDirectory(String name) throws IOException {
        File target = new File(rootDirectory, name).getCanonicalFile();
        String root = rootDirectory.getCanonicalPath() + File.separator;
        if (!target.getPath().startsWith(root) || target.equals(rootDirectory)) {
            throw new IOException("O caminho da instância é inválido.");
        }
        return target;
    }

    private void makeDirectory(File parent, String child) throws IOException {
        File directory = new File(parent, child);
        if (!directory.mkdir() && !directory.isDirectory()) {
            throw new IOException("Não foi possível criar a pasta " + child + ".");
        }
    }

    private void writeJsonAtomically(File target, JSONObject json) throws IOException {
        writeJsonAtomically(target, json.toString());
    }

    private void writeJsonAtomically(File target, JSONArray json) throws IOException {
        writeJsonAtomically(target, json.toString());
    }

    private void writeJsonAtomically(File target, String json) throws IOException {
        File temporary = new File(target.getParentFile(), target.getName() + ".tmp");
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            output.write(bytes);
            output.getFD().sync();
        }
        if (target.exists() && !target.delete()) {
            temporary.delete();
            throw new IOException("Não foi possível atualizar " + target.getName() + ".");
        }
        if (!temporary.renameTo(target)) {
            temporary.delete();
            throw new IOException("Não foi possível salvar " + target.getName() + ".");
        }
    }

    private boolean deleteTree(File target) {
        if (!target.exists()) return true;
        if (target.isDirectory()) {
            File[] children = target.listFiles();
            if (children == null) return false;
            for (File child : children) {
                if (!deleteTree(child)) return false;
            }
        }
        return target.delete();
    }
}
