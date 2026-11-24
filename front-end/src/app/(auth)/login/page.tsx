import { Suspense } from "react";
import { AuthPage } from "@/components/features/auth/auth-pages";
export default function Page() {
  return (
    <Suspense>
      <AuthPage />
    </Suspense>
  );
}
