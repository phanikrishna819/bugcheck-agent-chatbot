import type { ChatSession, SkillsResponse } from './types';

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? '';

async function request<T>(url: string, options?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE}${url}`, {
    headers: { 'Content-Type': 'application/json', ...(options?.headers ?? {}) },
    ...options
  });
  if (!response.ok) {
    const error = await response.json().catch(() => ({ error: response.statusText }));
    throw new Error(error.error ?? response.statusText);
  }
  return response.json();
}

export function createSession(workspaceRoot: string, allowEdits: boolean) {
  return request<ChatSession>('/api/chat/sessions', {
    method: 'POST',
    body: JSON.stringify({ workspaceRoot, allowEdits })
  });
}

export function sendMessage(sessionId: string, content: string, workspaceRoot: string, allowEdits: boolean) {
  return request<ChatSession>(`/api/chat/sessions/${sessionId}/messages`, {
    method: 'POST',
    body: JSON.stringify({ content, workspaceRoot, allowEdits })
  });
}

export function getSkills() {
  return request<SkillsResponse>('/api/skills');
}

export function registerWorkspace(path: string) {
  return request<{ path: string }>('/api/workspaces', {
    method: 'POST',
    body: JSON.stringify({ path })
  });
}
