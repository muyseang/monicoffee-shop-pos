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
