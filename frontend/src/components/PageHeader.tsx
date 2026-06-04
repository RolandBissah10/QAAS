export function PageHeader({
  title,
  description,
  actions,
}: {
  title: string;
  description?: string;
  actions?: React.ReactNode;
}) {
  return (
    <div className="flex flex-col gap-3 border-b border-line bg-white px-4 py-4 sm:px-6 sm:py-5 md:flex-row md:items-center md:justify-between">
      <div className="min-w-0">
        <h1 className="text-lg font-semibold text-ink sm:text-xl">{title}</h1>
        {description ? <p className="mt-1 text-sm text-slate-500">{description}</p> : null}
      </div>
      {actions ? <div className="flex flex-wrap items-center gap-2">{actions}</div> : null}
    </div>
  );
}
