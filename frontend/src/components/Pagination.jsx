export default function Pagination({ page, totalPages, onChange }) {
  if (totalPages <= 1) return null;

  return (
    <div className="mt-6 flex items-center justify-center gap-2">
      <button
        className="btn-secondary"
        disabled={page === 0}
        onClick={() => onChange(page - 1)}
      >
        ← Anterior
      </button>
      <span className="px-3 text-sm text-slate-600">
        Pagina {page + 1} din {totalPages}
      </span>
      <button
        className="btn-secondary"
        disabled={page >= totalPages - 1}
        onClick={() => onChange(page + 1)}
      >
        Următor →
      </button>
    </div>
  );
}
