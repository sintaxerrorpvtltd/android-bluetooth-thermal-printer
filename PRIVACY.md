# Privacy

The Android app has no INTERNET permission and contains no analytics, advertising SDK, cloud API or account system. Its requested runtime permissions are Bluetooth scan and connect (Nearby devices). Scans list only printer candidates; no nearby-device inventory is uploaded.

The printer selected by the user is saved in the app's private preferences as its Bluetooth name and address. These values are created on the user's device; no preselected address is shipped.

Print PDFs are received from Android's print framework and temporarily cached in the app's private cache directory. Cleanup is attempted after each job. A crash or cleanup failure may leave private cache copies. Uninstalling clears this app's own preferences/cache. Android's print spooler and the source application may independently retain print-job data.

The app has no paper-out sensor. Transport completion is not proof that a physical receipt printed. A failed or interrupted job may already have printed some data; inspect paper before manually retrying.

Never upload real customer receipts, payment details, authenticated URLs, printer/device addresses, device serials, or unredacted screenshots to a public issue. Use synthetic examples instead.
