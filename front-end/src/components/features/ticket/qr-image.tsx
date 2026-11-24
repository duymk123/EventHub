"use client";
// Vẽ mã QR từ token do Ticket service cấp (C07). Frontend chỉ hiển thị, không tự sinh hay giải mã token.
import { useEffect, useState } from "react";
import Image from "next/image";
import QRCode from "qrcode";

const OPTIONS = { margin: 1, errorCorrectionLevel: "M" } as const;

export function QrImage({ value, size = 220 }: { value: string; size?: number }) {
  const [src, setSrc] = useState<{ value: string; url: string } | null>(null);
  useEffect(() => {
    let alive = true;
    QRCode.toDataURL(value, { ...OPTIONS, width: size * 2 })
      .then((url) => {
        if (alive) setSrc({ value, url });
      })
      .catch(() => {
        if (alive) setSrc(null);
      });
    return () => {
      alive = false;
    };
  }, [value, size]);
  if (!src || src.value !== value)
    return <div className="skeleton" style={{ width: size, height: size }} aria-label="Đang tạo mã QR" />;
  return <Image unoptimized src={src.url} width={size} height={size} alt="Mã QR vào cổng" />;
}

/** Lưu mã QR thành ảnh PNG để mở offline tại cổng. */
export async function downloadQr(value: string, fileName: string) {
  const url = await QRCode.toDataURL(value, { ...OPTIONS, width: 720 });
  const a = document.createElement("a");
  a.href = url;
  a.download = fileName;
  a.click();
}
