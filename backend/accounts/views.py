from django.conf import settings
from django.utils import timezone
from rest_framework import status
from rest_framework.authtoken.models import Token
from rest_framework.permissions import AllowAny, IsAuthenticated
from rest_framework.response import Response
from rest_framework.views import APIView

from .models import PhoneVerification, User
from .serializers import (
    ClientSerializer,
    LoginRequestSerializer,
    RegisterRequestSerializer,
    VerifyOTPSerializer,
)


def _otp_response(otp):
    data = {
        "request_id": str(otp.request_id),
        "message": "OTP sent.",
        "expires_in": PhoneVerification.TTL_SECONDS,
    }
    if settings.DEBUG:
        # No SMS gateway is integrated in the MVP (see scope memo) — exposing the
        # code in DEBUG only keeps local/demo testing possible without a real provider.
        data["debug_code"] = otp.code
    print(f"[OTP] {otp.purpose} code for {otp.phone}: {otp.code}")
    return data


def _check_cooldown(phone, purpose):
    recent = (
        PhoneVerification.objects.filter(phone=phone, purpose=purpose, is_used=False)
        .order_by("-created_at")
        .first()
    )
    if recent:
        elapsed = (timezone.now() - recent.created_at).total_seconds()
        if elapsed < PhoneVerification.RESEND_COOLDOWN_SECONDS:
            return PhoneVerification.RESEND_COOLDOWN_SECONDS - elapsed
    return None


def _validate_otp(otp, code):
    if not otp:
        return "No active code for this phone number. Please request a new one."
    if otp.is_expired():
        return "This code has expired. Please request a new one."
    if otp.attempts >= PhoneVerification.MAX_ATTEMPTS:
        return "Too many incorrect attempts. Please request a new code."
    if otp.code != code:
        otp.attempts += 1
        otp.save(update_fields=["attempts"])
        return "Incorrect code."
    return None


class RegisterRequestOTPView(APIView):
    permission_classes = [AllowAny]

    def post(self, request):
        serializer = RegisterRequestSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        phone = serializer.validated_data["phone"]
        full_name = serializer.validated_data["full_name"]

        existing = User.objects.filter(phone=phone, role=User.Role.CLIENT).first()
        if existing and existing.is_active:
            return Response(
                {"detail": "This phone number is already registered. Please log in."},
                status=status.HTTP_400_BAD_REQUEST,
            )

        wait = _check_cooldown(phone, PhoneVerification.Purpose.REGISTER)
        if wait:
            return Response(
                {"detail": f"Please wait {int(wait)}s before requesting another code."},
                status=status.HTTP_429_TOO_MANY_REQUESTS,
            )

        if existing:
            existing.first_name = full_name
            existing.save(update_fields=["first_name"])
        else:
            User.objects.create_user(
                username=phone,
                phone=phone,
                first_name=full_name,
                role=User.Role.CLIENT,
                is_active=False,
            )

        otp = PhoneVerification.generate(phone, PhoneVerification.Purpose.REGISTER)
        return Response(_otp_response(otp), status=status.HTTP_200_OK)


class RegisterVerifyOTPView(APIView):
    permission_classes = [AllowAny]

    def post(self, request):
        serializer = VerifyOTPSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        phone = serializer.validated_data["phone"]
        code = serializer.validated_data["code"]

        user = User.objects.filter(phone=phone, role=User.Role.CLIENT, is_active=False).first()
        if not user:
            return Response(
                {"detail": "No pending registration for this phone number."},
                status=status.HTTP_400_BAD_REQUEST,
            )

        otp = (
            PhoneVerification.objects.filter(
                phone=phone, purpose=PhoneVerification.Purpose.REGISTER, is_used=False
            )
            .order_by("-created_at")
            .first()
        )
        error = _validate_otp(otp, code)
        if error:
            return Response({"detail": error}, status=status.HTTP_400_BAD_REQUEST)

        otp.is_used = True
        otp.save(update_fields=["is_used"])
        user.is_active = True
        user.save(update_fields=["is_active"])

        token, _ = Token.objects.get_or_create(user=user)
        return Response(
            {"token": token.key, "user": ClientSerializer(user).data}, status=status.HTTP_200_OK
        )


class LoginRequestOTPView(APIView):
    permission_classes = [AllowAny]

    def post(self, request):
        serializer = LoginRequestSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        phone = serializer.validated_data["phone"]

        user = User.objects.filter(phone=phone, role=User.Role.CLIENT, is_active=True).first()
        if not user:
            return Response(
                {"detail": "No account found for this phone number."},
                status=status.HTTP_404_NOT_FOUND,
            )

        wait = _check_cooldown(phone, PhoneVerification.Purpose.LOGIN)
        if wait:
            return Response(
                {"detail": f"Please wait {int(wait)}s before requesting another code."},
                status=status.HTTP_429_TOO_MANY_REQUESTS,
            )

        otp = PhoneVerification.generate(phone, PhoneVerification.Purpose.LOGIN)
        return Response(_otp_response(otp), status=status.HTTP_200_OK)


class LoginVerifyOTPView(APIView):
    permission_classes = [AllowAny]

    def post(self, request):
        serializer = VerifyOTPSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        phone = serializer.validated_data["phone"]
        code = serializer.validated_data["code"]

        user = User.objects.filter(phone=phone, role=User.Role.CLIENT, is_active=True).first()
        if not user:
            return Response(
                {"detail": "No account found for this phone number."},
                status=status.HTTP_404_NOT_FOUND,
            )

        otp = (
            PhoneVerification.objects.filter(
                phone=phone, purpose=PhoneVerification.Purpose.LOGIN, is_used=False
            )
            .order_by("-created_at")
            .first()
        )
        error = _validate_otp(otp, code)
        if error:
            return Response({"detail": error}, status=status.HTTP_400_BAD_REQUEST)

        otp.is_used = True
        otp.save(update_fields=["is_used"])

        token, _ = Token.objects.get_or_create(user=user)
        return Response(
            {"token": token.key, "user": ClientSerializer(user).data}, status=status.HTTP_200_OK
        )


def _status_response(request_id, purpose):
    otp = PhoneVerification.objects.filter(request_id=request_id, purpose=purpose).first()
    if not otp:
        return Response({"detail": "Unknown request_id."}, status=status.HTTP_404_NOT_FOUND)

    if not otp.is_used:
        payload = {"verified": False}
        if otp.is_expired():
            payload["expired"] = True
        return Response(payload)

    # The OTP was consumed by whoever actually proved the code (the SMS Forwarder
    # device, or a manual entry) — this just reports that outcome back to the
    # original requester, identified by their own request_id, not by phone number
    # alone (which an attacker could guess and race to claim the token with).
    user = User.objects.filter(phone=otp.phone, role=User.Role.CLIENT, is_active=True).first()
    if not user:
        return Response({"verified": False})

    token, _ = Token.objects.get_or_create(user=user)
    return Response({"verified": True, "token": token.key, "user": ClientSerializer(user).data})


class RegisterStatusView(APIView):
    permission_classes = [AllowAny]

    def get(self, request):
        request_id = request.query_params.get("request_id")
        if not request_id:
            return Response(
                {"detail": "request_id is required."}, status=status.HTTP_400_BAD_REQUEST
            )
        return _status_response(request_id, PhoneVerification.Purpose.REGISTER)


class LoginStatusView(APIView):
    permission_classes = [AllowAny]

    def get(self, request):
        request_id = request.query_params.get("request_id")
        if not request_id:
            return Response(
                {"detail": "request_id is required."}, status=status.HTTP_400_BAD_REQUEST
            )
        return _status_response(request_id, PhoneVerification.Purpose.LOGIN)


class LogoutView(APIView):
    permission_classes = [IsAuthenticated]

    def post(self, request):
        Token.objects.filter(user=request.user).delete()
        return Response(status=status.HTTP_204_NO_CONTENT)
