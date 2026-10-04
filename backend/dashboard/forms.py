from decimal import Decimal

from django import forms
from django.contrib.auth import authenticate
from django.contrib.auth.password_validation import validate_password
from django.db import transaction
from django.db.models import Max

from accounts.models import User
from accounts.serializers import normalize_phone
from menu.models import Category, ItemOption, MenuItem


class StaffLoginForm(forms.Form):
    username = forms.CharField()
    password = forms.CharField(widget=forms.PasswordInput)

    def __init__(self, request=None, *args, **kwargs):
        self.request = request
        self.user = None
        super().__init__(*args, **kwargs)

    def clean(self):
        cleaned = super().clean()
        username = cleaned.get("username")
        password = cleaned.get("password")
        if not username or not password:
            return cleaned

        # authenticate() already refuses inactive users (deactivated staff),
        # so that case folds into the same generic "invalid" error on purpose —
        # it shouldn't reveal to a logged-out caller whether a username exists.
        user = authenticate(self.request, username=username, password=password)
        if user is None or user.role == User.Role.CLIENT:
            raise forms.ValidationError("Incorrect username or password.")

        self.user = user
        return cleaned

    def get_user(self):
        return self.user


class StaffForm(forms.Form):
    full_name = forms.CharField(max_length=150)
    username = forms.CharField(max_length=150)
    password = forms.CharField(widget=forms.PasswordInput, required=False)
    is_active = forms.BooleanField(required=False, initial=True)

    def __init__(self, *args, instance=None, **kwargs):
        self.instance = instance
        super().__init__(*args, **kwargs)
        if instance is None:
            self.fields["password"].required = True
        else:
            self.fields["password"].help_text = "Leave blank to keep the current password."

    def clean_username(self):
        username = self.cleaned_data["username"]
        existing = User.objects.filter(username=username)
        if self.instance is not None:
            existing = existing.exclude(pk=self.instance.pk)
        if existing.exists():
            raise forms.ValidationError("This username is already taken.")
        return username

    def clean_password(self):
        password = self.cleaned_data.get("password")
        if password:
            validate_password(password)
        return password

    def save(self):
        full_name = self.cleaned_data["full_name"]
        username = self.cleaned_data["username"]
        password = self.cleaned_data.get("password")
        is_active = self.cleaned_data.get("is_active", True)

        if self.instance is None:
            user = User.objects.create_user(
                username=username,
                first_name=full_name,
                role=User.Role.STAFF,
                is_active=is_active,
            )
            user.set_password(password)
            user.save()
            return user

        user = self.instance
        user.first_name = full_name
        user.username = username
        user.is_active = is_active
        if password:
            user.set_password(password)
        user.save()
        return user


class ClientForm(forms.Form):
    full_name = forms.CharField(max_length=150)
    phone = forms.CharField(max_length=30)
    address = forms.CharField(
        max_length=255,
        required=False,
        widget=forms.TextInput(attrs={"placeholder": "Street, district, city"}),
    )
    is_active = forms.BooleanField(required=False, initial=True)

    def __init__(self, *args, instance=None, **kwargs):
        self.instance = instance
        super().__init__(*args, **kwargs)

    def clean_phone(self):
        phone = normalize_phone(self.cleaned_data["phone"])
        # Clients log in with phone + OTP (no password), and the phone IS their
        # username — so it must be unique the same way a username would be.
        existing = User.objects.filter(username=phone)
        if self.instance is not None:
            existing = existing.exclude(pk=self.instance.pk)
        if existing.exists():
            raise forms.ValidationError("A client with this phone number already exists.")
        return phone

    def save(self):
        full_name = self.cleaned_data["full_name"]
        phone = self.cleaned_data["phone"]
        address = self.cleaned_data.get("address", "")
        is_active = self.cleaned_data.get("is_active", True)

        if self.instance is None:
            # No password is set — admin-created clients authenticate via
            # phone + OTP on first app login, same as self-registered ones.
            return User.objects.create_user(
                username=phone,
                phone=phone,
                first_name=full_name,
                address=address,
                role=User.Role.CLIENT,
                is_active=is_active,
            )

        user = self.instance
        user.first_name = full_name
        user.username = phone
        user.phone = phone
        user.address = address
        user.is_active = is_active
        user.save()
        return user


class RestockForm(forms.Form):
    # Restock only ever adds; lowering a count (spoilage, a miscount) goes
    # through Adjust, so a typo here can't silently wipe out stock.
    quantity = forms.IntegerField(min_value=1, max_value=10000)


class AdjustStockForm(forms.Form):
    quantity = forms.IntegerField(min_value=0, max_value=100000)


class CategoryForm(forms.ModelForm):
    display_order = forms.IntegerField(
        min_value=1,
        max_value=1000,
        required=False,
        help_text="Lower numbers are listed first. Leave blank to add it at the end.",
    )

    class Meta:
        model = Category
        fields = ["name", "display_order"]
        widgets = {"name": forms.TextInput(attrs={"placeholder": "e.g. Cold Drinks"})}

    def clean_name(self):
        name = self.cleaned_data["name"].strip()
        # MySQL's default collation compares case-insensitively, so "coffee"
        # would hit the unique constraint as a crash — catch it as a form error.
        existing = Category.objects.filter(name__iexact=name).exclude(pk=self.instance.pk)
        if existing.exists():
            raise forms.ValidationError("A category with this name already exists.")
        return name

    def clean_display_order(self):
        display_order = self.cleaned_data.get("display_order")
        if display_order is None:
            last = Category.objects.exclude(pk=self.instance.pk).aggregate(Max("display_order"))
            display_order = (last["display_order__max"] or 0) + 1
        return display_order


class MenuItemForm(forms.ModelForm):
    MAX_IMAGE_BYTES = 2 * 1024 * 1024
    ALLOWED_IMAGE_FORMATS = {"PNG", "JPEG"}

    price = forms.DecimalField(min_value=Decimal("0.01"), max_value=Decimal("9999.99"), decimal_places=2)
    stock = forms.IntegerField(min_value=0, max_value=100000)
    image = forms.ImageField(required=False, widget=forms.FileInput(attrs={"accept": "image/png,image/jpeg"}))
    remove_image = forms.BooleanField(required=False)

    class Meta:
        model = MenuItem
        fields = ["name", "description", "category", "price", "stock", "is_available", "image"]
        widgets = {
            "name": forms.TextInput(attrs={"placeholder": "e.g. Caramel Macchiato"}),
            "description": forms.Textarea(attrs={"rows": 3, "placeholder": "Short description shown to customers"}),
        }

    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.fields["category"].empty_label = "Select a category"
        self.fields["price"].widget.attrs.update({"step": "0.01", "placeholder": "0.00"})

    def clean_name(self):
        return self.cleaned_data["name"].strip()

    def clean_image(self):
        image = self.cleaned_data.get("image")
        # Only a new upload carries .image (set by Pillow during validation);
        # the item's existing file needs no re-checking.
        if image and hasattr(image, "image"):
            if image.size > self.MAX_IMAGE_BYTES:
                raise forms.ValidationError("Image must be 2 MB or smaller.")
            if image.image.format not in self.ALLOWED_IMAGE_FORMATS:
                raise forms.ValidationError("Image must be a PNG or JPG file.")
        return image

    def clean(self):
        cleaned = super().clean()
        name = cleaned.get("name")
        category = cleaned.get("category")
        if name and category:
            # Same reason as CategoryForm: MySQL compares names case-insensitively,
            # so check here rather than let unique_together raise a crash.
            existing = MenuItem.objects.filter(category=category, name__iexact=name).exclude(pk=self.instance.pk)
            if existing.exists():
                self.add_error("name", f"{category.name} already has an item with this name.")
        return cleaned

    def save(self, commit=True):
        old_image = MenuItem.objects.get(pk=self.instance.pk).image if self.instance.pk else None
        new_upload = "image" in self.changed_data and self.cleaned_data.get("image")

        if self.cleaned_data.get("remove_image") and not new_upload:
            self.instance.image = None

        item = super().save(commit=commit)

        # Django never deletes replaced files on its own; without this every
        # re-upload would leave the old image behind in MEDIA_ROOT. on_commit
        # waits for the surrounding transaction, so a rollback keeps the file.
        if commit and old_image and old_image.name != (item.image.name if item.image else None):
            transaction.on_commit(lambda: old_image.delete(save=False))
        return item


class ItemOptionForm(forms.ModelForm):
    choices_text = forms.CharField(
        required=False,
        max_length=255,
        widget=forms.TextInput(attrs={"placeholder": "Optional"}),
    )
    extra_price = forms.DecimalField(
        required=False,
        min_value=0,
        max_value=Decimal("999.99"),
        decimal_places=2,
        widget=forms.NumberInput(attrs={"step": "0.01", "placeholder": "0.00"}),
    )

    class Meta:
        model = ItemOption
        fields = ["name", "extra_price"]
        widgets = {"name": forms.TextInput(attrs={"placeholder": "e.g. Size"})}

    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        if self.instance.pk:
            self.initial["choices_text"] = ", ".join(self.instance.choices)

    def clean_extra_price(self):
        return self.cleaned_data.get("extra_price") or Decimal("0")

    def save(self, commit=True):
        text = self.cleaned_data.get("choices_text", "")
        self.instance.choices = [choice.strip() for choice in text.split(",") if choice.strip()]
        return super().save(commit=commit)


ItemOptionFormSet = forms.inlineformset_factory(
    MenuItem,
    ItemOption,
    form=ItemOptionForm,
    extra=0,
    can_delete=True,
    max_num=10,
    validate_max=True,
)
