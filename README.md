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

## Demo accounts (seeded)
| Role | Login | Password |
|---|---|---|
| super_admin | admin | Admin@12345 |
| staff | staff1 | Staff@12345 |
| client | client1 | Client@12345 |

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
- Postman collection: `docs/<name>.postman_collection.json`
- ERD: `docs/erd.png` (must match the running schema)
- Video (view access checked from an incognito window): <link>
