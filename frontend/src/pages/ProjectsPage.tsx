import { FormEvent, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Plus, Trash2 } from "lucide-react";
import { Button } from "../components/Button";
import { EmptyState, ErrorState, LoadingState } from "../components/DataState";
import { Field, TextArea, TextInput } from "../components/Field";
import { PageHeader } from "../components/PageHeader";
import { projectApi } from "../lib/api";
import { errorMessage } from "../lib/errors";

export function ProjectsPage() {
  const queryClient = useQueryClient();
  const projects = useQuery({ queryKey: ["projects"], queryFn: projectApi.list });
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");

  const save = useMutation({
    mutationFn: projectApi.create,
    onSuccess: async () => {
      setName("");
      setDescription("");
      await queryClient.invalidateQueries({ queryKey: ["projects"] });
    },
  });

  const remove = useMutation({
    mutationFn: projectApi.remove,
    onSuccess: async () => queryClient.invalidateQueries({ queryKey: ["projects"] }),
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    save.mutate({ name, description });
  }

  return (
    <>
      <PageHeader title="Projects" description="Group API tests by product, service, or business domain." />
      <div className="grid gap-6 p-4 sm:p-6 xl:grid-cols-[380px_1fr]">
        <form className="grid gap-4 rounded-md border border-line bg-white p-4 sm:p-5" onSubmit={submit}>
          <Field label="Name">
            <TextInput value={name} onChange={(event) => setName(event.target.value)} required />
          </Field>
          <Field label="Description">
            <TextArea value={description} onChange={(event) => setDescription(event.target.value)} />
          </Field>
          {save.isError ? <div className="text-sm text-red-700">{errorMessage(save.error)}</div> : null}
          <Button className="w-full sm:w-fit" loading={save.isPending} type="submit">
            <Plus className="h-4 w-4" />
            Add project
          </Button>
        </form>
        <div className="rounded-md border border-line bg-white">
          <div className="border-b border-line px-4 py-3 text-sm font-semibold">Project Registry</div>
          {projects.isLoading ? <LoadingState /> : null}
          {projects.isError ? <ErrorState message={errorMessage(projects.error)} /> : null}
          {projects.data && projects.data.length === 0 ? <EmptyState title="No projects created." /> : null}
          <div className="grid gap-3 p-4">
            {projects.data?.map((project) => (
              <div key={project.id} className="grid gap-3 rounded-md border border-line p-3 lg:grid-cols-[1fr_auto] lg:items-center">
                <div className="min-w-0">
                  <div className="font-medium text-ink">{project.name}</div>
                  <div className="mt-1 break-words text-sm text-slate-500">{project.description || "No description"}</div>
                </div>
                <Button className="w-full sm:w-fit" variant="danger" onClick={() => remove.mutate(project.id)} title="Delete project">
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
