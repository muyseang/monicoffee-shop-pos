from rest_framework import serializers

from .models import PaymentSettings


class ManualQRSerializer(serializers.ModelSerializer):
    enabled = serializers.BooleanField(source="qr_available")
    qr_image_url = serializers.SerializerMethodField()

    class Meta:
        model = PaymentSettings
        fields = [
            "enabled",
            "qr_image_url",
            "account_name",
            "khr_account_number",
            "usd_account_number",
            "instructions",
        ]

    def get_qr_image_url(self, obj):
        if not obj.qr_image:
            return None
        request = self.context.get("request")
        url = obj.qr_image.url
        return request.build_absolute_uri(url) if request else url
