import { notFound } from "next/navigation";
import { Workspace } from "@/components/features/dashboard/workspace";
import { Overview } from "@/components/features/dashboard/overview";
import {
  EventReviews,
  RefundReviews,
  UsersAdmin,
  CategoriesAdmin,
  TemplatesAdmin,
} from "@/components/features/dashboard/admin";
import { AnalyticsAdmin } from "@/components/features/dashboard/analytics";
export default async function AdminPage({
  params,
}: {
  params: Promise<{ section?: string[] }>;
}) {
  const { section = [] } = await params;
  const path = section.join("/");
  const page =
    path === "" ? (
      <Overview admin />
    ) : path === "analytics" ? (
      <AnalyticsAdmin />
    ) : path === "events" ? (
      <EventReviews />
    ) : path === "refunds" ? (
      <RefundReviews />
    ) : path === "users" ? (
      <UsersAdmin />
    ) : path === "categories" ? (
      <CategoriesAdmin />
    ) : path === "templates" ? (
      <TemplatesAdmin />
    ) : null;
  if (!page) notFound();
  return <Workspace admin>{page}</Workspace>;
}
