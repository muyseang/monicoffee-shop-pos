# Coffee Shop POS - Backend

Django + Django REST Framework + MySQL.

## Run from a clean clone
Requirements: Python 3.12 (3.11+), MySQL 8.0 (confirm your exact versions and write them here).

1. Clone and enter the backend folder
   ```
   git clone https://github.com/muyseang/monicoffee-shop-pos.git
   cd monicoffee-shop-pos/backend
   ```
2. Create and activate a virtual environment
   ```
   python -m venv venv
   source venv/bin/activate        # Windows: venv\Scripts\activate
   ```
3. Install dependencies: `pip install -r requirements.txt`
4. Create the MySQL database
   ```
   mysql -u root -p -e "CREATE DATABASE coffee_pos CHARACTER SET utf8mb4;"
   ```
5. Copy `.env.example` to `.env` and set `DB_PASSWORD` (and a real `SECRET_KEY`)
6. Create tables: `python manage.py migrate`
7. Load demo data: `python manage.py seed_demo`
8. Start the server: `python manage.py runserver`
9. Open http://127.0.0.1:8000/admin/ and log in with `admin` / `Admin@12345`

Demo logins created by `seed_demo`: admin / Admin@12345, staff1 / Staff@12345, client1 / Client@12345.
