import { FormEvent, useEffect, useState } from 'react';
import { marked } from 'marked';
import { createSession, getSkills, registerWorkspace, sendMessage } from './api';
import type { ChatMessage, ChatSession, SkillsResponse } from './types';

const examplePrompt = 'Run bugcheck on the current diff. Use the uploaded bugcheck agent and skills. Apply minimal fixes when needed.';

export function App() {
  const [session, setSession] = useState<ChatSession | null>(null);
  const [skills, setSkills] = useState<SkillsResponse | null>(null);
  const [workspaceRoot, setWorkspaceRoot] = useState('');
  const [allowEdits, setAllowEdits] = useState(true);
  const [message, setMessage] = useState(examplePrompt);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getSkills().then(setSkills).catch((err) => setError(err.message));
  }, []);

  async function ensureSession() {
    if (session) {
      return session;
    }
    const created = await createSession(workspaceRoot, allowEdits);
    setSession(created);
    return created;
  }

  async function handleRegisterWorkspace() {
    setError(null);
    try {
      const result = await registerWorkspace(workspaceRoot);
      setWorkspaceRoot(result.path);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to register workspace.');
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
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-mark">B</span>
          <div>
            <h1>Bugcheck Agent</h1>
            <p>OpenAI-powered code review and fix harness</p>
          </div>
        </div>

        <section className="panel">
          <h2>Workspace</h2>
          <label>
            Repository path
            <input
              value={workspaceRoot}
              onChange={(event) => setWorkspaceRoot(event.target.value)}
              placeholder="C:\\Users\\phani\\some-project"
            />
          </label>
          <button type="button" onClick={handleRegisterWorkspace} disabled={!workspaceRoot.trim() || busy}>
            Register Workspace
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
            <h2>Agent Chat</h2>
            <p>{session ? `Session ${session.id.slice(0, 8)}` : 'Start by registering a workspace, then send a message.'}</p>
          </div>
          <span className={allowEdits ? 'status edits' : 'status readonly'}>{allowEdits ? 'Edits enabled' : 'Read only'}</span>
        </header>

        {error && <div className="error">{error}</div>}

        <div className="messages">
          {messages.map((item, index) => (
            <MessageBubble key={`${item.createdAt}-${index}`} message={item} />
          ))}
          {busy && <div className="thinking">Agent is working through tools and skills...</div>}
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

function MessageBubble({ message }: { message: ChatMessage }) {
  const html = marked.parse(message.content, { async: false }) as string;
  return (
    <article className={`message ${message.role}`}>
      <div className="message-role">{message.role}</div>
      <div className="message-content" dangerouslySetInnerHTML={{ __html: html }} />
    </article>
  );
}
