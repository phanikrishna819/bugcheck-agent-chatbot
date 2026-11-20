import { FormEvent, useEffect, useState } from 'react';
import { marked } from 'marked';
import { createSession, downloadFixesPdf, getSkills, registerWorkspace, sendMessage } from './api';
import type { ChatMessage, ChatSession, SkillsResponse } from './types';

const examplePrompt = 'Scan this repository for bugs. Briefly list only the files that contain real issues, with file:line evidence and the minimal fix.';

export function App() {
  const [session, setSession] = useState<ChatSession | null>(null);
  const [skills, setSkills] = useState<SkillsResponse | null>(null);
  const [workspaceRoot, setWorkspaceRoot] = useState('');
  const [sourceRef, setSourceRef] = useState('');
  const [allowEdits, setAllowEdits] = useState(true);
  const [message, setMessage] = useState(examplePrompt);
  const [busy, setBusy] = useState(false);
  const [reportBusy, setReportBusy] = useState(false);
  const [registering, setRegistering] = useState(false);
  const [registered, setRegistered] = useState(false);
  const [toast, setToast] = useState<{ kind: 'success' | 'error'; text: string } | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getSkills().then(setSkills).catch((err) => setError(err.message));
  }, []);

  useEffect(() => {
    if (!toast) {
      return;
    }
    const timer = setTimeout(() => setToast(null), 4000);
    return () => clearTimeout(timer);
  }, [toast]);

  async function ensureSession() {
    if (session) {
      return session;
    }
    const created = await createSession(workspaceRoot, allowEdits);
    setSession(created);
    return created;
  }

  async function handleRegisterWorkspace() {
    if (!workspaceRoot.trim()) {
      return;
    }
    setError(null);
    setRegistering(true);
    try {
      const source = workspaceRoot.trim();
      const result = await registerWorkspace(source);
      setSourceRef(source);
      setWorkspaceRoot(result.path);
      setRegistered(true);
      if (session && session.workspaceRoot !== result.path) {
        setSession(null);
      }
      setToast({ kind: 'success', text: `Workspace registered: ${result.path}` });
    } catch (err) {
      setRegistered(false);
      setToast({
        kind: 'error',
        text: err instanceof Error ? err.message : 'Unable to register workspace.'
      });
    } finally {
      setRegistering(false);
    }
  }

  async function handleDownloadPdf() {
    if (!workspaceRoot.trim()) {
      setError('Register a workspace first.');
      return;
    }
    const source = sourceRef || workspaceRoot.trim();
    setError(null);
    setReportBusy(true);
    try {
      const current = await ensureSession();
      const blob = await downloadFixesPdf(current.id);
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `buggy-fixes-${current.id.slice(0, 8)}.pdf`;
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);
      resetAfterReport(source, 'success',
        'Report downloaded. Session and temporary clone cleared; register again for another run.');
    } catch (err) {
      resetAfterReport(source, 'error',
        err instanceof Error ? err.message : 'Unable to generate the PDF report.');
    } finally {
      setReportBusy(false);
    }
  }

  function resetAfterReport(source: string, kind: 'success' | 'error', text: string) {
    setSession(null);
    setRegistered(false);
    setWorkspaceRoot(source);
    setToast({ kind, text });
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!message.trim()) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const current = await ensureSession();
      const updated = await sendMessage(current.id, message, workspaceRoot, allowEdits);
      setSession(updated);
      setMessage('');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Agent request failed.');
    } finally {
      setBusy(false);
    }
  }

  const messages = session?.messages ?? [];

  return (
    <div className="shell">
      {toast && (
        <div className={`toast ${toast.kind}`} role="status">
          {toast.text}
        </div>
      )}
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-mark">B</span>
          <div>
            <h1>Buggy</h1>
            <p>AI code review and fix agent</p>
          </div>
        </div>

        <section className="panel">
          <h2>Workspace</h2>
          <label>
            Repository path or git URL
            <input
              value={workspaceRoot}
              onChange={(event) => {
                setWorkspaceRoot(event.target.value);
                setRegistered(false);
              }}
              placeholder="https://github.com/org/repo"
            />
          </label>
          <button
            type="button"
            onClick={handleRegisterWorkspace}
            disabled={!workspaceRoot.trim() || busy || reportBusy || registering || registered}
          >
            {registering ? 'Registering...' : registered ? 'Registered' : 'Register Workspace'}
          </button>
          <button type="button" onClick={handleDownloadPdf} disabled={!workspaceRoot.trim() || busy || reportBusy}>
            {reportBusy ? 'Generating PDF...' : 'Download fixes PDF'}
          </button>
          <label className="toggle">
            <input type="checkbox" checked={allowEdits} onChange={(event) => setAllowEdits(event.target.checked)} />
            Allow agent edits
          </label>
        </section>

        <section className="panel skills">
          <h2>Loaded Skills</h2>
          <p className="muted">{skills?.assetsRoot ?? 'Loading assets...'}</p>
          <ul>
            {(skills?.skills ?? []).map((skill) => (
              <li key={skill}>{skill}</li>
            ))}
          </ul>
        </section>
      </aside>

      <main className="chat">
        <header className="chat-header">
          <div>
            <h2>Buggy Chat</h2>
            <p>{session ? `Session ${session.id.slice(0, 8)}` : 'Start by registering a workspace, then send a message.'}</p>
          </div>
          <span className={allowEdits ? 'status edits' : 'status readonly'}>{allowEdits ? 'Edits enabled' : 'Read only'}</span>
        </header>

        {error && <div className="error">{error}</div>}

        <div className="messages">
          {messages.length === 0 && !busy && (
            <div className="empty">
              <span className="empty-logo">
                <BuggyLogo size={64} />
              </span>
              <h3>Hi, I'm Buggy</h3>
              <p>Register a repository above, then ask me to scan it. I'll report only the files that contain real bugs.</p>
            </div>
          )}
          {messages.filter((item) => item.role !== 'tool').map((item, index) => (
            <MessageBubble key={`${item.createdAt}-${index}`} message={item} />
          ))}
          {busy && <div className="thinking">Buggy is scanning the repository...</div>}
        </div>

        <form className="composer" onSubmit={handleSubmit}>
          <textarea value={message} onChange={(event) => setMessage(event.target.value)} rows={2} />
          <button type="submit" disabled={busy || !message.trim()}>
            {busy ? 'Running...' : 'Send'}
          </button>
        </form>
      </main>
    </div>
  );
}

function roleLabel(role: ChatMessage['role']) {
  return role === 'user' ? 'You' : 'Buggy';
}

function MessageBubble({ message }: { message: ChatMessage }) {
  const html = marked.parse(message.content, { async: false }) as string;
  const isUser = message.role === 'user';
  return (
    <article className={`message ${message.role}`}>
      <div className="message-head">
        {isUser ? (
          <span className="avatar user" aria-hidden="true">Y</span>
        ) : (
          <span className="avatar" aria-hidden="true">
            <BuggyLogo size={30} />
          </span>
        )}
        <div className="message-role">{roleLabel(message.role)}</div>
      </div>
      <div className="message-content" dangerouslySetInnerHTML={{ __html: html }} />
    </article>
  );
}

function BuggyLogo({ size = 30 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 48 48" role="img" aria-label="Buggy">
      <defs>
        <linearGradient id="buggyLogoGrad" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="#67e8f9" />
          <stop offset="1" stopColor="#2563eb" />
        </linearGradient>
      </defs>
      <rect width="48" height="48" rx="14" fill="url(#buggyLogoGrad)" />
      <g stroke="#04111f" strokeWidth="2.4" strokeLinecap="round" fill="none">
        <path d="M18 15 C15 11 12.5 10 10 10" />
        <path d="M30 15 C33 11 35.5 10 38 10" />
        <path d="M13.5 25 H21" />
        <path d="M13.5 32 H21" />
        <path d="M27 25 H34.5" />
        <path d="M27 32 H34.5" />
      </g>
      <ellipse cx="24" cy="28" rx="8.5" ry="11" fill="#04111f" />
      <circle cx="21" cy="24" r="1.7" fill="#67e8f9" />
      <circle cx="27" cy="24" r="1.7" fill="#67e8f9" />
      <path d="M24 18 V38" stroke="#67e8f9" strokeWidth="1.3" opacity="0.55" />
    </svg>
  );
}
