from django import forms
from django.contrib.auth import authenticate
from django.contrib.auth.password_validation import validate_password

from accounts.models import User
from accounts.serializers import normalize_phone


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
