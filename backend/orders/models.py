from django.conf import settings
from django.db import models


class Order(models.Model):
    class Status(models.TextChoices):
        PENDING = "pending", "Pending"
        ACCEPTED = "accepted", "Accepted"
        PREPARING = "preparing", "Preparing"
        READY = "ready", "Ready"
        COMPLETED = "completed", "Completed"
        REJECTED = "rejected", "Rejected"
        CANCELLED = "cancelled", "Cancelled"

    class OrderType(models.TextChoices):
        DINE_IN = "dine_in", "Dine-in"
        PICKUP = "pickup", "Pickup"

    class Source(models.TextChoices):
        APP = "app", "Mobile app"
        POS = "pos", "POS"

    class PaymentMethod(models.TextChoices):
        CASH = "cash", "Cash"
        MANUAL_QR = "manual_qr", "Manual QR"

    class PaymentStatus(models.TextChoices):
        NOT_PAID = "not_paid", "Not paid"
        AWAITING = "awaiting", "Waiting for confirmation"
        PAID = "paid", "Paid"

    # The happy path, in order. Rejected and Cancelled branch off it.
    FLOW = [Status.PENDING, Status.ACCEPTED, Status.PREPARING, Status.READY, Status.COMPLETED]
    FINAL_STATUSES = {Status.COMPLETED, Status.REJECTED, Status.CANCELLED}

    # Empty for a walk-in customer without an account (POS).
    client = models.ForeignKey(
        settings.AUTH_USER_MODEL, on_delete=models.SET_NULL, null=True, blank=True, related_name="orders"
    )
    created_by = models.ForeignKey(
        settings.AUTH_USER_MODEL, on_delete=models.SET_NULL, null=True, blank=True, related_name="+"
    )
    source = models.CharField(max_length=10, choices=Source.choices)
    order_type = models.CharField(max_length=10, choices=OrderType.choices)
    table_number = models.CharField(max_length=20, blank=True)
    pickup_time = models.TimeField(null=True, blank=True)

    status = models.CharField(max_length=20, choices=Status.choices, default=Status.PENDING, db_index=True)
    rejection_reason = models.TextField(blank=True)
    rejected_at = models.DateTimeField(null=True, blank=True)

    payment_method = models.CharField(max_length=20, choices=PaymentMethod.choices)
    payment_status = models.CharField(max_length=20, choices=PaymentStatus.choices)
    # Screenshot of the bank transfer, uploaded by the customer from the app.
    payment_proof = models.ImageField(upload_to="payment_proofs/", blank=True, null=True)

    total = models.DecimalField(max_digits=10, decimal_places=2, default=0)
    created_at = models.DateTimeField(auto_now_add=True, db_index=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        ordering = ("-created_at", "-pk")

    @property
    def number(self):
        return f"MC-{self.pk:04d}"

    @property
    def next_status(self):
        if self.status in self.FLOW[:-1]:
            return self.FLOW[self.FLOW.index(self.status) + 1]
        return None

    @property
    def is_final(self):
        return self.status in self.FINAL_STATUSES

    @property
    def can_confirm_payment(self):
        return (
            self.payment_method == self.PaymentMethod.MANUAL_QR
            and self.payment_status != self.PaymentStatus.PAID
            and self.status not in (self.Status.REJECTED, self.Status.CANCELLED)
        )

    @property
    def payment_status_label(self):
        # A customer's uploaded screenshot is what staff are waiting to review.
        if self.payment_status == self.PaymentStatus.AWAITING and self.payment_proof:
            return "Pending review"
        return self.get_payment_status_display()

    def __str__(self):
        return f"#{self.number}"


class OrderItem(models.Model):
    order = models.ForeignKey(Order, on_delete=models.CASCADE, related_name="items")
    # Kept for returning stock; SET_NULL so deleting a menu item never deletes
    # order history — the name and price below are a snapshot for that reason.
    menu_item = models.ForeignKey("menu.MenuItem", on_delete=models.SET_NULL, null=True, related_name="+")
    name = models.CharField(max_length=150)
    unit_price = models.DecimalField(max_digits=8, decimal_places=2)
    quantity = models.PositiveIntegerField()
    # Display text of the chosen options, e.g. "Size M · Extra Shot".
    options = models.CharField(max_length=255, blank=True)

    class Meta:
        ordering = ("pk",)

    @property
    def line_total(self):
        return self.unit_price * self.quantity

    def __str__(self):
        return f"{self.name} x{self.quantity}"
