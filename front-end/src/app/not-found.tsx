import Link from "next/link";
import { ArrowLeft } from "lucide-react";
import { Button } from "@/components/ui/button";
export default function NotFound() {
  return (
    <div className="container empty-state py-24">
      <span className="eyebrow">404 · LẠC NHỊP MỘT CHÚT</span>
      <h1 className="text-4xl font-bold mt-4">Cuộc hẹn này không tồn tại.</h1>
      <p>
        Có thể đường dẫn đã thay đổi. Những trải nghiệm khác vẫn đang chờ bạn.
      </p>
      <Button asChild>
        <Link href="/explore">
          <ArrowLeft size={16} />
          Về trang khám phá
        </Link>
      </Button>
    </div>
  );
}
