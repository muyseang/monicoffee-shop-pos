from django.db import models


class Category(models.Model):
    name = models.CharField(max_length=100, unique=True)
    # Lower numbers are listed first — in the dashboard and in the Android menu.
    display_order = models.PositiveIntegerField(default=0)

    class Meta:
        verbose_name_plural = "categories"
        ordering = ("display_order", "name")

    def __str__(self):
        return self.name


class MenuItem(models.Model):
    class StockStatus(models.TextChoices):
        IN_STOCK = "in_stock", "In Stock"
        OUT_OF_STOCK = "out_of_stock", "Out of Stock"
        UNAVAILABLE = "unavailable", "Unavailable"

    category = models.ForeignKey(Category, on_delete=models.PROTECT, related_name="items")
    name = models.CharField(max_length=150)
    description = models.TextField(blank=True)
    image = models.ImageField(upload_to="menu/", blank=True, null=True)
    price = models.DecimalField(max_digits=8, decimal_places=2)
    stock = models.PositiveIntegerField(default=0)
    is_available = models.BooleanField(default=True)

    class Meta:
        unique_together = ("category", "name")

    @property
    def in_stock(self):
        return self.is_available and self.stock > 0

    @property
    def stock_status(self):
        # A manually hidden item reads "Unavailable" whatever its quantity —
        # hiding is the owner's explicit choice, so it outranks the count.
        if not self.is_available:
            return self.StockStatus.UNAVAILABLE
        if self.stock > 0:
            return self.StockStatus.IN_STOCK
        return self.StockStatus.OUT_OF_STOCK

    def restock(self, quantity):
        # F() makes the database do the addition, so two people restocking at
        # the same moment can't overwrite each other's change.
        MenuItem.objects.filter(pk=self.pk).update(stock=models.F("stock") + quantity)
        self.refresh_from_db(fields=["stock"])

    def __str__(self):
        return self.name


class ItemOption(models.Model):
    # Display-only in the MVP (proposal 4.1.4): options are shown to the
    # customer but never change stock deduction.
    menu_item = models.ForeignKey(MenuItem, on_delete=models.CASCADE, related_name="options")
    name = models.CharField(max_length=50)
    # e.g. ["Small", "Medium", "Large"]; empty for a simple add-on like "Extra Shot".
    choices = models.JSONField(default=list, blank=True)
    extra_price = models.DecimalField(max_digits=6, decimal_places=2, default=0)

    class Meta:
        ordering = ("pk",)

    def __str__(self):
        return f"{self.menu_item.name}: {self.name}"
