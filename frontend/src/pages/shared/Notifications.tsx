import { useState } from "react";
import { Link } from "react-router-dom";
import { toast } from "sonner";
import { Bell, CheckCheck } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { EmptyState, ErrorState, LoadingRows, PageHeader, Pagination } from "@/components/common";
import { api, errorMessage } from "@/lib/api";
import { formatDateTime } from "@/lib/format";
import { useApi } from "@/lib/useApi";
import type { NotificationItem, Page } from "@/lib/types";
import { cn } from "@/lib/utils";

export default function NotificationsPage() {
  const [page, setPage] = useState(0);
  const [unreadOnly, setUnreadOnly] = useState(false);
  const list = useApi(
    () => api.get<Page<NotificationItem>>(`/notifications?page=${page}&size=20&unreadOnly=${unreadOnly}`).then((r) => r.data),
    [page, unreadOnly],
  );

  const markRead = async (n: NotificationItem) => {
    if (n.readAt) return;
    try {
      await api.post(`/notifications/${n.id}/read`);
      list.setData((d) => (d ? { ...d, content: d.content.map((x) => (x.id === n.id ? { ...x, readAt: new Date().toISOString() } : x)) } : d));
    } catch {
      /* non-critical */
    }
  };

  const markAll = async () => {
    try {
      await api.post("/notifications/read-all");
      toast.success("All notifications marked as read");
      void list.reload();
    } catch (e) {
      toast.error(errorMessage(e));
    }
  };

  return (
    <>
      <PageHeader
        title="Notifications"
        description="Updates about complaints you filed or handle."
        actions={
          <>
            <Button variant={unreadOnly ? "secondary" : "outline"} size="sm" onClick={() => { setPage(0); setUnreadOnly(!unreadOnly); }} aria-pressed={unreadOnly}>
              Unread only
            </Button>
            <Button variant="outline" size="sm" onClick={markAll}>
              <CheckCheck /> Mark all read
            </Button>
          </>
        }
      />
      <Card>
        {list.loading && !list.data ? (
          <LoadingRows rows={6} />
        ) : list.error ? (
          <ErrorState message={list.error} onRetry={list.reload} />
        ) : list.data && list.data.content.length > 0 ? (
          <>
            <ul className="divide-y">
              {list.data.content.map((n) => {
                const inner = (
                  <div className="flex gap-3 px-5 py-3.5">
                    <span className={cn("mt-1.5 h-2 w-2 shrink-0 rounded-full", n.readAt ? "bg-transparent" : "bg-primary")} aria-label={n.readAt ? undefined : "Unread"} />
                    <div className="min-w-0 flex-1">
                      <div className={cn("text-sm", n.readAt ? "text-foreground" : "font-medium text-navy")}>{n.title}</div>
                      <p className="mt-0.5 text-sm text-muted-foreground">{n.message}</p>
                      <div className="mt-1 text-xs text-muted-foreground tabular">{formatDateTime(n.createdAt)}</div>
                    </div>
                  </div>
                );
                return (
                  <li key={n.id} className="hover:bg-muted/40">
                    {n.complaintId ? (
                      <Link to={`/app/complaints/${n.complaintId}`} onClick={() => markRead(n)} className="block">
                        {inner}
                      </Link>
                    ) : (
                      <button className="block w-full text-left" onClick={() => markRead(n)}>
                        {inner}
                      </button>
                    )}
                  </li>
                );
              })}
            </ul>
            <Pagination page={list.data.page} size={list.data.size} totalPages={list.data.totalPages} totalElements={list.data.totalElements} onPage={setPage} />
          </>
        ) : (
          <EmptyState icon={<Bell className="h-5 w-5" />} title={unreadOnly ? "No unread notifications" : "No notifications yet"} />
        )}
      </Card>
    </>
  );
}
