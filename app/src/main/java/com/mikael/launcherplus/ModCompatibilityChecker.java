package com.mikael.launcherplus;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Performs conservative, offline inspection of mod JAR metadata.
 * It reports evidence and warnings; it does not prove runtime compatibility.
 */
public final class ModCompatibilityChecker {
    private final InstanceManager instances;
    private final InstanceModManager mods;

    public ModCompatibilityChecker(InstanceManager instanceManager, InstanceModManager modManager) {
        if (instanceManager == null || modManager == null) {
            throw new IllegalArgumentException("Gerenciadores de instância e mods são obrigatórios.");
        }
        instances = instanceManager;
        mods = modManager;
    }

    public JSONObject inspect(String instanceName) throws IOException {
        JSONObject record;
        try {
            record = findRecord(instanceName);
        } catch (org.json.JSONException e) {
            throw new IOException("Não foi possível ler os dados da instância.", e);
        }
        String gameVersion = record.optString("version", "");
        String loader = record.optString("loader", "Vanilla");
        File directory;
        try {
            directory = instances.getInstanceDirectory(instanceName);
        } catch (org.json.JSONException | IllegalArgumentException e) {
            throw new IOException("A instância selecionada não existe.", e);
        }

        JSONArray entries = mods.listMods(instanceName);
        JSONArray findings = new JSONArray();
        Map<String, List<String>> idsToFiles = new HashMap<>();
        Set<String> knownIds = new HashSet<>();
        List<JSONObject> parsedMods = new ArrayList<>();
        int unreadable = 0;
        int metadataMissing = 0;

        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.optJSONObject(i);
            if (entry == null) continue;
            String name = entry.optString("name", "");
            File jar = new File(new File(directory, "mods"), name);
            JSONObject item = new JSONObject();
            try {
                item.put("file", name);
                item.put("sizeBytes", jar.length());
                if (!jar.isFile() || !jar.canRead()) {
                    item.put("status", "error");
                    item.put("message", "Arquivo não pode ser lido.");
                    findings.put(item);
                    unreadable++;
                    continue;
                }

                JSONObject metadata = readMetadata(jar);
                if (metadata == null) {
                    item.put("status", "warning");
                    item.put("message", "JAR válido como arquivo ZIP, mas não foi reconhecido mcmod.info, fabric.mod.json ou mods.toml.");
                    findings.put(item);
                    metadataMissing++;
                    continue;
                }

                String id = metadata.optString("id", "");
                String version = metadata.optString("version", "desconhecida");
                String format = metadata.optString("format", "metadata");
                item.put("modId", id);
                item.put("modVersion", version);
                item.put("metadataFormat", format);
                item.put("status", "ok");
                item.put("message", "Metadados lidos; compatibilidade de execução não comprovada.");
                findings.put(item);
                if (!id.isEmpty()) {
                    idsToFiles.computeIfAbsent(id.toLowerCase(Locale.ROOT), unused -> new ArrayList<>()).add(name);
                    knownIds.add(id.toLowerCase(Locale.ROOT));
                }
                metadata.put("file", name);
                parsedMods.add(metadata);
            } catch (Exception exception) {
                try {
                    item.put("status", "error");
                    item.put("message", "JAR ou metadados inválidos: " + safeMessage(exception));
                    findings.put(item);
                } catch (org.json.JSONException ignored) { }
                unreadable++;
            }
        }

        JSONArray warnings = new JSONArray();
        for (Map.Entry<String, List<String>> group : idsToFiles.entrySet()) {
            if (group.getValue().size() > 1) {
                warnings.put("ID de mod duplicado '" + group.getKey() + "': " + join(group.getValue()) + ".");
            }
        }
        if ("Vanilla".equalsIgnoreCase(loader) && entries.length() > 0) {
            warnings.put("A instância é Vanilla. Mods que exigem Forge ou Fabric não funcionarão sem o carregador correspondente.");
        }
        for (JSONObject metadata : parsedMods) {
            JSONObject depends = metadata.optJSONObject("depends");
            if (depends != null) {
                java.util.Iterator<String> keys = depends.keys();
                while (keys.hasNext()) {
                    String dependency = keys.next();
                    Object constraint = depends.opt(dependency);
                    if ("minecraft".equalsIgnoreCase(dependency)) {
                        String expected = String.valueOf(constraint);
                        if (!expected.isEmpty() && !expected.equals("*") && !expected.contains(gameVersion)
                                && !expected.contains(">") && !expected.contains("<") && !expected.contains("=")) {
                            warnings.put(metadata.optString("file", "Mod") + ": dependência Minecraft declarada como '" + expected
                                    + "'; versão desta instância: " + gameVersion + ". Confirme manualmente a faixa suportada.");
                        }
                    } else if (!knownIds.contains(dependency.toLowerCase(Locale.ROOT))
                            && !"fabricloader".equalsIgnoreCase(dependency)
                            && !"java".equalsIgnoreCase(dependency)) {
                        warnings.put(metadata.optString("file", "Mod") + ": dependência '" + dependency
                                + "' não foi encontrada nos metadados dos JARs importados.");
                    }
                }
            }
            JSONArray required = metadata.optJSONArray("requiredMods");
            if (required != null) {
                for (int j = 0; j < required.length(); j++) {
                    JSONObject requirement = required.optJSONObject(j);
                    if (requirement == null) continue;
                    String dependency = requirement.optString("modid", requirement.optString("modId", ""));
                    if (!dependency.isEmpty() && !knownIds.contains(dependency.toLowerCase(Locale.ROOT))) {
                        warnings.put(metadata.optString("file", "Mod") + ": dependência obrigatória '" + dependency
                                + "' não foi encontrada nos JARs importados.");
                    }
                }
            }
        }

        JSONObject report = new JSONObject();
        try {
            report.put("instance", instanceName);
            report.put("minecraftVersion", gameVersion);
            report.put("loader", loader);
            report.put("jarCount", entries.length());
            report.put("metadataRecognized", parsedMods.size());
            report.put("metadataMissing", metadataMissing);
            report.put("unreadable", unreadable);
            report.put("findings", findings);
            report.put("warnings", warnings);
            report.put("runtimeCompatibilityProven", false);
        } catch (org.json.JSONException e) {
            throw new IOException("Não foi possível montar o relatório de compatibilidade.", e);
        }
        return report;
    }

    private JSONObject findRecord(String name) throws IOException, org.json.JSONException {
        JSONArray records = instances.getInstances();
        for (int i = 0; i < records.length(); i++) {
            JSONObject record = records.getJSONObject(i);
            if (name.equals(record.optString("name", ""))) return record;
        }
        throw new IllegalArgumentException("A instância selecionada não existe.");
    }

    private JSONObject readMetadata(File jar) throws IOException, org.json.JSONException {
        try (ZipFile zip = new ZipFile(jar)) {
            ZipEntry fabric = zip.getEntry("fabric.mod.json");
            if (fabric != null) {
                JSONObject json = readJson(zip, fabric);
                JSONObject result = new JSONObject();
                result.put("id", json.optString("id", ""));
                result.put("version", json.optString("version", "desconhecida"));
                result.put("format", "fabric.mod.json");
                JSONObject depends = json.optJSONObject("depends");
                if (depends != null) result.put("depends", depends);
                return result;
            }

            ZipEntry legacy = zip.getEntry("mcmod.info");
            if (legacy != null) {
                String text = readText(zip, legacy);
                Object root = new org.json.JSONTokener(text).nextValue();
                JSONObject mod = null;
                if (root instanceof JSONArray) mod = ((JSONArray) root).optJSONObject(0);
                else if (root instanceof JSONObject) {
                    JSONObject object = (JSONObject) root;
                    JSONArray modList = object.optJSONArray("modList");
                    if (modList != null) mod = modList.optJSONObject(0);
                    if (mod == null && object.has("modid")) mod = object;
                }
                if (mod != null) {
                    JSONObject result = new JSONObject();
                    result.put("id", mod.optString("modid", mod.optString("modId", "")));
                    result.put("version", mod.optString("version", "desconhecida"));
                    result.put("format", "mcmod.info");
                    JSONArray required = mod.optJSONArray("requiredMods");
                    if (required != null) result.put("requiredMods", required);
                    return result;
                }
            }

            ZipEntry toml = zip.getEntry("META-INF/mods.toml");
            if (toml != null) {
                String text = readText(zip, toml);
                java.util.regex.Matcher id = java.util.regex.Pattern
                        .compile("(?m)^\\s*modId\\s*=\\s*[\"']([A-Za-z0-9_.-]+)[\"']").matcher(text);
                java.util.regex.Matcher version = java.util.regex.Pattern
                        .compile("(?m)^\\s*version\\s*=\\s*[\"']([^\"']+)[\"']").matcher(text);
                if (id.find()) {
                    JSONObject result = new JSONObject();
                    result.put("id", id.group(1));
                    result.put("version", version.find() ? version.group(1) : "desconhecida");
                    result.put("format", "META-INF/mods.toml");
                    return result;
                }
            }
        } catch (java.util.zip.ZipException e) {
            throw new IOException("O arquivo não é um JAR/ZIP válido.", e);
        }
        return null;
    }

    private JSONObject readJson(ZipFile zip, ZipEntry entry) throws IOException, org.json.JSONException {
        return new JSONObject(readText(zip, entry));
    }

    private String readText(ZipFile zip, ZipEntry entry) throws IOException {
        if (entry.getSize() > 2L * 1024L * 1024L) throw new IOException("Metadados excedem 2 MB.");
        try (java.io.InputStream input = zip.getInputStream(entry);
             java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (output.size() + count > 2 * 1024 * 1024) throw new IOException("Metadados excedem 2 MB.");
                output.write(buffer, 0, count);
            }
            return new String(output.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private String join(List<String> names) {
        StringBuilder result = new StringBuilder();
        for (String name : names) {
            if (result.length() > 0) result.append(", ");
            result.append(name);
        }
        return result.toString();
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.trim().isEmpty() ? exception.getClass().getSimpleName() : message;
    }
}
