import io
import shutil
import tempfile
from pathlib import Path
from unittest import mock

from django.core.management import call_command
from django.test import TestCase, override_settings
from django.utils.text import slugify
from PIL import Image

from accounts.models import User

from .management.commands.seed_demo import DEMO_MENU, PHOTOS_DIR
from .models import Category, MenuItem

MEDIA_ROOT = tempfile.mkdtemp()
DEMO_ITEM_COUNT = sum(len(items) for _, _, items in DEMO_MENU.values())


@override_settings(MEDIA_ROOT=MEDIA_ROOT)
class SeedDemoTests(TestCase):
    @classmethod
    def tearDownClass(cls):
        shutil.rmtree(MEDIA_ROOT, ignore_errors=True)
        super().tearDownClass()

    def seed(self, *args):
        call_command("seed_demo", *args, stdout=io.StringIO())

    def test_every_item_gets_its_photo_and_drinks_get_options(self):
        self.seed()
        self.assertEqual(MenuItem.objects.count(), DEMO_ITEM_COUNT)
        for item in MenuItem.objects.all():
            with Image.open(item.image.path) as image:
                self.assertEqual((image.format, image.size), ("JPEG", (800, 800)), item.name)
        latte = MenuItem.objects.get(name="Caramel Macchiato")
        self.assertEqual([o.name for o in latte.options.all()], ["Size", "Extra Shot", "Syrup"])
        self.assertFalse(MenuItem.objects.get(name="Cheesecake").options.exists())

    def test_every_demo_item_has_a_bundled_photo_and_credit(self):
        credits = (PHOTOS_DIR / "CREDITS.md").read_text()
        for _, _, items in DEMO_MENU.values():
            for name, *_ in items:
                self.assertTrue((PHOTOS_DIR / f"{slugify(name)}.jpg").exists(), name)
                self.assertIn(f"`{slugify(name)}.jpg`", credits)

    def test_illustration_fallback_when_no_photo(self):
        with mock.patch("menu.management.commands.seed_demo.PHOTOS_DIR", Path(MEDIA_ROOT) / "no-photos"):
            self.seed("--menu-only")
        with Image.open(MenuItem.objects.get(name="Latte").image.path) as image:
            self.assertEqual((image.format, image.size), ("PNG", (600, 600)))

    def test_earlier_illustration_is_upgraded_to_the_photo(self):
        with mock.patch("menu.management.commands.seed_demo.PHOTOS_DIR", Path(MEDIA_ROOT) / "no-photos"):
            self.seed("--menu-only")
        latte = MenuItem.objects.get(name="Latte")
        old_path = latte.image.path
        self.seed("--menu-only")
        latte.refresh_from_db()
        self.assertTrue(latte.image.name.endswith(".jpg"))
        self.assertFalse(Path(old_path).exists())

    def test_running_twice_adds_nothing(self):
        self.seed()
        self.seed()
        self.assertEqual(MenuItem.objects.count(), DEMO_ITEM_COUNT)

    def test_existing_items_and_renamed_categories_are_left_alone(self):
        # A team that renamed "Tea" and repriced Milk Tea, with its own photo.
        iced_tea = Category.objects.create(name="Iced Tea", display_order=2)
        milk_tea = MenuItem.objects.create(category=iced_tea, name="milk tea", price="9.99", stock=3)
        self.seed("--menu-only")
        milk_tea.refresh_from_db()
        self.assertEqual((milk_tea.category, str(milk_tea.price), milk_tea.stock), (iced_tea, "9.99", 3))
        self.assertTrue(milk_tea.image)  # an illustration was added because it had none
        self.assertEqual(MenuItem.objects.filter(name__iexact="Milk Tea").count(), 1)

    def test_uploaded_photo_is_never_replaced(self):
        self.seed()
        latte = MenuItem.objects.get(name="Latte")
        latte.image.name = "menu/real-photo.jpg"
        latte.save()
        self.seed()
        latte.refresh_from_db()
        self.assertEqual(latte.image.name, "menu/real-photo.jpg")

    def test_menu_only_leaves_accounts_alone(self):
        self.seed("--menu-only")
        self.assertFalse(User.objects.exists())
