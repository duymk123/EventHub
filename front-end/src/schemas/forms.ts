import { z } from "zod";
export const holderSchema = z.object({
  holders: z
    .array(
      z.object({
        name: z.string().trim().min(2, "Nhập tên người sử dụng vé").max(100),
        email: z.union([z.email("Email chưa đúng định dạng"), z.literal("")]),
        tierId: z.string(),
        seatId: z.string(),
        seatLabel: z.string(),
      }),
    )
    .min(1),
  consent: z
    .boolean()
    .refine((v) => v, "Bạn cần đồng ý với điều khoản trước khi tiếp tục"),
});
export type HolderForm = z.infer<typeof holderSchema>;
export const loginSchema = z.object({
  name: z.string(),
  email: z.email("Email chưa đúng định dạng"),
  password: z.string().min(8, "Mật khẩu cần ít nhất 8 ký tự"),
});
export type LoginForm = z.infer<typeof loginSchema>;
export const eventSchema = z.object({
  title: z.string().trim().min(5, "Tên sự kiện cần ít nhất 5 ký tự"),
  subtitle: z.string().trim().min(5, "Nhập lời giới thiệu ngắn"),
  description: z.string().trim().min(30, "Mô tả cần ít nhất 30 ký tự"),
  category: z.string().min(1),
  startTime: z.string().min(1, "Chọn thời gian bắt đầu"),
  endTime: z.string().min(1, "Chọn thời gian kết thúc"),
});
export type EventForm = z.infer<typeof eventSchema>;
export const passwordSchema = z
  .object({
    current: z.string().min(8, "Nhập ít nhất 8 ký tự thử nghiệm"),
    next: z.string().min(8, "Mật khẩu mới cần ít nhất 8 ký tự"),
    confirm: z.string(),
  })
  .refine((v) => v.next === v.confirm, {
    message: "Mật khẩu nhập lại chưa khớp",
    path: ["confirm"],
  })
  .refine((v) => v.current !== v.next, {
    message: "Mật khẩu mới cần khác mật khẩu hiện tại",
    path: ["next"],
  });
export type PasswordFormValues = z.infer<typeof passwordSchema>;
