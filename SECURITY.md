# Security

This is a preview Android print service with a deliberately small permission surface. The system print service is protected by `android.permission.BIND_PRINT_SERVICE`; ordinary apps cannot bind directly to it. It accepts PDF jobs through Android's print framework and connects only to the printer selected during setup.

Limits currently include 20 MiB PDF input, 50 pages, 10 copies and bounded raster dimensions. Rendering uses Android's native PDF implementation. Keep the device's Android security updates current. No browser page, document text or issue report is treated as a command to execute.

Use GitHub's **Security → Report a vulnerability** for a private report. Do not include real customer documents, credentials or device identifiers in a public issue. Public security advisories will describe affected versions and fixes when available.

Only download maintainer APKs from this repository's Releases and verify their published SHA-256 checksum. Local source builds use a separate development signing key unless explicitly configured with the maintainer key. Private keys/password files must never be committed or uploaded as release assets.

There is no automatic retry after print bytes have been sent. Cancellation may happen after some paper has already printed. The app cannot promise exactly-once physical output across printer power loss, OS failure or interrupted multi-copy jobs.
