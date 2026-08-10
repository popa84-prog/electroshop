import { useState } from 'react';
import { Link } from 'react-router-dom';
import { DashCard, EmptyState, XX_SERIES_AMBER } from '../../../components/xxii';
import adminToolsService from '../../../api/adminToolsService';
import usePanelData from '../../../hooks/usePanelData';

/**
 * Notes, reminders, tasks and shortcuts. Task 20.
 *
 * ## Every write goes through the server and then re-reads
 *
 * Unlike the dashboard layout, these are records rather than a preference: a
 * note that appears locally and fails to save is a note somebody believes they
 * wrote. So each action awaits the server and refreshes, and a failure is
 * visible rather than silently local.
 *
 * ## Completed tasks stay visible
 *
 * A task manager that erases finished work gives no sense of progress, and an
 * operator ticking items off wants to see the list they have cleared. Done items
 * sort below the open ones and are struck through.
 *
 * ## Shortcuts are derived from what this person actually does
 *
 * The server builds them from the audit log rather than from a fixed menu, so
 * somebody who lives in orders and somebody who lives in the catalogue get
 * different buttons. A new account with no history gets a sensible default set
 * instead of an empty box.
 */
export default function AdminToolsPanel({ compact, title, dragHandle, onHide }) {
  const [tab, setTab] = useState('tasks');
  const [draft, setDraft] = useState('');
  const [dueAt, setDueAt] = useState('');
  const [busy, setBusy] = useState(false);
  const [failure, setFailure] = useState(null);

  const { data, loading, error, reload } = usePanelData(
    (signal) => adminToolsService.list(signal),
    []
  );

  const tabs = [
    { key: 'tasks', label: 'Task-uri', kind: 'TASK', count: data?.openTaskCount },
    { key: 'reminders', label: 'Remindere', kind: 'REMINDER', count: data?.dueReminderCount },
    { key: 'notes', label: 'Notițe', kind: 'NOTE', count: data?.notes?.length },
  ];

  const active = tabs.find((t) => t.key === tab) || tabs[0];
  const items = data?.[tab] || [];

  const run = async (action) => {
    setBusy(true);
    setFailure(null);
    try {
      await action();
      reload();
    } catch (err) {
      setFailure(err?.response?.data?.message || 'Operația nu a putut fi salvată.');
    } finally {
      setBusy(false);
    }
  };

  const submit = (event) => {
    event.preventDefault();
    const content = draft.trim();
    if (!content) return;

    run(async () => {
      await adminToolsService.create({
        kind: active.kind,
        content,
        // A reminder without a due date is a note. The field only appears for
        // reminders, and an empty value is sent as null rather than as an
        // invalid date string.
        dueAt: active.kind === 'REMINDER' && dueAt ? new Date(dueAt).toISOString() : null,
        priority: 2,
      });
      setDraft('');
      setDueAt('');
    });
  };

  return (
    <DashCard
      title={title}
      subtitle="Notițe, remindere și task-uri personale"
      compact={compact}
      loading={loading}
      error={error}
      onRetry={reload}
      dragHandle={dragHandle}
      onHide={onHide}
      accent={XX_SERIES_AMBER}
      footer={
        data?.shortcuts?.length ? (
          <div>
            <p className="mb-1.5 text-[10px] uppercase tracking-[0.12em]
              text-[color:var(--xx-ink-dim)]">
              Acțiuni frecvente
            </p>
            <div className="flex flex-wrap gap-1.5">
              {data.shortcuts.map((shortcut) => (
                <Link
                  key={shortcut.key}
                  to={shortcut.linkTo}
                  className="rounded-lg border border-[rgba(255,255,255,0.12)] px-2 py-1
                    text-[11px] text-[color:var(--xx-ink-dim)] transition-colors duration-xx
                    hover:border-[color:var(--xx-cyan)] hover:text-[color:var(--xx-cyan)]"
                  title={
                    shortcut.fromUsage
                      ? `Ai folosit această secțiune de ${shortcut.useCount} ori recent`
                      : 'Scurtătură implicită'
                  }
                >
                  {shortcut.label}
                </Link>
              ))}
            </div>
          </div>
        ) : null
      }
    >
      <div className="mb-3 flex flex-wrap gap-1.5">
        {tabs.map((item) => (
          <button
            key={item.key}
            type="button"
            onClick={() => setTab(item.key)}
            aria-pressed={tab === item.key}
            className={`inline-flex items-center gap-1.5 rounded-lg border px-2.5 py-1
              text-[11px] font-medium transition-all duration-xx ${
                tab === item.key
                  ? 'border-[rgba(34,232,245,0.5)] bg-[rgba(34,232,245,0.12)] text-[color:var(--xx-cyan)]'
                  : 'border-[rgba(255,255,255,0.12)] text-[color:var(--xx-ink-dim)] hover:text-[color:var(--xx-ink)]'
              }`}
          >
            {item.label}
            {item.count ? <span className="tabular-nums opacity-70">{item.count}</span> : null}
          </button>
        ))}
      </div>

      <form onSubmit={submit} className="mb-3 flex flex-wrap gap-1.5">
        <input
          type="text"
          value={draft}
          onChange={(event) => setDraft(event.target.value)}
          placeholder={
            active.kind === 'TASK'
              ? 'Adaugă un task…'
              : active.kind === 'REMINDER'
              ? 'Adaugă un reminder…'
              : 'Adaugă o notiță…'
          }
          maxLength={data?.limits?.maxContentLength ?? 4000}
          className="min-w-0 flex-1 rounded-lg border border-[rgba(255,255,255,0.12)]
            bg-[rgba(255,255,255,0.04)] px-3 py-1.5 text-xs text-[color:var(--xx-ink)]
            placeholder:text-[color:var(--xx-ink-dim)] focus:border-[color:var(--xx-cyan)]
            focus:outline-none"
        />
        {active.kind === 'REMINDER' ? (
          <input
            type="datetime-local"
            value={dueAt}
            onChange={(event) => setDueAt(event.target.value)}
            aria-label="Când"
            className="rounded-lg border border-[rgba(255,255,255,0.12)]
              bg-[rgba(255,255,255,0.04)] px-2 py-1.5 text-xs text-[color:var(--xx-ink)]
              focus:border-[color:var(--xx-cyan)] focus:outline-none"
          />
        ) : null}
        <button
          type="submit"
          disabled={busy || !draft.trim()}
          className="rounded-lg border border-[rgba(34,232,245,0.4)] bg-[rgba(34,232,245,0.1)]
            px-3 py-1.5 text-xs font-medium text-[color:var(--xx-cyan)] transition-colors
            duration-xx disabled:opacity-40"
        >
          Adaugă
        </button>
      </form>

      {failure ? (
        <p className="mb-2 rounded-lg border border-[rgba(184,47,60,0.4)]
          bg-[rgba(184,47,60,0.08)] px-2.5 py-1.5 text-[11px] text-[#ff8a97]">
          {failure}
        </p>
      ) : null}

      {!loading && items.length === 0 ? (
        <EmptyState
          reason="empty"
          title={`Nicio intrare de tip ${active.label.toLowerCase()}`}
          compact
        />
      ) : (
        <ul className="xx-no-scrollbar max-h-56 space-y-1 overflow-y-auto pr-1">
          {items.map((item) => (
            <li
              key={item.id}
              className={`flex items-start gap-2 rounded-lg px-2 py-1.5 transition-colors
                duration-xx hover:bg-[rgba(255,255,255,0.035)] ${
                  item.overdue ? 'border border-[rgba(184,47,60,0.35)]' : ''
                }`}
            >
              {active.kind !== 'NOTE' ? (
                <input
                  type="checkbox"
                  checked={item.done}
                  disabled={busy}
                  onChange={() => run(() => adminToolsService.toggle(item.id))}
                  aria-label={item.done ? 'Marchează ca nefinalizat' : 'Marchează ca finalizat'}
                  className="mt-0.5 h-3.5 w-3.5 shrink-0 rounded border-[rgba(255,255,255,0.3)]
                    bg-transparent accent-[color:var(--xx-cyan)]"
                />
              ) : null}

              <span className="min-w-0 flex-1">
                <span
                  className={`block break-words text-xs ${
                    item.done
                      ? 'text-[color:var(--xx-ink-dim)] line-through'
                      : 'text-[color:var(--xx-ink)]'
                  }`}
                >
                  {item.content}
                </span>
                {item.dueAt ? (
                  <span className={`block text-[10px] ${
                    item.overdue ? 'text-[#ff8a97]' : 'text-[color:var(--xx-ink-dim)]'
                  }`}>
                    {item.overdue ? 'Depășit · ' : ''}
                    {new Date(item.dueAt).toLocaleString('ro-RO', {
                      day: '2-digit',
                      month: '2-digit',
                      hour: '2-digit',
                      minute: '2-digit',
                    })}
                  </span>
                ) : null}
              </span>

              <button
                type="button"
                disabled={busy}
                onClick={() => run(() => adminToolsService.remove(item.id))}
                aria-label="Șterge"
                className="shrink-0 text-[color:var(--xx-ink-dim)] transition-colors duration-xx
                  hover:text-[#ff8a97] disabled:opacity-40"
              >
                ✕
              </button>
            </li>
          ))}
        </ul>
      )}
    </DashCard>
  );
}
