package com.mikael.launcherplus;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
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

public final class MainActivity extends Activity {
    private static final int BACKGROUND = 0xFF080D0B;
    private static final int SURFACE = 0xFF111A16;
    private static final int EMERALD = 0xFF00D68F;
    private static final int TEXT = 0xFFF0F7F3;
    private static final int MUTED = 0xFF9EB3A8;
    private static final String VERSION_MANIFEST =
            "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private static final int MAX_MANIFEST_BYTES = 4 * 1024 * 1024;

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private TextView versionsOutput;
    private Button versionsButton;

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
                        + "Downloads do jogo: não implementados\n"
                        + "Runtime Java e renderizador: não integrados",
                14, MUTED, false);
        LinearLayout.LayoutParams detailParams = params(-1, -2);
        detailParams.topMargin = dp(10);
        statusCard.addView(detail, detailParams);

        Button play = button("JOGAR  ·  EM DESENVOLVIMENTO", false);
        LinearLayout.LayoutParams playParams = params(-1, dp(54));
        playParams.topMargin = dp(20);
        page.addView(play, playParams);
        play.setOnClickListener(v -> Toast.makeText(
                this, "O motor do Minecraft ainda não foi integrado.", Toast.LENGTH_LONG).show());

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
                "Planejado · logs, códigos de saída e métricas reais");

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
                JSONArray versions = manifest.getJSONArray("versions");
                StringBuilder display = new StringBuilder();
                display.append("Última release estável: ").append(latestRelease).append('\n');
                display.append("Versões publicadas no manifesto: ").append(versions.length()).append('\n');
                display.append("\nReleases recentes:\n");

                int shown = 0;
                for (int i = 0; i < versions.length() && shown < 12; i++) {
                    JSONObject version = versions.getJSONObject(i);
                    if (!"release".equals(version.optString("type"))) {
                        continue;
                    }
                    display.append("• ").append(version.optString("id", "desconhecida"));
                    display.append("  ·  ").append(version.optString("releaseTime", "").replace('T', ' '));
                    display.append('\n');
                    shown++;
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
                versionsButton.setEnabled(true);
                versionsButton.setText("ATUALIZAR VERSÕES");
            });
        });
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
