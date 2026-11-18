from rest_framework.response import Response
from rest_framework.views import APIView

from accounts.models import User

from .models import PaymentSettings
from .serializers import ManualQRSerializer


class PaymentMethodsView(APIView):
    """What checkout (app and POS) may offer. Uses the default IsAuthenticated:
    only a logged-in client or staff member reaches checkout."""

    def get(self, request):
        payment_settings = PaymentSettings.load()
        return Response(
            {
                # App orders are QR only (see PlaceOrderSerializer); cash is taken at the counter.
                "cash": {"enabled": request.user.role != User.Role.CLIENT},
                "manual_qr": ManualQRSerializer(payment_settings, context={"request": request}).data,
            }
        )
