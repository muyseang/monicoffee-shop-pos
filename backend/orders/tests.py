import io
import shutil
import tempfile
from datetime import timedelta

from django.core.files.uploadedfile import SimpleUploadedFile
from django.test import TestCase, override_settings
from django.urls import reverse
from django.utils import timezone
from PIL import Image

from accounts.models import User
from menu.models import Category, MenuItem
from payments.models import PaymentSettings

from .models import Order
from .services import OrderError, advance_order, cancel_order, confirm_payment, place_order, reject_order

MEDIA_ROOT = tempfile.mkdtemp()


def png_upload(name="qr.png"):
    buffer = io.BytesIO()
    Image.new("RGB", (10, 10), "white").save(buffer, format="PNG")
    return SimpleUploadedFile(name, buffer.getvalue(), content_type="image/png")


class OrderTestCase(TestCase):
    def setUp(self):
        coffee = Category.objects.create(name="Coffee", display_order=1)
        self.latte = MenuItem.objects.create(category=coffee, name="Iced Latte", price="9.50", stock=8)
        self.mocha = MenuItem.objects.create(category=coffee, name="Mocha", price="8.00", stock=0)
        self.owner = User.objects.create_user("owner", password="pw", role=User.Role.SUPER_ADMIN)
        self.staff = User.objects.create_user("staff", password="pw", role=User.Role.STAFF)
        self.client_user = User.objects.create_user(
            "012345678", phone="012345678", first_name="Sokha Chan", role=User.Role.CLIENT
        )

    def place(self, **overrides):
        kwargs = {
            "lines": [(self.latte.pk, 2)],
            "order_type": Order.OrderType.DINE_IN,
            "table_number": "5",
            "payment_method": Order.PaymentMethod.CASH,
            "source": Order.Source.POS,
        }
        kwargs.update(overrides)
        return place_order(**kwargs)

    def stock(self, item):
        item.refresh_from_db()
        return item.stock


class PlaceOrderTests(OrderTestCase):
    def test_deducts_stock_and_totals(self):
        order = self.place()
        self.assertEqual(self.stock(self.latte), 6)
        self.assertEqual(str(order.total), "19.00")
        self.assertEqual(order.status, Order.Status.PENDING)
        self.assertEqual(order.payment_status, Order.PaymentStatus.NOT_PAID)
        self.assertEqual(order.number, f"MC-{order.pk:04d}")

    def test_merges_repeated_lines(self):
        order = self.place(lines=[(self.latte.pk, 1), (self.latte.pk, 2)])
        self.assertEqual(order.items.get().quantity, 3)
        self.assertEqual(self.stock(self.latte), 5)

    def test_refuses_more_than_stock(self):
        with self.assertRaisesMessage(OrderError, "Only 8 Iced Latte left."):
            self.place(lines=[(self.latte.pk, 9)])
        self.assertEqual(self.stock(self.latte), 8)
        self.assertFalse(Order.objects.exists())

    def test_refuses_out_of_stock_and_hidden_items(self):
        with self.assertRaisesMessage(OrderError, "Mocha is out of stock."):
            self.place(lines=[(self.mocha.pk, 1)])
        self.latte.is_available = False
        self.latte.save()
        with self.assertRaisesMessage(OrderError, "Iced Latte is out of stock."):
            self.place()

    def test_dine_in_needs_table(self):
        with self.assertRaisesMessage(OrderError, "Table number is required"):
            self.place(table_number="  ")
        order = self.place(order_type=Order.OrderType.PICKUP, table_number="")
        self.assertEqual(order.table_number, "")

    def test_empty_cart(self):
        with self.assertRaisesMessage(OrderError, "Add at least one item"):
            self.place(lines=[])

    def test_qr_needs_settings(self):
        with self.assertRaisesMessage(OrderError, "Manual QR payments are turned off"):
            self.place(payment_method=Order.PaymentMethod.MANUAL_QR)


class OrderLifecycleTests(OrderTestCase):
    def test_reject_returns_stock_once(self):
        order = self.place()
        reject_order(order, "Out of oat milk today")
        order.refresh_from_db()
        self.assertEqual(order.status, Order.Status.REJECTED)
        self.assertEqual(order.rejection_reason, "Out of oat milk today")
        self.assertIsNotNone(order.rejected_at)
        self.assertEqual(self.stock(self.latte), 8)

        with self.assertRaises(OrderError):
            reject_order(order, "again")
        self.assertEqual(self.stock(self.latte), 8)

    def test_reject_needs_reason(self):
        order = self.place()
        with self.assertRaisesMessage(OrderError, "A reason is required"):
            reject_order(order, "   ")

    def test_cannot_reject_accepted_order(self):
        order = self.place()
        advance_order(order, Order.Status.PENDING)
        with self.assertRaises(OrderError):
            reject_order(order, "Too late")

    def test_advance_through_flow_marks_cash_paid(self):
        order = self.place()
        for expected in (Order.Status.PENDING, Order.Status.ACCEPTED, Order.Status.PREPARING, Order.Status.READY):
            order = advance_order(order, expected)
        self.assertEqual(order.status, Order.Status.COMPLETED)
        self.assertEqual(order.payment_status, Order.PaymentStatus.PAID)
        with self.assertRaises(OrderError):
            advance_order(order, Order.Status.COMPLETED)

    def test_advance_refuses_stale_status(self):
        order = self.place()
        advance_order(order, Order.Status.PENDING)
        # A second click from a page still showing "Pending" must not skip ahead.
        with self.assertRaises(OrderError):
            advance_order(order, Order.Status.PENDING)
        order.refresh_from_db()
        self.assertEqual(order.status, Order.Status.ACCEPTED)

    def test_cancel_returns_stock(self):
        order = self.place()
        cancel_order(order)
        self.assertEqual(self.stock(self.latte), 8)
        with self.assertRaises(OrderError):
            cancel_order(order)

    @override_settings(MEDIA_ROOT=MEDIA_ROOT)
    def test_rejected_qr_order_is_not_paid(self):
        PaymentSettings.objects.create(qr_enabled=True, qr_image=png_upload(), account_name="Muyseang Ung")
        order = self.place(payment_method=Order.PaymentMethod.MANUAL_QR)
        self.assertEqual(order.payment_status, Order.PaymentStatus.AWAITING)
        order = reject_order(order, "Closed early")
        self.assertEqual(order.payment_status, Order.PaymentStatus.NOT_PAID)
        self.assertFalse(order.can_confirm_payment)

    def test_confirm_payment_only_for_qr(self):
        order = self.place()
        with self.assertRaises(OrderError):
            confirm_payment(order)


@override_settings(MEDIA_ROOT=MEDIA_ROOT)
class OrderDashboardTests(OrderTestCase):
    @classmethod
    def tearDownClass(cls):
        super().tearDownClass()
        shutil.rmtree(MEDIA_ROOT, ignore_errors=True)

    def setUp(self):
        super().setUp()
        self.client.force_login(self.staff)

    def test_home_goes_to_orders(self):
        self.assertRedirects(self.client.get(reverse("dashboard:home")), reverse("dashboard:order-list"))

    def test_requires_login(self):
        self.client.logout()
        response = self.client.get(reverse("dashboard:order-list"))
        self.assertEqual(response.status_code, 302)

    def test_list_filters(self):
        today = self.place(client=self.client_user)
        old = self.place(order_type=Order.OrderType.PICKUP, table_number="")
        Order.objects.filter(pk=old.pk).update(created_at=timezone.now() - timedelta(days=3))

        response = self.client.get(reverse("dashboard:order-list"))
        self.assertContains(response, f"#{today.number}")
        self.assertNotContains(response, f"#{old.number}")

        response = self.client.get(reverse("dashboard:order-list"), {"date": "week", "type": "pickup"})
        self.assertContains(response, f"#{old.number}")
        self.assertNotContains(response, f"#{today.number}")

        response = self.client.get(reverse("dashboard:order-list"), {"q": "sokha"})
        self.assertContains(response, f"#{today.number}")
        response = self.client.get(reverse("dashboard:order-list"), {"q": f"#MC-{today.pk:04d}"})
        self.assertContains(response, f"#{today.number}")

    def test_list_is_newest_first(self):
        first, second = self.place(), self.place()
        orders = list(self.client.get(reverse("dashboard:order-list")).context["orders"])
        self.assertEqual(orders[:2], [second, first])

    def test_partial_refresh_returns_table_only(self):
        self.place()
        response = self.client.get(reverse("dashboard:order-list"), {"partial": "1"})
        self.assertContains(response, "<table")
        self.assertNotContains(response, "<html")

    def test_detail_accept_and_reject(self):
        order = self.place(client=self.client_user)
        url = reverse("dashboard:order-detail", args=[order.pk])
        response = self.client.get(url)
        self.assertContains(response, "Accept Order")
        self.assertContains(response, "Reject order #")

        self.client.post(reverse("dashboard:order-advance", args=[order.pk]), {"from": "pending"})
        order.refresh_from_db()
        self.assertEqual(order.status, Order.Status.ACCEPTED)
        self.assertContains(self.client.get(url), "Start Preparing")

    def test_reject_from_dashboard(self):
        order = self.place()
        response = self.client.post(reverse("dashboard:order-reject", args=[order.pk]), {"reason": ""}, follow=True)
        self.assertContains(response, "A reason is required to reject an order.")

        response = self.client.post(
            reverse("dashboard:order-reject", args=[order.pk]), {"reason": "Out of oat milk"}, follow=True
        )
        self.assertContains(response, "Reason for rejection")
        self.assertContains(response, "Stock for the 2 items was returned automatically.")
        self.assertEqual(self.stock(self.latte), 8)

    def test_confirm_qr_payment_from_detail(self):
        PaymentSettings.objects.create(qr_enabled=True, qr_image=png_upload(), account_name="Muyseang Ung")
        order = self.place(payment_method=Order.PaymentMethod.MANUAL_QR)
        self.assertContains(self.client.get(reverse("dashboard:order-detail", args=[order.pk])), "Confirm QR Payment")
        self.client.post(reverse("dashboard:order-confirm-payment", args=[order.pk]))
        order.refresh_from_db()
        self.assertEqual(order.payment_status, Order.PaymentStatus.PAID)


@override_settings(MEDIA_ROOT=MEDIA_ROOT)
class PosTests(OrderTestCase):
    def setUp(self):
        super().setUp()
        self.client.force_login(self.staff)
        self.url = reverse("dashboard:pos")

    def post(self, **overrides):
        data = {"order_type": "dine_in", "table_number": "5", "payment_method": "cash", "item": [f"{self.latte.pk}:2"]}
        data.update(overrides)
        return self.client.post(self.url, data)

    def test_pos_page_lists_items(self):
        response = self.client.get(self.url)
        self.assertContains(response, "Iced Latte")
        self.assertContains(response, "Unavailable")  # Mocha has no stock

    def test_cash_order_goes_to_receipt(self):
        response = self.post()
        order = Order.objects.get()
        self.assertRedirects(response, reverse("dashboard:pos-receipt", args=[order.pk]))
        self.assertEqual(order.source, Order.Source.POS)
        self.assertEqual(order.created_by, self.staff)
        self.assertIsNone(order.client)
        receipt = self.client.get(reverse("dashboard:pos-receipt", args=[order.pk]))
        self.assertContains(receipt, f"Order #{order.number}")
        self.assertContains(receipt, "Iced Latte x2")

    def test_links_client_by_phone(self):
        self.post(client="012 345 678")
        self.assertEqual(Order.objects.get().client, self.client_user)

    def test_unknown_client_keeps_cart(self):
        response = self.post(client="099999999")
        self.assertContains(response, "No active client account has that phone number.")
        self.assertContains(response, f'"id": {self.latte.pk}')
        self.assertFalse(Order.objects.exists())

    def test_stock_error_is_shown(self):
        response = self.post(item=[f"{self.latte.pk}:50"])
        self.assertContains(response, "Only 8 Iced Latte left.")

    def test_qr_order_goes_to_customer_display(self):
        PaymentSettings.objects.create(
            qr_enabled=True, qr_image=png_upload(), account_name="Muyseang Ung", khr_account_number="600 863 042"
        )
        response = self.post(payment_method="manual_qr")
        order = Order.objects.get()
        pay_url = reverse("dashboard:pos-pay", args=[order.pk])
        self.assertRedirects(response, pay_url)

        screen = self.client.get(pay_url)
        self.assertContains(screen, "Scan to Pay")
        self.assertContains(screen, "KHR 600 863 042")

        self.client.post(reverse("dashboard:pos-confirm-payment", args=[order.pk]))
        order.refresh_from_db()
        self.assertEqual(order.payment_status, Order.PaymentStatus.PAID)
        # Once paid, the customer display hands over to the receipt.
        self.assertRedirects(self.client.get(pay_url), reverse("dashboard:pos-receipt", args=[order.pk]))

    def test_cancel_from_customer_display(self):
        PaymentSettings.objects.create(qr_enabled=True, qr_image=png_upload(), account_name="Muyseang Ung")
        self.post(payment_method="manual_qr")
        order = Order.objects.get()
        self.assertRedirects(self.client.post(reverse("dashboard:pos-cancel", args=[order.pk])), self.url)
        order.refresh_from_db()
        self.assertEqual(order.status, Order.Status.CANCELLED)
        self.assertEqual(self.stock(self.latte), 8)
