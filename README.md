# monicoffee-shop-pos
## Team workflow
- `main` is always working code. No direct pushes.
- Branches: feature/backend-<task>, feature/android-<task>
- Every change goes through a Pull Request reviewed by the other member.
- Backend lives in /backend, Android in /android.


-----

# <monicoffee-shop-pos>

> **Originality claim (one paragraph, max 4 sentences).** Who the real user is (Mani coffee), the moment the problem happens, and the ONE thing this system does that nothing they use today does. This paragraph must match the title slide of your video and section 0 of your report.

**Graduation Project · FT SD E17 · IT Academy STEP Cambodia**
Student(s): <Ung Muy Seang> · <Sang Visal> .  Mentor: Mr. Chau Magn
Scope Memo: <Drive link>   Report: <Drive: https://drive.google.com/drive/folders/15Qkef1EwjaHntbcCRpOEuh6NLbMB6bNo >   Trello board: <https://trello.com/b/VhMQZHpy/02-coffee-shop-pos>

## Stack (frozen at S1 — changes need a written agreement)
- Backend: <Django 5 / Django REST Framework> · Database: <MySQL 8>
- Frontend / client: <Android Kotlin>
- Tools: Git + GitHub, Trello, Postman, <figma>, <Android Studio>, <Visual Studio Code>

## Run from a clean clone (this section is tested by the mentor before every gate)
```bash
git clone https://github.com/muyseang/monicoffee-shop-pos.git
cd monicoffee-shop-pos/backend
# 1. dependencies
python3 -m venv venv && source venv/bin/activate   # Windows: venv\Scripts\activate
pip install -r requirements.txt
# 2. environment
cp .env.example .env        # then fill DB credentials — never commit .env
# 3. database
mysql -u root -p -e "CREATE DATABASE coffee_pos CHARACTER SET utf8mb4;"
python manage.py migrate && python manage.py seed_demo
# 4. run
python manage.py runserver
```
Expected result after step 4: `http://127.0.0.1:8000/admin/` shows the Django admin login page.

> **Client sign-up and login require the Moni SMS Forwarder app** on the shop phone. It confirms the code each customer texts to the shop. APK: [moni-sms-forwarder-v1.0.apk](https://drive.google.com/file/d/1GYinhEnKm_JNQE4QqNoiGJ1F3G-Cv0le/view) · Source: [github.com/muyseang/MoniSmsForwarder](https://github.com/muyseang/MoniSmsForwarder) · Setup: [Test phone sign-in with the SMS Forwarder](#test-phone-sign-in-with-the-sms-forwarder)

## Demo accounts (seeded)
| Role | Login | Password |
|---|---|---|
| super_admin | admin | Admin@12345 |
| staff | staff1 | Staff@12345 |
| client | client1 | Client@12345 |

## Test phone sign-in with the SMS Forwarder
Clients sign up and log in by texting a 4-digit code to the shop phone. The **Moni SMS Forwarder** app runs on that phone and confirms the code with the backend.

**Download:** [moni-sms-forwarder-v1.0.apk](https://drive.google.com/file/d/1GYinhEnKm_JNQE4QqNoiGJ1F3G-Cv0le/view) (v1.0, 20 MB, Android 7+ on 64-bit ARM phones).  
**Source code:** [github.com/muyseang/MoniSmsForwarder](https://github.com/muyseang/MoniSmsForwarder).

1. Start the backend so the phone can reach it: `python manage.py runserver 0.0.0.0:8000`, and add the PC's Wi-Fi IP to `ALLOWED_HOSTS` in `backend/.env`. The phone and the PC must be on the same Wi-Fi.
2. Install the APK on the shop phone. Play Protect blocks sideloaded apps that read SMS, so either:
   - install from the PC: turn on *Developer options → Wireless debugging* (or USB debugging), then `adb install moni-sms-forwarder-v1.0.apk`, or
   - in *Play Store → Play Protect → Settings*, turn off *Scan apps with Play Protect*, install the APK, then turn scanning back on.
3. In the app:
   - tap **Edit gateway** and enter `http://<PC Wi-Fi IP>:8000/api/auth/`
   - on the **Permissions** tab, allow SMS
   - turn on **SMS listening** and allow notifications
4. In *Settings → Apps → MoniSmsForwarder → Battery*, choose **Unrestricted** so the phone doesn't stop it in the background.
5. In the client app, sign up, then tap **Open SMS App** and send the code to the shop phone. The client app signs in within a few seconds. Without a real SMS, enter the phone number and code in the Forwarder's **Manual OTP entry** instead.

## MVP status (mirrors the Trello board — every ✅ must have a commit/PR link on its card)
| # | MVP item (from Scope Memo) | Owner | Status | Evidence (commit / PR) |
|---|---|---|---|---|
| 1 | <...> | <name> | ☐ / ⏳ / ✅ | <link> |
| 2 | <...> | <name> | ☐ | |

## Cut from this project (appears as Future Work in the report)
- <item> — cut at S1 because <one reason>

## Module ownership (groups only)
- <Ung Muy Seang> owns <Backend-Django> — individually assessed on it
- <Sang Visal> owns <Android>

## Boundaries
- <e.g. Simulation only. No real money, no real bank identifiers, no real customer data.>

## AI assistance — declared
| File / area | Tool | What was generated | What I changed and why I can defend every line |
|---|---|---|---|
| <path> | <ChatGPT / Claude / Copilot> | <...> | <...> |

## Tests
`<command to run tests>` — <n> tests, last run <date>, all passing.

## Links
- Moni SMS Forwarder (shop phone app): [APK](https://drive.google.com/file/d/1GYinhEnKm_JNQE4QqNoiGJ1F3G-Cv0le/view) · [source](https://github.com/muyseang/MoniSmsForwarder)
- Postman collection: `docs/<name>.postman_collection.json`
- ERD: `docs/erd.png` (must match the running schema)
- Video (view access checked from an incognito window): <link>
