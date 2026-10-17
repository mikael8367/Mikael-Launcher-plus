package com.mikael.launcherplus;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ActivityManager;
import android.os.Build;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.content.SharedPreferences;
import android.widget.TextView;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends Activity {
    private static final int BACKGROUND = 0xFF080D0B;
    private static final int SURFACE = 0xFF111A16;
    private static final int EMERALD = 0xFF00D68F;
    private static final int TEXT = 0xFFF0F7F3;
    private static final int MUTED = 0xFF9EB3A8;
    private static final String VERSION_MANIFEST =
            "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private static final int MAX_MANIFEST_BYTES = 4 * 1024 * 1024;
    private static final int REQUEST_PICK_MOD_FILE = 7402;

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private TextView versionsOutput;
    private Button versionsButton;
    private Button metadataButton;
    private Button copyMetadataButton;
    private Spinner versionSpinner;
    private Spinner typeSpinner;
    private EditText versionSearch;
    private TextView deviceOutput;
    private EditText instanceNameInput;
    private Spinner instanceSpinner;
    private Spinner loaderSpinner;
    private TextView instancesOutput;
    private InstanceManager instanceManager;
    private InstanceModManager instanceModManager;
    private VersionArtifactInstaller versionArtifactInstaller;
    private Button stageVanillaButton;
    private TextView installStatusOutput;
    private TextView modsOutput;
    private boolean updatingInstances = false;
    private String lastMetadataText = "";
    private final List<String> allVersionIds = new ArrayList<>();
    private final List<String> allVersionUrls = new ArrayList<>();
    private final List<String> allVersionTypes = new ArrayList<>();
    private final List<String> visibleVersionIds = new ArrayList<>();
    private final List<String> visibleVersionUrls = new ArrayList<>();
    private final List<String> visibleVersionTypes = new ArrayList<>();
    private boolean updatingVersionControls = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BACKGROUND);
        getWindow().setNavigationBarColor(BACKGROUND);
        setContentView(createDashboard());
    }

    @Override
    protected void onDestroy() {
        ioExecutor.shutdownNow();
        super.onDestroy();
    }

    private View createDashboard() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BACKGROUND);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(22), dp(24), dp(22), dp(28));
        scroll.addView(page);

        page.addView(text("MIKAEL PROJECTS  /  ANDROID", 12, EMERALD, true));

        TextView title = text("Mikael Launcher\nPlus", 32, TEXT, true);
        LinearLayout.LayoutParams titleParams = params(-1, -2);
        titleParams.topMargin = dp(10);
        page.addView(title, titleParams);

        TextView intro = text(
                "Sua central para Minecraft Java. Consulte o manifesto oficial de versões. "
                        + "A inicialização do jogo ainda está em desenvolvimento.",
                15, MUTED, false);
        LinearLayout.LayoutParams introParams = params(-1, -2);
        introParams.topMargin = dp(12);
        page.addView(intro, introParams);

        LinearLayout statusCard = new LinearLayout(this);
        statusCard.setOrientation(LinearLayout.VERTICAL);
        statusCard.setPadding(dp(18), dp(18), dp(18), dp(18));
        statusCard.setBackground(roundRect(SURFACE, 18, 0xFF20352B, 1));
        LinearLayout.LayoutParams cardParams = params(-1, -2);
        cardParams.topMargin = dp(24);
        page.addView(statusCard, cardParams);

        statusCard.addView(text("ESTADO DO PROJETO", 12, EMERALD, true));
        TextView state = text("Base Android pronta para validação", 19, TEXT, true);
        LinearLayout.LayoutParams stateParams = params(-1, -2);
        stateParams.topMargin = dp(9);
        statusCard.addView(state, stateParams);

        TextView detail = text(
                "Manifesto oficial: consulta implementada\n"
                        + "Perfis de instância: gerenciamento local implementado\n"
                        + "Motor de jogo: abre o app complementar se instalado",
                14, MUTED, false);
        LinearLayout.LayoutParams detailParams = params(-1, -2);
        detailParams.topMargin = dp(10);
        statusCard.addView(detail, detailParams);

        Button play = button("TESTAR MINECRAFT VANILLA", true);
        LinearLayout.LayoutParams playParams = params(-1, dp(54));
        playParams.topMargin = dp(20);
        page.addView(play, playParams);
        play.setOnClickListener(v -> {
            String selectedVersion = getSelectedVersionForVanillaTest();
            new AlertDialog.Builder(this)
                    .setTitle("Etapa 2 — teste Vanilla")
                    .setMessage("Versão escolhida nesta tela: " + selectedVersion + "\n\n"
                            + "Esta tela ainda consulta os metadados da Mojang, mas não instala o jogo nem transfere a versão para o motor. "
                            + "No motor, escolha a mesma versão Vanilla, conclua a autenticação compatível, aguarde os downloads e toque em Jogar. "
                            + "Depois, entre em um mundo e confirme que ele carrega. Mods e loaders ficam fora deste teste.")
                    .setNegativeButton("Cancelar", (dialog, which) -> dialog.dismiss())
                    .setPositiveButton("Abrir motor", (dialog, which) -> openEngineForVanillaTest())
                    .show();
        });

        LinearLayout versionsCard = new LinearLayout(this);
        versionsCard.setOrientation(LinearLayout.VERTICAL);
        versionsCard.setPadding(dp(18), dp(18), dp(18), dp(18));
        versionsCard.setBackground(roundRect(SURFACE, 18, 0xFF20352B, 1));
        LinearLayout.LayoutParams versionsCardParams = params(-1, -2);
        versionsCardParams.topMargin = dp(20);
        page.addView(versionsCard, versionsCardParams);

        versionsCard.addView(text("VERSÕES OFICIAIS", 12, EMERALD, true));
        versionsOutput = text(
                "Toque no botão para consultar as versões publicadas pela Mojang.",
                14, MUTED, false);
        LinearLayout.LayoutParams outputParams = params(-1, -2);
        outputParams.topMargin = dp(10);
        versionsCard.addView(versionsOutput, outputParams);

        versionsButton = button("CONSULTAR VERSÕES OFICIAIS", true);
        LinearLayout.LayoutParams versionsButtonParams = params(-1, dp(50));
        versionsButtonParams.topMargin = dp(14);
        versionsCard.addView(versionsButton, versionsButtonParams);
        versionsButton.setOnClickListener(v -> loadOfficialVersions());

        versionSpinner = new Spinner(this);
        versionSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (updatingVersionControls || position < 0 || position >= visibleVersionIds.size()) return;
                getSharedPreferences("launcher_preferences", MODE_PRIVATE).edit()
                        .putString("selected_version", visibleVersionIds.get(position)).apply();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        LinearLayout.LayoutParams spinnerParams = params(-1, dp(50));
        spinnerParams.topMargin = dp(12);
        versionsCard.addView(versionSpinner, spinnerParams);

        metadataButton = button("VER METADADOS DA VERSÃO", true);
        LinearLayout.LayoutParams metadataParams = params(-1, dp(50));
        metadataParams.topMargin = dp(8);
        versionsCard.addView(metadataButton, metadataParams);
        metadataButton.setOnClickListener(v -> loadSelectedVersionMetadata());

        typeSpinner = new Spinner(this);
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{"Todas as versões", "Releases estáveis", "Snapshots", "Betas antigas", "Alphas antigas"});
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        typeSpinner.setAdapter(typeAdapter);
        LinearLayout.LayoutParams typeParams = params(-1, dp(48));
        typeParams.topMargin = dp(10);
        versionsCard.addView(typeSpinner, typeParams);

        versionSearch = new EditText(this);
        versionSearch.setSingleLine(true);
        versionSearch.setTextSize(15);
        versionSearch.setTextColor(TEXT);
        versionSearch.setHintTextColor(MUTED);
        versionSearch.setHint("Pesquisar versão (ex.: 1.12.2)");
        versionSearch.setPadding(dp(12), 0, dp(12), 0);
        versionSearch.setBackground(roundRect(BACKGROUND, 12, 0xFF355344, 1));
        LinearLayout.LayoutParams searchParams = params(-1, dp(48));
        searchParams.topMargin = dp(8);
        versionsCard.addView(versionSearch, searchParams);
        versionSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filterVersions(); }
            @Override public void afterTextChanged(Editable s) { }
        });
        typeSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (!updatingVersionControls) filterVersions();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });

        copyMetadataButton = button("COPIAR METADADOS", true);
        LinearLayout.LayoutParams copyParams = params(-1, dp(48));
        copyParams.topMargin = dp(8);
        versionsCard.addView(copyMetadataButton, copyParams);
        copyMetadataButton.setOnClickListener(v -> copyLastMetadata());

        TextView sections = text("MÓDULOS DO PROJETO", 12, EMERALD, true);
        LinearLayout.LayoutParams sectionParams = params(-1, -2);
        sectionParams.topMargin = dp(28);
        page.addView(sections, sectionParams);

        addModule(page, "Versões e downloads",
                "Consulta de manifesto implementada · instalação ainda pendente");
        addModule(page, "Instâncias e mods",
                "Planejado · pastas isoladas, loaders e dependências");
        addModule(page, "Contas e sessões",
                "Planejado · autenticação oficial e armazenamento seguro");
        addModule(page, "Desempenho e diagnóstico",
                "Informações reais do dispositivo e memória disponíveis abaixo.");

        LinearLayout instancesCard = new LinearLayout(this);
        instancesCard.setOrientation(LinearLayout.VERTICAL);
        instancesCard.setPadding(dp(18), dp(18), dp(18), dp(18));
        instancesCard.setBackground(roundRect(SURFACE, 18, 0xFF20352B, 1));
        LinearLayout.LayoutParams instancesCardParams = params(-1, -2);
        instancesCardParams.topMargin = dp(14);
        page.addView(instancesCard, instancesCardParams);
        instancesCard.addView(text("INSTÂNCIAS LOCAIS", 12, EMERALD, true));
        TextView instancesNote = text(
                "Crie perfis separados com pastas para mods, saves, configurações e logs. "
                        + "Isso ainda não baixa o jogo nem instala o loader escolhido.",
                13, MUTED, false);
        LinearLayout.LayoutParams instancesNoteParams = params(-1, -2);
        instancesNoteParams.topMargin = dp(8);
        instancesCard.addView(instancesNote, instancesNoteParams);

        instanceNameInput = new EditText(this);
        instanceNameInput.setSingleLine(true);
        instanceNameInput.setTextSize(15);
        instanceNameInput.setTextColor(TEXT);
        instanceNameInput.setHintTextColor(MUTED);
        instanceNameInput.setHint("Nome da instância (ex.: Survival 1.12.2)");
        instanceNameInput.setPadding(dp(12), 0, dp(12), 0);
        instanceNameInput.setBackground(roundRect(BACKGROUND, 12, 0xFF355344, 1));
        LinearLayout.LayoutParams instanceNameParams = params(-1, dp(50));
        instanceNameParams.topMargin = dp(12);
        instancesCard.addView(instanceNameInput, instanceNameParams);

        TextView loaderLabel = text("Perfil do carregador", 14, TEXT, true);
        LinearLayout.LayoutParams loaderLabelParams = params(-1, -2);
        loaderLabelParams.topMargin = dp(10);
        instancesCard.addView(loaderLabel, loaderLabelParams);
        loaderSpinner = new Spinner(this);
        String[] loaderOptions = {"Vanilla", "Forge", "Fabric", "OptiFine", "Forge + OptiFine"};
        ArrayAdapter<String> loaderAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, loaderOptions);
        loaderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        loaderSpinner.setAdapter(loaderAdapter);
        LinearLayout.LayoutParams loaderSpinnerParams = params(-1, dp(48));
        instancesCard.addView(loaderSpinner, loaderSpinnerParams);

        Button createInstanceButton = button("CRIAR INSTÂNCIA", true);
        LinearLayout.LayoutParams createInstanceParams = params(-1, dp(50));
        createInstanceParams.topMargin = dp(10);
        instancesCard.addView(createInstanceButton, createInstanceParams);
        createInstanceButton.setOnClickListener(v -> createLocalInstance());

        TextView existingLabel = text("Instâncias salvas neste aparelho", 14, TEXT, true);
        LinearLayout.LayoutParams existingLabelParams = params(-1, -2);
        existingLabelParams.topMargin = dp(14);
        instancesCard.addView(existingLabel, existingLabelParams);
        instanceSpinner = new Spinner(this);
        instanceSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (updatingInstances || position < 0 || position >= instanceSpinner.getCount()) return;
                Object selected = instanceSpinner.getSelectedItem();
                if (selected == null) return;
                getSharedPreferences("launcher_preferences", MODE_PRIVATE).edit()
                        .putString("active_instance_name", String.valueOf(selected)).apply();
                showSelectedInstance();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        LinearLayout.LayoutParams instanceSpinnerParams = params(-1, dp(48));
        instancesCard.addView(instanceSpinner, instanceSpinnerParams);

        instancesOutput = text("Nenhuma instância criada ainda.", 13, MUTED, false);
        LinearLayout.LayoutParams instancesOutputParams = params(-1, -2);
        instancesOutputParams.topMargin = dp(8);
        instancesCard.addView(instancesOutput, instancesOutputParams);

        installStatusOutput = text(
                "Instalação real: ainda não iniciada. O próximo passo baixa e verifica apenas os metadados oficiais e o JAR do cliente Vanilla.",
                13, MUTED, false);
        LinearLayout.LayoutParams installStatusParams = params(-1, -2);
        installStatusParams.topMargin = dp(10);
        instancesCard.addView(installStatusOutput, installStatusParams);

        stageVanillaButton = button("BAIXAR ARQUIVOS OFICIAIS VANILLA", true);
        LinearLayout.LayoutParams stageVanillaParams = params(-1, dp(50));
        stageVanillaParams.topMargin = dp(8);
        instancesCard.addView(stageVanillaButton, stageVanillaParams);
        stageVanillaButton.setOnClickListener(v -> stageVanillaArtifacts());

        Button deleteInstanceButton = button("EXCLUIR INSTÂNCIA SELECIONADA", true);
        LinearLayout.LayoutParams deleteInstanceParams = params(-1, dp(48));
        deleteInstanceParams.topMargin = dp(10);
        instancesCard.addView(deleteInstanceButton, deleteInstanceParams);
        deleteInstanceButton.setOnClickListener(v -> confirmDeleteSelectedInstance());

        try {
            instanceManager = new InstanceManager(this);
            instanceModManager = new InstanceModManager(instanceManager);
            versionArtifactInstaller = new VersionArtifactInstaller(instanceManager);
            refreshInstancesList();
        } catch (Exception exception) {
            instancesOutput.setText("Gerenciador de instâncias indisponível: " + safeExceptionMessage(exception));
            createInstanceButton.setEnabled(false);
            deleteInstanceButton.setEnabled(false);
            if (stageVanillaButton != null) stageVanillaButton.setEnabled(false);
        }

        LinearLayout modsCard = new LinearLayout(this);
        modsCard.setOrientation(LinearLayout.VERTICAL);
        modsCard.setPadding(dp(18), dp(18), dp(18), dp(18));
        modsCard.setBackground(roundRect(SURFACE, 18, 0xFF20352B, 1));
        LinearLayout.LayoutParams modsCardParams = params(-1, -2);
        modsCardParams.topMargin = dp(14);
        page.addView(modsCard, modsCardParams);
        modsCard.addView(text("GERENCIADOR DE MODS", 12, EMERALD, true));
        TextView modsNote = text(
                "Importe arquivos JAR para a instância selecionada e remova-os quando precisar. "
                        + "Arquivos JAR executam código: importe somente mods de fontes em que você confia. "
                        + "Os arquivos ficam guardados no perfil; o jogo ainda não está ligado a estas pastas.",
                13, MUTED, false);
        LinearLayout.LayoutParams modsNoteParams = params(-1, -2);
        modsNoteParams.topMargin = dp(8);
        modsCard.addView(modsNote, modsNoteParams);

        Button addModButton = button("IMPORTAR MOD .JAR", true);
        LinearLayout.LayoutParams addModParams = params(-1, dp(50));
        addModParams.topMargin = dp(12);
        modsCard.addView(addModButton, addModParams);
        addModButton.setOnClickListener(v -> openModPicker());

        modsOutput = text("Selecione uma instância para ver os mods.", 13, MUTED, false);
        LinearLayout.LayoutParams modsOutputParams = params(-1, -2);
        modsOutputParams.topMargin = dp(10);
        modsCard.addView(modsOutput, modsOutputParams);

        LinearLayout modsActions = new LinearLayout(this);
        modsActions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams modsActionsParams = params(-1, dp(48));
        modsActionsParams.topMargin = dp(8);
        modsCard.addView(modsActions, modsActionsParams);
        Button refreshModsButton = button("ATUALIZAR LISTA", true);
        Button removeModButton = button("REMOVER JAR", true);
        modsActions.addView(refreshModsButton, new LinearLayout.LayoutParams(0, -1, 1));
        LinearLayout.LayoutParams removeModParams = new LinearLayout.LayoutParams(0, -1, 1);
        removeModParams.leftMargin = dp(8);
        modsActions.addView(removeModButton, removeModParams);
        refreshModsButton.setOnClickListener(v -> refreshModsList());
        removeModButton.setOnClickListener(v -> chooseModToRemove());
        refreshModsList();

        LinearLayout deviceCard = new LinearLayout(this);
        deviceCard.setOrientation(LinearLayout.VERTICAL);
        deviceCard.setPadding(dp(18), dp(18), dp(18), dp(18));
        deviceCard.setBackground(roundRect(SURFACE, 18, 0xFF20352B, 1));
        LinearLayout.LayoutParams deviceCardParams = params(-1, -2);
        deviceCardParams.topMargin = dp(12);
        page.addView(deviceCard, deviceCardParams);
        deviceCard.addView(text("DIAGNÓSTICO DO DISPOSITIVO", 12, EMERALD, true));
        deviceOutput = text("Carregando informações do Android...", 14, MUTED, false);
        LinearLayout.LayoutParams deviceOutputParams = params(-1, -2);
        deviceOutputParams.topMargin = dp(10);
        deviceCard.addView(deviceOutput, deviceOutputParams);
        Button refreshDevice = button("ATUALIZAR DIAGNÓSTICO", true);
        LinearLayout.LayoutParams refreshParams = params(-1, dp(48));
        refreshParams.topMargin = dp(10);
        deviceCard.addView(refreshDevice, refreshParams);
        refreshDevice.setOnClickListener(v -> refreshDeviceDiagnostics());
        refreshDeviceDiagnostics();

        LinearLayout settingsCard = new LinearLayout(this);
        settingsCard.setOrientation(LinearLayout.VERTICAL);
        settingsCard.setPadding(dp(18), dp(18), dp(18), dp(18));
        settingsCard.setBackground(roundRect(SURFACE, 18, 0xFF20352B, 1));
        LinearLayout.LayoutParams settingsCardParams = params(-1, -2);
        settingsCardParams.topMargin = dp(14);
        page.addView(settingsCard, settingsCardParams);
        settingsCard.addView(text("PREFERÊNCIAS DO LAUNCHER", 12, EMERALD, true));
        TextView settingsNote = text("As preferências são salvas no aparelho, mas ainda não são aplicadas ao Minecraft até a integração do motor.", 13, MUTED, false);
        LinearLayout.LayoutParams settingsNoteParams = params(-1, -2);
        settingsNoteParams.topMargin = dp(8);
        settingsCard.addView(settingsNote, settingsNoteParams);

        settingsCard.addView(text("Limite de RAM preferido", 15, TEXT, true));
        Spinner ramSpinner = new Spinner(this);
        String[] ramOptions = {"512 MB", "768 MB", "1024 MB", "1280 MB", "1536 MB", "2048 MB"};
        ArrayAdapter<String> ramAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, ramOptions);
        ramAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        ramSpinner.setAdapter(ramAdapter);
        LinearLayout.LayoutParams ramParams = params(-1, dp(48));
        ramParams.topMargin = dp(4);
        settingsCard.addView(ramSpinner, ramParams);

        LinearLayout.LayoutParams rendererLabelParams = params(-1, -2);
        rendererLabelParams.topMargin = dp(10);
        TextView rendererLabel = text("Renderizador preferido", 15, TEXT, true);
        settingsCard.addView(rendererLabel, rendererLabelParams);
        Spinner rendererSpinner = new Spinner(this);
        String[] renderers = {"GL4ES (recomendado para compatibilidade)", "Zink (experimental)", "VirGL (experimental)"};
        ArrayAdapter<String> rendererAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, renderers);
        rendererAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        rendererSpinner.setAdapter(rendererAdapter);
        LinearLayout.LayoutParams rendererParams = params(-1, dp(48));
        settingsCard.addView(rendererSpinner, rendererParams);

        SharedPreferences preferences = getSharedPreferences("launcher_preferences", MODE_PRIVATE);
        ramSpinner.setSelection(Math.max(0, Math.min(ramOptions.length - 1, preferences.getInt("preferred_ram_index", 2))));
        rendererSpinner.setSelection(Math.max(0, Math.min(renderers.length - 1, preferences.getInt("preferred_renderer_index", 0))));
        android.widget.AdapterView.OnItemSelectedListener savePreferences = new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                preferences.edit().putInt("preferred_ram_index", ramSpinner.getSelectedItemPosition())
                        .putInt("preferred_ram_mb", Integer.parseInt(ramOptions[ramSpinner.getSelectedItemPosition()].replace(" MB", "")))
                        .putInt("preferred_renderer_index", rendererSpinner.getSelectedItemPosition())
                        .putString("preferred_renderer", rendererSpinner.getSelectedItem().toString()).apply();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        };
        ramSpinner.setOnItemSelectedListener(savePreferences);
        rendererSpinner.setOnItemSelectedListener(savePreferences);

        TextView footer = text("Mikael Launcher Plus · versão 0.1.0 · base inicial",
                12, MUTED, false);
        LinearLayout.LayoutParams footerParams = params(-1, -2);
        footerParams.topMargin = dp(24);
        footerParams.gravity = Gravity.CENTER_HORIZONTAL;
        footer.setGravity(Gravity.CENTER);
        page.addView(footer, footerParams);
        return scroll;
    }

    private void loadOfficialVersions() {
        versionsButton.setEnabled(false);
        versionsButton.setText("CONSULTANDO MANIFESTO...");
        versionsOutput.setText("Conectando ao manifesto oficial da Mojang...");
        ioExecutor.execute(() -> {
            HttpURLConnection connection = null;
            String result;
            try {
                connection = (HttpURLConnection) new URL(VERSION_MANIFEST).openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(12000);
                connection.setReadTimeout(12000);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("User-Agent", "MikaelLauncherPlus/0.1.0");
                int status = connection.getResponseCode();
                if (status != HttpURLConnection.HTTP_OK) {
                    throw new IllegalStateException("O servidor respondeu HTTP " + status + ".");
                }

                String body;
                try (InputStream input = connection.getInputStream();
                     ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int total = 0;
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        total += count;
                        if (total > MAX_MANIFEST_BYTES) {
                            throw new IllegalStateException("Manifesto maior que o limite de segurança.");
                        }
                        output.write(buffer, 0, count);
                    }
                    body = output.toString(StandardCharsets.UTF_8.name());
                }

                JSONObject manifest = new JSONObject(body);
                JSONObject latest = manifest.getJSONObject("latest");
                String latestRelease = latest.getString("release");
                getSharedPreferences("launcher_preferences", MODE_PRIVATE).edit().putString("latest_release", latestRelease).apply();
                JSONArray versions = manifest.getJSONArray("versions");
                StringBuilder display = new StringBuilder();
                display.append("Última release estável: ").append(latestRelease).append('\n');
                display.append("Versões publicadas no manifesto: ").append(versions.length()).append('\n');
                display.append("Lista de releases carregada. Selecione uma versão para ver os metadados.");

                List<String> foundIds = new ArrayList<>();
                List<String> foundUrls = new ArrayList<>();
                List<String> foundTypes = new ArrayList<>();
                for (int i = 0; i < versions.length(); i++) {
                    JSONObject version = versions.getJSONObject(i);
                    foundIds.add(version.optString("id", ""));
                    foundUrls.add(version.optString("url", ""));
                    foundTypes.add(version.optString("type", "release"));
                }
                synchronized (allVersionIds) {
                    allVersionIds.clear(); allVersionIds.addAll(foundIds);
                    allVersionUrls.clear(); allVersionUrls.addAll(foundUrls);
                    allVersionTypes.clear(); allVersionTypes.addAll(foundTypes);
                }
                result = display.toString();
            } catch (Exception exception) {
                String message = exception.getMessage();
                result = "Não foi possível consultar as versões oficiais.\n"
                        + (message == null ? exception.getClass().getSimpleName() : message)
                        + "\nVerifique a conexão e tente novamente.";
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }

            final String displayResult = result;
            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed() || versionsOutput == null || versionsButton == null) {
                    return;
                }
                versionsOutput.setText(displayResult);
                if (!allVersionIds.isEmpty()) {
                    SharedPreferences prefs = getSharedPreferences("launcher_preferences", MODE_PRIVATE);
                    String saved = prefs.getString("selected_version", latestVersionFromDisplay(displayResult));
                    updatingVersionControls = true;
                    typeSpinner.setSelection(prefs.getInt("version_type_filter", 1));
                    updatingVersionControls = false;
                    filterVersions();
                    int selected = visibleVersionIds.indexOf(saved);
                    if (selected >= 0) versionSpinner.setSelection(selected);
                }
                versionsButton.setEnabled(true);
                versionsButton.setText("ATUALIZAR VERSÕES");
            });
        });
    }


    private String latestVersionFromDisplay(String text) {
        String prefix = "Última release estável: ";
        for (String line : text.split("\\n")) {
            if (line.startsWith(prefix)) return line.substring(prefix.length()).trim();
        }
        return "";
    }

    private void filterVersions() {
        if (versionSpinner == null || typeSpinner == null || versionSearch == null) return;
        String query = versionSearch.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
        int filter = typeSpinner.getSelectedItemPosition();
        getSharedPreferences("launcher_preferences", MODE_PRIVATE).edit()
                .putInt("version_type_filter", filter).apply();
        String previous = versionSpinner.getSelectedItem() == null ? ""
                : String.valueOf(versionSpinner.getSelectedItem());
        List<String> ids = new ArrayList<>(), urls = new ArrayList<>(), types = new ArrayList<>();
        synchronized (allVersionIds) {
            for (int i = 0; i < allVersionIds.size(); i++) {
                String type = allVersionTypes.get(i);
                boolean matchesType = filter == 0
                        || (filter == 1 && "release".equals(type))
                        || (filter == 2 && "snapshot".equals(type))
                        || (filter == 3 && "old_beta".equals(type))
                        || (filter == 4 && "old_alpha".equals(type));
                if (!matchesType || !allVersionIds.get(i).toLowerCase(java.util.Locale.ROOT).contains(query)) continue;
                ids.add(allVersionIds.get(i)); urls.add(allVersionUrls.get(i)); types.add(type);
            }
        }
        synchronized (visibleVersionIds) {
            visibleVersionIds.clear(); visibleVersionIds.addAll(ids);
            visibleVersionUrls.clear(); visibleVersionUrls.addAll(urls);
            visibleVersionTypes.clear(); visibleVersionTypes.addAll(types);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, ids);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        updatingVersionControls = true;
        versionSpinner.setAdapter(adapter);
        int selection = ids.indexOf(previous);
        if (selection < 0) {
            String saved = getSharedPreferences("launcher_preferences", MODE_PRIVATE)
                    .getString("selected_version", "");
            selection = ids.indexOf(saved);
        }
        if (selection >= 0) versionSpinner.setSelection(selection);
        updatingVersionControls = false;
        if (versionsOutput != null && !allVersionIds.isEmpty()) {
            versionsOutput.setText("Versões no manifesto: " + allVersionIds.size()
                    + "\\nResultados deste filtro: " + ids.size()
                    + "\\nÚltima release estável: " + getSharedPreferences("launcher_preferences", MODE_PRIVATE)
                    .getString("latest_release", "consulte o manifesto"));
        }
    }

    private void loadSelectedVersionMetadata() {
        if (versionSpinner == null || versionSpinner.getSelectedItem() == null) {
            Toast.makeText(this, "Consulte o manifesto e escolha uma versão primeiro.", Toast.LENGTH_SHORT).show();
            return;
        }
        String selected = String.valueOf(versionSpinner.getSelectedItem());
        int index;
        synchronized (visibleVersionIds) { index = visibleVersionIds.indexOf(selected); }
        if (index < 0) return;
        final String metadataUrl;
        final String selectedType;
        synchronized (visibleVersionIds) {
            if (index >= visibleVersionUrls.size() || index >= visibleVersionTypes.size()) return;
            metadataUrl = visibleVersionUrls.get(index);
            selectedType = visibleVersionTypes.get(index);
        }
        getSharedPreferences("launcher_preferences", MODE_PRIVATE).edit()
                .putString("selected_version", selected).apply();
        metadataButton.setEnabled(false);
        metadataButton.setText("CARREGANDO METADADOS...");
        ioExecutor.execute(() -> {
            HttpURLConnection connection = null;
            String result;
            try {
                if (!metadataUrl.startsWith("https://piston-meta.mojang.com/")) {
                    throw new IllegalStateException("URL de metadados fora do domínio oficial esperado.");
                }
                connection = (HttpURLConnection) new URL(metadataUrl).openConnection();
                connection.setConnectTimeout(12000);
                connection.setReadTimeout(12000);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("User-Agent", "MikaelLauncherPlus/0.1.0");
                int status = connection.getResponseCode();
                if (status != HttpURLConnection.HTTP_OK) throw new IllegalStateException("O servidor respondeu HTTP " + status + ".");
                String body;
                try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192]; int total = 0, count;
                    while ((count = input.read(buffer)) != -1) {
                        total += count;
                        if (total > MAX_MANIFEST_BYTES) throw new IllegalStateException("Metadados excederam o limite.");
                        output.write(buffer, 0, count);
                    }
                    body = output.toString(StandardCharsets.UTF_8.name());
                }
                JSONObject version = new JSONObject(body);
                JSONObject downloads = version.optJSONObject("downloads");
                JSONObject client = downloads == null ? null : downloads.optJSONObject("client");
                JSONObject javaVersion = version.optJSONObject("javaVersion");
                result = "Versão: " + version.optString("id", selected)
                        + "\\nTipo: " + version.optString("type", selectedType)
                        + "\\nData: " + version.optString("releaseTime", "não informada")
                        + "\\nJava requerida: " + (javaVersion == null ? "não informada" : javaVersion.optString("majorVersion", "não informada"))
                        + "\\nArquivo client: " + (client == null ? "não disponível" : client.optString("url", "não disponível"))
                        + "\\nSHA-1: " + (client == null ? "não disponível" : client.optString("sha1", "não disponível"))
                        + "\\nTamanho do client: " + (client == null ? "não informado" : client.optString("size", "não informado"))
                        + "\\n\\nMetadados consultados; download e inicialização do jogo ainda não estão implementados.";
            } catch (Exception exception) {
                String message = exception.getMessage();
                result = "Não foi possível obter os metadados da versão.\\n"
                        + (message == null ? exception.getClass().getSimpleName() : message);
            } finally { if (connection != null) connection.disconnect(); }
            final String displayResult = result;
            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed() || versionsOutput == null || metadataButton == null) return;
                lastMetadataText = displayResult;
                versionsOutput.setText(displayResult);
                metadataButton.setEnabled(true);
                metadataButton.setText("VER METADADOS DA VERSÃO");
                copyMetadataButton.setEnabled(true);
            });
        });
    }

    private void copyLastMetadata() {
        if (lastMetadataText == null || lastMetadataText.trim().isEmpty()) {
            Toast.makeText(this, "Consulte os metadados de uma versão primeiro.", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("Metadados Minecraft", lastMetadataText));
            Toast.makeText(this, "Metadados copiados.", Toast.LENGTH_SHORT).show();
        }
    }

    private void refreshDeviceDiagnostics() {
        if (deviceOutput == null) return;
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        if (manager != null) manager.getMemoryInfo(info);
        long availableMb = info.availMem / (1024L * 1024L);
        long totalMb = info.totalMem / (1024L * 1024L);
        String device = Build.MANUFACTURER + " " + Build.MODEL;
        deviceOutput.setText("Dispositivo: " + device
                + "\\nAndroid: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")"
                + "\\nMemória RAM total reportada: " + totalMb + " MB"
                + "\\nRAM disponível neste momento: " + availableMb + " MB"
                + "\\nHeap máxima do app: " + (Runtime.getRuntime().maxMemory() / (1024L * 1024L)) + " MB"
                + "\\nDiagnóstico informativo; não altera nem reserva RAM para o Minecraft.");
    }

    private void createLocalInstance() {
        if (instanceManager == null || instanceNameInput == null || loaderSpinner == null) {
            Toast.makeText(this, "O gerenciador de instâncias não está disponível.", Toast.LENGTH_LONG).show();
            return;
        }
        String version = versionSpinner != null && versionSpinner.getSelectedItem() != null
                ? String.valueOf(versionSpinner.getSelectedItem())
                : getSharedPreferences("launcher_preferences", MODE_PRIVATE).getString("selected_version", "");
        if (version == null || version.trim().isEmpty()) {
            Toast.makeText(this, "Consulte as versões oficiais e selecione uma versão antes de criar a instância.", Toast.LENGTH_LONG).show();
            return;
        }
        String name = instanceNameInput.getText().toString();
        String loader = loaderSpinner.getSelectedItem() == null
                ? "Vanilla" : String.valueOf(loaderSpinner.getSelectedItem());
        try {
            JSONObject created = instanceManager.createInstance(name, version, loader);
            getSharedPreferences("launcher_preferences", MODE_PRIVATE).edit()
                    .putString("active_instance_name", created.getString("name")).apply();
            instanceNameInput.setText("");
            refreshInstancesList();
            Toast.makeText(this, "Perfil local criado. O jogo e o carregador ainda não foram instalados.", Toast.LENGTH_LONG).show();
        } catch (Exception exception) {
            Toast.makeText(this, safeExceptionMessage(exception), Toast.LENGTH_LONG).show();
        }
    }

    private void stageVanillaArtifacts() {
        final String instanceName = selectedInstanceName();
        if (instanceManager == null || versionArtifactInstaller == null || instanceName == null) {
            Toast.makeText(this, "Crie ou selecione uma instância primeiro.", Toast.LENGTH_LONG).show();
            return;
        }
        if (loaderSpinner == null || !"Vanilla".equals(String.valueOf(loaderSpinner.getSelectedItem()))) {
            new AlertDialog.Builder(this)
                    .setTitle("Escolha Vanilla")
                    .setMessage("Esta etapa instala apenas arquivos do cliente Vanilla. Forge, Fabric e OptiFine serão tratados em outra etapa.")
                    .setPositiveButton("Entendi", (dialog, which) -> dialog.dismiss())
                    .show();
            return;
        }
        final String version = getSelectedVersionForVanillaTest();
        final String metadataUrl = getSelectedVersionMetadataUrl(version);
        if (version.isEmpty() || metadataUrl == null) {
            Toast.makeText(this, "Consulte o manifesto oficial e selecione uma versão primeiro.", Toast.LENGTH_LONG).show();
            return;
        }
        if (stageVanillaButton != null) {
            stageVanillaButton.setEnabled(false);
            stageVanillaButton.setText("BAIXANDO E VERIFICANDO...");
        }
        if (installStatusOutput != null) {
            installStatusOutput.setText("Baixando metadados oficiais e JAR do cliente para " + instanceName
                    + " (" + version + "). O jogo ainda não ficará pronto para iniciar.");
        }
        ioExecutor.execute(() -> {
            String resultText;
            boolean success = false;
            try {
                JSONObject result = versionArtifactInstaller.stageVanillaClient(instanceName, version, metadataUrl);
                long bytes = result.optLong("clientBytes", 0L);
                resultText = "Arquivos do cliente verificados para " + version
                        + "\nTamanho: " + (bytes / (1024L * 1024L)) + " MB"
                        + "\nSHA-1: " + result.optString("clientSha1", "")
                        + "\nEstado: arquivos staged, não jogável ainda."
                        + "\nFaltam bibliotecas, assets, runtime Java e conexão com a pasta ativa do motor.";
                success = true;
            } catch (Exception exception) {
                resultText = "Falha ao baixar/verificar " + version + ": " + safeExceptionMessage(exception);
            }
            final String message = resultText;
            final boolean completed = success;
            mainHandler.post(() -> {
                if (installStatusOutput != null) installStatusOutput.setText(message);
                if (stageVanillaButton != null) {
                    stageVanillaButton.setEnabled(true);
                    stageVanillaButton.setText("BAIXAR ARQUIVOS OFICIAIS VANILLA");
                }
                refreshInstancesList();
                Toast.makeText(this, completed ? "Arquivos oficiais baixados e verificados." : "Instalação parcial falhou.",
                        Toast.LENGTH_LONG).show();
            });
        });
    }

    private String getSelectedVersionMetadataUrl(String version) {
        synchronized (visibleVersionIds) {
            int index = visibleVersionIds.indexOf(version);
            if (index < 0 || index >= visibleVersionUrls.size()) return null;
            String url = visibleVersionUrls.get(index);
            return url != null && url.startsWith("https://piston-meta.mojang.com/") ? url : null;
        }
    }

    private void refreshInstancesList() {
        if (instanceManager == null || instanceSpinner == null || instancesOutput == null) return;
        try {
            JSONArray records = instanceManager.getInstances();
            List<String> names = new ArrayList<>();
            for (int i = 0; i < records.length(); i++) {
                String name = records.getJSONObject(i).optString("name", "").trim();
                if (!name.isEmpty()) names.add(name);
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_item, names);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            SharedPreferences preferences = getSharedPreferences("launcher_preferences", MODE_PRIVATE);
            String active = preferences.getString("active_instance_name", "");
            updatingInstances = true;
            instanceSpinner.setAdapter(adapter);
            int selected = names.indexOf(active);
            if (selected < 0 && !names.isEmpty()) selected = 0;
            if (selected >= 0) {
                instanceSpinner.setSelection(selected);
                preferences.edit().putString("active_instance_name", names.get(selected)).apply();
            } else {
                preferences.edit().remove("active_instance_name").apply();
            }
            updatingInstances = false;
            showSelectedInstance();
        } catch (Exception exception) {
            updatingInstances = false;
            instancesOutput.setText("Não foi possível ler as instâncias locais: " + safeExceptionMessage(exception));
        }
    }

    private void showSelectedInstance() {
        if (instanceManager == null || instanceSpinner == null || instancesOutput == null) return;
        refreshModsList();
        try {
            JSONArray records = instanceManager.getInstances();
            Object selected = instanceSpinner.getSelectedItem();
            if (selected == null) {
                instancesOutput.setText("Instâncias locais: " + records.length()
                        + "\\nCrie um perfil depois de consultar e selecionar uma versão oficial.");
                return;
            }
            String name = String.valueOf(selected);
            JSONObject selectedRecord = null;
            for (int i = 0; i < records.length(); i++) {
                JSONObject candidate = records.getJSONObject(i);
                if (name.equals(candidate.optString("name", ""))) {
                    selectedRecord = candidate;
                    break;
                }
            }
            if (selectedRecord == null) {
                instancesOutput.setText("A instância selecionada não está mais no índice local. Atualize a lista.");
                return;
            }
            instancesOutput.setText("Instâncias locais: " + records.length()
                    + "\\nNome: " + selectedRecord.optString("name", "—")
                    + "\\nVersão escolhida: " + selectedRecord.optString("version", "—")
                    + "\\nPerfil de carregador: " + selectedRecord.optString("loader", "Vanilla")
                    + "\\nPasta privada: " + selectedRecord.optString("relativePath", "instances")
                    + "\\nEstado: " + selectedRecord.optString("installStatus", "perfil criado; instalação não iniciada")
                    + "\\nVersão baixada: " + selectedRecord.optString("stagedVersion", "nenhuma")
                    + "\\nJogo jogável: não; arquivos de cliente, bibliotecas/assets/runtime e ligação ao motor ainda incompletos.");
        } catch (Exception exception) {
            instancesOutput.setText("Erro ao ler os detalhes: " + safeExceptionMessage(exception));
        }
    }

    private String selectedInstanceName() {
        if (instanceSpinner == null || instanceSpinner.getSelectedItem() == null) return null;
        String name = String.valueOf(instanceSpinner.getSelectedItem()).trim();
        return name.isEmpty() ? null : name;
    }

    private void openModPicker() {
        if (selectedInstanceName() == null || instanceModManager == null) {
            Toast.makeText(this, "Crie ou selecione uma instância antes de importar mods.", Toast.LENGTH_LONG).show();
            return;
        }
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        picker.setType("*/*");
        try {
            startActivityForResult(picker, REQUEST_PICK_MOD_FILE);
        } catch (Exception exception) {
            Toast.makeText(this, "Não foi possível abrir o seletor de arquivos.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PICK_MOD_FILE || resultCode != RESULT_OK
                || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        String fileName = queryDisplayName(uri);
        if (fileName == null || !fileName.toLowerCase(java.util.Locale.ROOT).endsWith(".jar")) {
            Toast.makeText(this, "Escolha um arquivo .jar de mod.", Toast.LENGTH_LONG).show();
            return;
        }
        String instanceName = selectedInstanceName();
        if (instanceName == null || instanceModManager == null) {
            Toast.makeText(this, "Selecione uma instância antes de importar.", Toast.LENGTH_LONG).show();
            return;
        }
        final String selectedFileName = fileName;
        new AlertDialog.Builder(this)
                .setTitle("Importar arquivo JAR?")
                .setMessage("Arquivo: " + selectedFileName
                        + "\\nDestino: " + instanceName + "/mods"
                        + "\\n\\nUm JAR pode executar código no computador/dispositivo em que é usado. Continue apenas se confiar na origem.")
                .setNegativeButton("Cancelar", (dialog, which) -> dialog.dismiss())
                .setPositiveButton("Importar", (dialog, which) -> importModFile(uri, instanceName, selectedFileName))
                .show();
    }

    private String queryDisplayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        } catch (Exception ignored) { }
        String last = uri.getLastPathSegment();
        if (last == null) return null;
        int separator = last.lastIndexOf('/');
        return separator >= 0 ? last.substring(separator + 1) : last;
    }

    private void importModFile(Uri uri, String instanceName, String fileName) {
        if (instanceModManager == null) return;
        try {
            InputStream stream = getContentResolver().openInputStream(uri);
            if (stream == null) throw new java.io.IOException("O arquivo selecionado não pôde ser lido.");
            JSONObject imported = instanceModManager.importMod(instanceName, fileName, stream);
            refreshModsList();
            String hash = imported.optString("sha256", "");
            Toast.makeText(this, "Mod copiado para a instância. SHA-256: "
                    + (hash.length() > 16 ? hash.substring(0, 16) + "…" : hash), Toast.LENGTH_LONG).show();
        } catch (Exception exception) {
            Toast.makeText(this, safeExceptionMessage(exception), Toast.LENGTH_LONG).show();
        }
    }

    private void refreshModsList() {
        if (modsOutput == null) return;
        String instanceName = selectedInstanceName();
        if (instanceName == null || instanceModManager == null) {
            modsOutput.setText("Selecione uma instância para ver os mods.");
            return;
        }
        try {
            JSONArray mods = instanceModManager.listMods(instanceName);
            StringBuilder summary = new StringBuilder("JARs guardados nesta instância: ")
                    .append(mods.length());
            int shown = Math.min(mods.length(), 18);
            for (int i = 0; i < shown; i++) {
                JSONObject mod = mods.getJSONObject(i);
                long mbHundredths = (mod.optLong("sizeBytes", 0) * 100L) / (1024L * 1024L);
                summary.append("\\n• ").append(mod.optString("name", "arquivo.jar"))
                        .append(" — ").append(mbHundredths / 100).append('.')
                        .append(String.format(java.util.Locale.ROOT, "%02d", mbHundredths % 100))
                        .append(" MB");
            }
            if (mods.length() > shown) summary.append("\\n… e mais ").append(mods.length() - shown).append(" arquivo(s).");
            if (mods.length() == 0) summary.append("\\nNenhum JAR importado ainda.");
            modsOutput.setText(summary.toString());
        } catch (Exception exception) {
            modsOutput.setText("Não foi possível listar os mods: " + safeExceptionMessage(exception));
        }
    }

    private void chooseModToRemove() {
        String instanceName = selectedInstanceName();
        if (instanceName == null || instanceModManager == null) {
            Toast.makeText(this, "Selecione uma instância primeiro.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            JSONArray mods = instanceModManager.listMods(instanceName);
            if (mods.length() == 0) {
                Toast.makeText(this, "Esta instância não tem arquivos JAR para remover.", Toast.LENGTH_SHORT).show();
                return;
            }
            String[] names = new String[mods.length()];
            for (int i = 0; i < mods.length(); i++) names[i] = mods.getJSONObject(i).optString("name", "arquivo.jar");
            new AlertDialog.Builder(this)
                    .setTitle("Escolha o JAR para remover")
                    .setItems(names, (dialog, which) -> confirmRemoveMod(instanceName, names[which]))
                    .setNegativeButton("Cancelar", (dialog, which) -> dialog.dismiss())
                    .show();
        } catch (Exception exception) {
            Toast.makeText(this, safeExceptionMessage(exception), Toast.LENGTH_LONG).show();
        }
    }

    private void confirmRemoveMod(String instanceName, String fileName) {
        new AlertDialog.Builder(this)
                .setTitle("Remover mod?")
                .setMessage("Excluir " + fileName + " desta instância?")
                .setNegativeButton("Cancelar", (dialog, which) -> dialog.dismiss())
                .setPositiveButton("Remover", (dialog, which) -> {
                    try {
                        if (instanceModManager != null && instanceModManager.deleteMod(instanceName, fileName)) {
                            refreshModsList();
                            Toast.makeText(this, "Arquivo removido da instância.", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, "Arquivo não encontrado.", Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception exception) {
                        Toast.makeText(this, safeExceptionMessage(exception), Toast.LENGTH_LONG).show();
                    }
                }).show();
    }

    private void confirmDeleteSelectedInstance() {
        if (instanceSpinner == null || instanceSpinner.getSelectedItem() == null) {
            Toast.makeText(this, "Não há instância selecionada para excluir.", Toast.LENGTH_SHORT).show();
            return;
        }
        String name = String.valueOf(instanceSpinner.getSelectedItem());
        new AlertDialog.Builder(this)
                .setTitle("Excluir instância?")
                .setMessage("O perfil local '" + name + "' e seus arquivos serão removidos. Esta ação não pode ser desfeita.")
                .setNegativeButton("Cancelar", (dialog, which) -> dialog.dismiss())
                .setPositiveButton("Excluir", (dialog, which) -> {
                    try {
                        boolean deleted = instanceManager != null && instanceManager.deleteInstance(name);
                        if (deleted) {
                            getSharedPreferences("launcher_preferences", MODE_PRIVATE).edit()
                                    .remove("active_instance_name").apply();
                            refreshInstancesList();
                            Toast.makeText(this, "Instância excluída.", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, "A instância não foi encontrada.", Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception exception) {
                        Toast.makeText(this, safeExceptionMessage(exception), Toast.LENGTH_LONG).show();
                    }
                }).show();
    }

    private String safeExceptionMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.trim().isEmpty()) return exception.getClass().getSimpleName();
        return message;
    }

    private String getSelectedVersionForVanillaTest() {
        if (versionSpinner != null) {
            int position = versionSpinner.getSelectedItemPosition();
            if (position >= 0 && position < visibleVersionIds.size()) {
                return visibleVersionIds.get(position);
            }
        }
        return getSharedPreferences("launcher_preferences", MODE_PRIVATE)
                .getString("selected_version", "nenhuma versão selecionada");
    }

    private void openEngineForVanillaTest() {
        if (MinecraftEngineBridge.openInstalledEngine(this)) {
            Toast.makeText(this,
                    "Motor aberto. Selecione nele a mesma versão Vanilla, instale-a e teste um mundo. "
                            + "A versão escolhida nesta tela não é transferida automaticamente.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Motor do Minecraft não encontrado")
                .setMessage("Não foi encontrada uma instalação compatível do motor Amethyst/Pojav. "
                        + "O teste real de Vanilla depende da integração do motor e de um teste em aparelho. "
                        + "Nenhum APK foi gerado.")
                .setPositiveButton("Entendi", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void addModule(LinearLayout parent, String heading, String description) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setPadding(dp(15), dp(14), dp(15), dp(14));
        item.setBackground(roundRect(SURFACE, 14, 0x0020352B, 0));
        LinearLayout.LayoutParams itemParams = params(-1, -2);
        itemParams.topMargin = dp(10);
        parent.addView(item, itemParams);
        item.addView(text(heading, 16, TEXT, true));
        TextView subtitle = text(description, 13, MUTED, false);
        LinearLayout.LayoutParams subtitleParams = params(-1, -2);
        subtitleParams.topMargin = dp(5);
        item.addView(subtitle, subtitleParams);
    }

    private Button button(String label, boolean enabled) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(enabled ? BACKGROUND : MUTED);
        button.setEnabled(enabled);
        button.setBackground(roundRect(enabled ? EMERALD : SURFACE, 14, 0xFF20352B, 1));
        return button;
    }

    private TextView text(String value, int sizeSp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setIncludeFontPadding(true);
        return view;
    }

    private GradientDrawable roundRect(int color, int radiusDp, int strokeColor, int strokeDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) shape.setStroke(dp(strokeDp), strokeColor);
        return shape;
    }

    private LinearLayout.LayoutParams params(int width, int height) {
        return new LinearLayout.LayoutParams(width, height);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
