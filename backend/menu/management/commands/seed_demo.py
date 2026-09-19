from django.contrib.auth import get_user_model
from django.core.management.base import BaseCommand
from django.db import transaction

from menu.models import Category, MenuItem

DEMO_MENU = {
    "Coffee": [
        ("Espresso", "Single shot espresso", "2.00", 50),
        ("Americano", "Espresso with hot water", "2.50", 50),
        ("Latte", "Espresso with steamed milk", "3.00", 40),
        ("Iced Coffee", "Cold brew over ice", "2.75", 40),
    ],
    "Tea": [
        ("Green Tea", "Hot green tea", "2.00", 30),
        ("Milk Tea", "Classic milk tea", "2.50", 30),
    ],
    "Pastry": [
        ("Croissant", "Butter croissant", "2.20", 20),
        ("Chocolate Cake", "Slice of chocolate cake", "3.50", 10),
    ],
}


class Command(BaseCommand):
    help = "Create demo users, categories and menu items. Safe to run more than once."

    @transaction.atomic
    def handle(self, *args, **options):
        User = get_user_model()

        admin, _ = User.objects.get_or_create(
            username="admin",
            defaults={"role": "super_admin", "is_staff": True, "is_superuser": True, "email": "admin@example.com"},
        )
        admin.set_password("Admin@12345")
        admin.save()

        staff, _ = User.objects.get_or_create(
            username="staff1",
            defaults={"role": "staff", "is_staff": True, "email": "staff1@example.com"},
        )
        staff.set_password("Staff@12345")
        staff.save()

        client, _ = User.objects.get_or_create(
            username="client1",
            defaults={"role": "client", "email": "client1@example.com", "phone": "012345678"},
        )
        client.set_password("Client@12345")
        client.save()

        for cat_name, items in DEMO_MENU.items():
            category, _ = Category.objects.get_or_create(name=cat_name)
            for name, desc, price, stock in items:
                MenuItem.objects.update_or_create(
                    category=category,
                    name=name,
                    defaults={"description": desc, "price": price, "stock": stock, "is_available": True},
                )

        self.stdout.write(self.style.SUCCESS("Demo data ready."))
        self.stdout.write("Logins: admin / Admin@12345, staff1 / Staff@12345, client1 / Client@12345")
