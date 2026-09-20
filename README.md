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
- Backend: <e.g. Laravel 11 / Django 5 / FastAPI> · Database: <MySQL 8 / PostgreSQL / Room>
- Frontend / client: <React + Tailwind / Android Kotlin / ...>
- Tools: Git + GitHub, Trello, Postman, <draw.io>, <Android Studio>

## Run from a clean clone (this section is tested by the mentor before every gate)
```bash
git clone <repo-url>
cd <folder>
# 1. dependencies
<composer install / pip install -r requirements.txt / ./gradlew build>
# 2. environment
cp .env.example .env        # then fill DB credentials — never commit .env
# 3. database
<php artisan migrate --seed / python manage.py migrate && python manage.py seed_demo>
# 4. run
<php artisan serve / python manage.py runserver / install app-debug.apk>
```
Expected result after step 4: <e.g. "http://localhost:8000 shows the login page">.

## Demo accounts (seeded)
| Role | Login | Password |
|---|---|---|
| <merchant / admin> | demo.admin@example.com | Demo1234! |
| <customer / staff> | demo.user@example.com | Demo1234! |

## MVP status (mirrors the Trello board — every ✅ must have a commit/PR link on its card)
| # | MVP item (from Scope Memo) | Owner | Status | Evidence (commit / PR) |
|---|---|---|---|---|
| 1 | <...> | <name> | ☐ / ⏳ / ✅ | <link> |
| 2 | <...> | <name> | ☐ | |

## Cut from this project (appears as Future Work in the report)
- <item> — cut at S1 because <one reason>

## Module ownership (groups only)
- <name> owns <module> — individually assessed on it
- <name> owns <module>

## Boundaries
- <e.g. Simulation only. No real money, no real bank identifiers, no real customer data.>

## AI assistance — declared
| File / area | Tool | What was generated | What I changed and why I can defend every line |
|---|---|---|---|
| <path> | <ChatGPT / Claude / Copilot> | <...> | <...> |

## Tests
`<command to run tests>` — <n> tests, last run <date>, all passing.

## Links
- Postman collection: `docs/<name>.postman_collection.json`
- ERD: `docs/erd.png` (must match the running schema)
- Video (view access checked from an incognito window): <link>
