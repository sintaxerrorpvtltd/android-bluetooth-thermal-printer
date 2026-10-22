# Android Bluetooth Thermal Printer — 58mm Print Service for Chrome & PDF

Print receipts from **Chrome, PDF readers and web POS/billing apps** to a compatible **58mm Bluetooth ESC/POS receipt printer**, using Android's normal **Print** menu.

This open-source Android print service addresses a common problem: a thermal printer works in its own app, but Chrome shows only **Save as PDF**, or prints tiny receipts on a wide A4-sized page. It advertises real **58 × 500 mm** and **58 × 200 mm** receipt paper, renders at approximately 203 dpi, and preserves content at both edges of the printable area.

**Preview release:** tested on one Lenovo Tab TB311FU running Android 16 with one generic, rebranded 58mm dual-mode Bluetooth receipt printer. Compatibility with every printer is not established. Android 12+ is the minimum supported OS; other Android versions still need device testing.

[Download the Android APK](https://github.com/sintaxerrorpvtltd/android-bluetooth-thermal-printer/releases/latest) · [Setup and troubleshooting](https://sintaxerrorpvtltd.github.io/android-bluetooth-thermal-printer/) · [Build from source](#build-from-source) · [MIT license](LICENSE)

## What it does

- Adds **58mm Receipt Printer** to Android's system print picker.
- Prints from applications that use Android's standard print framework, including Chrome's print dialog. No website integration or vendor SDK is required for that flow.
- Uses paired **Bluetooth Classic SPP** first on compatible bonded Classic/dual-mode devices. If the Classic connection fails before sending print data, it can try the **BLE GATT 18F0/2AF1** receipt endpoint.
- Uses a **384-dot printable width** (approximately 48mm on a 58mm roll).
- Keeps physical sizing for content that fits; recenters or proportionally fits content that would otherwise be clipped at the left or right edge.
- Trims blank top/bottom page edges and uses a small page-join feed, instead of feeding a whole long blank page.
- Includes a readable size-test receipt with an approximately 40mm calibration line.

**Local printing:** no internet permission, cloud account, ads, analytics, or telemetry in this app. It requests only Nearby devices/Bluetooth scan and connect permissions. The source website and Android itself have their own network/privacy behavior.

## Install and print

1. Download the APK from **this repository's Releases**, transfer it to your Android phone/tablet and install it. Android may ask you to allow installation from the app opening the APK. You can turn that permission off afterward. This preview is not on Google Play.
2. Keep the printer powered on nearby. Release its connection from other phones, tablets or computers. For Classic printing, pair it in Android's Bluetooth settings first.
3. Open **58mm Receipt Printer**, allow **Nearby devices**, tap **Scan for printer**, and select your printer.
4. Tap **Enable print service**, then enable **58mm Receipt Printer** in Android's printing settings. Menu names vary by device.
5. Return to the helper and tap **Print size test**. In the print picker choose **58mm Receipt Printer**, not another installed printer service. Check the printed text and line before a full receipt.
6. Open the receipt in Chrome or your billing app, choose its 58mm receipt layout, tap **Print**, then select **58mm Receipt Printer → 58 × 500 mm receipt**. Start with one copy. Check the full prices, total and footer.

After setup, the helper's main screen can be closed; leave its Android print service enabled. A Mac, USB cable or remote print server is not part of the printing path. USB debugging is only useful for development, not normal use.

## Compatibility and limitations

| Area | Current behavior |
| --- | --- |
| Android | Requires Android 12 / API 31 or newer; physically tested on Android 16 |
| Printer language | ESC/POS raster commands (`GS v 0`); not ZPL, TSPL, CPCL or proprietary label protocols |
| Classic transport | Secure paired SPP, standard serial-port UUID; verified on the tested dual-mode printer |
| BLE transport | Writable service `18F0`, characteristic `2AF1`, with acknowledged final write; this tablet's BLE connection attempts failed, so Android BLE printing is not hardware-verified |
| Discovery | Uses BLE scanning and lists devices named `PRINTER` or advertising service `18F0`; Classic-only printers and some otherwise compatible devices will not appear |
| Paper | 58mm roll, 384 printable dots, approximately 203 dpi; not an 80mm or A4 driver |
| Color/images | Monochrome threshold conversion; no photographic halftoning |
| Limits | Up to 20 MiB PDF input, 50 document pages, 10 copies, and a bounded page raster height |
| Wide documents | A4 and other wide PDF pages are rejected; this service does not reflow an A4 invoice into a receipt |
| Availability | The picker lists the saved printer; power/range/connection are checked when sending a job |
| Status | No paper-out sensor, automatic cutter control or proof of physical paper output |

No automatic retry is performed after print data has been sent. A failed, cancelled or interrupted job may already have partly printed. Check the paper before manually restarting it, especially for multiple copies.

If Chrome already clipped content while creating its PDF, a print service cannot recover the missing content. Use a receipt layout that fits the chosen page width.

## Troubleshooting

### Bluetooth printer connected, but Chrome only shows Save as PDF

Bluetooth pairing and Android print-service registration are separate. Enable this service in Android **Settings → Printing**, select a printer inside the helper, then close and reopen Chrome's print preview.

### Receipt is too small or surrounded by blank paper

Choose this service and its actual **58mm receipt paper**, rather than an A4/A5/A6 or a 180mm-wide page offered by another service. Match the source app's receipt format to the roll. Avoid squeezing an entire A4 invoice onto a narrow roll.

### Left-hand text or right-hand prices are cut off

This service fits overflowing receipt content within 384 printable dots. Close an old print preview and reopen it after an app update. If the problem remains, check whether the source PDF itself is already clipped. Report page size and a **synthetic, anonymized** reproduction.

### Printer does not connect, or Android reports GATT error 133

Release the printer from other apps/devices and power-cycle it once. The helper tries paired Classic SPP first on bonded dual-mode printers, which worked on the tested Android 16 tablet when BLE failed. Do not switch to another service and assume its output came from this one. A BLE-only or differently named printer may need additional compatibility work.

### Is it compatible with every cheap 58mm Bluetooth printer?

No. Similar cases and reseller names can hide different firmware and protocols. Only one generic dual-mode ESC/POS printer has been physically validated. Contributions with redacted hardware and transport details are welcome.

## Build from source

Requirements: JDK 21, Android SDK platform **36**, build-tools **36.1.0**, Bash, `zip`, and `openssl`. No Gradle, Maven, AndroidX or downloaded app libraries are used. Install developer tools from their official sources and review their licenses separately.

```sh
export JAVA_HOME=/path/to/jdk-21
export ANDROID_SDK_ROOT=/path/to/android-sdk
./scripts/build.sh
```

The script compiles the Java code, runs the pure raster self-check, creates the APK and verifies its signature. Output is under `dist/`; temporary classes and the locally generated development signing key stay under ignored `build/`.

Local builds use a new private development key. They cannot update a maintainer-signed release without the same signing key. Uninstalling first removes the helper's own printer selection and cache. Maintainers can supply `THERMAL_KEYSTORE` and `THERMAL_KEYSTORE_PASSWORD_FILE`; never commit either file.

An optional GitHub Actions build template is provided in `examples/github-actions-build.yml`. It is not enabled automatically; a repository maintainer can copy it into `.github/workflows/` using an account with workflow permission.

### Tests

```sh
./scripts/test-device.sh DEVICE_SERIAL
```

This builds and runs a separate, self-targeted instrumentation APK on an authorized Android device, then removes the test package. It does not print, change Bluetooth pairing or interact with another app's UI. Use a development/test device. Never publish its device serial in an issue or log.

Checks include raster bit order, strip boundaries, actual Android PDF scaling, an approximately 40mm line, blank-edge trimming, compact page joins, selected pages, cancellation, and preservation of both overflowing edges. Hardware printing remains a separate physical check.

## Privacy and security

Selected printer name/address are stored in this app's private preferences, not in the distributed APK. Print PDFs are temporarily cached privately, with cleanup attempted after each job. A crash or cleanup failure may leave private cache files; uninstalling clears this app's own data. Android's print spooler may retain its own print-job data.

The repository and release contain no printer-session data, real receipts, customer information, device addresses/serials, private signing keys or personal screenshots. See [PRIVACY.md](PRIVACY.md), [SECURITY.md](SECURITY.md) and [CONTRIBUTING.md](CONTRIBUTING.md).

## Project scope

This repository contains the **Android print service only**. It does not include a macOS or Windows driver. It is an independent community project, not an official Lenovo, Android, Chrome or printer-manufacturer driver. App and printer names belong to their respective owners.
