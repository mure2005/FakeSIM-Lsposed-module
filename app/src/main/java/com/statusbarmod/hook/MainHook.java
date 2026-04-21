package com.statusbarmod.hook;

import android.os.Build;
import android.telephony.ServiceState;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Method;

/**
 * StatusBarMod – MainHook
 * Compatible: Android 11–16 (API 30–36)
 *
 * DIAGNÓSTICO CONFIRMADO POR LOGS (build BP4A.251205.006 / API 36):
 * ──────────────────────────────────────────────────────────────────
 * En Android 16, MobileSignalController NO EXISTE en el proceso SystemUI.
 * Google migró completamente la UI de señal al modelo MVVM de Kotlin:
 *   - MobileIconsInteractor  (lógica de negocio)
 *   - ImsInteractor          (estado IMS/VoLTE)
 *   - MobileUiAdapter        (adaptador a la UI)
 *   - NetworkController      (sigue existiendo, pero ya no gestiona VoLTE)
 *
 * MtkImsManager e ImsApp tampoco existen (HAL nativo en este build).
 *
 * TelephonyDisplayInfo reporta network=UNKNOWN porque el hook de
 * getNetworkType() actúa sobre llamadas directas, pero el DIC
 * (DisplayInfoController) construye el objeto antes de que llegue al hook.
 *
 * ESTRATEGIA PARA ANDROID 16:
 * ────────────────────────────
 * 1. Hookear TelephonyDisplayInfo en construcción / getter → LTE
 * 2. Hookear ImsInteractor (nuevo en A16) para VoLTE
 * 3. Hookear MobileIconsInteractor para forzar el estado del icono
 * 4. Hookear NetworkController (sigue presente) como respaldo
 * 5. Hookear ImsMmTelManager.isAvailable → VoLTE capability
 * 6. Mantener hooks de TelephonyManager/ServiceState (funcionan bien)
 */
public class MainHook implements IXposedHookLoadPackage {

    private static final String TAG    = "StatusBarMod";
    private static final String ST_DIR = "/data/local/tmp/minenet";

    private static final String[] TARGET_PACKAGES = {
        "com.android.systemui",
        "com.android.settings",
        "com.android.phone",
        "android",
        "com.mediatek.ims"
    };

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        for (String pkg : TARGET_PACKAGES) {
            if (lpparam.packageName.equals(pkg)) {
                hookTelephonyGlobal(lpparam);
                break;
            }
        }
        if (lpparam.packageName.equals("com.android.systemui")) {
            hookSystemUI(lpparam);
        }
        // MtkImsApp ya no existe en este build, no lo intentamos
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TELEFONÍA GLOBAL (procesos: android, phone, settings)
    // ══════════════════════════════════════════════════════════════════════════
    private void hookTelephonyGlobal(final XC_LoadPackage.LoadPackageParam lpparam) {
        safeHook("SimState",             () -> hookSimState());
        safeHook("PhoneNumberFix",       () -> hookPhoneNumber());
        safeHook("OperatorNames",        () -> hookOperatorNames());
        safeHook("CarrierSettings",      () -> hookCarrierSettings());
        safeHook("DataState",            () -> hookDataState());
        safeHook("PhoneSubInfo",         () -> hookPhoneSubInfoController(lpparam));
        safeHook("ImsManager.Legacy",    () -> hookImsManagerLegacy(lpparam));
        safeHook("ImsMmTel",             () -> hookImsMmTel(lpparam));
        safeHook("MmTelCapabilities",    () -> hookMmTelCapabilities(lpparam));
        safeHook("TelephonyManager.IMS", () -> hookTelephonyManagerIms());
        safeHook("ImsPhone.IMS",         () -> hookImsPhone(lpparam));
        safeHook("ImsRegImplBase",       () -> hookImsRegistrationImplBase(lpparam));
        // TelephonyDisplayInfo: hookeamos aquí para todos los procesos
        safeHook("TelephonyDisplayInfo", () -> hookTelephonyDisplayInfo(lpparam));
    }

    // ── Estado SIM → READY (5) ────────────────────────────────────────────────
    private void hookSimState() {
        XC_MethodReplacement ready = XC_MethodReplacement.returnConstant(5);
        XposedHelpers.findAndHookMethod(TelephonyManager.class, "getSimState", ready);
        XposedHelpers.findAndHookMethod(TelephonyManager.class, "getSimState", Integer.TYPE, ready);
    }

    // ── Número de teléfono ────────────────────────────────────────────────────
    private void hookPhoneNumber() {
        XC_MethodHook h0 = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                p.setResult(readMinenet("number", "+34600111222"));
            }
        };
        XC_MethodHook h1 = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                int slotId = getRealSlotId(((Integer) p.args[0]).intValue());
                p.setResult(slotId == 1
                    ? readMinenet("number_1", "+34666666666")
                    : readMinenet("number",   "+34600111222"));
            }
        };
        XposedHelpers.findAndHookMethod(TelephonyManager.class, "getLine1Number", h0);
        XposedHelpers.findAndHookMethod(TelephonyManager.class, "getLine1Number", Integer.TYPE, h1);
    }

    // ── PhoneSubInfoController ────────────────────────────────────────────────
    private void hookPhoneSubInfoController(XC_LoadPackage.LoadPackageParam lpparam) {
        Class<?> psic = XposedHelpers.findClass(
            "com.android.internal.telephony.PhoneSubInfoController", lpparam.classLoader);
        XC_MethodHook fix = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                String r = (String) p.getResult();
                if (r == null || r.isEmpty() || r.startsWith("{"))
                    p.setResult(readMinenet("number", "+34600111222"));
            }
        };
        safeHookMethod(psic, "getLine1Number", String.class, String.class, fix);
        safeHookMethod(psic, "getLine1NumberForSubscriber", Integer.TYPE, String.class, fix);
        safeHookMethod(psic, "getLine1Number", Integer.TYPE, String.class, String.class, fix);
    }

    // ── Nombres del operador ──────────────────────────────────────────────────
    private void hookOperatorNames() {
        XC_MethodHook nameHook = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                p.setResult(readMinenet("carrier", "FAKENET"));
            }
        };
        XposedHelpers.findAndHookMethod(ServiceState.class, "getOperatorAlphaLong", nameHook);
        XposedHelpers.findAndHookMethod(ServiceState.class, "getOperatorAlphaShort", nameHook);
        XposedHelpers.findAndHookMethod(ServiceState.class, "getState",
            XC_MethodReplacement.returnConstant(0)); // STATE_IN_SERVICE
        // Roaming = true → icono R
        XposedHelpers.findAndHookMethod(ServiceState.class, "getRoaming",
            XC_MethodReplacement.returnConstant(true));
        safeHookMethod(ServiceState.class, "getDataRoaming",
            XC_MethodReplacement.returnConstant(true));
    }

    // ── Carrier name ──────────────────────────────────────────────────────────
    private void hookCarrierSettings() {
        XposedHelpers.findAndHookMethod(SubscriptionInfo.class, "getCarrierName",
            new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        int slotId = ((Integer) XposedHelpers.callMethod(
                            p.thisObject, "getSimSlotIndex")).intValue();
                        p.setResult(slotId == 1
                            ? readMinenet("carrier_1", "HOOKNET")
                            : readMinenet("carrier",   "FAKENET"));
                    } catch (Throwable ignored) {}
                }
            });
    }

    // ── Tipo de red → LTE (13) ────────────────────────────────────────────────
    private void hookDataState() {
        XC_MethodReplacement lte = XC_MethodReplacement.returnConstant(13);
        XC_MethodReplacement con = XC_MethodReplacement.returnConstant(2);
        XposedHelpers.findAndHookMethod(TelephonyManager.class, "getDataState", con);
        safeHookMethod(TelephonyManager.class, "getDataNetworkType",            lte);
        safeHookMethod(TelephonyManager.class, "getNetworkType",                lte);
        safeHookMethod(TelephonyManager.class, "getVoiceNetworkType",           lte);
        safeHookMethod(TelephonyManager.class, "getDataNetworkType",  Integer.TYPE, lte);
        safeHookMethod(TelephonyManager.class, "getNetworkType",       Integer.TYPE, lte);
        safeHookMethod(TelephonyManager.class, "getVoiceNetworkType",  Integer.TYPE, lte);
    }

    // ── TelephonyDisplayInfo → LTE, sin 5G override ───────────────────────────
    // CLAVE EN ANDROID 16: el DIC construye TelephonyDisplayInfo desde el modem.
    // Hookeamos getNetworkType() y getOverrideNetworkType() en la propia clase
    // para que cualquier consulta devuelva LTE independientemente del proceso.
    private void hookTelephonyDisplayInfo(XC_LoadPackage.LoadPackageParam lpparam) {
        Class<?> tdi = XposedHelpers.findClass(
            "android.telephony.TelephonyDisplayInfo", lpparam.classLoader);
        safeHookMethod(tdi, "getNetworkType",
            XC_MethodReplacement.returnConstant(13)); // NETWORK_TYPE_LTE
        safeHookMethod(tdi, "getOverrideNetworkType",
            XC_MethodReplacement.returnConstant(0));  // OVERRIDE_NETWORK_TYPE_NONE
        // Android 16 puede tener isRoaming() en TelephonyDisplayInfo
        safeHookMethod(tdi, "isRoaming",
            XC_MethodReplacement.returnConstant(true));
    }

    // ── ImsMmTelManager – VoLTE capability ───────────────────────────────────
    private void hookImsMmTel(XC_LoadPackage.LoadPackageParam lpparam) {
        Class<?> c = XposedHelpers.findClass(
            "android.telephony.ims.ImsMmTelManager", lpparam.classLoader);
        XC_MethodReplacement t = XC_MethodReplacement.returnConstant(true);
        XposedHelpers.findAndHookMethod(c, "isAvailable", Integer.TYPE, Integer.TYPE, t);
        XposedHelpers.findAndHookMethod(c, "isCapable",   Integer.TYPE, Integer.TYPE, t);
        safeHookMethod(c, "isAdvancedCallingSettingEnabled", t);
        // NO hookeamos isVoWiFiSettingEnabled → sin VoWiFi
    }

    // ── MmTelCapabilities.isCapable() → true ─────────────────────────────────
    private void hookMmTelCapabilities(XC_LoadPackage.LoadPackageParam lpparam) {
        Class<?> c = XposedHelpers.findClass(
            "android.telephony.ims.feature.MmTelFeature$MmTelCapabilities",
            lpparam.classLoader);
        safeHookMethod(c, "isCapable", Integer.TYPE,
            XC_MethodReplacement.returnConstant(true));
    }

    // ── ImsManager legacy ─────────────────────────────────────────────────────
    private void hookImsManagerLegacy(XC_LoadPackage.LoadPackageParam lpparam) {
        Class<?> c = XposedHelpers.findClass("com.android.ims.ImsManager", lpparam.classLoader);
        XC_MethodReplacement t    = XC_MethodReplacement.returnConstant(true);
        XC_MethodReplacement zero = XC_MethodReplacement.returnConstant(0);
        safeHookMethod(c, "isRegistered",                       t);
        safeHookMethod(c, "getRegistrationTech",                zero);
        safeHookMethod(c, "isVolteProvisionedOnDevice",         t);
        safeHookMethod(c, "isWfcProvisionedOnDevice",           t);
        safeHookMethod(c, "isImsServiceUp",                     t);
        safeHookMethod(c, "isVolteEnabledByPlatform",           t);
        safeHookMethod(c, "isEnhanced4gLteModeSettingEnabledByUser", t);
        safeHookMethod(c, "isEnhanced4gLteModeSettingEnabled",  t);
        safeHookMethod(c, "isVolteEnabled",                     t);
        safeHookMethod(c, "isCapable",                          t);
    }

    // ── TelephonyManager – IMS ────────────────────────────────────────────────
    private void hookTelephonyManagerIms() {
        XC_MethodReplacement t = XC_MethodReplacement.returnConstant(true);
        safeHookMethod(TelephonyManager.class, "isImsRegistered",  t);
        safeHookMethod(TelephonyManager.class, "isImsRegistered",  Integer.TYPE, t);
        safeHookMethod(TelephonyManager.class, "isVolteAvailable", t);
        safeHookMethod(TelephonyManager.class, "isVolteEnabled",   t);
        safeHookMethod(TelephonyManager.class, "isVolteEnabled",   Integer.TYPE, t);
        // Sin VoWiFi: no hookeamos isWifiCallingAvailable ni isWifiCallingEnabled
    }

    // ── ImsPhone ──────────────────────────────────────────────────────────────
    private void hookImsPhone(XC_LoadPackage.LoadPackageParam lpparam) {
        Class<?> c = XposedHelpers.findClass(
            "com.android.internal.telephony.imsphone.ImsPhone", lpparam.classLoader);
        XC_MethodReplacement t = XC_MethodReplacement.returnConstant(true);
        safeHookMethod(c, "isImsRegistered", t);
        safeHookMethod(c, "isVolteEnabled",  t);
    }

    // ── ImsRegistrationImplBase ───────────────────────────────────────────────
    private void hookImsRegistrationImplBase(XC_LoadPackage.LoadPackageParam lpparam) {
        Class<?> c = XposedHelpers.findClass(
            "android.telephony.ims.stub.ImsRegistrationImplBase", lpparam.classLoader);
        // REGISTRATION_TECH_LTE = 0
        safeHookMethod(c, "getRegistrationTechnology",
            XC_MethodReplacement.returnConstant(0));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SYSTEMUI (proceso com.android.systemui, UID 10242)
    //
    // En Android 16 MobileSignalController NO EXISTE.
    // La arquitectura nueva usa:
    //
    //   NetworkController (sigue existiendo, gestiona señal general)
    //   ├── MobileSignalController  ← ELIMINADO en A16
    //   └── WifiSignalController
    //
    //   Nueva arquitectura MVVM (Kotlin, paquete com.android.systemui.statusbar.pipeline):
    //   ├── mobile.data.repository.MobileConnectionRepositoryImpl
    //   ├── mobile.domain.interactor.MobileIconsInteractor
    //   ├── mobile.domain.interactor.ImsInteractor  ← NUEVO, gestiona VoLTE
    //   ├── mobile.ui.viewmodel.MobileIconViewModel
    //   └── mobile.ui.MobileUiAdapter
    //
    // ESTRATEGIA:
    //   1. Hookear ImsInteractor para VoLTE
    //   2. Hookear MobileIconsInteractor para estado general
    //   3. Hookear NetworkController como respaldo
    //   4. Hookear RegistrationCallback.onDeregistered (Binder, sigue igual)
    //   5. Hookear CapabilitiesCallback.onCapabilitiesStatusChanged
    // ══════════════════════════════════════════════════════════════════════════
    private void hookSystemUI(final XC_LoadPackage.LoadPackageParam lpparam) {

        // ── 1. Bloquear onDeregistered (Binder, sigue llegando igual que en A13) ──
        safeHook("SystemUI.onDeregistered", () -> {
            Class<?> regCb = XposedHelpers.findClass(
                "android.telephony.ims.RegistrationManager$RegistrationCallback",
                lpparam.classLoader);
            XC_MethodHook block = new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    p.setResult(null);
                }
            };
            hookAllOverloads(regCb, "onDeregistered",          block);
            hookAllOverloads(regCb, "onTechnologyChangeFailed", block);
        });

        // ── 2. onCapabilitiesStatusChanged → voz=true, video=false ──────────────
        safeHook("SystemUI.onCapabilities", () -> {
            Class<?> mmTelCb = XposedHelpers.findClass(
                "android.telephony.ims.ImsMmTelManager$CapabilityCallback",
                lpparam.classLoader);
            hookAllOverloads(mmTelCb, "onCapabilitiesStatusChanged",
                new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        if (p.args.length >= 1 && p.args[0] instanceof Boolean)
                            p.args[0] = true;  // isVoiceCapable
                        if (p.args.length >= 2 && p.args[1] instanceof Boolean)
                            p.args[1] = false; // isVideoCapable (sin VT)
                    }
                });
        });

        // ── 3. ImsInteractor (nuevo en Android 14-16) ─────────────────────────
        // Paquete: com.android.systemui.statusbar.pipeline.mobile.domain.interactor
        safeHook("SystemUI.ImsInteractor", () ->
            hookImsInteractor(lpparam));

        // ── 4. MobileIconsInteractor ──────────────────────────────────────────
        safeHook("SystemUI.MobileIconsInteractor", () ->
            hookMobileIconsInteractor(lpparam));

        // ── 5. MobileConnectionRepository ────────────────────────────────────
        safeHook("SystemUI.MobileConnectionRepo", () ->
            hookMobileConnectionRepository(lpparam));

        // ── 6. NetworkController (sigue presente, como respaldo) ──────────────
        safeHook("SystemUI.NetworkController", () ->
            hookNetworkController(lpparam));

        // ── 7. TelephonyDisplayInfo en el proceso SystemUI también ────────────
        safeHook("SystemUI.TelephonyDisplayInfo", () ->
            hookTelephonyDisplayInfo(lpparam));

        // ── 8. Bloquar ImsStateCallback.onUnavailable ─────────────────────────
        safeHook("SystemUI.ImsStateCallback.onUnavailable", () -> {
            Class<?> cb = XposedHelpers.findClass(
                "android.telephony.ims.ImsStateCallback", lpparam.classLoader);
            hookAllOverloads(cb, "onUnavailable", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    p.setResult(null);
                }
            });
        });
    }

    /**
     * ImsInteractor – Android 14-16.
     * Gestiona el estado IMS/VoLTE en el nuevo pipeline de SystemUI.
     * Paquetes posibles según versión del build.
     */
    private void hookImsInteractor(XC_LoadPackage.LoadPackageParam lpparam) {
        String[] candidates = {
            // Android 14-15
            "com.android.systemui.statusbar.pipeline.mobile.domain.interactor.ImsInteractor",
            "com.android.systemui.statusbar.pipeline.mobile.domain.interactor.ImsInteractorImpl",
            // Android 16 (puede haber renombrado)
            "com.android.systemui.statusbar.pipeline.mobile.domain.interactor.MobileImsInteractor",
            "com.android.systemui.statusbar.pipeline.mobile.data.repository.MobileConnectionRepositoryImpl",
        };

        XC_MethodReplacement t    = XC_MethodReplacement.returnConstant(true);
        XC_MethodReplacement zero = XC_MethodReplacement.returnConstant(0);

        for (String name : candidates) {
            try {
                Class<?> cls = XposedHelpers.findClass(name, lpparam.classLoader);
                // Métodos habituales del ImsInteractor
                safeHookMethod(cls, "isVolteAvailable",      t);
                safeHookMethod(cls, "isVoLteEnabled",        t);
                safeHookMethod(cls, "isImsRegistered",       t);
                safeHookMethod(cls, "getRegistrationTech",   zero);
                safeHookMethod(cls, "isAvailable", Integer.TYPE, Integer.TYPE, t);
                // Bloquear reportes negativos
                hookAllOverloads(cls, "onDeregistered", new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        p.setResult(null);
                    }
                });
                XposedBridge.log(TAG + ": [OK] ImsInteractor → " + name);
            } catch (Throwable ignored) {}
        }
    }

    /**
     * MobileIconsInteractor – gestiona los iconos de señal móvil en A14-16.
     */
    private void hookMobileIconsInteractor(XC_LoadPackage.LoadPackageParam lpparam) {
        String[] candidates = {
            "com.android.systemui.statusbar.pipeline.mobile.domain.interactor.MobileIconsInteractor",
            "com.android.systemui.statusbar.pipeline.mobile.domain.interactor.MobileIconsInteractorImpl",
        };
        XC_MethodReplacement t = XC_MethodReplacement.returnConstant(true);
        for (String name : candidates) {
            try {
                Class<?> cls = XposedHelpers.findClass(name, lpparam.classLoader);
                safeHookMethod(cls, "isRoaming",       t);
                safeHookMethod(cls, "hasDataCapable",  t);
                safeHookMethod(cls, "isInService",     t);
                XposedBridge.log(TAG + ": [OK] MobileIconsInteractor → " + name);
            } catch (Throwable ignored) {}
        }
    }

    /**
     * MobileConnectionRepository – fuente de datos del pipeline de A14-16.
     * Hookeamos los Flow/StateFlow directamente no es posible desde Xposed,
     * pero sí los métodos de acceso síncronos que la alimentan.
     */
    private void hookMobileConnectionRepository(XC_LoadPackage.LoadPackageParam lpparam) {
        String[] candidates = {
            "com.android.systemui.statusbar.pipeline.mobile.data.repository.MobileConnectionRepositoryImpl",
            "com.android.systemui.statusbar.pipeline.mobile.data.repository.prod.MobileConnectionRepositoryImpl",
        };
        XC_MethodReplacement t    = XC_MethodReplacement.returnConstant(true);
        XC_MethodReplacement lte  = XC_MethodReplacement.returnConstant(13);
        for (String name : candidates) {
            try {
                Class<?> cls = XposedHelpers.findClass(name, lpparam.classLoader);
                safeHookMethod(cls, "isInService",          t);
                safeHookMethod(cls, "isRoaming",            t);
                safeHookMethod(cls, "getNetworkType",       lte);
                safeHookMethod(cls, "isImsRegistered",      t);
                safeHookMethod(cls, "isVolteCapable",       t);
                XposedBridge.log(TAG + ": [OK] MobileConnectionRepository → " + name);
            } catch (Throwable ignored) {}
        }
    }

    /**
     * NetworkController – sigue presente en Android 16 como capa de
     * compatibilidad. Hookeamos los métodos que aún usa.
     */
    private void hookNetworkController(XC_LoadPackage.LoadPackageParam lpparam) {
        String nc = "com.android.systemui.statusbar.connectivity.NetworkControllerImpl";
        // Android 16 puede haberlo movido:
        String[] candidates = {
            nc,
            "com.android.systemui.statusbar.policy.NetworkControllerImpl",
        };
        XC_MethodReplacement t = XC_MethodReplacement.returnConstant(true);
        for (String name : candidates) {
            try {
                Class<?> cls = XposedHelpers.findClass(name, lpparam.classLoader);
                safeHookMethod(cls, "isEmergencyOnly",   XC_MethodReplacement.returnConstant(false));
                safeHookMethod(cls, "hasMobileDataFeature", t);
                // Bloquar onDeregistered si lo propaga
                hookAllOverloads(cls, "onDeregistered", new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        p.setResult(null);
                    }
                });
                XposedBridge.log(TAG + ": [OK] NetworkController → " + name);
            } catch (Throwable ignored) {}
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // UTILIDADES
    // ══════════════════════════════════════════════════════════════════════════

    /** Hookea todos los overloads de un método en una clase. */
    private void hookAllOverloads(Class<?> cls, String methodName, XC_MethodHook hook) {
        try {
            for (Method m : cls.getDeclaredMethods()) {
                if (m.getName().equals(methodName)) {
                    try { XposedBridge.hookMethod(m, hook); }
                    catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}
    }

    private void safeSetField(Object obj, String field, Object value) {
        try {
            if (value instanceof Boolean)
                XposedHelpers.setBooleanField(obj, field, (Boolean) value);
            else if (value instanceof Integer)
                XposedHelpers.setIntField(obj, field, (Integer) value);
            else
                XposedHelpers.setObjectField(obj, field, value);
        } catch (Throwable ignored) {}
    }

    private void safeHookMethod(Class<?> cls, String method, Object... params) {
        try { XposedHelpers.findAndHookMethod(cls, method, params); }
        catch (Throwable ignored) {}
    }

    private int getRealSlotId(int subId) {
        try {
            return ((Integer) XposedHelpers.callStaticMethod(
                SubscriptionManager.class, "getSlotIndex",
                Integer.valueOf(subId))).intValue();
        } catch (Throwable t1) {
            try {
                return ((Integer) XposedHelpers.callStaticMethod(
                    SubscriptionManager.class, "getSlotId",
                    Integer.valueOf(subId))).intValue();
            } catch (Throwable t2) { return subId > 1 ? 1 : 0; }
        }
    }

    /**
     * Lee /data/local/tmp/minenet/<fileName>
     *
     * Configurar con ADB:
     *   adb shell "mkdir -p /data/local/tmp/minenet"
     *   adb shell "echo 'Movistar' > /data/local/tmp/minenet/carrier"
     *   adb shell "echo '+34600123456' > /data/local/tmp/minenet/number"
     *   adb shell chmod 644 /data/local/tmp/minenet/*
     */
    private String readMinenet(String fileName, String defaultValue) {
        try {
            File f = new File(ST_DIR + "/" + fileName);
            if (!f.exists()) return defaultValue;
            BufferedReader br   = new BufferedReader(new FileReader(f));
            String         line = br.readLine();
            br.close();
            return (line == null || line.trim().isEmpty()) ? defaultValue : line.trim();
        } catch (Exception e) { return defaultValue; }
    }

    private void safeHook(String name, Runnable r) {
        try { r.run(); }
        catch (Throwable t) {
            XposedBridge.log(TAG + ": [FAIL] " + name + " → " + t.getMessage());
        }
    }
}
