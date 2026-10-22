# Contributing

Useful contributions include testing another Android version, adding compatible printer discovery, and small fixes with a reproducible synthetic example.

For a compatibility report, include only the printer's commercial model (if known), Android version, Classic/BLE connection type, selected paper size and the visible error. Do not post Bluetooth MAC addresses, device serials, customer receipts, payment information, authenticated web URLs, signing keys or private logs.

Run `./scripts/build.sh` before opening a pull request. For rendering or sizing changes, run the self-targeted Android checks in `./scripts/test-device.sh` on a test device, then physically validate text size, left/right edges, complete totals/footer and blank gaps. Test fixtures must contain synthetic text and shapes only.

Keep changes focused, use Android platform APIs where practical, and document limits honestly. A matching reseller name or Bluetooth pairing alone is not evidence of printing compatibility. Do not claim universal printer support without device evidence.

Code contributions are accepted under the repository's MIT license. Never add a real signing key or a compiled APK to Git history; maintainer downloads belong in Releases.
