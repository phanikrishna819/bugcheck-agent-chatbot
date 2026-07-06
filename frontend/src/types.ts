export type ChatMessage = {
  role: 'user' | 'assistant' | 'tool';
  content: string;
  createdAt: string;
};

export type ChatSession = {
  id: string;
  workspaceRoot: string | null;
  allowEdits: boolean;
  messages: ChatMessage[];
};

export type SkillsResponse = {
  assetsRoot: string;
  skills: string[];
};
