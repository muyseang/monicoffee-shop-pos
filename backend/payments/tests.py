import io
import shutil
import tempfile

from django.core.files.uploadedfile import SimpleUploadedFile
from django.test import TestCase, override_settings
from django.urls import reverse
from PIL import Image
from rest_framework.authtoken.models import Token

from accounts.models import User

from .models import PaymentSettings

MEDIA_ROOT = tempfile.mkdtemp()


def png_upload(name="qr.png"):
    buffer = io.BytesIO()
    Image.new("RGB", (10, 10), "white").save(buffer, format="PNG")
    return SimpleUploadedFile(name, buffer.getvalue(), content_type="image/png")


@override_settings(MEDIA_ROOT=MEDIA_ROOT)
class PaymentSettingsDashboardTests(TestCase):
    @classmethod
    def tearDownClass(cls):
        super().tearDownClass()
        shutil.rmtree(MEDIA_ROOT, ignore_errors=True)

    def setUp(self):
        self.owner = User.objects.create_user("owner", password="pw", role=User.Role.SUPER_ADMIN)
        self.staff = User.objects.create_user("staff", password="pw", role=User.Role.STAFF)
        self.url = reverse("dashboard:settings")

    def form_data(self, **overrides):
        data = {
            "qr_enabled": "on",
            "account_name": "Muyseang Ung",
            "khr_account_number": "600 863 042",
            "usd_account_number": "002 172 240",
            "instructions": "Scan and pay.",
        }
        data.update(overrides)
        return data

    def test_staff_cannot_open_settings(self):
        self.client.force_login(self.staff)
        self.assertEqual(self.client.get(self.url).status_code, 403)

    def test_owner_sees_settings(self):
        self.client.force_login(self.owner)
        response = self.client.get(self.url)
        self.assertContains(response, "Manual QR Code Details")
        self.assertContains(response, "Live Preview")

    def test_cannot_enable_qr_without_image(self):
        self.client.force_login(self.owner)
        response = self.client.post(self.url, self.form_data())
        self.assertContains(response, "Upload a QR code before turning on Manual QR payments.")
        self.assertFalse(PaymentSettings.load().qr_enabled)

    def test_cannot_enable_qr_without_account_name(self):
        self.client.force_login(self.owner)
        response = self.client.post(self.url, self.form_data(account_name="  ", qr_image=png_upload()))
        self.assertContains(response, "Enter the account holder name shown to customers.")

    def test_save_with_qr_image(self):
        self.client.force_login(self.owner)
        response = self.client.post(self.url, self.form_data(qr_image=png_upload()))
        self.assertRedirects(response, self.url)

        saved = PaymentSettings.load()
        self.assertTrue(saved.qr_available)
        self.assertEqual(saved.account_name, "Muyseang Ung")
        self.assertTrue(saved.qr_image.name.startswith("payments/"))

    def test_rejects_non_image_upload(self):
        self.client.force_login(self.owner)
        fake = SimpleUploadedFile("qr.png", b"not an image", content_type="image/png")
        response = self.client.post(self.url, self.form_data(qr_image=fake))
        self.assertEqual(response.status_code, 200)
        self.assertFalse(PaymentSettings.load().qr_image)

    def test_turning_qr_off_keeps_details(self):
        self.client.force_login(self.owner)
        self.client.post(self.url, self.form_data(qr_image=png_upload()))
        self.client.post(self.url, self.form_data(qr_enabled=""))

        saved = PaymentSettings.load()
        self.assertFalse(saved.qr_enabled)
        self.assertTrue(saved.qr_image)
        self.assertEqual(saved.account_name, "Muyseang Ung")


@override_settings(MEDIA_ROOT=MEDIA_ROOT)
class PaymentMethodsAPITests(TestCase):
    def setUp(self):
        client_user = User.objects.create_user("012345678", role=User.Role.CLIENT)
        self.token = Token.objects.create(user=client_user)
        self.url = reverse("payment-methods")

    def test_requires_login(self):
        self.assertEqual(self.client.get(self.url).status_code, 401)

    def test_qr_hidden_until_configured(self):
        response = self.client.get(self.url, HTTP_AUTHORIZATION=f"Token {self.token.key}")
        self.assertEqual(response.status_code, 200)
        self.assertTrue(response.json()["cash"]["enabled"])
        self.assertFalse(response.json()["manual_qr"]["enabled"])
        self.assertIsNone(response.json()["manual_qr"]["qr_image_url"])

    def test_returns_qr_details_when_enabled(self):
        PaymentSettings.objects.create(
            qr_enabled=True,
            qr_image=png_upload(),
            account_name="Muyseang Ung",
            khr_account_number="600 863 042",
            usd_account_number="002 172 240",
        )
        response = self.client.get(self.url, HTTP_AUTHORIZATION=f"Token {self.token.key}")
        qr = response.json()["manual_qr"]
        self.assertTrue(qr["enabled"])
        self.assertTrue(qr["qr_image_url"].startswith("http://testserver/media/payments/"))
        self.assertEqual(qr["account_name"], "Muyseang Ung")
        self.assertEqual(qr["usd_account_number"], "002 172 240")
