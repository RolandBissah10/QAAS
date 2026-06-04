import { FormEvent, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Plus, Trash2 } from "lucide-react";
import { Button } from "../components/Button";
import { EmptyState, ErrorState, LoadingState } from "../components/DataState";
import { Field, SelectInput, TextInput } from "../components/Field";
import { PageHeader } from "../components/PageHeader";
import { environmentApi, projectApi } from "../lib/api";
import { errorMessage } from "../lib/errors";

export function EnvironmentsPage() {
  const queryClient = useQueryClient();
  const projects = useQuery({ queryKey: ["projects"], queryFn: projectApi.list });
  const environments = useQuery({ queryKey: ["environments"], queryFn: environmentApi.list });
  const [projectId, setProjectId] = useState("");
  const [name, setName] = useState("Staging");
  const [baseUrl, setBaseUrl] = useState("https://api.example.com");

  const save = useMutation({
    mutationFn: environmentApi.create,
    onSuccess: async () => queryClient.invalidateQueries({ queryKey: ["environments"] }),
  });
  const remove = useMutation({
    mutationFn: environmentApi.remove,
    onSuccess: async () => queryClient.invalidateQueries({ queryKey: ["environments"] }),
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    save.mutate({ projectId, name, baseUrl });
  }

  return (
    <>
      <PageHeader title="Environments" description="Define base URLs for development, staging, and production targets." />
      <div className="grid gap-6 p-4 sm:p-6 xl:grid-cols-[380px_1fr]">
        <form className="grid gap-4 rounded-md border border-line bg-white p-4 sm:p-5" onSubmit={submit}>
          <Field label="Project">
            <SelectInput value={projectId} onChange={(event) => setProjectId(event.target.value)} required>
              <option value="">Select project</option>
              {projects.data?.map((project) => <option key={project.id} value={project.id}>{project.name}</option>)}
            </SelectInput>
          </Field>
          <Field label="Name">
            <TextInput value={name} onChange={(event) => setName(event.target.value)} required />
          </Field>
          <Field label="Base URL">
            <TextInput value={baseUrl} onChange={(event) => setBaseUrl(event.target.value)} required />
          </Field>
          {save.isError ? <div className="text-sm text-red-700">{errorMessage(save.error)}</div> : null}
          <Button className="w-full sm:w-fit" loading={save.isPending} type="submit">
            <Plus className="h-4 w-4" />
            Add environment
          </Button>
        </form>
        <div className="rounded-md border border-line bg-white">
          <div className="border-b border-line px-4 py-3 text-sm font-semibold">Environment Registry</div>
          {environments.isLoading ? <LoadingState /> : null}
          {environments.isError ? <ErrorState message={errorMessage(environments.error)} /> : null}
          {environments.data && environments.data.length === 0 ? <EmptyState title="No environments configured." /> : null}
          <div className="grid gap-3 p-4">
            {environments.data?.map((environment) => (
              <div key={environment.id} className="grid gap-3 rounded-md border border-line p-3 lg:grid-cols-[1fr_auto] lg:items-center">
                <div className="min-w-0">
                  <div className="font-medium text-ink">{environment.name}</div>
                  <div className="mt-1 break-all text-sm text-slate-500">{environment.baseUrl}</div>
                </div>
                <Button className="w-full sm:w-fit" variant="danger" onClick={() => remove.mutate(environment.id)} title="Delete environment">
                  <Trash2 className="h-4 w-4" />
                </Button>
              </div>
            ))}
          </div>
        </div>
      </div>
    </>
  );
}
