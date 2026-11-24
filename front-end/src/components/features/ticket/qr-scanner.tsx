"use client";
// Quét QR bằng camera qua BarcodeDetector có sẵn của trình duyệt (Chrome/Edge/Android). Trình duyệt không hỗ trợ
// thì nhân viên nhập mã thủ công. Không thêm thư viện quét ngoài.
import { useEffect, useRef, useState } from "react";
import { Camera, CameraOff, ScanLine } from "lucide-react";
import { Button } from "@/components/ui/button";

interface DetectedCode {
  rawValue: string;
}
interface BarcodeDetectorLike {
  detect(source: HTMLVideoElement): Promise<DetectedCode[]>;
}
type BarcodeDetectorCtor = new (options: { formats: string[] }) => BarcodeDetectorLike;

function detectorClass(): BarcodeDetectorCtor | undefined {
  if (typeof window === "undefined") return undefined;
  return (window as unknown as { BarcodeDetector?: BarcodeDetectorCtor }).BarcodeDetector;
}

type CameraState = "off" | "starting" | "on" | "unsupported" | "denied";

export function QrScanner({ onDetect, disabled = false }: { onDetect: (value: string) => void; disabled?: boolean }) {
  const video = useRef<HTMLVideoElement>(null);
  const stream = useRef<MediaStream | null>(null);
  const [state, setState] = useState<CameraState>("off");
  const handler = useRef(onDetect);
  const paused = useRef(disabled);
  useEffect(() => {
    handler.current = onDetect;
    paused.current = disabled;
  }, [onDetect, disabled]);

  function stop() {
    stream.current?.getTracks().forEach((t) => t.stop());
    stream.current = null;
    setState((s) => (s === "on" || s === "starting" ? "off" : s));
  }

  async function start() {
    const Detector = detectorClass();
    if (!Detector || !navigator.mediaDevices?.getUserMedia) {
      setState("unsupported");
      return;
    }
    setState("starting");
    try {
      stream.current = await navigator.mediaDevices.getUserMedia({ video: { facingMode: "environment" } });
      if (video.current) {
        video.current.srcObject = stream.current;
        await video.current.play();
      }
      setState("on");
    } catch {
      stop();
      setState("denied");
    }
  }

  useEffect(() => {
    if (state !== "on") return;
    const Detector = detectorClass();
    if (!Detector) return;
    const detector = new Detector({ formats: ["qr_code"] });
    // Cùng một mã trong 3 giây chỉ gửi một lần (camera đọc được nhiều khung hình liên tiếp).
    let last = { value: "", at: 0 };
    let busy = false;
    const timer = setInterval(async () => {
      if (busy || paused.current || !video.current || video.current.readyState < 2) return;
      busy = true;
      try {
        const value = (await detector.detect(video.current))[0]?.rawValue?.trim();
        const now = Date.now();
        if (value && (value !== last.value || now - last.at > 3000)) {
          last = { value, at: now };
          handler.current(value);
        }
      } catch {
        // Khung hình lỗi: bỏ qua, thử ở lần sau.
      } finally {
        busy = false;
      }
    }, 350);
    return () => clearInterval(timer);
  }, [state]);

  useEffect(() => () => stream.current?.getTracks().forEach((t) => t.stop()), []);

  return (
    <div className="scanner-preview">
      <video
        ref={video}
        muted
        playsInline
        aria-label="Camera quét mã QR"
        style={{ display: state === "on" ? "block" : "none", width: "100%", borderRadius: 12 }}
      />
      {state !== "on" && <ScanLine size={90} strokeWidth={1} />}
      <p>
        {state === "on"
          ? "Đưa mã QR vào khung hình"
          : state === "unsupported"
            ? "Trình duyệt này chưa hỗ trợ quét bằng camera"
            : state === "denied"
              ? "Chưa được cấp quyền camera"
              : "Quét mã QR trên vé của khách"}
      </p>
      <span>
        {state === "unsupported"
          ? "Dùng Chrome/Edge trên điện thoại hoặc nhập mã thủ công bên dưới."
          : "Hoặc nhập mã thủ công bên dưới."}
      </span>
      {state === "on" || state === "starting" ? (
        <Button type="button" variant="secondary" size="sm" className="mt-3" onClick={stop}>
          <CameraOff size={15} />
          Tắt camera
        </Button>
      ) : (
        state !== "unsupported" && (
          <Button type="button" size="sm" className="mt-3" onClick={() => void start()}>
            <Camera size={15} />
            Bật camera
          </Button>
        )
      )}
    </div>
  );
}
