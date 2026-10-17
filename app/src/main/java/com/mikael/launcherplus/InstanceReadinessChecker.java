package com.mikael.launcherplus;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * Validates staged vanilla client artifacts without claiming that an instance is playable.
 * Libraries, assets, a compatible Java runtime, loaders, and engine-directory wiring remain
 * separate requirements and are always reported as launch blockers by this stage.
 */
public final class InstanceReadinessChecker {
    private static final int MAX_PROFILE_BYTES = 256 * 1024;
    private static final int MAX_METADATA_BYTES = 8 * 1024 * 1024;
    private final InstanceManager instanceManager;

    public InstanceReadinessChecker(InstanceManager manager) {
        if (manager == null) throw new IllegalArgumentException("Gerenciador de instâncias obrigatório.");
        instanceManager = manager;
    }

    public JSONObject inspect(String instanceName) throws IOException, JSONException {
        File instanceDirectory = instanceManager.getInstanceDirectory(instanceName);
        JSONObject profile = readJsonObject(safeChild(instanceDirectory, "instance.json"), MAX_PROFILE_BYTES);
        JSONArray checks = new JSONArray();
        JSONArray blockers = new JSONArray();

        String profileName = profile.optString("name", "");
        String version = profile.optString("version", "");
        String loader = profile.optString("loader", "Vanilla");
        String installStatus = profile.optString("installStatus", "profile_only");
        String stagedVersion = profile.optString("stagedVersion", "");

        boolean profileValid = profileName.equals(instanceName)
                && version.matches("[A-Za-z0-9._-]{1,100}");
        addCheck(checks, "Perfil da instância", profileValid,
                profileValid ? "O nome e o identificador de versão são válidos."
                        : "O perfil não corresponde à instância selecionada ou a versão não é válida.");
        if (!profileValid) blockers.put("Corrija ou recrie o perfil local: os metadados básicos são inválidos.");

        boolean statusRecorded = "client_artifacts_staged".equals(installStatus);
        addCheck(checks, "Estado registrado", statusRecorded,
                statusRecorded ? "O perfil registra arquivos do cliente preparados."
                        : "Nenhum estágio válido de arquivos do cliente está registrado.");
        if (!statusRecorded) blockers.put("Baixe os arquivos oficiais do cliente para esta instância.");

        boolean stagedIdValid = stagedVersion.matches("[A-Za-z0-9._-]{1,100}");
        boolean sameVersion = stagedIdValid && stagedVersion.equals(version);
        addCheck(checks, "Versão do perfil", sameVersion,
                sameVersion ? "A versão preparada corresponde à versão selecionada."
                        : "Versão do perfil: " + version + "; versão preparada: "
                                + (stagedVersion.isEmpty() ? "nenhuma" : stagedVersion) + ".");
        if (!sameVersion) blockers.put("Os arquivos preparados não correspondem à versão selecionada.");

        boolean metadataValid = false;
        boolean clientValid = false;
        if (statusRecorded && stagedIdValid) {
            File versionsRoot = safeChild(instanceDirectory, "versions");
            File versionDirectory = safeChild(versionsRoot, stagedVersion);
            File metadataFile = safeChild(versionDirectory, stagedVersion + ".json");
            File clientFile = safeChild(versionDirectory, stagedVersion + ".jar");

            boolean metadataExists = metadataFile.isFile()
                    && metadataFile.length() > 0 && metadataFile.length() <= MAX_METADATA_BYTES;
            addCheck(checks, "JSON da versão", metadataExists,
                    metadataExists ? "O JSON preparado está presente e dentro do limite de tamanho."
                            : "O JSON da versão está ausente, vazio ou excede 8 MiB.");

            if (metadataExists) {
                JSONObject metadata = readJsonObject(metadataFile, MAX_METADATA_BYTES);
                String actualMetadataSha1 = sha1(metadataFile);
                String recordedMetadataSha1 = profile.optString("metadataSha1", "").toLowerCase(Locale.ROOT);
                String recordedClientSha1 = profile.optString("clientSha1", "").toLowerCase(Locale.ROOT);
                long recordedClientBytes = profile.optLong("clientBytes", -1L);
                JSONObject downloads = metadata.optJSONObject("downloads");
                JSONObject manifestClient = downloads == null ? null : downloads.optJSONObject("client");
                String manifestClientSha1 = manifestClient == null ? "" :
                        manifestClient.optString("sha1", "").toLowerCase(Locale.ROOT);
                long manifestClientBytes = manifestClient == null ? -1L :
                        manifestClient.optLong("size", -1L);
                boolean idMatches = stagedVersion.equals(metadata.optString("id", ""));
                boolean metadataHashMatches = recordedMetadataSha1.matches("[0-9a-f]{40}")
                        && recordedMetadataSha1.equals(actualMetadataSha1);
                boolean manifestClientMatchesRecord = manifestClientSha1.matches("[0-9a-f]{40}")
                        && manifestClientSha1.equals(recordedClientSha1)
                        && manifestClientBytes > 0 && manifestClientBytes == recordedClientBytes;
                metadataValid = idMatches && metadataHashMatches && manifestClientMatchesRecord;
                addCheck(checks, "Identificador e SHA-1 do JSON", metadataValid,
                        metadataValid ? "ID, SHA-1 do JSON e tamanho/hash esperados do cliente correspondem ao registro."
                                : "O ID, o SHA-1 do JSON ou o hash/tamanho do cliente declarado no manifesto diverge do registro.");
                if (!metadataValid) blockers.put("Os metadados da versão estão inconsistentes; baixe novamente os arquivos oficiais.");
            } else {
                addCheck(checks, "Identificador e SHA-1 do JSON", false,
                        "Não foi possível validar os metadados sem um JSON de versão íntegro.");
                blockers.put("O JSON oficial da versão está faltando ou inválido.");
            }

            boolean clientExists = clientFile.isFile()
                    && clientFile.length() > 0
                    && clientFile.length() <= 1024L * 1024L * 1024L;
            addCheck(checks, "JAR do cliente", clientExists,
                    clientExists ? "O arquivo JAR está presente e dentro do limite de 1 GiB."
                            : "O JAR do cliente está ausente, vazio ou excede 1 GiB.");

            if (clientExists) {
                String actualSha1 = sha1(clientFile);
                String expectedSha1 = profile.optString("clientSha1", "").toLowerCase(Locale.ROOT);
                long expectedBytes = profile.optLong("clientBytes", -1L);
                boolean sizeMatches = expectedBytes > 0 && clientFile.length() == expectedBytes;
                boolean hashMatches = expectedSha1.matches("[0-9a-f]{40}")
                        && expectedSha1.equals(actualSha1);
                clientValid = sizeMatches && hashMatches;
                addCheck(checks, "Tamanho e SHA-1 do JAR", clientValid,
                        clientValid ? "Tamanho e SHA-1 correspondem ao registro da instância."
                                : "O tamanho ou o SHA-1 do JAR não corresponde ao registro; baixe novamente.");
                if (!clientValid) blockers.put("A integridade do JAR do cliente não foi confirmada.");
            } else {
                addCheck(checks, "Tamanho e SHA-1 do JAR", false,
                        "Não foi possível validar o JAR porque o arquivo não está disponível.");
                blockers.put("O JAR oficial do cliente está faltando ou inválido.");
            }
        } else {
            addCheck(checks, "JSON da versão", false, "Ainda não existem artefatos registrados para validar.");
            addCheck(checks, "Identificador e SHA-1 do JSON", false, "A validação será feita após o download dos arquivos oficiais.");
            addCheck(checks, "JAR do cliente", false, "Ainda não existe um JAR do cliente registrado.");
            addCheck(checks, "Tamanho e SHA-1 do JAR", false, "A validação será feita após o download.");
        }

        if (!"Vanilla".equals(loader) && !profile.optBoolean("loaderInstalled", false)) {
            blockers.put("O perfil seleciona " + loader + ", mas esse carregador não está instalado.");
        }

        boolean assetsRecorded = false;
        String assetStatus = profile.optString("assetInstallStatus", "");
        String assetIndexId = profile.optString("assetIndexId", "");
        String assetIndexSha1 = profile.optString("assetIndexSha1", "").toLowerCase(Locale.ROOT);
        long assetObjectCount = profile.optLong("assetObjectCount", 0L);
        long assetsVerified = profile.optLong("assetObjectsVerified", -1L);
        long logicalPathCount = profile.optLong("assetLogicalPathCount", 0L);
        long logicalPathsVerified = profile.optLong("assetLogicalPathsVerified", -1L);
        int mappedCopies = (profile.optBoolean("assetVirtual", false) ? 1 : 0)
                + (profile.optBoolean("assetMapToResources", false) ? 1 : 0);
        long expectedLogicalCopies = logicalPathCount * mappedCopies;
        if ("assets_staged".equals(assetStatus) && profile.optBoolean("assetsInstalled", false)
                && assetIndexId.matches("[A-Za-z0-9._-]{1,100}")
                && !". ".equals(assetIndexId) && !"..".equals(assetIndexId)
                && assetIndexSha1.matches("[0-9a-f]{40}")
                && assetObjectCount > 0 && assetsVerified == assetObjectCount
                && logicalPathsVerified == expectedLogicalCopies) {
            File indexFile = safeChild(safeChild(instanceDirectory, "assets"),
                    "indexes" + File.separator + assetIndexId + ".json");
            assetsRecorded = indexFile.isFile() && indexFile.length() > 0
                    && indexFile.length() <= 16L * 1024L * 1024L
                    && assetIndexSha1.equals(sha1(indexFile));
        }
        addCheck(checks, "Assets oficiais", assetsRecorded,
                assetsRecorded
                        ? "O índice de assets foi revalidado e o registro confirma que todos os objetos/caminhos foram verificados no último download. Esta checagem rápida não recalcula o hash de cada objeto."
                        : "Assets não foram totalmente instalados, o índice foi alterado/está ausente ou os totais registrados estão inconsistentes.");
        if (!assetsRecorded) blockers.put("Baixe e verifique todos os assets oficiais desta instância.");

        blockers.put("Bibliotecas Minecraft ainda não foram baixadas e verificadas por esta instância.");
        blockers.put("Um runtime Java compatível ainda não foi associado e validado.");
        blockers.put("A instância privada ainda não está conectada ao diretório de jogo usado pelo motor Amethyst/Pojav.");

        JSONObject result = new JSONObject();
        result.put("instanceName", profileName);
        result.put("minecraftVersion", version);
        result.put("loader", loader);
        result.put("installStatus", installStatus);
        result.put("stagedVersion", stagedVersion);
        result.put("metadataVerified", metadataValid);
        result.put("clientJarVerified", clientValid);
        result.put("clientArtifactsVerified", metadataValid && clientValid && statusRecorded);
        result.put("assetsRecordedInstalled", assetsRecorded);
        result.put("readyToLaunch", false);
        result.put("status", metadataValid && clientValid && statusRecorded && sameVersion
                ? (assetsRecorded ? "client_and_assets_verified_not_playable"
                    : "client_artifacts_verified_assets_pending") : "incomplete");
        result.put("checks", checks);
        result.put("blockers", blockers);
        return result;
    }

    private JSONObject readJsonObject(File file, int maxBytes) throws IOException, JSONException {
        if (!file.isFile() || file.length() <= 0 || file.length() > maxBytes) {
            throw new IOException("Arquivo JSON ausente, vazio ou acima do limite.");
        }
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (output.size() + count > maxBytes) throw new IOException("O JSON excede o limite de segurança.");
                output.write(buffer, 0, count);
            }
            return new JSONObject(new String(output.toByteArray(), StandardCharsets.UTF_8));
        }
    }

    private File safeChild(File parent, String name) throws IOException {
        File base = parent.getCanonicalFile();
        File child = new File(base, name).getCanonicalFile();
        if (!child.getPath().startsWith(base.getPath() + File.separator)) {
            throw new IOException("Caminho da instância inválido.");
        }
        return child;
    }

    private String sha1(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            try (InputStream input = new FileInputStream(file)) {
                byte[] buffer = new byte[64 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
            }
            return toHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-1 não está disponível.", e);
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder output = new StringBuilder(bytes.length * 2);
        for (byte item : bytes) output.append(String.format(Locale.ROOT, "%02x", item & 0xff));
        return output.toString();
    }

    private void addCheck(JSONArray checks, String name, boolean passed, String detail) throws JSONException {
        JSONObject item = new JSONObject();
        item.put("name", name);
        item.put("passed", passed);
        item.put("detail", detail);
        checks.put(item);
    }
}
