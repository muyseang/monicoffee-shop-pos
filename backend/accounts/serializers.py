from rest_framework import serializers

from .models import PhoneVerification, User


def normalize_phone(phone):
    return phone.replace(" ", "").replace("-", "")


class RegisterRequestSerializer(serializers.Serializer):
    full_name = serializers.CharField(max_length=150)
    phone = serializers.CharField(max_length=30)

    def validate_phone(self, value):
        return normalize_phone(value)


class LoginRequestSerializer(serializers.Serializer):
    phone = serializers.CharField(max_length=30)

    def validate_phone(self, value):
        return normalize_phone(value)


class VerifyOTPSerializer(serializers.Serializer):
    phone = serializers.CharField(max_length=30)
    code = serializers.CharField(max_length=PhoneVerification.CODE_LENGTH)

    def validate_phone(self, value):
        return normalize_phone(value)


class ClientSerializer(serializers.ModelSerializer):
    full_name = serializers.CharField(source="first_name")

    class Meta:
        model = User
        fields = ["id", "full_name", "phone", "address"]


class ClientProfileSerializer(ClientSerializer):
    """What a client may edit about themselves (design 33-Personal Info).

    The phone number is read-only: it is the login identity, so changing it
    would need a fresh OTP check — that flow doesn't exist yet."""

    full_name = serializers.CharField(source="first_name", max_length=150)
    address = serializers.CharField(max_length=255, required=False, allow_blank=True)

    class Meta(ClientSerializer.Meta):
        read_only_fields = ["id", "phone"]

    def validate_full_name(self, value):
        value = value.strip()
        if not value:
            raise serializers.ValidationError("Full name can't be empty.")
        return value

    def validate_address(self, value):
        return value.strip()
