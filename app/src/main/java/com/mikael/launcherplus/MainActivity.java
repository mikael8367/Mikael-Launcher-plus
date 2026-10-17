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
import android.widget.HorizontalScrollView;
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
    private static final int SURFACE_RAISED = 0xFF142019;
    private static final int BORDER = 0xFF294738;
    private static final int EMERALD = 0xFF00D68F;
    private static final int EMERALD_DIM = 0xFF153D2D;
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
    private TextView selectedVersionSummary;
    private EditText instanceNameInput;
    private Spinner instanceSpinner;
    private Spinner loaderSpinner;
    private TextView instancesOutput;
    private InstanceManager instanceManager;
    private InstanceModManager instanceModManager;
    private ModCompatibilityChecker modCompatibilityChecker;
    private DiagnosticLogManager diagnosticLogManager;
    private VersionArtifactInstaller versionArtifactInstaller;
    private InstanceReadinessChecker instanceReadinessChecker;
    private VanillaAssetInstaller vanillaAssetInstaller;
    private VanillaLibraryInstaller vanillaLibraryInstaller;
    private final java.util.concurrent.atomic.AtomicBoolean cancelAssetsRequested =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    private final java.util.concurrent.atomic.AtomicBoolean cancelLibrariesRequested =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    private Button stageVanillaButton;
    private Button diagnoseInstanceButton;
    private Button stageAssetsButton;
    private Button cancelAssetsButton;
    private Button stageLibrariesButton;
    private Button cancelLibrariesButton;
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
        try {
            diagnosticLogManager = new DiagnosticLogManager(this);
            diagnosticLogManager.installCrashHandler();
        } catch (Exception ignored) {
            // Diagnostics are optional and must not block launcher startup.
        }
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
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BACKGROUND);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BACKGROUND);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(22), dp(24), dp(22), dp(28));
        scroll.addView(page);

        LinearLayout brandRow = new LinearLayout(this);
        brandRow.setOrientation(LinearLayout.HORIZONTAL);
        brandRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView brandMark = text("M+", 19, BACKGROUND, true);
        brandMark.setGravity(Gravity.CENTER);
        brandMark.setBackground(roundRect(EMERALD, 15, 0xFF4AF0B0, 1));
        brandRow.addView(brandMark, params(dp(50), dp(50)));

        LinearLayout brandCopy = new LinearLayout(this);
        brandCopy.setOrientation(LinearLayout.VERTICAL);
        brandCopy.setPadding(dp(12), 0, dp(6), 0);
        brandCopy.addView(text("MIKAEL LAUNCHER PLUS", 13, TEXT, true));
        TextView platformLabel = text("MINECRAFT JAVA  •  ANDROID", 10, MUTED, true);
        LinearLayout.LayoutParams platformParams = params(-2, -2);
        platformParams.topMargin = dp(4);
        brandCopy.addView(platformLabel, platformParams);
        brandRow.addView(brandCopy, new LinearLayout.LayoutParams(0, -2, 1));

        TextView previewTag = text("PRÉVIA", 10, BACKGROUND, true);
        previewTag.setGravity(Gravity.CENTER);
        previewTag.setPadding(dp(10), dp(7), dp(10), dp(7));
        previewTag.setTextColor(EMERALD);
        previewTag.setBackground(roundRect(EMERALD_DIM, 12, BORDER, 1));
        brandRow.addView(previewTag, params(-2, -2));
        brandRow.setTag("inicio");
        page.addView(brandRow);

        TextView title = text("Seu Minecraft.\nDo seu jeito.", 29, TEXT, true);
        LinearLayout.LayoutParams titleParams = params(-1, -2);
        titleParams.topMargin = dp(22);
        title.setTag("inicio");
        page.addView(title, titleParams);

        TextView intro = text(
                "Organize versões, instâncias, mods e diagnóstico em um só lugar. "
                        + "A inicialização integrada do jogo ainda está em desenvolvimento.",
                14, MUTED, false);
        LinearLayout.LayoutParams introParams = params(-1, -2);
        introParams.topMargin = dp(10);
        intro.setTag("inicio");
        page.addView(intro, introParams);

        HorizontalScrollView sectionScroll = new HorizontalScrollView(this);
        sectionScroll.setHorizontalScrollBarEnabled(false);
        sectionScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout sectionRail = new LinearLayout(this);
        sectionRail.setOrientation(LinearLayout.HORIZONTAL);
        sectionRail.setGravity(Gravity.CENTER_VERTICAL);

        TextView navVersions = navChip("Versões");
        TextView navInstances = navChip("Instâncias");
        TextView navMods = navChip("Mods");
        TextView navDevice = navChip("Dispositivo");
        TextView navSettings = navChip("Ajustes");
        sectionRail.addView(navVersions, params(-2, -1));
        LinearLayout.LayoutParams navChipParams = params(-2, -1);
        navChipParams.leftMargin = dp(8);
        sectionRail.addView(navInstances, navChipParams);
        navChipParams = params(-2, -1);
        navChipParams.leftMargin = dp(8);
        sectionRail.addView(navMods, navChipParams);
        navChipParams = params(-2, -1);
        navChipParams.leftMargin = dp(8);
        sectionRail.addView(navDevice, navChipParams);
        navChipParams = params(-2, -1);
        navChipParams.leftMargin = dp(8);
        sectionRail.addView(navSettings, navChipParams);
        sectionScroll.addView(sectionRail);
        LinearLayout.LayoutParams sectionScrollParams = params(-1, dp(42));
        sectionScrollParams.topMargin = dp(18);
        sectionScrollParams.bottomMargin = dp(2);
        sectionScroll.setTag("ocultar");
        page.addView(sectionScroll, sectionScrollParams);

        LinearLayout statusCard = new LinearLayout(this);
        statusCard.setOrientation(LinearLayout.VERTICAL);
        statusCard.setPadding(dp(18), dp(18), dp(18), dp(18));
        GradientDrawable heroBackground = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF183829, 0xFF101813});
        heroBackground.setCornerRadius(dp(20));
        heroBackground.setStroke(dp(1), BORDER);
        statusCard.setBackground(heroBackground);
        LinearLayout.LayoutParams cardParams = params(-1, -2);
        cardParams.topMargin = dp(18);
        statusCard.setTag("inicio");
        page.addView(statusCard, cardParams);

        statusCard.addView(text("ESTADO DO PROJETO  •  0.1.0", 12, EMERALD, true));
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

        Button play = primaryButton("JOGAR MINECRAFT", true);
        LinearLayout.LayoutParams playParams = params(-1, dp(56));
        playParams.topMargin = dp(20);
        play.setTag("inicio");
        page.addView(play, playParams);
        play.setOnClickListener(v -> openEngineForVanillaTest());

        LinearLayout selectedVersionCard = new LinearLayout(this);
        selectedVersionCard.setOrientation(LinearLayout.VERTICAL);
        selectedVersionCard.setPadding(dp(16), dp(14), dp(16), dp(14));
        selectedVersionCard.setBackground(roundRect(SURFACE_RAISED, 18, BORDER, 1));
        selectedVersionCard.setTag("inicio");
        LinearLayout.LayoutParams selectedVersionCardParams = params(-1, -2);
        selectedVersionCardParams.topMargin = dp(12);
        page.addView(selectedVersionCard, selectedVersionCardParams);
        selectedVersionCard.addView(text("VERSÃO SELECIONADA", 11, EMERALD, true));
        selectedVersionSummary = text(getSharedPreferences("launcher_preferences", MODE_PRIVATE)
                .getString("selected_version", "Nenhuma versão selecionada"), 17, TEXT, true);
        LinearLayout.LayoutParams selectedSummaryParams = params(-1, -2);
        selectedSummaryParams.topMargin = dp(6);
        selectedVersionCard.addView(selectedVersionSummary, selectedSummaryParams);
        Button configureVersionButton = button("CONFIGURAR VERSÃO", true);
        LinearLayout.LayoutParams configureVersionParams = params(-1, dp(44));
        configureVersionParams.topMargin = dp(8);
        selectedVersionCard.addView(configureVersionButton, configureVersionParams);
        configureVersionButton.setOnClickListener(v -> showDashboardTab(page, "versoes"));

        LinearLayout versionsCard = new LinearLayout(this);
        versionsCard.setOrientation(LinearLayout.VERTICAL);
        versionsCard.setPadding(dp(18), dp(18), dp(18), dp(18));
        versionsCard.setBackground(roundRect(SURFACE_RAISED, 20, BORDER, 1));
        LinearLayout.LayoutParams versionsCardParams = params(-1, -2);
        versionsCardParams.topMargin = dp(20);
        versionsCard.setTag("versoes");
        page.addView(versionsCard, versionsCardParams);
        navVersions.setOnClickListener(v -> scroll.smoothScrollTo(0, Math.max(0, versionsCard.getTop() - dp(12))));

        versionsCard.addView(text("VERSÕES OFICIAIS", 12, EMERALD, true));
        versionsOutput = text(
                "Toque no botão para consultar as versões publicadas pela Mojang.",
                14, MUTED, false);
        LinearLayout.LayoutParams outputParams = params(-1, -2);
        outputParams.topMargin = dp(10);
        versionsCard.addView(versionsOutput, outputParams);

        versionsButton = primaryButton("CONSULTAR VERSÕES OFICIAIS", true);
        LinearLayout.LayoutParams versionsButtonParams = params(-1, dp(50));
        versionsButtonParams.topMargin = dp(14);
        versionsCard.addView(versionsButton, versionsButtonParams);
        versionsButton.setOnClickListener(v -> loadOfficialVersions());

        versionSpinner = new Spinner(this);
        versionSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (updatingVersionControls || position < 0 || position >= visibleVersionIds.size()) return;
                String selectedId = visibleVersionIds.get(position);
                getSharedPreferences("launcher_preferences", MODE_PRIVATE).edit()
                        .putString("selected_version", selectedId).apply();
                if (selectedVersionSummary != null) selectedVersionSummary.setText(selectedId);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        LinearLayout.LayoutParams spinnerParams = params(-1, dp(50));
        spinnerParams.topMargin = dp(12);
        versionsCard.addView(versionSpinner, spinnerParams);

        versionsCard.addView(text("1. Selecione a versão do Minecraft", 14, TEXT, true));

        TextView creationFlowNote = text(
                "Escolha a versão do Minecraft acima e depois selecione o modloader. "
                        + "O motor integrado instala os componentes compatíveis antes de iniciar o jogo.",
                13, MUTED, false);
        LinearLayout.LayoutParams creationFlowNoteParams = params(-1, -2);
        creationFlowNoteParams.topMargin = dp(8);
        versionsCard.addView(creationFlowNote, creationFlowNoteParams);

        Button createVersionButton = primaryButton("CRIAR VERSÃO E BAIXAR", true);
        LinearLayout.LayoutParams createVersionParams = params(-1, dp(50));
        createVersionParams.topMargin = dp(10);
        versionsCard.addView(createVersionButton, createVersionParams);
        createVersionButton.setOnClickListener(v -> createLocalInstance());

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
        versionSearch.setBackground(roundRect(BACKGROUND, 12, BORDER, 1));
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
                "Perfis locais e importação de mods implementados · loaders e dependências ainda pendentes");
        addModule(page, "Contas e sessões",
                "Planejado · autenticação oficial e armazenamento seguro");
        addModule(page, "Desempenho e diagnóstico",
                "Informações reais do dispositivo e memória disponíveis abaixo.");

        LinearLayout instancesCard = new LinearLayout(this);
        instancesCard.setOrientation(LinearLayout.VERTICAL);
        instancesCard.setPadding(dp(18), dp(18), dp(18), dp(18));
        instancesCard.setBackground(roundRect(SURFACE_RAISED, 20, BORDER, 1));
        LinearLayout.LayoutParams instancesCardParams = params(-1, -2);
        instancesCardParams.topMargin = dp(14);
        instancesCard.setTag("instancias");
        page.addView(instancesCard, instancesCardParams);
        navInstances.setOnClickListener(v -> scroll.smoothScrollTo(0, Math.max(0, instancesCard.getTop() - dp(12))));
        instancesCard.addView(text("INSTÂNCIAS LOCAIS", 12, EMERALD, true));
        TextView instancesNote = text(
                "Perfis locais auxiliares para organizar mods, saves, configurações e logs. "
                        + "O jogo é instalado e executado pelo motor integrado quando disponível.",
                13, MUTED, false);
        LinearLayout.LayoutParams instancesNoteParams = params(-1, -2);
        instancesNoteParams.topMargin = dp(8);
        instancesCard.addView(instancesNote, instancesNoteParams);

        TextView instancesHint = text(
                "As instalações do motor aparecem no launcher integrado. Esta área organiza os perfis locais auxiliares do projeto.",
                13, MUTED, false);
        LinearLayout.LayoutParams instancesHintParams = params(-1, -2);
        instancesHintParams.topMargin = dp(8);
        instancesCard.addView(instancesHint, instancesHintParams);

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

        stageVanillaButton = primaryButton("BAIXAR ARQUIVOS OFICIAIS VANILLA", true);
        LinearLayout.LayoutParams stageVanillaParams = params(-1, dp(50));
        stageVanillaParams.topMargin = dp(8);
        instancesCard.addView(stageVanillaButton, stageVanillaParams);
        stageVanillaButton.setOnClickListener(v -> stageVanillaArtifacts());

        stageAssetsButton = button("BAIXAR E VERIFICAR ASSETS", true);
        LinearLayout.LayoutParams stageAssetsParams = params(-1, dp(50));
        stageAssetsParams.topMargin = dp(8);
        instancesCard.addView(stageAssetsButton, stageAssetsParams);
        stageAssetsButton.setOnClickListener(v -> stageVanillaAssets());

        cancelAssetsButton = button("CANCELAR DOWNLOAD DE ASSETS", true);
        LinearLayout.LayoutParams cancelAssetsParams = params(-1, dp(46));
        cancelAssetsParams.topMargin = dp(6);
        instancesCard.addView(cancelAssetsButton, cancelAssetsParams);
        cancelAssetsButton.setEnabled(false);
        cancelAssetsButton.setOnClickListener(v -> {
            if (cancelAssetsRequested.compareAndSet(false, true)) {
                cancelAssetsButton.setText("CANCELANDO... OS ARQUIVOS COMPLETOS SERÃO MANTIDOS");
                Toast.makeText(this, "Cancelando com segurança; assets já verificados serão mantidos.", Toast.LENGTH_LONG).show();
            }
        });

        stageLibrariesButton = button("BAIXAR E VERIFICAR BIBLIOTECAS", true);
        LinearLayout.LayoutParams stageLibrariesParams = params(-1, dp(50));
        stageLibrariesParams.topMargin = dp(8);
        instancesCard.addView(stageLibrariesButton, stageLibrariesParams);
        stageLibrariesButton.setOnClickListener(v -> stageVanillaLibraries());

        cancelLibrariesButton = button("CANCELAR DOWNLOAD DE BIBLIOTECAS", true);
        LinearLayout.LayoutParams cancelLibrariesParams = params(-1, dp(46));
        cancelLibrariesParams.topMargin = dp(6);
        instancesCard.addView(cancelLibrariesButton, cancelLibrariesParams);
        cancelLibrariesButton.setEnabled(false);
        cancelLibrariesButton.setOnClickListener(v -> {
            if (cancelLibrariesRequested.compareAndSet(false, true)) {
                cancelLibrariesButton.setText("CANCELANDO DOWNLOAD...");
                Toast.makeText(this, "Os arquivos de biblioteca já verificados serão preservados.", Toast.LENGTH_LONG).show();
            }
        });

        diagnoseInstanceButton = button("VERIFICAR INTEGRIDADE DA INSTÂNCIA", true);
        LinearLayout.LayoutParams diagnoseInstanceParams = params(-1, dp(50));
        diagnoseInstanceParams.topMargin = dp(8);
        instancesCard.addView(diagnoseInstanceButton, diagnoseInstanceParams);
        diagnoseInstanceButton.setOnClickListener(v -> diagnoseSelectedInstance());

        Button deleteInstanceButton = button("EXCLUIR INSTÂNCIA SELECIONADA", true);
        LinearLayout.LayoutParams deleteInstanceParams = params(-1, dp(48));
        deleteInstanceParams.topMargin = dp(10);
        instancesCard.addView(deleteInstanceButton, deleteInstanceParams);
        deleteInstanceButton.setOnClickListener(v -> confirmDeleteSelectedInstance());

        try {
            instanceManager = new InstanceManager(this);
            instanceModManager = new InstanceModManager(instanceManager);
            modCompatibilityChecker = new ModCompatibilityChecker(instanceManager, instanceModManager);
            versionArtifactInstaller = new VersionArtifactInstaller(instanceManager);
            instanceReadinessChecker = new InstanceReadinessChecker(instanceManager);
            vanillaAssetInstaller = new VanillaAssetInstaller(instanceManager);
            vanillaLibraryInstaller = new VanillaLibraryInstaller(instanceManager);
            refreshInstancesList();
        } catch (Exception exception) {
            instancesOutput.setText("Gerenciador de instâncias indisponível: " + safeExceptionMessage(exception));
            createVersionButton.setEnabled(false);
            deleteInstanceButton.setEnabled(false);
            if (stageVanillaButton != null) stageVanillaButton.setEnabled(false);
            if (diagnoseInstanceButton != null) diagnoseInstanceButton.setEnabled(false);
            if (stageAssetsButton != null) stageAssetsButton.setEnabled(false);
            if (cancelAssetsButton != null) cancelAssetsButton.setEnabled(false);
            if (stageLibrariesButton != null) stageLibrariesButton.setEnabled(false);
            if (cancelLibrariesButton != null) cancelLibrariesButton.setEnabled(false);
        }

        LinearLayout modsCard = new LinearLayout(this);
        modsCard.setOrientation(LinearLayout.VERTICAL);
        modsCard.setPadding(dp(18), dp(18), dp(18), dp(18));
        modsCard.setBackground(roundRect(SURFACE_RAISED, 20, BORDER, 1));
        LinearLayout.LayoutParams modsCardParams = params(-1, -2);
        modsCardParams.topMargin = dp(14);
        modsCard.setTag("instancias");
        page.addView(modsCard, modsCardParams);
        navMods.setOnClickListener(v -> scroll.smoothScrollTo(0, Math.max(0, modsCard.getTop() - dp(12))));
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

        Button analyzeModsButton = button("ANALISAR COMPATIBILIDADE", true);
        LinearLayout.LayoutParams analyzeModsParams = params(-1, dp(48));
        analyzeModsParams.topMargin = dp(8);
        modsCard.addView(analyzeModsButton, analyzeModsParams);
        analyzeModsButton.setOnClickListener(v -> analyzeSelectedMods());
        refreshModsList();

        LinearLayout deviceCard = new LinearLayout(this);
        deviceCard.setOrientation(LinearLayout.VERTICAL);
        deviceCard.setPadding(dp(18), dp(18), dp(18), dp(18));
        deviceCard.setBackground(roundRect(SURFACE_RAISED, 20, BORDER, 1));
        LinearLayout.LayoutParams deviceCardParams = params(-1, -2);
        deviceCardParams.topMargin = dp(12);
        deviceCard.setTag("configuracoes");
        page.addView(deviceCard, deviceCardParams);
        navDevice.setOnClickListener(v -> scroll.smoothScrollTo(0, Math.max(0, deviceCard.getTop() - dp(12))));
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
        Button copyDiagnosticButton = button("COPIAR DIAGNÓSTICO SEGURO", true);
        LinearLayout.LayoutParams copyDiagnosticParams = params(-1, dp(48));
        copyDiagnosticParams.topMargin = dp(8);
        deviceCard.addView(copyDiagnosticButton, copyDiagnosticParams);
        copyDiagnosticButton.setOnClickListener(v -> copySafeDiagnosticReport());
        Button viewCrashButton = button("VER ÚLTIMA FALHA", false);
        LinearLayout.LayoutParams viewCrashParams = params(-1, dp(48));
        viewCrashParams.topMargin = dp(8);
        deviceCard.addView(viewCrashButton, viewCrashParams);
        viewCrashButton.setOnClickListener(v -> showLastCrashLog());
        refreshDeviceDiagnostics();

        LinearLayout settingsCard = new LinearLayout(this);
        settingsCard.setOrientation(LinearLayout.VERTICAL);
        settingsCard.setPadding(dp(18), dp(18), dp(18), dp(18));
        settingsCard.setBackground(roundRect(SURFACE_RAISED, 20, BORDER, 1));
        LinearLayout.LayoutParams settingsCardParams = params(-1, -2);
        settingsCardParams.topMargin = dp(14);
        settingsCard.setTag("configuracoes");
        page.addView(settingsCard, settingsCardParams);
        navSettings.setOnClickListener(v -> scroll.smoothScrollTo(0, Math.max(0, settingsCard.getTop() - dp(12))));
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

        TextView languageLabel = text("Idioma", 15, TEXT, true);
        LinearLayout.LayoutParams languageLabelParams = params(-1, -2);
        languageLabelParams.topMargin = dp(12);
        settingsCard.addView(languageLabel, languageLabelParams);
        Spinner languageSpinner = new Spinner(this);
        String[] languages = {"Português (Brasil)", "English", "Español"};
        ArrayAdapter<String> languageAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, languages);
        languageAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        languageSpinner.setAdapter(languageAdapter);
        settingsCard.addView(languageSpinner, params(-1, dp(48)));

        TextView accountLabel = text("Conta do jogo", 15, TEXT, true);
        LinearLayout.LayoutParams accountLabelParams = params(-1, -2);
        accountLabelParams.topMargin = dp(12);
        settingsCard.addView(accountLabel, accountLabelParams);
        Spinner accountSpinner = new Spinner(this);
        String[] accountTypes = {"Offline (nome de jogador)", "Microsoft", "ely.by"};
        ArrayAdapter<String> accountAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, accountTypes);
        accountAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        accountSpinner.setAdapter(accountAdapter);
        settingsCard.addView(accountSpinner, params(-1, dp(48)));
        TextView accountNote = text("A seleção fica salva neste aparelho. O login Microsoft/ely.by ainda precisa ser integrado para autenticar de verdade.", 12, MUTED, false);
        LinearLayout.LayoutParams accountNoteParams = params(-1, -2);
        accountNoteParams.topMargin = dp(6);
        settingsCard.addView(accountNote, accountNoteParams);

        Button manageAccountsButton = primaryButton("CRIAR / ADICIONAR CONTA", true);
        LinearLayout.LayoutParams manageAccountsParams = params(-1, dp(48));
        manageAccountsParams.topMargin = dp(10);
        settingsCard.addView(manageAccountsButton, manageAccountsParams);
        manageAccountsButton.setOnClickListener(v -> showAccountManager());

        SharedPreferences preferences = getSharedPreferences("launcher_preferences", MODE_PRIVATE);
        languageSpinner.setSelection(Math.max(0, Math.min(languages.length - 1, preferences.getInt("language_index", 0))));
        accountSpinner.setSelection(Math.max(0, Math.min(accountTypes.length - 1, preferences.getInt("account_type_index", 0))));
        languageSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                preferences.edit().putInt("language_index", position).putString("preferred_language", languages[position]).apply();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        accountSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                preferences.edit().putInt("account_type_index", position).putString("preferred_account_type", accountTypes[position]).apply();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
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
        footer.setTag("configuracoes");
        LinearLayout.LayoutParams footerParams = params(-1, -2);
        footerParams.topMargin = dp(24);
        footerParams.gravity = Gravity.CENTER_HORIZONTAL;
        footer.setGravity(Gravity.CENTER);
        page.addView(footer, footerParams);

        // O conteúdo é organizado em quatro áreas: início, versões, instâncias/mods e configurações.
        for (int i = 0; i < page.getChildCount(); i++) {
            View child = page.getChildAt(i);
            if (child.getTag() == null) child.setTag("versoes");
        }
        LinearLayout bottomNavigation = new LinearLayout(this);
        bottomNavigation.setOrientation(LinearLayout.HORIZONTAL);
        bottomNavigation.setGravity(Gravity.CENTER);
        bottomNavigation.setPadding(dp(8), dp(8), dp(8), dp(8));
        bottomNavigation.setBackground(roundRect(SURFACE, 16, BORDER, 1));
        Button homeTab = button("⌂ Início", true);
        Button versionsTab = button("▣ Versões", true);
        Button instancesTab = button("▤ Instâncias", true);
        Button settingsTab = button("⚙ Ajustes", true);
        bottomNavigation.addView(homeTab, new LinearLayout.LayoutParams(0, dp(48), 1));
        bottomNavigation.addView(versionsTab, new LinearLayout.LayoutParams(0, dp(48), 1));
        bottomNavigation.addView(instancesTab, new LinearLayout.LayoutParams(0, dp(48), 1));
        bottomNavigation.addView(settingsTab, new LinearLayout.LayoutParams(0, dp(48), 1));
        LinearLayout.LayoutParams navParams = params(-1, dp(64));
        navParams.leftMargin = dp(10);
        navParams.rightMargin = dp(10);
        navParams.bottomMargin = dp(6);
        root.addView(bottomNavigation, navParams);
        homeTab.setOnClickListener(v -> showDashboardTab(page, "inicio"));
        versionsTab.setOnClickListener(v -> showDashboardTab(page, "versoes"));
        instancesTab.setOnClickListener(v -> showDashboardTab(page, "instancias"));
        settingsTab.setOnClickListener(v -> showDashboardTab(page, "configuracoes"));
        showDashboardTab(page, "inicio");
        return root;
    }

    private void showDashboardTab(LinearLayout page, String selectedTab) {
        for (int i = 0; i < page.getChildCount(); i++) {
            View child = page.getChildAt(i);
            Object tag = child.getTag();
            String section = tag == null ? "versoes" : String.valueOf(tag);
            child.setVisibility(!"ocultar".equals(section) && selectedTab.equals(section) ? View.VISIBLE : View.GONE);
        }
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
                    if (selectedVersionSummary != null) selectedVersionSummary.setText(saved);
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
                    + "\nResultados deste filtro: " + ids.size()
                    + "\nÚltima release estável: " + getSharedPreferences("launcher_preferences", MODE_PRIVATE)
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
                        + "\nTipo: " + version.optString("type", selectedType)
                        + "\nData: " + version.optString("releaseTime", "não informada")
                        + "\nJava requerida: " + (javaVersion == null ? "não informada" : javaVersion.optString("majorVersion", "não informada"))
                        + "\nArquivo client: " + (client == null ? "não disponível" : client.optString("url", "não disponível"))
                        + "\nSHA-1: " + (client == null ? "não disponível" : client.optString("sha1", "não disponível"))
                        + "\nTamanho do client: " + (client == null ? "não informado" : client.optString("size", "não informado"))
                        + "\n\nMetadados consultados; download e inicialização do jogo ainda não estão implementados.";
            } catch (Exception exception) {
                String message = exception.getMessage();
                result = "Não foi possível obter os metadados da versão.\n"
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
                + "\nAndroid: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")"
                + "\nMemória RAM total reportada: " + totalMb + " MB"
                + "\nRAM disponível neste momento: " + availableMb + " MB"
                + "\nHeap máxima do app: " + (Runtime.getRuntime().maxMemory() / (1024L * 1024L)) + " MB"
                + "\nDiagnóstico informativo; não altera nem reserva RAM para o Minecraft.");
    }

    private void copySafeDiagnosticReport() {
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        if (manager != null) manager.getMemoryInfo(info);
        long availableMb = info.availMem / (1024L * 1024L);
        long totalMb = info.totalMem / (1024L * 1024L);
        SharedPreferences preferences = getSharedPreferences("launcher_preferences", MODE_PRIVATE);
        int ramMb = preferences.getInt("preferred_ram_mb", 1024);
        String renderer = preferences.getString("preferred_renderer", "GL4ES (recomendado para compatibilidade)");
        String report = diagnosticLogManager == null
                ? "Diagnóstico de falhas indisponível.\nDispositivo: " + Build.MANUFACTURER + " " + Build.MODEL
                : diagnosticLogManager.createSafeDiagnosticReport(
                        Build.MANUFACTURER + " " + Build.MODEL, totalMb, availableMb,
                        ramMb, renderer, "0.1.0");
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("Diagnóstico seguro Mikael Launcher Plus", report));
            Toast.makeText(this, "Diagnóstico copiado. Revise antes de compartilhar.", Toast.LENGTH_LONG).show();
        }
    }

    private void showLastCrashLog() {
        if (diagnosticLogManager == null) {
            new AlertDialog.Builder(this).setTitle("Diagnóstico indisponível")
                    .setMessage("O armazenamento de logs não pôde ser inicializado.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        try {
            String report = diagnosticLogManager.readLastCrash();
            ScrollView scroll = new ScrollView(this);
            TextView content = text(report, 13, TEXT, false);
            content.setTextIsSelectable(true);
            content.setPadding(dp(12), dp(12), dp(12), dp(12));
            scroll.addView(content);
            new AlertDialog.Builder(this).setTitle("Última falha registrada")
                    .setView(scroll)
                    .setPositiveButton("Copiar", (dialog, which) -> {
                        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        if (clipboard != null) {
                            clipboard.setPrimaryClip(ClipData.newPlainText("Log de falha", report));
                            Toast.makeText(this, "Log copiado; revise antes de compartilhar.", Toast.LENGTH_LONG).show();
                        }
                    })
                    .setNegativeButton("Fechar", null).show();
        } catch (Exception exception) {
            new AlertDialog.Builder(this).setTitle("Erro ao ler log")
                    .setMessage("Não foi possível ler o log de falha salvo.")
                    .setPositiveButton("OK", null).show();
        }
    }

    private void createLocalInstance() {
        if (instanceManager == null || versionSpinner == null) {
            Toast.makeText(this, "O gerenciador de versões não está disponível.", Toast.LENGTH_LONG).show();
            return;
        }
        Object selected = versionSpinner.getSelectedItem();
        String version = selected == null ? "" : String.valueOf(selected).trim();
        if (version.isEmpty() || !version.matches("[A-Za-z0-9._-]{1,100}")) {
            Toast.makeText(this, "Toque em CONSULTAR VERSÕES e selecione uma versão na lista.", Toast.LENGTH_LONG).show();
            return;
        }
        showLoaderChoiceDialog("", version);
    }

    private void showLoaderChoiceDialog(String name, String version) {
        String[] loaderOptions = {"Vanilla", "Forge", "Fabric", "Quilt", "NeoForge", "OptiFine", "Forge + OptiFine"};
        Spinner loaderChoice = new Spinner(this);
        ArrayAdapter<String> loaderChoiceAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, loaderOptions);
        loaderChoiceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        loaderChoice.setAdapter(loaderChoiceAdapter);

        LinearLayout loaderDialogContent = new LinearLayout(this);
        loaderDialogContent.setOrientation(LinearLayout.VERTICAL);
        loaderDialogContent.setPadding(dp(20), dp(8), dp(20), dp(8));
        loaderDialogContent.addView(text("2. Escolha o modloader", 14, TEXT, true));
        loaderDialogContent.addView(text("Minecraft " + version + "\\nAgora escolha o modloader.", 14, MUTED, false));
        loaderDialogContent.addView(loaderChoice, params(-1, dp(52)));

        new AlertDialog.Builder(this)
                .setTitle("Escolha o modloader")
                .setView(loaderDialogContent)
                .setNegativeButton("CANCELAR", (dialog, which) -> dialog.dismiss())
                .setPositiveButton("CRIAR E BAIXAR", (dialog, which) -> {
                    String loader = String.valueOf(loaderChoice.getSelectedItem());
                    String profileName = (name == null || name.trim().isEmpty())
                            ? "Minecraft " + version + " - " + loader : name.trim();
                    createAndInstallVersion(profileName, version, loader);
                })
                .show();
    }

    private void createAndInstallVersion(String name, String version, String loader) {
        try {
            JSONObject created = instanceManager.createInstance(name, version, loader);
            getSharedPreferences("launcher_preferences", MODE_PRIVATE).edit()
                    .putString("active_instance_name", created.getString("name"))
                    .putString("selected_version", version).apply();
            refreshInstancesList();

            boolean opened = MinecraftEngineBridge.openVersionInstaller(this, version, loader, name);
            if (opened) {
                String message = "Versão " + version + " • " + loader
                        + "\\nO instalador do motor foi aberto. Conclua as etapas exibidas para baixar os componentes.";
                if (installStatusOutput != null) installStatusOutput.setText(message);
                Toast.makeText(this, loader.equals("Vanilla")
                        ? "Preparando o Minecraft " + version + " no motor."
                        : "Abrindo o instalador nativo de " + loader + " para Minecraft " + version + ".",
                        Toast.LENGTH_LONG).show();
            } else {
                String message = "O perfil local foi criado, mas a instalação real não foi iniciada. "
                        + "O mecanismo de Minecraft precisa estar integrado ao Mikael Launcher Plus; "
                        + "a interface isolada não instala modloaders.";
                if (installStatusOutput != null) installStatusOutput.setText(message);
                new AlertDialog.Builder(this)
                        .setTitle("Motor integrado necessário")
                        .setMessage(message)
                        .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                        .show();
            }
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
        final String version;
        final String instanceLoader;
        try {
            JSONObject selectedRecord = findInstanceRecord(instanceName);
            if (selectedRecord == null) throw new IllegalArgumentException("A instância selecionada não existe mais.");
            version = selectedRecord.optString("version", "");
            instanceLoader = selectedRecord.optString("loader", "Vanilla");
        } catch (Exception exception) {
            Toast.makeText(this, safeExceptionMessage(exception), Toast.LENGTH_LONG).show();
            return;
        }
        if (!"Vanilla".equals(instanceLoader)) {
            new AlertDialog.Builder(this)
                    .setTitle("Esta instância não é Vanilla")
                    .setMessage("A instância selecionada usa " + instanceLoader
                            + ". Esta etapa baixa apenas o cliente Vanilla; loaders serão tratados em outra etapa.")
                    .setPositiveButton("Entendi", (dialog, which) -> dialog.dismiss())
                    .show();
            return;
        }
        final String metadataUrl = getSelectedVersionMetadataUrl(version);
        if (version.isEmpty() || metadataUrl == null) {
            Toast.makeText(this, "A versão desta instância não está disponível no manifesto carregado. Consulte as versões oficiais e tente novamente.", Toast.LENGTH_LONG).show();
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

    private void stageVanillaAssets() {
        final String instanceName = selectedInstanceName();
        if (instanceName == null || instanceManager == null || vanillaAssetInstaller == null) {
            Toast.makeText(this, "Selecione uma instância primeiro.", Toast.LENGTH_SHORT).show();
            return;
        }
        final JSONObject selectedRecord;
        try {
            selectedRecord = findInstanceRecord(instanceName);
            if (selectedRecord == null) throw new IllegalArgumentException("A instância selecionada não existe mais.");
            if (!"Vanilla".equals(selectedRecord.optString("loader", "Vanilla"))) {
                throw new IllegalArgumentException("O download de assets desta etapa só aceita instâncias Vanilla.");
            }
            if (!selectedRecord.optString("version", "").equals(selectedRecord.optString("stagedVersion", ""))
                    || !"client_artifacts_staged".equals(selectedRecord.optString("installStatus", ""))) {
                throw new IllegalArgumentException("Baixe e verifique primeiro os arquivos oficiais do cliente Vanilla.");
            }
        } catch (Exception exception) {
            Toast.makeText(this, safeExceptionMessage(exception), Toast.LENGTH_LONG).show();
            return;
        }

        cancelAssetsRequested.set(false);
        if (stageAssetsButton != null) {
            stageAssetsButton.setEnabled(false);
            stageAssetsButton.setText("PREPARANDO DOWNLOAD...");
        }
        if (cancelAssetsButton != null) {
            cancelAssetsButton.setEnabled(true);
            cancelAssetsButton.setText("CANCELAR DOWNLOAD DE ASSETS");
        }
        if (installStatusOutput != null) {
            installStatusOutput.setText("Lendo índice oficial de assets para "
                    + selectedRecord.optString("version", "") + ". Os downloads e hashes serão verificados.");
        }

        ioExecutor.execute(() -> {
            String resultText;
            boolean completed = false;
            try {
                JSONObject result = vanillaAssetInstaller.downloadAssets(instanceName,
                        new VanillaAssetInstaller.ProgressListener() {
                            @Override
                            public void onProgress(String phase, long done, long total) {
                                String progress = phase + "\nItens verificados/processados: "
                                        + done + " / " + total
                                        + "\nO jogo não ficará jogável somente com assets.";
                                mainHandler.post(() -> {
                                    if (!cancelAssetsRequested.get() && installStatusOutput != null) {
                                        installStatusOutput.setText(progress);
                                    }
                                });
                            }

                            @Override
                            public boolean isCancelled() {
                                return cancelAssetsRequested.get();
                            }
                        });
                long bytes = result.optLong("assetTotalBytes", 0L);
                resultText = "Assets oficiais baixados e verificados.\n"
                        + "Índice: " + result.optString("assetIndexId", "—") + "\n"
                        + "Objetos únicos: " + result.optLong("assetObjectsVerified", 0L) + "\n"
                        + "Caminhos lógicos: " + result.optLong("assetLogicalPathCount", 0L) + "\n"
                        + "Tamanho total dos objetos: " + (bytes / (1024L * 1024L)) + " MiB\n"
                        + "Estado: assets_staged.\n\n"
                        + "Ainda faltam bibliotecas do jogo, runtime Java compatível e ligação da instância ao diretório real do motor.";
                completed = true;
            } catch (VanillaAssetInstaller.DownloadCancelledException exception) {
                resultText = "Download de assets cancelado com segurança.\n"
                        + "Os assets completos e verificados foram mantidos; toque novamente em BAIXAR E VERIFICAR ASSETS para continuar.\n"
                        + "A instância não foi marcada como jogável.";
            } catch (Exception exception) {
                resultText = "Não foi possível concluir os assets oficiais: "
                        + safeExceptionMessage(exception) + "\nOs arquivos completos já verificados foram mantidos sempre que possível.";
            }
            final String displayResult = resultText;
            final boolean success = completed;
            mainHandler.post(() -> {
                if (installStatusOutput != null) installStatusOutput.setText(displayResult);
                if (stageAssetsButton != null) {
                    stageAssetsButton.setEnabled(true);
                    stageAssetsButton.setText("BAIXAR E VERIFICAR ASSETS");
                }
                if (cancelAssetsButton != null) {
                    cancelAssetsButton.setEnabled(false);
                    cancelAssetsButton.setText("CANCELAR DOWNLOAD DE ASSETS");
                }
                refreshInstancesList();
                Toast.makeText(this,
                        success ? "Assets oficiais verificados." : "Download de assets não concluído.",
                        Toast.LENGTH_LONG).show();
            });
        });
    }

    private void stageVanillaLibraries() {
        final String instanceName = selectedInstanceName();
        if (instanceName == null || instanceManager == null || vanillaLibraryInstaller == null) {
            Toast.makeText(this, "Selecione uma instância primeiro.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            JSONObject record = findInstanceRecord(instanceName);
            if (record == null) throw new IllegalArgumentException("A instância selecionada não existe mais.");
            if (!"Vanilla".equals(record.optString("loader", "Vanilla"))) {
                throw new IllegalArgumentException("Esta etapa aceita somente instâncias Vanilla.");
            }
            if (!record.optString("version", "").equals(record.optString("stagedVersion", ""))
                    || !"client_artifacts_staged".equals(record.optString("installStatus", ""))) {
                throw new IllegalArgumentException("Baixe primeiro os arquivos oficiais do cliente.");
            }
        } catch (Exception exception) {
            Toast.makeText(this, safeExceptionMessage(exception), Toast.LENGTH_LONG).show();
            return;
        }

        cancelLibrariesRequested.set(false);
        if (stageLibrariesButton != null) {
            stageLibrariesButton.setEnabled(false);
            stageLibrariesButton.setText("PREPARANDO BIBLIOTECAS...");
        }
        if (cancelLibrariesButton != null) {
            cancelLibrariesButton.setEnabled(true);
            cancelLibrariesButton.setText("CANCELAR DOWNLOAD DE BIBLIOTECAS");
        }
        if (installStatusOutput != null) {
            installStatusOutput.setText("Lendo o manifesto oficial e verificando bibliotecas Java aplicáveis a Linux/ARM64. Bibliotecas nativas e runtime ainda dependem do motor.");
        }

        ioExecutor.execute(() -> {
            String resultText;
            boolean complete = false;
            boolean partial = false;
            try {
                JSONObject result = vanillaLibraryInstaller.downloadLibraries(instanceName,
                        new VanillaLibraryInstaller.ProgressListener() {
                            @Override
                            public void onProgress(String phase, long done, long total) {
                                String message = phase + "\nArquivos verificados: " + done + " / " + total
                                        + "\nClassificadores nativos e runtime Java não são instalados nesta etapa.";
                                mainHandler.post(() -> {
                                    if (!cancelLibrariesRequested.get() && installStatusOutput != null) {
                                        installStatusOutput.setText(message);
                                    }
                                });
                            }
                            @Override
                            public boolean isCancelled() {
                                return cancelLibrariesRequested.get();
                            }
                        });
                long bytes = result.optLong("totalBytes", 0L);
                int unresolved = (int) result.optLong("unresolvedCount", 0L);
                partial = "libraries_partial".equals(result.optString("status", ""));
                resultText = (partial ? "BIBLIOTECAS PARCIAIS — " : "BIBLIOTECAS VERIFICADAS — ")
                        + result.optString("version", "") + "\n"
                        + "Arquivos: " + result.optLong("verifiedCount", 0L) + " / "
                        + result.optLong("plannedCount", 0L) + "\n"
                        + "Tamanho: " + (bytes / (1024L * 1024L)) + " MiB\n"
                        + "Itens não resolvidos: " + unresolved + "\n";
                org.json.JSONArray unresolvedItems = result.optJSONArray("unresolved");
                if (unresolvedItems != null) {
                    for (int i = 0; i < Math.min(unresolvedItems.length(), 8); i++) {
                        resultText += "• " + unresolvedItems.optString(i) + "\n";
                    }
                    if (unresolvedItems.length() > 8) resultText += "• ... e mais itens no manifesto local.\n";
                }
                resultText += "\nO launcher ainda não está pronto para iniciar: runtime Java, classificadores nativos, integração ao diretório do motor e teste real continuam pendentes.";
                complete = !partial;
            } catch (VanillaLibraryInstaller.DownloadCancelledException exception) {
                resultText = "Download de bibliotecas cancelado. Os JARs completos já verificados foram mantidos; tente novamente para continuar.\nA instância não foi marcada como jogável.";
            } catch (Exception exception) {
                resultText = "Não foi possível concluir as bibliotecas: " + safeExceptionMessage(exception)
                        + "\nOs JARs completos e verificados existentes foram preservados quando possível.";
            }
            final String display = resultText;
            final boolean success = complete;
            final boolean isPartial = partial;
            mainHandler.post(() -> {
                if (installStatusOutput != null) installStatusOutput.setText(display);
                if (stageLibrariesButton != null) {
                    stageLibrariesButton.setEnabled(true);
                    stageLibrariesButton.setText("BAIXAR E VERIFICAR BIBLIOTECAS");
                }
                if (cancelLibrariesButton != null) {
                    cancelLibrariesButton.setEnabled(false);
                    cancelLibrariesButton.setText("CANCELAR DOWNLOAD DE BIBLIOTECAS");
                }
                refreshInstancesList();
                Toast.makeText(this, success ? "Bibliotecas verificadas."
                                : isPartial ? "Bibliotecas baixadas parcialmente; confira os bloqueios."
                                : "Download de bibliotecas não concluído.",
                        Toast.LENGTH_LONG).show();
            });
        });
    }

    private void diagnoseSelectedInstance() {
        final String instanceName = selectedInstanceName();
        if (instanceName == null || instanceReadinessChecker == null) {
            Toast.makeText(this, "Selecione uma instância primeiro.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (diagnoseInstanceButton != null) {
            diagnoseInstanceButton.setEnabled(false);
            diagnoseInstanceButton.setText("VERIFICANDO INTEGRIDADE...");
        }
        if (installStatusOutput != null) {
            installStatusOutput.setText("Verificando perfil, JSON da versão, tamanho e SHA-1 do JAR. Esta checagem não inicia o jogo.");
        }
        ioExecutor.execute(() -> {
            String value;
            try {
                JSONObject report = instanceReadinessChecker.inspect(instanceName);
                StringBuilder summary = new StringBuilder("DIAGNÓSTICO DA INSTÂNCIA — ")
                        .append(report.optString("instanceName", instanceName))
                        .append("\nMinecraft: ").append(report.optString("minecraftVersion", "desconhecido"))
                        .append(" | Loader: ").append(report.optString("loader", "Vanilla"))
                        .append("\nEstado: ").append(report.optString("status", "incompleto"))
                        .append("\nArtefatos do cliente verificados: ")
                        .append(report.optBoolean("clientArtifactsVerified", false) ? "sim" : "não")
                        .append("\nPronta para iniciar: NÃO — instalação incompleta.\n\nVERIFICAÇÕES:");
                JSONArray checks = report.optJSONArray("checks");
                if (checks != null) {
                    for (int i = 0; i < checks.length(); i++) {
                        JSONObject check = checks.optJSONObject(i);
                        if (check == null) continue;
                        summary.append("\n• ")
                                .append(check.optBoolean("passed", false) ? "OK — " : "PENDENTE — ")
                                .append(check.optString("name", "verificação"))
                                .append(": ").append(check.optString("detail", ""));
                    }
                }
                summary.append("\n\nO QUE FALTA:");
                JSONArray blockers = report.optJSONArray("blockers");
                if (blockers == null || blockers.length() == 0) {
                    summary.append("\nNenhum bloqueio adicional relatado; ainda é necessário testar o motor em aparelho físico.");
                } else {
                    for (int i = 0; i < blockers.length(); i++) {
                        summary.append("\n• ").append(blockers.optString(i, "Requisito incompleto."));
                    }
                }
                summary.append("\n\nEsta ferramenta não altera o estado de instalação e nunca marca a instância como jogável.");
                value = summary.toString();
            } catch (Exception exception) {
                value = "Não foi possível verificar a instância: " + safeExceptionMessage(exception);
            }
            final String displayValue = value;
            mainHandler.post(() -> {
                if (installStatusOutput != null) installStatusOutput.setText(displayValue);
                if (diagnoseInstanceButton != null) {
                    diagnoseInstanceButton.setEnabled(true);
                    diagnoseInstanceButton.setText("VERIFICAR INTEGRIDADE DA INSTÂNCIA");
                }
            });
        });
    }

    private JSONObject findInstanceRecord(String instanceName) throws Exception {
        if (instanceManager == null) return null;
        JSONArray records = instanceManager.getInstances();
        for (int i = 0; i < records.length(); i++) {
            JSONObject record = records.getJSONObject(i);
            if (instanceName.equals(record.optString("name", ""))) return record;
        }
        return null;
    }

    private String getSelectedVersionMetadataUrl(String version) {
        synchronized (allVersionIds) {
            int index = allVersionIds.indexOf(version);
            if (index < 0 || index >= allVersionUrls.size()) return null;
            String url = allVersionUrls.get(index);
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
                        + "\nCrie um perfil depois de consultar e selecionar uma versão oficial.");
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
                    + "\nNome: " + selectedRecord.optString("name", "—")
                    + "\nVersão escolhida: " + selectedRecord.optString("version", "—")
                    + "\nPerfil de carregador: " + selectedRecord.optString("loader", "Vanilla")
                    + "\nPasta privada: " + selectedRecord.optString("relativePath", "instances")
                    + "\nEstado: " + selectedRecord.optString("installStatus", "perfil criado; instalação não iniciada")
                    + "\nVersão baixada: " + selectedRecord.optString("stagedVersion", "nenhuma")
                    + "\nJogo jogável: não; arquivos de cliente, bibliotecas/assets/runtime e ligação ao motor ainda incompletos.");
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
                        + "\nDestino: " + instanceName + "/mods"
                        + "\n\nUm JAR pode executar código no computador/dispositivo em que é usado. Continue apenas se confiar na origem.")
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
        Toast.makeText(this, "Importando e verificando o JAR…", Toast.LENGTH_SHORT).show();
        ioExecutor.execute(() -> {
            try (InputStream stream = getContentResolver().openInputStream(uri)) {
                if (stream == null) throw new java.io.IOException("O arquivo selecionado não pôde ser lido.");
                JSONObject imported = instanceModManager.importMod(instanceName, fileName, stream);
                String hash = imported.optString("sha256", "");
                String shortHash = hash.length() > 16 ? hash.substring(0, 16) + "…" : hash;
                mainHandler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    refreshModsList();
                    Toast.makeText(this, "Mod copiado para a instância. SHA-256: "
                            + shortHash, Toast.LENGTH_LONG).show();
                });
            } catch (Exception exception) {
                String message = safeExceptionMessage(exception);
                mainHandler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                });
            }
        });
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
                summary.append("\n• ").append(mod.optString("name", "arquivo.jar"))
                        .append(" — ").append(mbHundredths / 100).append('.')
                        .append(String.format(java.util.Locale.ROOT, "%02d", mbHundredths % 100))
                        .append(" MB");
            }
            if (mods.length() > shown) summary.append("\n… e mais ").append(mods.length() - shown).append(" arquivo(s).");
            if (mods.length() == 0) summary.append("\nNenhum JAR importado ainda.");
            modsOutput.setText(summary.toString());
        } catch (Exception exception) {
            modsOutput.setText("Não foi possível listar os mods: " + safeExceptionMessage(exception));
        }
    }

    private void analyzeSelectedMods() {
        final String instanceName = selectedInstanceName();
        if (instanceName == null || modCompatibilityChecker == null) {
            Toast.makeText(this, "Selecione uma instância primeiro.", Toast.LENGTH_LONG).show();
            return;
        }
        if (modsOutput != null) modsOutput.setText("Analisando metadados dos mods…");
        ioExecutor.execute(() -> {
            String reportValue;
            try {
                JSONObject report = modCompatibilityChecker.inspect(instanceName);
                StringBuilder summary = new StringBuilder();
                summary.append("ANÁLISE DE MODS — ").append(instanceName)
                        .append("\nMinecraft: ").append(report.optString("minecraftVersion", "desconhecido"))
                        .append(" | Loader: ").append(report.optString("loader", "Vanilla"))
                        .append("\nJARs: ").append(report.optInt("jarCount", 0))
                        .append(" | Metadados reconhecidos: ").append(report.optInt("metadataRecognized", 0))
                        .append(" | Sem metadados reconhecidos: ").append(report.optInt("metadataMissing", 0))
                        .append("\n");
                org.json.JSONArray warnings = report.optJSONArray("warnings");
                if (warnings == null || warnings.length() == 0) {
                    summary.append("\nNenhuma dependência ausente ou ID duplicado foi detectado pelos metadados disponíveis.");
                } else {
                    summary.append("\nAVISOS (").append(warnings.length()).append("):");
                    for (int i = 0; i < warnings.length(); i++) {
                        summary.append("\n• ").append(warnings.optString(i));
                    }
                }
                org.json.JSONArray findings = report.optJSONArray("findings");
                if (findings != null) {
                    summary.append("\n\nARQUIVOS:");
                    for (int i = 0; i < findings.length(); i++) {
                        JSONObject item = findings.optJSONObject(i);
                        if (item == null) continue;
                        summary.append("\n• ").append(item.optString("file", "arquivo.jar"))
                                .append(": ").append(item.optString("status", "desconhecido"));
                        if (item.has("modId")) {
                            summary.append(" — ").append(item.optString("modId"))
                                    .append(" ").append(item.optString("modVersion", ""));
                        }
                        String message = item.optString("message", "");
                        if (!message.isEmpty()) summary.append(" (").append(message).append(")");
                    }
                }
                summary.append("\n\nImportante: é uma verificação estática parcial. Não instala Forge/Fabric/OptiFine, não verifica todas as faixas de versão e não prova que o jogo iniciará.");
                reportValue = summary.toString();
            } catch (Exception exception) {
                reportValue = "Falha na análise: " + safeExceptionMessage(exception);
            }
            final String reportText = reportValue;
            mainHandler.post(() -> {
                if (modsOutput != null) modsOutput.setText(reportText);
            });
        });
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
        String requestedVersion = getSelectedVersionForVanillaTest();
        if (!requestedVersion.matches("[A-Za-z0-9._-]{1,100}")) {
            Toast.makeText(this, "Escolha uma versão oficial válida antes de iniciar.", Toast.LENGTH_LONG).show();
            return;
        }
        if (MinecraftEngineBridge.openInstalledEngine(this, requestedVersion)) {
            Toast.makeText(this,
                    "Pedido enviado ao motor para iniciar Minecraft " + requestedVersion
                            + ". Na primeira execução, o download e a autenticação podem ser necessários.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Motor do Minecraft não encontrado")
                .setMessage("Este repositório contém a interface e a integração-fonte, mas o jogo real precisa "
                        + "do mecanismo de Minecraft integrado ao aplicativo. Nenhum mecanismo compatível foi encontrado. "
                        + "A compilação de validação não gera APK e não comprova uma execução em aparelho.")
                .setPositiveButton("Entendi", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void addModule(LinearLayout parent, String heading, String description) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setPadding(dp(16), dp(15), dp(16), dp(15));
        item.setBackground(roundRect(SURFACE_RAISED, 16, BORDER, 1));
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
        button.setTextSize(13);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(enabled ? EMERALD : MUTED);
        button.setEnabled(enabled);
        button.setMinHeight(dp(48));
        button.setPadding(dp(14), dp(8), dp(14), dp(8));
        button.setBackground(roundRect(enabled ? SURFACE_RAISED : SURFACE, 15,
                enabled ? BORDER : 0xFF1B2922, 1));
        return button;
    }

    private Button primaryButton(String label, boolean enabled) {
        Button button = button(label, enabled);
        button.setTextColor(enabled ? BACKGROUND : MUTED);
        button.setBackground(roundRect(enabled ? EMERALD : SURFACE, 16,
                enabled ? 0xFF4AF0B0 : BORDER, 1));
        return button;
    }

    private TextView navChip(String label) {
        TextView chip = text(label, 12, TEXT, true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(14), dp(8), dp(14), dp(8));
        chip.setBackground(roundRect(SURFACE_RAISED, 24, BORDER, 1));
        chip.setClickable(true);
        chip.setFocusable(true);
        chip.setContentDescription("Ir para a seção " + label);
        return chip;
    }

    private TextView text(String value, int sizeSp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setLetterSpacing(bold ? 0.01f : 0.0f);
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
    private void showAccountManager() {
        String[] actions = {
                "Criar conta offline (nome de jogador)",
                "Criar conta Microsoft",
                "Criar conta ely.by",
                "Ver contas salvas"
        };
        new AlertDialog.Builder(this)
                .setTitle("Contas do Minecraft")
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) showCreateOfflineAccountDialog();
                    else if (which == 1) openAccountRegistration("https://signup.live.com/");
                    else if (which == 2) openAccountRegistration("https://ely.by/register");
                    else showSavedAccounts();
                })
                .setNegativeButton("Fechar", null)
                .show();
    }

    private void showCreateOfflineAccountDialog() {
        EditText usernameInput = new EditText(this);
        usernameInput.setSingleLine(true);
        usernameInput.setTextSize(16);
        usernameInput.setTextColor(TEXT);
        usernameInput.setHintTextColor(MUTED);
        usernameInput.setHint("Nome: 3 a 16 letras, números ou _");
        usernameInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        usernameInput.setPadding(dp(14), dp(8), dp(14), dp(8));
        usernameInput.setBackground(roundRect(SURFACE, 12, BORDER, 1));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(8), dp(20), dp(4));
        content.addView(text("Cria um perfil local para jogar em servidores que aceitam contas offline. "
                + "Isso não cria uma conta Microsoft nem uma conta premium.", 13, MUTED, false));
        LinearLayout.LayoutParams inputParams = params(-1, dp(52));
        inputParams.topMargin = dp(12);
        content.addView(usernameInput, inputParams);
        new AlertDialog.Builder(this)
                .setTitle("Nova conta offline")
                .setView(content)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Salvar conta", (dialog, which) -> {
                    String username = usernameInput.getText().toString().trim();
                    if (!username.matches("[A-Za-z0-9_]{3,16}")) {
                        Toast.makeText(this, "Use 3–16 caracteres: letras, números ou _.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    SharedPreferences prefs = getSharedPreferences("launcher_preferences", MODE_PRIVATE);
                    try {
                        JSONArray accounts = new JSONArray(prefs.getString("offline_accounts", "[]"));
                        boolean exists = false;
                        for (int i = 0; i < accounts.length(); i++) {
                            if (username.equalsIgnoreCase(accounts.optString(i))) {
                                exists = true;
                                break;
                            }
                        }
                        if (!exists) accounts.put(username);
                        prefs.edit()
                                .putString("offline_accounts", accounts.toString())
                                .putString("offline_username", username)
                                .putString("preferred_account_type", "Offline (nome de jogador)")
                                .putInt("account_type_index", 0)
                                .apply();
                        Toast.makeText(this, "Perfil offline salvo: " + username, Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        Toast.makeText(this, "Não foi possível salvar o perfil offline.", Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }

    private void showSavedAccounts() {
        SharedPreferences prefs = getSharedPreferences("launcher_preferences", MODE_PRIVATE);
        JSONArray accounts;
        try {
            accounts = new JSONArray(prefs.getString("offline_accounts", "[]"));
        } catch (Exception e) {
            accounts = new JSONArray();
        }
        if (accounts.length() == 0) {
            new AlertDialog.Builder(this)
                    .setTitle("Contas salvas")
                    .setMessage("Você ainda não criou uma conta offline neste aparelho.")
                    .setPositiveButton("Criar conta", (dialog, which) -> showCreateOfflineAccountDialog())
                    .setNegativeButton("Fechar", null)
                    .show();
            return;
        }
        String[] names = new String[accounts.length()];
        for (int i = 0; i < accounts.length(); i++) names[i] = accounts.optString(i);
        new AlertDialog.Builder(this)
                .setTitle("Escolher conta offline")
                .setItems(names, (dialog, which) -> {
                    String username = names[which];
                    prefs.edit()
                            .putString("offline_username", username)
                            .putString("preferred_account_type", "Offline (nome de jogador)")
                            .putInt("account_type_index", 0)
                            .apply();
                    Toast.makeText(this, "Conta selecionada: " + username, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Fechar", null)
                .show();
    }

    private void openAccountRegistration(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
            Toast.makeText(this,
                    "Depois de criar a conta, o login dentro do launcher ainda precisa ser integrado.",
                    Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Não foi possível abrir a página de cadastro.", Toast.LENGTH_LONG).show();
        }
    }

}
