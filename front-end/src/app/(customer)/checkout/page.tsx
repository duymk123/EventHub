import { Suspense } from "react";
import { Checkout } from "@/components/features/order/checkout";
export default function Page() {
  return (
    <Suspense>
      <Checkout />
    </Suspense>
  );
}
