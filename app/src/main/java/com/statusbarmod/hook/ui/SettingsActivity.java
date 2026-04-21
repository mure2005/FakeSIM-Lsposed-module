package com.statusbarmod.hook.ui;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import android.widget.LinearLayout.LayoutParams;

import java.io.File;
import java.io.FileWriter;

@SuppressWarnings("deprecation")
public class SettingsActivity extends Activity {

    public static final String PREFS_NAME   = "statusbar_mod_prefs";
    public static final String KEY_OPERATOR  = "operator_name";
    public static final String KEY_OPERATOR2 = "operator_name_2";
    public static final String KEY_SIGNAL   = "signal_level";
    public static final String KEY_NETTYPE  = "network_type";

    private static final String ST_DIR = "/data/local/tmp/minenet";

    private EditText etOperator;
    private EditText etOperator2;
    private SeekBar  sbSignal;
    private TextView tvSignalVal;
    private Spinner  spNetType;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS_NAME, MODE_WORLD_READABLE);
        setContentView(buildLayout());
        loadSaved();
    }

    private View buildLayout() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#0D1117"));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(32), dp(24), dp(32));

        TextView title = new TextView(this);
        title.setText("StatusBar Customizer");
        title.setTextColor(Color.parseColor("#58A6FF"));
        title.setTextSize(22);
        title.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("LSPosed · MineNet sync");
        subtitle.setTextColor(Color.parseColor("#8B949E"));
        subtitle.setTextSize(12);
        subtitle.setTypeface(Typeface.MONOSPACE);
        LayoutParams sp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        sp.setMargins(0, 0, 0, dp(8));
        root.addView(subtitle, sp);

        // Nota informativa
        TextView note = new TextView(this);
        note.setText("Los valores se escriben en /data/local/tmp/minenet/\nigual que el comando minenet.");
        note.setTextColor(Color.parseColor("#8B949E"));
        note.setTextSize(11);
        note.setTypeface(Typeface.MONOSPACE);
        LayoutParams np = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        np.setMargins(0, 0, 0, dp(32));
        root.addView(note, np);

        // Operador
        root.addView(label("Nombre del operador"));
        etOperator = new EditText(this);
        etOperator.setHint("Ej: Movistar");
        etOperator.setHintTextColor(Color.parseColor("#484F58"));
        etOperator.setTextColor(Color.WHITE);
        etOperator.setTypeface(Typeface.MONOSPACE);
        etOperator.setBackgroundColor(Color.parseColor("#161B22"));
        etOperator.setPadding(dp(12), dp(10), dp(12), dp(10));
        LayoutParams ep = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        ep.setMargins(0, dp(6), 0, dp(24));
        root.addView(etOperator, ep);

        // Operador SIM 2
        root.addView(sectionLabel("● SIM 2 (virtual)"));
        root.addView(label("Nombre del operador"));
        etOperator2 = new EditText(this);
        etOperator2.setHint("Ej: Orange");
        etOperator2.setHintTextColor(Color.parseColor("#484F58"));
        etOperator2.setTextColor(Color.WHITE);
        etOperator2.setTypeface(Typeface.MONOSPACE);
        etOperator2.setBackgroundColor(Color.parseColor("#161B22"));
        etOperator2.setPadding(dp(12), dp(10), dp(12), dp(10));
        LayoutParams ep2 = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        ep2.setMargins(0, dp(6), 0, dp(24));
        root.addView(etOperator2, ep2);

        root.addView(sectionLabel("● Señal y red (ambas SIMs)"));

        // Señal
        root.addView(label("Barras de señal  (0 – 4)"));
        sbSignal = new SeekBar(this);
        sbSignal.setMax(4);
        LayoutParams sbp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        sbp.setMargins(0, dp(6), 0, 0);
        tvSignalVal = new TextView(this);
        tvSignalVal.setTextColor(Color.parseColor("#3FB950"));
        tvSignalVal.setTypeface(Typeface.MONOSPACE);
        sbSignal.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) {
                tvSignalVal.setText("Nivel: " + p + "/4  " + bars(p));
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        root.addView(sbSignal, sbp);
        LayoutParams tvp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        tvp.setMargins(0, dp(4), 0, dp(24));
        root.addView(tvSignalVal, tvp);

        // Tipo de red
        root.addView(label("Tipo de red"));
        spNetType = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item,
            new String[]{"5g","5g+","5ge","lte+","lte","h+","hspa++","h","3g","e+","e","gprs+","g","1x","s","sw","starlink"});
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spNetType.setAdapter(adapter);
        LayoutParams ntp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        ntp.setMargins(0, dp(6), 0, dp(40));
        root.addView(spNetType, ntp);

        // Botón guardar
        Button btn = new Button(this);
        btn.setText("GUARDAR Y APLICAR");
        btn.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        btn.setTextColor(Color.parseColor("#0D1117"));
        btn.setBackgroundColor(Color.parseColor("#58A6FF"));
        btn.setOnClickListener(v -> save());
        root.addView(btn);

        TextView noteBottom = new TextView(this);
        noteBottom.setText("\n⚠ Los cambios aplican inmediatamente\nsin necesidad de reiniciar.");
        noteBottom.setTextColor(Color.parseColor("#3FB950"));
        noteBottom.setTextSize(11);
        noteBottom.setTypeface(Typeface.MONOSPACE);
        root.addView(noteBottom);

        scroll.addView(root);
        return scroll;
    }

    private void loadSaved() {
        // Carga desde los archivos de minenet si existen, si no desde prefs
        String carrier = readFile(ST_DIR + "/carrier");
        String type    = readFile(ST_DIR + "/type");
        String level   = readFile(ST_DIR + "/level");

        String carrier2 = readFile(ST_DIR + "/carrier2");
        etOperator2.setText(carrier2.isEmpty()
            ? prefs.getString(KEY_OPERATOR2, "fakenet") : carrier2);

        etOperator.setText(carrier.isEmpty()
            ? prefs.getString(KEY_OPERATOR, "minenet") : carrier);

        int lvl = level.isEmpty()
            ? prefs.getInt(KEY_SIGNAL, 4)
            : parseInt(level, 4);
        sbSignal.setProgress(lvl);
        tvSignalVal.setText("Nivel: " + lvl + "/4  " + bars(lvl));

        String nt = type.isEmpty()
            ? prefs.getString(KEY_NETTYPE, "5g") : type;
        String[] types = {"5g","5g+","5ge","lte+","lte","h+","hspa++","h","3g","e+","e","gprs+","g","1x","s","sw","starlink"};
        for (int i = 0; i < types.length; i++) {
            if (types[i].equals(nt)) { spNetType.setSelection(i); break; }
        }
    }

    private void save() {
        String carrier  = etOperator.getText().toString().trim();
        String carrier2 = etOperator2.getText().toString().trim();
        int    level   = sbSignal.getProgress();
        String type    = spNetType.getSelectedItem().toString();

        // Escribe en /data/local/tmp/minenet/ — igual que el comando minenet
        boolean ok = writeFile(ST_DIR + "/carrier",  carrier)
                  && writeFile(ST_DIR + "/carrier2", carrier2)
                  && writeFile(ST_DIR + "/level",   String.valueOf(level))
                  && writeFile(ST_DIR + "/type",    type);

        // También guarda en prefs como respaldo
        prefs.edit()
            .putString(KEY_OPERATOR,  carrier)
            .putString(KEY_OPERATOR2, carrier2)
            .putInt(KEY_SIGNAL, level)
            .putString(KEY_NETTYPE, type)
            .apply();

        if (ok) {
            Toast.makeText(this, "✓ Aplicado — " + type + " | " + level + "/4 | " + carrier,
                Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this,
                "⚠ No se pudo escribir en " + ST_DIR + "\nUsa el comando minenet directamente.",
                Toast.LENGTH_LONG).show();
        }
    }

    // ── Utilidades ────────────────────────────────────────────────────────────

    private boolean writeFile(String path, String content) {
        try {
            new File(ST_DIR).mkdirs();
            FileWriter fw = new FileWriter(path);
            fw.write(content);
            fw.close();
            return true;
        } catch (Exception e) { return false; }
    }

    private String readFile(String path) {
        try {
            java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(path));
            String line = br.readLine();
            br.close();
            return line != null ? line.trim() : "";
        } catch (Exception e) { return ""; }
    }

    private int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    private TextView sectionLabel(String t) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextColor(Color.parseColor("#58A6FF"));
        tv.setTextSize(13);
        tv.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        LayoutParams p = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        p.setMargins(0, 0, 0, dp(8));
        tv.setLayoutParams(p);
        return tv;
    }

    private TextView label(String t) {
        TextView tv = new TextView(this);
        tv.setText(t); tv.setTextColor(Color.parseColor("#8B949E"));
        tv.setTextSize(11); tv.setTypeface(Typeface.MONOSPACE, Typeface.BOLD); tv.setAllCaps(true);
        return tv;
    }

    private String bars(int l) {
        String[] b = {"▁___","▁▂__","▁▂▃_","▁▂▃▄","▁▂▃▄▅"};
        return (l >= 0 && l <= 4) ? b[l] : "";
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
