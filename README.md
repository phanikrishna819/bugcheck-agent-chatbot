# Bugcheck Agent Chatbot

Standalone ChatGPT-style bugcheck agent UI with a Spring Boot backend and React frontend.

This project does not modify the existing construction backend or frontend apps. It loads the uploaded bugcheck agent assets from OpenCode's config folder by default:

```text
C:\Users\phani\.config\opencode\bugcheck.agent.md
C:\Users\phani\.config\opencode\skills\*.md
```

## Prerequisites

- Java 17+
- Maven 3.9+
- Node.js 18+
- OpenAI API key

## Backend

```powershell
$env:OPENAI_API_KEY="your-api-key"
cd backend
mvn spring-boot:run
```

Backend runs on `http://localhost:8088`.

Optional configuration:

```powershell
$env:OPENAI_MODEL="gpt-4.1-mini"
$env:AGENT_ASSETS_DIR="C:/Users/phani/.config/opencode"
```

## Frontend

```powershell
cd frontend
npm install
npm run dev
```

Frontend runs on `http://localhost:5173`.

## Safety Model

- A workspace must be registered before the agent can inspect it.
- File edits are allowed only when the chat session has `allowEdits=true`.
- File writes are constrained to the selected workspace root.
- Dangerous git and shell operations are blocked.
- The uploaded bugcheck prompt and skills are used as the agent's operating instructions.
