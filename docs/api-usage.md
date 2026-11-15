# Moni Coffee API — Postman usage notes

Collection: [`MoniCoffee.postman_collection.json`](MoniCoffee.postman_collection.json) (Postman v2.1).
It covers the REST API used by the client Android app and the Moni SMS Forwarder.
The admin dashboard (`/dashboard/`) is server-rendered Django pages, not part of this API.

## Setup

1. Start the backend (see the main README) — `python manage.py runserver`. Keep `DEBUG=True`
   in `backend/.env` so OTP responses include `debug_code`.
2. In Postman: **Import** → choose `docs/MoniCoffee.postman_collection.json`.
3. Open the collection → **Variables** and check:

| Variable | Default | Notes |
|---|---|---|
| `base_url` | `http://127.0.0.1:8000` | Change if the server runs elsewhere |
| `phone` | `0990000001` | Use a number that isn't registered yet to try sign-up |
| `full_name` | `Postman Tester` | Name for sign-up |

The other variables (`token`, `request_id`, `otp_code`, `item_id`, `option_id`, `order_id`, …)
are filled in automatically by the requests' test scripts — leave them empty.

## Quick run

Send the requests from top to bottom:

1. **Auth → Register – request OTP** — saves `request_id` and `otp_code`.
2. **Auth → Register – verify OTP** — saves `token`. Every later request sends it as
   `Authorization: Token {{token}}` (set once on the collection).
3. **Profile**, **Menu** (List menu items picks an in-stock item and its options for ordering),
   **Payments**.
4. **Orders** — place a dine-in/cash and a pickup/QR order, list, get, upload a screenshot
   (pick an image file for the `image` field first), then cancel.
5. **Auth → Logout** clears the token.

To log in again with the same phone, use the **Login** requests instead of Register.
The whole folder can also be run with Postman's **Run collection**; the upload request needs a
file selected first.

## Things to know

- **Auth:** token-based. Public endpoints are only the OTP ones; everything else returns
  `401` without a token. Profile and orders accept client tokens only (staff tokens get `403`).
- **OTP rules:** a code lasts 120 s, a new one can be requested after 30 s (`429` before),
  and 5 wrong tries lock the code.
- **SMS Forwarder flow:** the app calls *request OTP* and then polls *status* with its
  `request_id`; the Forwarder phone (or a manual entry) calls *verify OTP*. Once verified,
  *status* returns the token.
- **Errors** come back as `{"detail": "..."}` or field errors like
  `{"table_number": ["..."]}`.
- **Money** values are strings with two decimals (`"3.50"`). The server prices every order
  from the database; the client only sends item ids, quantities and chosen options.
- **Orders** are scoped to the caller: another client's order id returns `404`.
  A client can cancel only while the order is `pending`.
- **Images** (`image_url`, `qr_image_url`, `payment_proof_url`) are full URLs built from the
  host the request used. From the Android emulator, use `http://10.0.2.2:8000`.

Each request has saved example responses (success and common errors), captured from the
running backend.
