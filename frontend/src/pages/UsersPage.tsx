import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Trash2 } from "lucide-react";
import { Button } from "../components/Button";
import { EmptyState, ErrorState, LoadingState } from "../components/DataState";
import { PageHeader } from "../components/PageHeader";
import { Pagination } from "../components/Pagination";
import { StatusPill } from "../components/StatusPill";
import { usersApi } from "../lib/api";
import { errorMessage } from "../lib/errors";
import { useAuth } from "../state/auth";
import type { PagedResponse, Role, User } from "../lib/types";

const ROLES: Role[] = ["OWNER", "TESTER", "VIEWER"];

const selectCls =
  "rounded-md border border-line bg-white px-2 py-1 text-xs focus:outline-none focus:ring-2 focus:ring-brand disabled:opacity-60";

function RoleSelect({ user, currentUserId, page }: { user: User; currentUserId: string; page: number }) {
  const queryClient = useQueryClient();

  const update = useMutation({
    mutationFn: (role: Role) =>
      usersApi.update(user.id, { role, displayName: user.displayName }),
    onSuccess: (updated) => {
      queryClient.setQueryData<PagedResponse<User>>(["users", page], (old) =>
        old ? { ...old, content: old.content.map((u) => (u.id === updated.id ? updated : u)) } : old,
      );
    },
  });

  if (user.id === currentUserId) return <StatusPill status={user.role} />;

  return (
    <select
      className={selectCls}
      value={user.role}
      disabled={update.isPending}
      onChange={(e) => update.mutate(e.target.value as Role)}
    >
      {ROLES.map((r) => <option key={r} value={r}>{r}</option>)}
    </select>
  );
}

export function UsersPage() {
  const queryClient = useQueryClient();
  const { user: currentUser } = useAuth();
  const [page, setPage] = useState(0);

  const users = useQuery<PagedResponse<User>>({
    queryKey: ["users", page],
    queryFn: () => usersApi.list(page),
  });

  const remove = useMutation({
    mutationFn: (id: string) => usersApi.remove(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["users"] });
    },
  });

  const usersContent = users.data?.content ?? [];

  return (
    <>
      <PageHeader
        title="Team"
        description="Manage team members and their roles. Only owners can access this page."
      />
      <div className="p-4 sm:p-6">
        <div className="rounded-md border border-line bg-white">
          <div className="border-b border-line px-4 py-3 text-sm font-semibold">
            Members {users.data ? `(${users.data.totalElements})` : ""}
          </div>

          {users.isLoading ? <LoadingState /> : null}
          {users.isError ? <ErrorState message={errorMessage(users.error)} /> : null}
          {!users.isLoading && users.data?.totalElements === 0 ? <EmptyState title="No users found." /> : null}

          {usersContent.length > 0 && (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-line bg-slate-50 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
                    <th className="px-4 py-3">User</th>
                    <th className="px-4 py-3">Role</th>
                    <th className="px-4 py-3">Joined</th>
                    <th className="px-4 py-3" />
                  </tr>
                </thead>
                <tbody className="divide-y divide-line">
                  {usersContent.map((u) => {
                    const isSelf = u.id === currentUser?.id;
                    return (
                      <tr key={u.id} className={isSelf ? "bg-teal-50/30" : ""}>
                        <td className="px-4 py-3">
                          <div className="flex items-center gap-2">
                            <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-teal-100 text-xs font-semibold text-brand">
                              {(u.displayName || u.email).charAt(0).toUpperCase()}
                            </div>
                            <div className="min-w-0">
                              <div className="truncate font-medium text-ink">
                                {u.displayName || <span className="text-slate-400">—</span>}
                                {isSelf && (
                                  <span className="ml-2 rounded-full bg-teal-100 px-2 py-0.5 text-[10px] font-medium text-brand">
                                    You
                                  </span>
                                )}
                              </div>
                              <div className="truncate text-xs text-slate-500">{u.email}</div>
                            </div>
                          </div>
                        </td>
                        <td className="px-4 py-3">
                          <RoleSelect user={u} currentUserId={currentUser?.id ?? ""} page={page} />
                        </td>
                        <td className="px-4 py-3 text-slate-500">
                          {new Date(u.createdAt).toLocaleDateString()}
                        </td>
                        <td className="px-4 py-3 text-right">
                          <Button
                            variant="danger"
                            disabled={isSelf || remove.isPending}
                            title={isSelf ? "You cannot delete your own account" : "Remove user"}
                            onClick={() => { if (!isSelf) remove.mutate(u.id); }}
                          >
                            <Trash2 className="h-4 w-4" />
                          </Button>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}

          <Pagination
            page={page}
            totalPages={users.data?.totalPages ?? 1}
            totalElements={users.data?.totalElements ?? 0}
            onPageChange={setPage}
          />

          {remove.isError && (
            <div className="px-4 py-2 text-sm text-red-700">{errorMessage(remove.error)}</div>
          )}
        </div>

        <div className="mt-4 rounded-md border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
          <strong>Roles:</strong> OWNER can manage team and all settings · TESTER can run analyses · VIEWER has read-only access.
        </div>
      </div>
    </>
  );
}