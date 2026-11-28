"use client";
// Quét QR bằng camera. Ưu tiên BarcodeDetector có sẵn của trình duyệt (Android, macOS, ChromeOS); Chrome/Edge trên
// Windows, Firefox và Safari không có API này nên giải mã bằng jsQR (thuần JS, không phụ thuộc mạng). Thêm jsQR vì
// trước đây máy Windows không bật được camera quét. Nhân viên vẫn có thể nhập mã thủ công.
import { useEffect, useRef, useState } from "react";
import jsQR from "jsqr";
import { Camera, CameraOff, ImageUp, ScanLine } from "lucide-react";
import { Button } from "@/components/ui/button";

interface DetectedCode {
  rawValue: string;
}
interface BarcodeDetectorLike {
  detect(source: HTMLVideoElement): Promise<DetectedCode[]>;
}
type BarcodeDetectorCtor = new (options: { formats: string[] }) => BarcodeDetectorLike;

/** Bộ đọc QR dùng chung cho cả hai cách: trả mã đọc được trong khung hình hiện tại hoặc null. */
type FrameReader = (video: HTMLVideoElement) => Promise<string | null>;

function nativeReader(): FrameReader | null {
  const Detector = (window as unknown as { BarcodeDetector?: BarcodeDetectorCtor }).BarcodeDetector;
  if (!Detector) return null;
  try {
    const detector = new Detector({ formats: ["qr_code"] });
    return async (video) => (await detector.detect(video))[0]?.rawValue ?? null;
  } catch {
    // Có API nhưng không hỗ trợ định dạng qr_code trên máy này.
    return null;
  }
}

/**
 * Vẽ nguồn ảnh lên canvas với cạnh dài tối đa {@code maxSide} rồi giải mã bằng jsQR.
 * attemptBoth thử cả QR sáng trên nền tối (chậm gấp đôi), chỉ dùng cho ảnh tải lên.
 */
function decodeWithJsQr(
  canvas: HTMLCanvasElement,
  source: CanvasImageSource,
  sourceWidth: number,
  sourceHeight: number,
  maxSide: number,
  inversion: "dontInvert" | "attemptBoth",
): string | null {
  const context = canvas.getContext("2d", { willReadFrequently: true });
  if (!context || !sourceWidth || !sourceHeight) return null;
  const scale = Math.min(1, maxSide / Math.max(sourceWidth, sourceHeight));
  const width = Math.max(1, Math.round(sourceWidth * scale));
  const height = Math.max(1, Math.round(sourceHeight * scale));
  canvas.width = width;
  canvas.height = height;
  // Nền trắng: ảnh PNG trong suốt (vd chụp màn hình QR) không bị hiểu thành nền đen.
  context.fillStyle = "#fff";
  context.fillRect(0, 0, width, height);
  context.drawImage(source, 0, 0, width, height);
  const image = context.getImageData(0, 0, width, height);
  return jsQR(image.data, width, height, { inversionAttempts: inversion })?.data ?? null;
}

/** Đọc QR từ camera bằng jsQR, khung hình thu nhỏ còn tối đa 800px để vừa nhanh vừa đủ nét. */
function jsQrReader(canvas: HTMLCanvasElement): FrameReader {
  return async (video) => decodeWithJsQr(canvas, video, video.videoWidth, video.videoHeight, 800, "dontInvert");
}

/**
 * Đọc QR từ ảnh tải lên. Thử nhiều kích thước: ảnh chụp điện thoại rất lớn, QR nhỏ trong ảnh cần độ phân giải cao
 * hơn, còn ảnh mờ đôi khi đọc được tốt hơn khi thu nhỏ.
 */
async function readImageFile(file: File, canvas: HTMLCanvasElement): Promise<string | null> {
  const bitmap = await createImageBitmap(file);
  try {
    const Detector = (window as unknown as { BarcodeDetector?: new (o: { formats: string[] }) => {
      detect(source: ImageBitmap): Promise<DetectedCode[]>;
    } }).BarcodeDetector;
    if (Detector) {
      try {
        const found = (await new Detector({ formats: ["qr_code"] }).detect(bitmap))[0]?.rawValue;
        if (found) return found;
      } catch {
        // Không hỗ trợ trên máy này: dùng jsQR bên dưới.
      }
    }
    for (const maxSide of [1200, 2000, 800]) {
      const value = decodeWithJsQr(canvas, bitmap, bitmap.width, bitmap.height, maxSide, "attemptBoth");
      if (value) return value;
    }
    return null;
  } finally {
    bitmap.close();
  }
}

type CameraState = "off" | "starting" | "on" | "unsupported" | "insecure" | "denied" | "no-camera" | "busy" | "error";

/** Đổi lỗi getUserMedia thành trạng thái để báo đúng nguyên nhân cho nhân viên. */
function cameraError(error: unknown): CameraState {
  const name = error instanceof DOMException ? error.name : "";
  if (name === "NotAllowedError" || name === "SecurityError") return "denied";
  if (name === "NotFoundError" || name === "OverconstrainedError") return "no-camera";
  if (name === "NotReadableError" || name === "AbortError") return "busy";
  return "error";
}

const MESSAGES: Record<CameraState, { title: string; hint: string }> = {
  off: { title: "Quét mã QR trên vé của khách", hint: "Hoặc nhập mã thủ công bên dưới." },
  starting: { title: "Đang mở camera...", hint: "Nếu trình duyệt hỏi quyền camera, hãy chọn Cho phép." },
  on: { title: "Đưa mã QR vào khung hình", hint: "Giữ mã thẳng, đủ sáng, cách camera khoảng 15–30 cm." },
  unsupported: {
    title: "Trình duyệt này không hỗ trợ camera",
    hint: "Dùng Chrome, Edge hoặc Firefox bản mới, hoặc nhập mã thủ công bên dưới.",
  },
  insecure: {
    title: "Camera chỉ mở được trên trang bảo mật",
    hint: "Mở trang bằng http://localhost:3000 hoặc https://. Địa chỉ IP dạng http://192.168... bị trình duyệt chặn camera.",
  },
  denied: {
    title: "Chưa được cấp quyền camera",
    hint: "Bấm biểu tượng ổ khóa/camera trên thanh địa chỉ, chọn Cho phép camera rồi bấm Bật camera lại.",
  },
  "no-camera": { title: "Không tìm thấy camera", hint: "Kiểm tra camera đã cắm/bật chưa, hoặc nhập mã thủ công bên dưới." },
  busy: {
    title: "Camera đang được ứng dụng khác dùng",
    hint: "Tắt Zoom, Teams, Camera của Windows hoặc tab khác đang dùng camera rồi thử lại.",
  },
  error: { title: "Không mở được camera", hint: "Thử lại, hoặc nhập mã thủ công bên dưới." },
};

export function QrScanner({ onDetect, disabled = false }: { onDetect: (value: string) => void; disabled?: boolean }) {
  const video = useRef<HTMLVideoElement>(null);
  const canvas = useRef<HTMLCanvasElement>(null);
  const uploadCanvas = useRef<HTMLCanvasElement>(null);
  const fileInput = useRef<HTMLInputElement>(null);
  const stream = useRef<MediaStream | null>(null);
  const [state, setState] = useState<CameraState>("off");
  const [reading, setReading] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);

  async function readUpload(file: File | undefined) {
    if (!file || !uploadCanvas.current) return;
    if (!file.type.startsWith("image/")) {
      setUploadError("Vui lòng chọn file ảnh (PNG, JPG...).");
      return;
    }
    setReading(true);
    setUploadError(null);
    try {
      const value = (await readImageFile(file, uploadCanvas.current))?.trim();
      if (value) {
        handler.current(value);
      } else {
        setUploadError("Không tìm thấy mã QR trong ảnh. Hãy chọn ảnh rõ nét, chụp thẳng và thấy đủ cả mã.");
      }
    } catch {
      setUploadError("Không đọc được ảnh này. Hãy thử ảnh khác.");
    } finally {
      setReading(false);
      // Cho phép chọn lại đúng file vừa chọn (sự kiện change mới chạy lại).
      if (fileInput.current) fileInput.current.value = "";
    }
  }
  const handler = useRef(onDetect);
  const paused = useRef(disabled);
  useEffect(() => {
    handler.current = onDetect;
    paused.current = disabled;
  }, [onDetect, disabled]);

  function releaseCamera() {
    stream.current?.getTracks().forEach((t) => t.stop());
    stream.current = null;
  }

  function stop() {
    releaseCamera();
    setState("off");
  }

  async function start() {
    if (!window.isSecureContext) {
      setState("insecure");
      return;
    }
    if (!navigator.mediaDevices?.getUserMedia) {
      setState("unsupported");
      return;
    }
    setState("starting");
    try {
      // "ideal" thay vì bắt buộc: điện thoại chọn camera sau, laptop chỉ có webcam trước vẫn mở được.
      // Xin độ phân giải cao hơn mặc định (thường 640x480) để QR nhỏ hoặc ở xa vẫn đủ nét.
      stream.current = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: { ideal: "environment" }, width: { ideal: 1280 }, height: { ideal: 720 } },
        audio: false,
      });
      if (video.current) {
        video.current.srcObject = stream.current;
        await video.current.play();
      }
      setState("on");
    } catch (error) {
      releaseCamera();
      setState(cameraError(error));
    }
  }

  useEffect(() => {
    if (state !== "on" || !canvas.current) return;
    const read = nativeReader() ?? jsQrReader(canvas.current);
    // Cùng một mã trong 3 giây chỉ gửi một lần (camera đọc được nhiều khung hình liên tiếp).
    let last = { value: "", at: 0 };
    let busy = false;
    const timer = setInterval(async () => {
      if (busy || paused.current || !video.current || video.current.readyState < 2) return;
      busy = true;
      try {
        const value = (await read(video.current))?.trim();
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
    }, 250);
    return () => clearInterval(timer);
  }, [state]);

  useEffect(() => () => stream.current?.getTracks().forEach((t) => t.stop()), []);

  const message = MESSAGES[state];
  const canRetry = state !== "unsupported" && state !== "insecure";
  return (
    <div className="scanner-preview">
      <video
        ref={video}
        muted
        playsInline
        aria-label="Camera quét mã QR"
        style={{ display: state === "on" ? "block" : "none", width: "100%", borderRadius: 12 }}
      />
      <canvas ref={canvas} hidden />
      <canvas ref={uploadCanvas} hidden />
      {state !== "on" && <ScanLine size={90} strokeWidth={1} />}
      <p role="status">{message.title}</p>
      <span>{message.hint}</span>
      <div className="mt-3 flex flex-wrap justify-center gap-2">
        {state === "on" || state === "starting" ? (
          <Button type="button" variant="secondary" size="sm" onClick={stop}>
            <CameraOff size={15} />
            Tắt camera
          </Button>
        ) : (
          canRetry && (
            <Button type="button" size="sm" onClick={() => void start()}>
              <Camera size={15} />
              {state === "off" ? "Bật camera" : "Thử lại"}
            </Button>
          )
        )}
        <Button
          type="button"
          variant="secondary"
          size="sm"
          disabled={reading || disabled}
          onClick={() => fileInput.current?.click()}
        >
          <ImageUp size={15} />
          {reading ? "Đang đọc ảnh..." : "Tải ảnh QR lên"}
        </Button>
      </div>
      <input
        ref={fileInput}
        type="file"
        accept="image/*"
        hidden
        aria-label="Chọn ảnh có mã QR"
        onChange={(e) => void readUpload(e.target.files?.[0])}
      />
      {uploadError && (
        <p className="form-error mt-2" role="alert">
          {uploadError}
        </p>
      )}
    </div>
  );
}
