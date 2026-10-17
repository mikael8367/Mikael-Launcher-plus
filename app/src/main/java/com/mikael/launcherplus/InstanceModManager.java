package com.mikael.launcherplus;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/** Stages user-provided JAR files inside one private instance's mods directory. */
public final class InstanceModManager {
    private static final long MAX_MOD_BYTES = 250L * 1024L * 1024L;
    private final InstanceManager instanceManager;

    public InstanceModManager(InstanceManager manager) {
        if (manager == null) throw new IllegalArgumentException("Gerenciador de instâncias obrigatório.");
        instanceManager = manager;
    }

    public synchronized JSONObject importMod(String instanceName, String fileName, InputStream input)
            throws IOException {
        if (input == null) throw new IllegalArgumentException("O arquivo selecionado não pôde ser aberto.");
        String safeName = validateJarName(fileName);
        File modsDirectory = modsDirectory(instanceName);
        File target = safeChild(modsDirectory, safeName);
        if (target.exists()) {
            throw new IllegalArgumentException("Já existe um arquivo com esse nome nesta instância.");
        }

        File temporary = safeChild(modsDirectory, safeName + ".part");
        if (temporary.exists() && !temporary.delete()) {
            throw new IOException("Não foi possível limpar um arquivo temporário anterior.");
        }

        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException("SHA-256 não está disponível nesta instalação de Java.", impossible);
        }

        long total = 0;
        boolean complete = false;
        try (InputStream source = input;
             FileOutputStream output = new FileOutputStream(temporary)) {
            byte[] buffer = new byte[16 * 1024];
            int count;
            while ((count = source.read(buffer)) != -1) {
                total += count;
                if (total > MAX_MOD_BYTES) {
                    throw new IOException("O JAR ultrapassa o limite de 250 MB.");
                }
                digest.update(buffer, 0, count);
                output.write(buffer, 0, count);
            }
            if (total == 0) throw new IOException("O arquivo selecionado está vazio.");
            output.flush();
            output.getFD().sync();
            complete = true;
        } finally {
            if (!complete || !temporary.isFile()) temporary.delete();
        }

        String importedSha256 = toHex(digest.digest());
        File[] existingMods = modsDirectory.listFiles();
        if (existingMods == null) {
            temporary.delete();
            throw new IOException("Não foi possível verificar duplicados na pasta de mods.");
        }
        for (File existing : existingMods) {
            if (!existing.isFile() || !existing.getName().toLowerCase(Locale.ROOT).endsWith(".jar")) continue;
            if (existing.getName().equalsIgnoreCase(safeName)) {
                temporary.delete();
                throw new IllegalArgumentException("Já existe um arquivo com esse nome nesta instância.");
            }
            if (importedSha256.equals(sha256(existing))) {
                temporary.delete();
                throw new IllegalArgumentException("Este JAR já está nesta instância com outro nome (conteúdo SHA-256 idêntico).");
            }
        }
        if (target.exists()) {
            temporary.delete();
            throw new IllegalArgumentException("Já existe um arquivo com esse nome nesta instância.");
        }
        if (!temporary.renameTo(target)) {
            temporary.delete();
            throw new IOException("Não foi possível finalizar a cópia do JAR.");
        }

        JSONObject result = new JSONObject();
        try {
            result.put("name", safeName);
            result.put("sizeBytes", total);
            result.put("sha256", importedSha256);
        } catch (org.json.JSONException e) {
            target.delete();
            throw new IOException("Não foi possível preparar os metadados do arquivo.", e);
        }
        return result;
    }

    public synchronized JSONArray listMods(String instanceName) throws IOException {
        File mods = modsDirectory(instanceName);
        File[] files = mods.listFiles();
        JSONArray result = new JSONArray();
        if (files == null) throw new IOException("Não foi possível ler a pasta de mods.");
        for (File file : files) {
            if (!file.isFile() || !file.getName().toLowerCase(Locale.ROOT).endsWith(".jar")) continue;
            JSONObject item = new JSONObject();
            try {
                item.put("name", file.getName());
                item.put("sizeBytes", file.length());
                item.put("modifiedAt", file.lastModified());
                result.put(item);
            } catch (org.json.JSONException e) {
                throw new IOException("Não foi possível ler os detalhes do arquivo.", e);
            }
        }
        return result;
    }

    public synchronized boolean deleteMod(String instanceName, String fileName) throws IOException {
        String safeName = validateJarName(fileName);
        File mods = modsDirectory(instanceName);
        File[] files = mods.listFiles();
        if (files == null) throw new IOException("Não foi possível ler a pasta de mods.");
        for (File file : files) {
            if (file.isFile() && file.getName().equals(safeName)
                    && file.getName().toLowerCase(Locale.ROOT).endsWith(".jar")) {
                File canonical = safeChild(mods, file.getName());
                if (!canonical.exists()) return false;
                if (!canonical.delete()) throw new IOException("Não foi possível excluir o JAR.");
                return true;
            }
        }
        return false;
    }

    private File modsDirectory(String instanceName) throws IOException {
        try {
            File instance = instanceManager.getInstanceDirectory(instanceName).getCanonicalFile();
            File mods = new File(instance, "mods").getCanonicalFile();
            String expectedPrefix = instance.getCanonicalPath() + File.separator;
            if (!mods.getPath().startsWith(expectedPrefix) || !mods.isDirectory()) {
                throw new IOException("A pasta privada de mods não existe ou o caminho é inválido.");
            }
            return mods;
        } catch (org.json.JSONException | IllegalArgumentException e) {
            throw new IOException("A instância escolhida não existe.", e);
        }
    }

    private File safeChild(File parent, String name) throws IOException {
        File child = new File(parent, name).getCanonicalFile();
        String prefix = parent.getCanonicalPath() + File.separator;
        if (!child.getPath().startsWith(prefix)) {
            throw new IOException("Caminho de arquivo inválido.");
        }
        return child;
    }

    private String validateJarName(String value) {
        String name = value == null ? "" : value.trim();
        if (name.isEmpty() || name.length() > 180
                || !name.equals(new File(name).getName())
                || name.contains("/") || name.contains("\\")
                || name.equals(".") || name.equals("..")
                || !name.toLowerCase(Locale.ROOT).endsWith(".jar")
                || !name.matches("[\\p{L}\\p{N} _().\\-+\\[\\]]+\\.[jJ][aA][rR]")) {
            throw new IllegalArgumentException("Selecione um arquivo JAR com nome válido.");
        }
        return name;
    }

    private String sha256(File file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException("SHA-256 não está disponível.", impossible);
        }
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        return toHex(digest.digest());
    }

    private String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte item : bytes) result.append(String.format(Locale.ROOT, "%02x", item & 0xff));
        return result.toString();
    }
}
