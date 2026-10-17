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
