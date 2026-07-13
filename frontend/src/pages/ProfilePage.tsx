import { FormEvent, useEffect, useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { User } from "lucide-react";
import { Button } from "../components/Button";
import { Field, TextInput } from "../components/Field";
import { PageHeader } from "../components/PageHeader";
import { StatusPill } from "../components/StatusPill";
import { useToast } from "../components/Toast";
import { profileApi } from "../lib/api";
import { errorMessage } from "../lib/errors";
import { useAuth } from "../state/auth";

export function ProfilePage() {
  const { user, updateUser } = useAuth();
  const toast = useToast();
  const [displayName, setDisplayName] = useState(user?.displayName ?? "");

  const profile = useQuery({
    queryKey: ["profile"],
    queryFn: profileApi.get,
    staleTime: 60_000,
  });

  useEffect(() => {
    if (profile.data?.displayName != null) {
      setDisplayName(profile.data.displayName);
    }
  }, [profile.data?.displayName]);

  const update = useMutation({
    mutationFn: () => profileApi.update(displayName),
    onSuccess: (updated) => {
      updateUser(updated);
      toast.success("Profile updated.");
    },
    onError: (err) => toast.error(errorMessage(err)),
  });

  function submit(e: FormEvent) {
    e.preventDefault();
    update.mutate();
  }

  const initials = (user?.displayName || user?.email || "?")
    .split(/[\s@]/)
    .filter(Boolean)
    .slice(0, 2)
    .map((w) => w[0].toUpperCase())
    .join("");

  return (
    <>
      <PageHeader title="Profile" description="Manage your account details." />
      <div className="mx-auto max-w-lg p-4 sm:p-6">
        <div className="mb-6 flex items-center gap-4 rounded-md border border-line bg-white p-5">
          <div className="flex h-16 w-16 shrink-0 items-center justify-center rounded-full bg-teal-100 text-xl font-bold text-brand">
            {initials || <User className="h-7 w-7" />}
          </div>
          <div className="min-w-0">
            <div className="truncate text-lg font-semibold text-ink">
              {user?.displayName || user?.email}
            </div>
            <div className="mt-0.5 truncate text-sm text-slate-500">{user?.email}</div>
            <div className="mt-1.5 flex items-center gap-2">
              <StatusPill status={user?.role ?? ""} />
              {user?.createdAt && (
                <span className="text-xs text-slate-400">
                  Member since {new Date(user.createdAt).toLocaleDateString()}
                </span>
              )}
            </div>
          </div>
        </div>

        <form
          className="grid gap-4 rounded-md border border-line bg-white p-5"
          onSubmit={submit}
        >
          <h2 className="text-sm font-semibold text-ink">Edit Profile</h2>

          <Field label="Email">
            <TextInput value={user?.email ?? ""} disabled readOnly />
          </Field>

          <Field label="Display Name">
            <TextInput
              placeholder="Your name"
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
              maxLength={120}
            />
          </Field>

          <Field label="Role">
            <TextInput value={user?.role ?? ""} disabled readOnly />
          </Field>

          <Button
            type="submit"
            className="w-full"
            loading={update.isPending}
            disabled={displayName === (profile.data?.displayName ?? user?.displayName ?? "")}
          >
            Save Changes
          </Button>
        </form>
      </div>
    </>
  );
}