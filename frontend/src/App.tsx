import { FormEvent, useEffect, useState } from 'react';
import { marked } from 'marked';
import { createSession, downloadFixesPdf, getSkills, registerWorkspace, sendMessage } from './api';
import type { ChatMessage, ChatSession, SkillsResponse } from './types';

const examplePrompt = 'Scan this repository for bugs. Briefly list only the files that contain real issues, with file:line evidence and the minimal fix.';

export function App() {
  const [session, setSession] = useState<ChatSession | null>(null);
  const [skills, setSkills] = useState<SkillsResponse | null>(null);
  const [workspaceRoot, setWorkspaceRoot] = useState('');
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
      const result = await registerWorkspace(workspaceRoot);
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
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to generate the PDF report.');
    } finally {
      setReportBusy(false);
    }
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
          {messages.filter((item) => item.role !== 'tool').map((item, index) => (
            <MessageBubble key={`${item.createdAt}-${index}`} message={item} />
          ))}
          {busy && <div className="thinking">Buggy is scanning the repository...</div>}
        </div>

        <form className="composer" onSubmit={handleSubmit}>
          <textarea value={message} onChange={(event) => setMessage(event.target.value)} rows={4} />
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
  return (
    <article className={`message ${message.role}`}>
      <div className="message-role">{roleLabel(message.role)}</div>
      <div className="message-content" dangerouslySetInnerHTML={{ __html: html }} />
    </article>
  );
}
