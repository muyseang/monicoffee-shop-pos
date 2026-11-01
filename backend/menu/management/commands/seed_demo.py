from pathlib import Path

from django.contrib.auth import get_user_model
from django.core.files.base import ContentFile
from django.core.management.base import BaseCommand
from django.db import transaction
from django.utils.text import slugify

from menu.demo_images import render
from menu.models import Category, ItemOption, MenuItem

# Real photos (Unsplash License, see demo_photos/CREDITS.md), named <slug>.jpg.
# Items without one get a drawn illustration from menu.demo_images instead.
PHOTOS_DIR = Path(__file__).resolve().parents[2] / "demo_photos"
DEMO_IMAGE_PREFIX = "menu/demo-"

# Option sets attached to newly created drinks (display-only in the MVP).
OPTION_SETS = {
    "coffee": [
        ("Size", ["Small", "Medium", "Large"], "0"),
        ("Extra Shot", [], "0.50"),
        ("Syrup", ["Vanilla", "Caramel", "Hazelnut"], "0.25"),
    ],
    "drink": [("Size", ["Small", "Medium", "Large"], "0")],
    None: [],
}

# category -> (image kind, option set, items)
# item: (name, description, price, stock, image colour or None)
DEMO_MENU = {
    "Hot Coffee": ("hot", "coffee", [
        ("Espresso", "Single shot espresso", "2.00", 50, "#3E2417"),
        ("Americano", "Espresso with hot water", "2.50", 50, "#4A2C1D"),
        ("Latte", "Espresso with steamed milk", "3.00", 40, None),
        ("Cappuccino", "Espresso, steamed milk and thick foam", "3.00", 40, "#8A5A3B"),
        ("Flat White", "Double ristretto with silky milk", "3.25", 30, "#9A6B4B"),
        ("Caramel Macchiato", "Espresso, steamed milk and caramel drizzle", "3.50", 30, "#B07A45"),
        ("Mocha", "Espresso, chocolate and steamed milk", "3.50", 30, "#5B3A29"),
    ]),
    "Iced Coffee": ("iced", "coffee", [
        ("Iced Coffee", "Cold brew over ice", "2.75", 40, None),
        ("Iced Americano", "Espresso and cold water over ice", "2.75", 40, "#4A2C1D"),
        ("Iced Latte", "Espresso over cold milk and ice", "3.25", 40, "#B88A63"),
        ("Cold Brew", "Steeped for 18 hours, smooth and bold", "3.50", 25, "#3B2316"),
        ("Caramel Frappuccino", "Blended coffee, caramel and ice", "4.00", 25, "#C9A27E"),
    ]),
    "Tea": ("tea", "drink", [
        ("Green Tea", "Hot green tea", "2.00", 30, None),
        ("Milk Tea", "Classic milk tea", "2.50", 30, "#C8A27C"),
        ("Jasmine Tea", "Fragrant jasmine green tea", "2.00", 30, "#C9B458"),
        ("Thai Milk Tea", "Spiced black tea with condensed milk", "2.75", 30, "#E07B39"),
        ("Matcha Latte", "Stone-ground matcha with steamed milk", "3.50", 25, "#7FA04A"),
        ("Lemon Iced Tea", "Black tea, lemon and ice", "2.50", 30, "#F2C94C"),
    ]),
    "Pastry": ("pastry", None, [
        ("Croissant", "Butter croissant", "2.20", 20, "#E3A857"),
        ("Chocolate Cake", "Slice of chocolate cake", "3.50", 10, "#5B3A29"),
        ("Blueberry Muffin", "Muffin packed with blueberries", "2.50", 15, "#6B5B95"),
        ("Cinnamon Roll", "Soft roll with cinnamon sugar and icing", "2.75", 15, "#C68B59"),
        ("Cheesecake", "New York style baked cheesecake", "3.75", 10, "#F1D9A7"),
    ]),
}


class Command(BaseCommand):
    help = "Create demo users, categories and menu items with photos. Safe to run more than once."

    def add_arguments(self, parser):
        parser.add_argument(
            "--menu-only",
            action="store_true",
            help="Only add menu items and images; leave user accounts alone.",
        )

    @transaction.atomic
    def handle(self, *args, **options):
        if not options["menu_only"]:
            self.seed_users()
        created, imaged = self.seed_menu()
        self.stdout.write(self.style.SUCCESS(f"Demo data ready: {created} items added, {imaged} images set."))
        if not options["menu_only"]:
            self.stdout.write("Logins: admin / Admin@12345, staff1 / Staff@12345, client1 / Client@12345")

    def seed_users(self):
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

    def seed_menu(self):
        """Add missing items, and an image to items that need one.

        Never changes an existing item's price, stock, category or options, and
        never replaces a photo someone uploaded — so running it on a database the
        team has already edited only fills gaps. Items are matched by name across
        all categories, so a renamed category doesn't cause duplicates."""
        created = imaged = 0
        for position, (cat_name, (kind, option_set, items)) in enumerate(DEMO_MENU.items(), start=1):
            category, _ = Category.objects.get_or_create(name=cat_name, defaults={"display_order": position})
            for name, description, price, stock, colour in items:
                item = MenuItem.objects.filter(name__iexact=name).first()
                if item is None:
                    item = MenuItem.objects.create(
                        category=category, name=name, description=description, price=price, stock=stock
                    )
                    for option_name, choices, extra_price in OPTION_SETS[option_set]:
                        ItemOption.objects.create(
                            menu_item=item, name=option_name, choices=choices, extra_price=extra_price
                        )
                    created += 1
                if self.set_demo_image(item, kind, colour):
                    imaged += 1
        return created, imaged

    def set_demo_image(self, item, kind, colour):
        """Give `item` the bundled photo (or a drawn illustration if there's no
        photo for it). Only touches items with no image or an image this command
        set earlier (stored as menu/demo-*), so uploaded photos are kept."""
        current = item.image.name if item.image else ""
        if current and not current.startswith(DEMO_IMAGE_PREFIX):
            return False

        slug = slugify(item.name)
        photo = PHOTOS_DIR / f"{slug}.jpg"
        if photo.exists():
            filename, content = f"demo-{slug}.jpg", photo.read_bytes()
        else:
            filename, content = f"demo-{slug}.png", render(item.name, kind, colour)
        if current == f"menu/{filename}":
            return False  # already the best image available

        if current:
            item.image.delete(save=False)  # e.g. an earlier illustration, now replaced by a photo
        item.image.save(filename, ContentFile(content), save=True)
        return True
