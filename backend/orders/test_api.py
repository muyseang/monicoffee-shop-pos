import shutil
from datetime import time

from django.test import override_settings
from django.urls import reverse
from rest_framework.authtoken.models import Token
from rest_framework.test import APIClient

from menu.models import ItemOption
from payments.models import PaymentSettings

from .models import Order
from .tests import MEDIA_ROOT, OrderTestCase, png_upload


@override_settings(MEDIA_ROOT=MEDIA_ROOT)
class ClientApiTestCase(OrderTestCase):
    @classmethod
    def tearDownClass(cls):
        shutil.rmtree(MEDIA_ROOT, ignore_errors=True)
        super().tearDownClass()

    def setUp(self):
        super().setUp()
        self.size = ItemOption.objects.create(menu_item=self.latte, name="Size", choices=["Small", "Medium"])
        self.shot = ItemOption.objects.create(menu_item=self.latte, name="Extra Shot", extra_price="0.50")
        self.api = self.api_for(self.client_user)

    def api_for(self, user):
        api = APIClient()
        api.credentials(HTTP_AUTHORIZATION=f"Token {Token.objects.create(user=user).key}")
        return api

    def order_body(self, **overrides):
        body = {
            "order_type": "dine_in",
            "table_number": "5",
            "payment_method": "cash",
            "items": [{"menu_item": self.latte.pk, "quantity": 1}],
        }
        body.update(overrides)
        return body


class MenuApiTests(ClientApiTestCase):
    def test_requires_login(self):
        self.assertEqual(APIClient().get(reverse("menu-items")).status_code, 401)

    def test_lists_visible_items_with_stock_flag_and_options(self):
        self.mocha.is_available = False
        self.mocha.save()
        items = self.api.get(reverse("menu-items")).json()
        self.assertEqual([i["name"] for i in items], ["Iced Latte"])  # hidden Mocha left out
        self.assertTrue(items[0]["in_stock"])
        self.assertEqual([o["name"] for o in items[0]["options"]], ["Size", "Extra Shot"])

    def test_out_of_stock_item_is_listed_but_flagged(self):
        item = self.api.get(reverse("menu-item-detail", args=[self.mocha.pk])).json()
        self.assertFalse(item["in_stock"])

    def test_hidden_item_detail_is_404(self):
        self.latte.is_available = False
        self.latte.save()
        self.assertEqual(self.api.get(reverse("menu-item-detail", args=[self.latte.pk])).status_code, 404)

    def test_filters(self):
        self.assertEqual(len(self.api.get(reverse("menu-items"), {"q": "latte"}).json()), 1)
        other = self.api.get(reverse("menu-items"), {"category": 999}).json()
        self.assertEqual(other, [])


class PlaceOrderApiTests(ClientApiTestCase):
    def test_places_order_with_options_priced_by_server(self):
        body = self.order_body(items=[{
            "menu_item": self.latte.pk, "quantity": 2,
            "options": [{"option": self.size.pk, "choice": "Medium"}, {"option": self.shot.pk}],
        }])
        response = self.api.post(reverse("orders"), body, format="json")
        self.assertEqual(response.status_code, 201, response.json())
        order = response.json()
        self.assertEqual(order["total"], "20.00")  # (9.50 + 0.50) x 2
        self.assertEqual(order["items"][0]["options"], "Size: Medium · Extra Shot")
        self.assertEqual(order["status"], "pending")
        self.assertTrue(order["number"].startswith("MC-"))
        self.assertEqual(self.stock(self.latte), 6)
        self.assertEqual(Order.objects.get().source, Order.Source.APP)

    def test_same_item_with_different_options_counts_against_one_stock(self):
        self.latte.stock = 3
        self.latte.save()
        body = self.order_body(items=[
            {"menu_item": self.latte.pk, "quantity": 2, "options": [{"option": self.size.pk, "choice": "Small"}]},
            {"menu_item": self.latte.pk, "quantity": 2, "options": [{"option": self.size.pk, "choice": "Medium"}]},
        ])
        response = self.api.post(reverse("orders"), body, format="json")
        self.assertEqual(response.status_code, 400)
        self.assertEqual(response.json()["detail"], "Only 3 Iced Latte left.")
        self.assertEqual(self.stock(self.latte), 3)

    def test_out_of_stock_rejected(self):
        body = self.order_body(items=[{"menu_item": self.mocha.pk, "quantity": 1}])
        response = self.api.post(reverse("orders"), body, format="json")
        self.assertEqual(response.json()["detail"], "Mocha is out of stock.")

    def test_dine_in_needs_table_and_pickup_needs_time(self):
        response = self.api.post(reverse("orders"), self.order_body(table_number=""), format="json")
        self.assertIn("table_number", response.json())
        response = self.api.post(reverse("orders"), self.order_body(order_type="pickup"), format="json")
        self.assertIn("pickup_time", response.json())
        response = self.api.post(
            reverse("orders"), self.order_body(order_type="pickup", pickup_time="10:30"), format="json"
        )
        self.assertEqual(response.status_code, 201)
        self.assertEqual(Order.objects.get().pickup_time, time(10, 30))

    def test_invalid_options_rejected(self):
        bad_choice = [{"option": self.size.pk, "choice": "Huge"}]
        other_item = [{"option": ItemOption.objects.create(menu_item=self.mocha, name="X").pk}]
        twice = [{"option": self.shot.pk}, {"option": self.shot.pk}]
        for options in (bad_choice, other_item, twice):
            body = self.order_body(items=[{"menu_item": self.latte.pk, "quantity": 1, "options": options}])
            self.assertEqual(self.api.post(reverse("orders"), body, format="json").status_code, 400)
        self.assertFalse(Order.objects.exists())

    def test_staff_token_refused(self):
        response = self.api_for(self.staff).post(reverse("orders"), self.order_body(), format="json")
        self.assertEqual(response.status_code, 403)

    def test_qr_refused_when_turned_off(self):
        response = self.api.post(reverse("orders"), self.order_body(payment_method="manual_qr"), format="json")
        self.assertEqual(response.status_code, 400)


class TrackOrderApiTests(ClientApiTestCase):
    def setUp(self):
        super().setUp()
        self.order = self.place(client=self.client_user, source=Order.Source.APP)

    def test_lists_only_own_orders_and_filters_by_tab(self):
        other = self.place(client=None)
        ids = [o["id"] for o in self.api.get(reverse("orders")).json()]
        self.assertEqual(ids, [self.order.pk])
        self.assertEqual(len(self.api.get(reverse("orders"), {"status": "active"}).json()), 1)
        self.assertEqual(self.api.get(reverse("orders"), {"status": "completed"}).json(), [])
        self.assertEqual(self.api.get(reverse("order-detail", args=[other.pk])).status_code, 404)

    def test_cancel_only_while_pending_and_returns_stock(self):
        response = self.api.post(reverse("order-cancel", args=[self.order.pk]))
        self.assertEqual(response.json()["status"], "cancelled")
        self.assertEqual(self.stock(self.latte), 8)
        second = self.place(client=self.client_user)
        second.status = Order.Status.ACCEPTED
        second.save()
        self.assertEqual(self.api.post(reverse("order-cancel", args=[second.pk])).status_code, 400)

    def test_line_image_survives_menu_item_deletion(self):
        self.latte.image = png_upload("latte.png")
        self.latte.save()
        line = self.api.get(reverse("order-detail", args=[self.order.pk])).json()["items"][0]
        self.assertIn("/media/menu/", line["image_url"])
        self.latte.delete()
        line = self.api.get(reverse("order-detail", args=[self.order.pk])).json()["items"][0]
        self.assertEqual((line["name"], line["image_url"]), ("Iced Latte", None))

    def test_rejection_reason_visible_to_client(self):
        from .services import reject_order

        reject_order(self.order, "Out of oat milk")
        order = self.api.get(reverse("order-detail", args=[self.order.pk])).json()
        self.assertEqual((order["status"], order["rejection_reason"]), ("rejected", "Out of oat milk"))


class PaymentProofApiTests(ClientApiTestCase):
    def setUp(self):
        super().setUp()
        settings = PaymentSettings.load()
        settings.qr_enabled = True
        settings.qr_image = png_upload()
        settings.save()
        self.order = self.place(client=self.client_user, payment_method=Order.PaymentMethod.MANUAL_QR)

    def upload(self, order, image):
        return self.api.post(reverse("order-payment-proof", args=[order.pk]), {"image": image}, format="multipart")

    def test_upload_marks_pending_review(self):
        response = self.upload(self.order, png_upload("proof.png"))
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()["payment_status_label"], "Pending review")
        self.assertIsNotNone(response.json()["payment_proof_url"])

    def test_rejects_non_image_and_cash_orders(self):
        from django.core.files.uploadedfile import SimpleUploadedFile

        fake = SimpleUploadedFile("x.png", b"not an image", content_type="image/png")
        self.assertEqual(self.upload(self.order, fake).status_code, 400)
        cash = self.place(client=self.client_user)
        self.assertEqual(self.upload(cash, png_upload()).status_code, 400)


class StatusEndpointTests(ClientApiTestCase):
    def test_malformed_request_id_is_400_not_500(self):
        response = APIClient().get("/api/auth/login/status/", {"request_id": "x"})
        self.assertEqual(response.status_code, 400)


class ProfileApiTests(ClientApiTestCase):
    def test_get_own_profile(self):
        me = self.api.get(reverse("me")).json()
        self.assertEqual((me["full_name"], me["phone"]), ("Sokha Chan", "012345678"))

    def test_update_name_and_address_but_not_phone(self):
        response = self.api.patch(
            reverse("me"), {"full_name": "  Sokha C. ", "address": "#12, St 289", "phone": "099999999"}, format="json"
        )
        self.assertEqual(response.status_code, 200)
        self.client_user.refresh_from_db()
        self.assertEqual(
            (self.client_user.first_name, self.client_user.address, self.client_user.phone),
            ("Sokha C.", "#12, St 289", "012345678"),
        )

    def test_blank_name_rejected(self):
        response = self.api.patch(reverse("me"), {"full_name": "   "}, format="json")
        self.assertEqual(response.status_code, 400)

    def test_clients_only(self):
        self.assertEqual(APIClient().get(reverse("me")).status_code, 401)
        self.assertEqual(self.api_for(self.staff).get(reverse("me")).status_code, 403)
