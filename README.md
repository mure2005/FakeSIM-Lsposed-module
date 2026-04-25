# 🚀 FakeSIM - LSPosed Module

![Android](https://img.shields.io/badge/Android-10--13-green)
![MIUI](https://img.shields.io/badge/Compatible-green)
![LSPosed](https://img.shields.io/badge/LSPosed-Compatible-blue)
![Status](https://img.shields.io/badge/Status-Stable-brightgreen)

## 📌 Description

**FakeSIM** is an LSPosed module that allows you to simulate (spoof) one or two SIM cards for testing and visual purposes.

It works across multiple Android versions and ROMs, including MIUI.

---

## ⚙️ Compatibility

- ✅ Android 10 → Android 13 (crdroid with Android 11 not works, systemUI crash)
- ✅ MIUI 12.0 / 12.5  
- ⚠️ Android 14+ → Not supported (breaking changes)

---

## 🧪 Features

- 📶 Simulate disabled SIM (PIN/PUK blocked or deactivated)  
- 📱 Dual SIM support  
- 🏷️ Custom carrier name  
- 🔢 Custom phone number  
- 📡 Fake signal display (based on emergency/SOS detection)  
- 📶 VoLTE icon spoof (Android 11 only)  
- 🌍 Roaming always enabled  

---

## 🛠️ Configuration

Customize carrier and number manually:
/data/local/tmp/minenet
### Files

- `carrier` → SIM 1 carrier  
- `carrier_1` → SIM 2 carrier  
- `number` → SIM 1 number  
- `number_1` → SIM 2 number  

---

## ⚠️ Important Warning

> 🚫 This module does NOT provide access to real mobile networks  
> 🚫 It does NOT allow real calls or SMS  
> 📡 Signal shown is only from emergency/SOS antenna detection  
> 🎭 Everything is purely visual and simulated  
> ⚠️ At least one SIM card without service or blocked is required to activate the modem

This project is intended **only for:**

- 🧪 Development  
- 🎓 Educational purposes  
- 🧩 UI testing  

❌ It must NOT be used to:
- bypass carrier restrictions  
- spoof identity in services  
- trick apps requiring a real SIM

- ## ⚠️ Known Issues

- ❗ On some crDroid builds, **SystemUI may crash**
- 📶 On certain ROMs, the signal may appear as **VoWiFi instead of VoLTE**
  

**The author is not responsible for misuse of this module.**

---

## 🧠 Technical Notes

- Hooks system-level telephony behavior using LSPosed  
- Designed for stability across Android 10–13  
- Does NOT interact with real network services  

---

## 📢 Notes

- VoLTE icon spoof works only on Android 11  
- Behavior may vary between ROMs (MIUI / AOSP)  
- Android 14+ introduces breaking changes  

---

## ❤️ Credits

Developed by **Pacuwu**

---

# 🌍 Español

## 📌 Descripción

**FakeSIM** es un módulo para LSPosed que permite simular una o dos tarjetas SIM con fines visuales y de prueba.

---

## ⚙️ Compatibilidad

- ✅ Android 10 → Android 13 (crdroid con Android 11 no funciona, crashea systemUI)
- ✅ MIUI 12.0 / 12.5  
- ⚠️ Android 14+ introduce cambios que rompen el hook
---

## 🧪 Funciones

- 📶 Simulación de SIM desactivada o bloqueada (PIN/PUK)  
- 📱 Soporte Dual SIM  
- 🏷️ Operadora personalizada  
- 🔢 Número personalizado  
- 📡 Señal simulada (basada en SOS/emergencia)  
- 📶 Icono VoLTE (solo Android 11)  
- 🌍 Roaming siempre activo  

---

## 🛠️ Configuración

Ruta:
/data/local/tmp/minenet

Archivos:

- `carrier`, `carrier_1`  
- `number`, `number_1`  

---

## ⚠️ Advertencia

> 🚫 Este módulo NO proporciona acceso a redes reales  
> 🚫 NO permite llamadas reales  
> 📡 La cobertura mostrada es solo señal de emergencia/SOS  
> 🎭 Todo es completamente estético
> ⚠️ Se requiere minimo una SIM sin servicio o bloqueada para activar el módem

## ⚠️ Problemas conocidos

- ❗ En algunas versiones de crDroid, **SystemUI puede crashear**
- 📶 En ciertas ROMs, la señal puede mostrarse como **VoWiFi en lugar de VoLTE**

Uso exclusivo para:

- 🧪 pruebas  
- 🎓 desarrollo  
- 📚 fines educativos  

❌ No usar para:
- evadir restricciones de operadores  
- falsificar identidad en servicios  

**El autor no se hace responsable del uso indebido.**

