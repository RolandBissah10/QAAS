import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Trash2 } from "lucide-react";
import { Button } from "../components/Button";
import { EmptyState, ErrorState, LoadingState } from "../components/DataState";
import { Field, SelectInput, TextInput } from "../components/Field";
import { PageHeader } from "../components/PageHeader";
import { userApi } from "../lib/api";
import { errorMessage } from "../lib/errors";
import type { Role } from "../lib/types";
import { useAuth } from "../state/auth";

type UserEdit = {
  role: Role;
  displayName: string;
};

export function UsersPage() {
  const queryClient = useQueryClient();
  const { user: currentUser } = useAuth();
  const users = useQuery({ queryKey: ["users"], queryFn: userApi.list });
  const [edits, setEdits] = useState<Record<string, UserEdit>>({});

  useEffect(() => {
    if (users.data) {
      setEdits(
        Object.fromEntries(
          users.data.map((item) => [item.id, { role: item.role, displayName: item.displayName ?? "" }]),
        ),
      );
    }
  }, [users.data]);

  const save = useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: UserEdit }) => userApi.update(id, payload),
    onSuccess: async () => queryClient.invalidateQueries({ queryKey: ["users"] }),
  });

  const remove = useMutation({
    mutationFn: (id: string) => userApi.remove(id),
    onSuccess: async () => queryClient.invalidateQueries({ queryKey: ["users"] }),
  });

  function updateEdit(id: string, update: Partial<UserEdit>) {
    setEdits((current) => ({
      ...current,
      [id]: {
        ...current[id],
        ...update,
      },
    }));
  }

  function saveUser(id: string) {
    const userEdit = edits[id];
    if (!userEdit) {
      return;
    }
    save.mutate({ id, payload: userEdit });
  }

  return (
    <>
      <PageHeader
        title="Users"
        description="Owner-only user management: list accounts, update roles/display names, and remove users." 
      />
      <div className="grid gap-6 p-4 sm:p-6">
        {users.isLoading ? <LoadingState /> : null}
        {users.isError ? <ErrorState message={errorMessage(users.error)} /> : null}
        {users.data && users.data.length === 0 ? <EmptyState title="No users found." /> : null}

        <div className="grid gap-4">
          {users.data?.map((user) => {
            const edit = edits[user.id] ?? { role: user.role, displayName: user.displayName ?? "" };
            const isSelf = currentUser?.id === user.id;

            return (
              <div key={user.id} className="grid gap-4 rounded-md border border-line bg-white p-4 sm:p-5">
                <div className="flex flex-col gap-1 sm:flex-row sm:items-center sm:justify-between">
                  <div className="min-w-0">
                    <div className="font-medium text-ink break-all">{user.email}</div>
                    <div className="text-sm text-slate-500">
                      {user.displayName ? `${user.displayName} · ${user.role}` : user.role}
                    </div>
                  </div>
                  <div className="text-sm text-slate-500">Created {new Date(user.createdAt).toLocaleDateString()}</div>
                </div>

                <div className="grid gap-4 xl:grid-cols-[220px_1fr_180px]">
                  <Field label="Role">
                    <SelectInput value={edit.role} onChange={(event) => updateEdit(user.id, { role: event.target.value as Role })}>
                      {(["OWNER", "TESTER", "VIEWER"] as Role[]).map((option) => (
                        <option key={option} value={option}>
                          {option}
                        </option>
                      ))}
                    </SelectInput>
                  </Field>

                  <Field label="Display name">
                    <TextInput
                      value={edit.displayName}
                      onChange={(event) => updateEdit(user.id, { displayName: event.target.value })}
                      placeholder="Optional display name"
                    />
                  </Field>

                  <div className="grid gap-2 lg:grid-cols-2">
                    <Button className="w-full" loading={save.isPending} onClick={() => saveUser(user.id)}>
                      Save
                    </Button>
                    <Button
                      className="w-full"
                      variant="danger"
                      disabled={isSelf}
                      onClick={() => remove.mutate(user.id)}
                      title={isSelf ? "You cannot remove yourself" : "Delete user"}
                    >
                      <Trash2 className="h-4 w-4" />
                      Delete
                    </Button>
                  </div>
                </div>

                {isSelf ? (
                  <div className="text-sm text-slate-500">You cannot delete your own account from this page.</div>
                ) : null}
                {save.isError && <div className="text-sm text-red-700">{errorMessage(save.error)}</div>}
                {remove.isError && <div className="text-sm text-red-700">{errorMessage(remove.error)}</div>}
              </div>
            );
          })}
        </div>
      </div>
    </>
  );
}
