from django.test import TestCase
from django.urls import reverse

from .models import OtpSettings, User


class OtpReceivingNumberTests(TestCase):
    """The app is told which shop phone to text the code to, as set in Settings."""

    def test_code_request_returns_the_receiving_number(self):
        response = self.client.post(reverse("register-request-otp"), {"full_name": "Sokha Chan", "phone": "012345678"})
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()["sms_number"], OtpSettings.DEFAULT_NUMBER)

    def test_number_changed_in_settings_reaches_the_app(self):
        User.objects.create_user("012345678", phone="012345678", first_name="Sokha Chan", role=User.Role.CLIENT)
        OtpSettings.objects.update_or_create(pk=1, defaults={"receiving_number": "098765432"})
        response = self.client.post(reverse("login-request-otp"), {"phone": "012345678"})
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()["sms_number"], "098765432")
