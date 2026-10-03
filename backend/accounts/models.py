import random
import uuid

from django.contrib.auth.models import AbstractUser
from django.db import models
from django.utils import timezone


class User(AbstractUser):
    class Role(models.TextChoices):
        SUPER_ADMIN = "super_admin", "Super Admin"
        STAFF = "staff", "Staff"
        CLIENT = "client", "Client"

    role = models.CharField(max_length=20, choices=Role.choices, default=Role.CLIENT)
    phone = models.CharField(max_length=30, blank=True)
    address = models.CharField(max_length=255, blank=True)

    def __str__(self):
        return f"{self.username} ({self.role})"


class PhoneVerification(models.Model):
    class Purpose(models.TextChoices):
        REGISTER = "register", "Register"
        LOGIN = "login", "Login"

    CODE_LENGTH = 4
    TTL_SECONDS = 120
    RESEND_COOLDOWN_SECONDS = 30
    MAX_ATTEMPTS = 5

    # Identifies one request-otp call so the requester (and only the requester) can
    # poll for its outcome — the phone number alone isn't enough, since anyone could
    # guess/know a phone number and race to claim the token once a real device verifies it.
    request_id = models.UUIDField(default=uuid.uuid4, unique=True, editable=False)
    phone = models.CharField(max_length=30, db_index=True)
    purpose = models.CharField(max_length=10, choices=Purpose.choices)
    code = models.CharField(max_length=CODE_LENGTH)
    attempts = models.PositiveSmallIntegerField(default=0)
    is_used = models.BooleanField(default=False)
    created_at = models.DateTimeField(auto_now_add=True)
    expires_at = models.DateTimeField()

    @classmethod
    def generate(cls, phone, purpose):
        code = "".join(random.choices("0123456789", k=cls.CODE_LENGTH))
        return cls.objects.create(
            phone=phone,
            purpose=purpose,
            code=code,
            expires_at=timezone.now() + timezone.timedelta(seconds=cls.TTL_SECONDS),
        )

    def is_expired(self):
        return timezone.now() >= self.expires_at

    def __str__(self):
        return f"{self.phone} [{self.purpose}] {'used' if self.is_used else 'active'}"
