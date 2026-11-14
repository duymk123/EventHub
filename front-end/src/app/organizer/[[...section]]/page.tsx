import { notFound } from "next/navigation";
import { Workspace } from "@/components/features/dashboard/workspace";
import { Overview } from "@/components/features/dashboard/overview";
import {
  OrganizerEvents,
  EventEditor,
  Attendees,
  Checkin,
} from "@/components/features/dashboard/organizer";
export default async function OrganizerPage({
  params,
}: {
  params: Promise<{ section?: string[] }>;
}) {
  const { section = [] } = await params;
  const path = section.join("/");
  const page =
    path === "" ? (
      <Overview />
    ) : path === "reports" ? (
      <Overview reports />
    ) : path === "events" ? (
      <OrganizerEvents />
    ) : path === "events/new" ? (
      <EventEditor />
    ) : section.length === 3 &&
      section[0] === "events" &&
      section[2] === "edit" ? (
      <EventEditor id={section[1]} />
    ) : path === "attendees" ? (
      <Attendees />
    ) : path === "checkin" ? (
      <Checkin />
    ) : null;
  if (!page) notFound();
  return <Workspace>{page}</Workspace>;
}
