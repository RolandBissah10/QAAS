import { useQuery } from "@tanstack/react-query";
import { EmptyState, ErrorState, LoadingState } from "../components/DataState";
import { PageHeader } from "../components/PageHeader";
import { StatusPill } from "../components/StatusPill";
import { resultApi } from "../lib/api";
import { errorMessage } from "../lib/errors";

export function ResultsPage() {
  const results = useQuery({ queryKey: ["results"], queryFn: resultApi.list });

  return (
    <>
      <PageHeader title="Results" description="Review stored execution output and timing." />
      <div className="p-4 sm:p-6">
        <div className="overflow-hidden rounded-md border border-line bg-white">
          {results.isLoading ? <LoadingState /> : null}
          {results.isError ? <ErrorState message={errorMessage(results.error)} /> : null}
          {results.data && results.data.length === 0 ? <EmptyState title="No execution results yet." /> : null}
          {results.data && results.data.length > 0 ? (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[760px] border-collapse text-left text-sm">
                <thead className="bg-panel text-xs uppercase text-slate-500">
                  <tr>
                    <th className="px-4 py-3">Status</th>
                    <th className="px-4 py-3">Response Time</th>
                    <th className="px-4 py-3">Executed</th>
                    <th className="px-4 py-3">Response Body</th>
                  </tr>
                </thead>
                <tbody>
                  {results.data.map((result) => (
                    <tr key={result.id} className="border-t border-line align-top">
                      <td className="px-4 py-3"><StatusPill status={result.status} /></td>
                      <td className="px-4 py-3">{result.responseTime} ms</td>
                      <td className="px-4 py-3 text-slate-500">{new Date(result.executedAt).toLocaleString()}</td>
                      <td className="px-4 py-3">
                        <pre className="max-h-32 max-w-[70vw] overflow-auto rounded-md bg-slate-950 p-3 text-xs text-slate-100 md:max-w-none">
                          {JSON.stringify(result.responseBody, null, 2)}
                        </pre>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : null}
        </div>
      </div>
    </>
  );
}
