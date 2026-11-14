import { Suspense } from "react";
import { Explore } from "@/components/features/event/public";
export default function Page() {
  return (
    <Suspense>
      <Explore />
    </Suspense>
  );
}
