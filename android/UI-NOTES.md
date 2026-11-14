# Moni Coffee Android client

Native Android app (Kotlin, no third-party libraries) for customers of Moni Coffee. The screens follow the PNG references in `../Android-UI/Moni coffee` and all data comes from the Django backend in `../backend`.

## Code layout

| File | What it holds |
|---|---|
| `MainActivity.kt` | Every screen, drawn in code, plus navigation and app state (cart, session). |
| `Api.kt` | `ApiClient` — one method per backend endpoint — and the data classes it returns. |
| `Images.kt` | Downloads and caches menu photos and the shop's QR code. |

## What is connected to the backend

| Screen | Endpoint |
|---|---|
| Login / Register / OTP verification | `POST /api/auth/{login,register}/request-otp/`, `.../verify-otp/`, `GET .../status/` (polled, so a code confirmed by the shop's SMS device signs the app in) |
| Home, Explore, Favorites, Details | `GET /api/menu/categories/`, `GET /api/menu/items/` (photos, options, out-of-stock) |
| Checkout | `GET /api/payments/methods/`, `POST /api/orders/` |
| QR payment | `GET /api/payments/methods/`, `POST /api/orders/<id>/payment-proof/` |
| My Orders, Transaction Details | `GET /api/orders/?status=…`, `GET /api/orders/<id>/` (refreshed every 15 s), `POST /api/orders/<id>/cancel/` |
| Profile, Personal Info | `GET` / `PATCH /api/auth/me/`, `POST /api/auth/logout/` |

Kept on the phone only: the cart (in memory), favorites and the preferred payment method (SharedPreferences) — the backend has no API for them. The Cafe screen is sample content.

Prices and totals shown before checkout are for display; the server recalculates every order from its own prices.

## Running against the backend

The backend is used exactly as it is in `../backend` — no backend code or settings need to change for the emulator.

1. Start the backend as its README says (`python manage.py runserver`).
2. Run the app:
   - **Emulator:** nothing to configure. The app's default server is `http://10.0.2.2:8000`, the emulator's address for this computer.
   - **Real phone over USB:** run `adb reverse tcp:8000 tcp:8000`, then in the app tap **Server: … (change)** on the login screen and enter `http://127.0.0.1:8000`.
   - **Real phone over Wi-Fi:** start the backend with `python manage.py runserver 0.0.0.0:8000`, add the computer's IP address to `ALLOWED_HOSTS` in your own `backend/.env` (a local file, not committed), and enter `http://<that IP>:8000` as the server in the app.
3. Log in with the seeded client phone number `012345678`, or register a new number. There is no SMS gateway yet: while the backend runs with `DEBUG=True` it returns the code, and the app shows it on the verification screen.

QR payment appears at checkout only after the owner enables it and uploads a QR image in the dashboard settings.

## Build and verify

```sh
./gradlew :app:assembleDebug :app:lintDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.
