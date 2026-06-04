import { FormEvent, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Plus, Trash2 } from "lucide-react";
import { Button } from "../components/Button";
import { EmptyState, ErrorState, LoadingState } from "../components/DataState";
import { Field, SelectInput, TextArea, TextInput } from "../components/Field";
import { PageHeader } from "../components/PageHeader";
import { environmentApi, projectApi, testApi } from "../lib/api";
import { errorMessage, parseJsonObject } from "../lib/errors";
import type { HttpMethod } from "../lib/types";

export function TestsPage() {
  const queryClient = useQueryClient();
  const projects = useQuery({ queryKey: ["projects"], queryFn: projectApi.list });
  const environments = useQuery({ queryKey: ["environments"], queryFn: environmentApi.list });
  const tests = useQuery({ queryKey: ["tests"], queryFn: testApi.list });
  const [projectId, setProjectId] = useState("");
  const [environmentId, setEnvironmentId] = useState("");
  const [name, setName] = useState("Health check");
  const [method, setMethod] = useState<HttpMethod>("GET");
  const [endpoint, setEndpoint] = useState("/health");
  const [expectedStatusCode, setExpectedStatusCode] = useState(200);
  const [headers, setHeaders] = useState("{}");
  const [requestBody, setRequestBody] = useState("{}");
  const [expectedResponse, setExpectedResponse] = useState("{}");
  const [formError, setFormError] = useState<string | null>(null);

  const save = useMutation({
    mutationFn: testApi.create,
    onSuccess: async () => queryClient.invalidateQueries({ queryKey: ["tests"] }),
  });
  const remove = useMutation({
    mutationFn: testApi.remove,
    onSuccess: async () => queryClient.invalidateQueries({ queryKey: ["tests"] }),
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    setFormError(null);
    try {
      save.mutate({
        projectId,
        environmentId,
        name,
        method,
        endpoint,
        headers: parseJsonObject(headers),
        requestBody: parseJsonObject(requestBody),
        expectedStatusCode,
        expectedResponse: parseJsonObject(expectedResponse),
      });
    } catch (error) {
      setFormError(errorMessage(error));
    }
  }

  return (
    <>
      <PageHeader title="API Tests" description="Store request definitions and expected responses for repeatable checks." />
      <div className="grid gap-6 p-4 sm:p-6 2xl:grid-cols-[minmax(560px,640px)_1fr]">
        <form className="grid content-start gap-5 rounded-md border border-line bg-white p-4 sm:p-5" onSubmit={submit}>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Project">
              <SelectInput value={projectId} onChange={(event) => setProjectId(event.target.value)} required>
                <option value="">Select</option>
                {projects.data?.map((project) => <option key={project.id} value={project.id}>{project.name}</option>)}
              </SelectInput>
            </Field>
            <Field label="Environment">
              <SelectInput value={environmentId} onChange={(event) => setEnvironmentId(event.target.value)} required>
                <option value="">Select</option>
                {environments.data?.map((environment) => <option key={environment.id} value={environment.id}>{environment.name}</option>)}
              </SelectInput>
            </Field>
          </div>

          <Field label="Name">
            <TextInput value={name} onChange={(event) => setName(event.target.value)} required />
          </Field>

          <div className="grid gap-4 lg:grid-cols-[150px_minmax(0,1fr)_150px]">
            <Field label="Method">
              <SelectInput value={method} onChange={(event) => setMethod(event.target.value as HttpMethod)}>
                {["GET", "POST", "PUT", "PATCH", "DELETE"].map((item) => <option key={item}>{item}</option>)}
              </SelectInput>
            </Field>
            <Field label="Endpoint">
              <TextInput value={endpoint} onChange={(event) => setEndpoint(event.target.value)} placeholder="/health" required />
            </Field>
            <Field label="Expected Status">
              <TextInput
                type="number"
                min={100}
                max={599}
                value={expectedStatusCode}
                onChange={(event) => setExpectedStatusCode(Number(event.target.value))}
                required
              />
            </Field>
          </div>

          <div className="grid gap-4 lg:grid-cols-2">
            <Field label="Headers JSON">
              <TextArea className="min-h-32 font-mono" value={headers} onChange={(event) => setHeaders(event.target.value)} />
            </Field>
            <Field label="Request Body JSON">
              <TextArea className="min-h-32 font-mono" value={requestBody} onChange={(event) => setRequestBody(event.target.value)} />
            </Field>
            <Field label="Expected Response JSON" className="lg:col-span-2">
              <TextArea className="min-h-36 font-mono" value={expectedResponse} onChange={(event) => setExpectedResponse(event.target.value)} />
            </Field>
          </div>

          {formError || save.isError ? <div className="text-sm text-red-700">{formError ?? errorMessage(save.error)}</div> : null}
          <Button className="w-full sm:w-fit" loading={save.isPending} type="submit">
            <Plus className="h-4 w-4" />
            Add test
          </Button>
        </form>
        <div className="rounded-md border border-line bg-white">
          <div className="border-b border-line px-4 py-3 text-sm font-semibold">Test Registry</div>
          {tests.isLoading ? <LoadingState /> : null}
          {tests.isError ? <ErrorState message={errorMessage(tests.error)} /> : null}
          {tests.data && tests.data.length === 0 ? <EmptyState title="No API tests saved." /> : null}
          <div className="grid gap-3 p-4">
            {tests.data?.map((test) => (
              <div key={test.id} className="grid gap-3 rounded-md border border-line p-3 lg:grid-cols-[1fr_auto] lg:items-center">
                <div className="min-w-0">
                  <div className="font-medium text-ink">{test.name}</div>
                  <div className="mt-1 break-all text-sm text-slate-500">{test.method} {test.endpoint} - expected {test.expectedStatusCode}</div>
                </div>
                <Button className="w-full sm:w-fit" variant="danger" onClick={() => remove.mutate(test.id)} title="Delete test">
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
