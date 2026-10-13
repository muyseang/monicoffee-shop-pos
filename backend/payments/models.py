from django.db import models


class PaymentSettings(models.Model):
    """The shop's payment configuration — a single row, edited by the Owner.

    Cash is always accepted, so only the manual QR method is stored. "Manual"
    because there is no bank integration in the MVP: the customer scans the
    shop's own bank QR and staff confirm the payment by hand.
    """

    qr_enabled = models.BooleanField(default=False)
    qr_image = models.ImageField(upload_to="payments/", blank=True, null=True)
    account_name = models.CharField(max_length=100, blank=True)
    khr_account_number = models.CharField(max_length=30, blank=True)
    usd_account_number = models.CharField(max_length=30, blank=True)
    instructions = models.TextField(
        blank=True,
        default=(
            "Scan the QR with your banking app and pay the exact amount. "
            "Staff will confirm once payment is received."
        ),
    )
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        verbose_name = "payment settings"
        verbose_name_plural = "payment settings"

    @classmethod
    def load(cls):
        # pk=1 is the only row; created with defaults the first time it's read.
        settings, _ = cls.objects.get_or_create(pk=1)
        return settings

    @property
    def qr_available(self):
        # Checkout only offers QR when it's switched on AND there's a code to
        # show — a stale toggle with no image must not reach customers.
        return self.qr_enabled and bool(self.qr_image)

    def save(self, *args, **kwargs):
        self.pk = 1
        super().save(*args, **kwargs)

    def __str__(self):
        return "Payment settings"
