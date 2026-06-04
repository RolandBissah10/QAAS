import { FormEvent, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Plus, Trash2 } from "lucide-react";
import { Button } from "../components/Button";
import { EmptyState, ErrorState, LoadingState } from "../components/DataState";
import { Field, SelectInput, TextArea, TextInput } from "../components/Field";
import { PageHeader } from "../components/PageHeader";
import { collectionApi, projectApi, testApi } from "../lib/api";
import { errorMessage } from "../lib/errors";

export function CollectionsPage() {
  const queryClient = useQueryClient();
  const projects = useQuery({ queryKey: ["projects"], queryFn: projectApi.list });
  const tests = useQuery({ queryKey: ["tests"], queryFn: testApi.list });
  const collections = useQuery({ queryKey: ["collections"], queryFn: collectionApi.list });
  const [projectId, setProjectId] = useState("");
  const [name, setName] = useState("Authentication Suite");
  const [description, setDescription] = useState("");
  const [testIds, setTestIds] = useState<string[]>([]);

  const save = useMutation({
    mutationFn: collectionApi.create,
    onSuccess: async () => queryClient.invalidateQueries({ queryKey: ["collections"] }),
  });
  const remove = useMutation({
    mutationFn: collectionApi.remove,
    onSuccess: async () => queryClient.invalidateQueries({ queryKey: ["collections"] }),
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    save.mutate({ projectId, name, description, testIds });
  }

  return (
    <>
      <PageHeader title="Collections" description="Bundle tests into executable suites for product workflows." />
      <div className="grid gap-6 p-4 sm:p-6 xl:grid-cols-[400px_1fr]">
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
          <Field label="Description">
            <TextArea value={description} onChange={(event) => setDescription(event.target.value)} />
          </Field>
          <Field label="Tests">
            <select
              className="min-h-32 w-full min-w-0 rounded-md border border-line bg-white px-3 py-2 text-sm outline-none focus:border-brand focus:ring-2 focus:ring-teal-100"
              multiple
              value={testIds}
              onChange={(event) => setTestIds(Array.from(event.target.selectedOptions).map((option) => option.value))}
            >
              {tests.data?.map((test) => <option key={test.id} value={test.id}>{test.name}</option>)}
            </select>
          </Field>
          {save.isError ? <div className="text-sm text-red-700">{errorMessage(save.error)}</div> : null}
          <Button className="w-full sm:w-fit" loading={save.isPending} type="submit">
            <Plus className="h-4 w-4" />
            Add collection
          </Button>
        </form>
        <div className="rounded-md border border-line bg-white">
          <div className="border-b border-line px-4 py-3 text-sm font-semibold">Collection Registry</div>
          {collections.isLoading ? <LoadingState /> : null}
          {collections.isError ? <ErrorState message={errorMessage(collections.error)} /> : null}
          {collections.data && collections.data.length === 0 ? <EmptyState title="No collections created." /> : null}
          <div className="grid gap-3 p-4">
            {collections.data?.map((collection) => (
              <div key={collection.id} className="grid gap-3 rounded-md border border-line p-3 lg:grid-cols-[1fr_auto] lg:items-center">
                <div className="min-w-0">
                  <div className="font-medium text-ink">{collection.name}</div>
                  <div className="mt-1 break-words text-sm text-slate-500">{collection.testIds.length} tests - {collection.description || "No description"}</div>
                </div>
                <Button className="w-full sm:w-fit" variant="danger" onClick={() => remove.mutate(collection.id)} title="Delete collection">
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
