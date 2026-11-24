import { Suspense } from "react";
import { CheckoutResult } from "@/components/features/order/checkout";
// returnUrl của VNPay (VNPAY_RETURN_URL). Trang gọi verify, không tin tham số vnp_* trên URL.
export default function Page() {
  return (
    <Suspense>
      <CheckoutResult />
    </Suspense>
  );
}
